package com.bakery.erp.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bakery.erp.domain.AuthRepository
import com.bakery.erp.domain.BranchRepository
import com.bakery.erp.domain.CashSessionRepository
import com.bakery.erp.domain.DispatchRepository
import com.bakery.erp.domain.FixedAssetRepository
import com.bakery.erp.domain.IngredientRepository
import com.bakery.erp.domain.ProductionRepository
import com.bakery.erp.domain.PurchaseRepository
import com.bakery.erp.domain.SaleRepository
import com.bakery.erp.domain.report.AlertSeverity
import com.bakery.erp.domain.report.AlertTarget
import com.bakery.erp.domain.report.DailySalesPoint
import com.bakery.erp.domain.report.HomeAlert
import com.bakery.erp.model.AssetStatus
import com.bakery.erp.model.CashSessionStatus
import com.bakery.erp.model.DispatchStatus
import com.bakery.erp.model.Permission
import com.bakery.erp.model.ProductionLotStatus
import com.bakery.erp.model.ProductionOrderStatus
import com.bakery.erp.model.PurchaseDisplayStatus
import com.bakery.erp.model.User
import com.bakery.erp.model.UserRole
import com.bakery.erp.model.canAccessBranch
import com.bakery.erp.model.balanceDue
import com.bakery.erp.model.expiringBefore
import com.bakery.erp.model.daysUntilDue
import com.bakery.erp.model.displayStatus
import com.bakery.erp.model.hasPermission
import com.bakery.erp.presentation.purchases.formatPurchaseDate
import com.bakery.erp.util.fmtMoney
import kotlin.time.Clock
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

data class HomeUiState(
    val isLoading: Boolean = false,
    val isAggregating: Boolean = false,
    val error: String? = null,
    val salesTodayTotal: Double = 0.0,
    val salesTodayCount: Int = 0,
    val lotsTodayCount: Int = 0,
    val lowStockCount: Int = 0,
    val maintenanceDueCount: Int = 0,
    val weeklySales: List<DailySalesPoint> = emptyList(),
    val alerts: List<HomeAlert> = emptyList()
)

class HomeViewModel(
    private val authRepository: AuthRepository,
    private val saleRepository: SaleRepository,
    private val productionRepository: ProductionRepository,
    private val branchRepository: BranchRepository,
    private val ingredientRepository: IngredientRepository,
    private val fixedAssetRepository: FixedAssetRepository,
    private val dispatchRepository: DispatchRepository,
    private val cashSessionRepository: CashSessionRepository,
    private val purchaseRepository: PurchaseRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var lastLoadedUserId: String? = null

    /** Llamado por HomeScreen cuando obtiene el [User] desde AuthViewModel. */
    fun ensureLoadedFor(user: User) {
        if (lastLoadedUserId == user.id) return
        lastLoadedUserId = user.id
        refresh(user)
    }

    fun refresh(user: User) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching { loadAll(user) }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }

    private suspend fun loadAll(user: User) = coroutineScope {
        val companyId = user.orgId
        val tz = TimeZone.currentSystemDefault()
        val today: LocalDate = Clock.System.now().toLocalDateTime(tz).date
        val todayStart = today.atStartOfDayIn(tz).toEpochMilliseconds()
        val tomorrowStart = today.plus(DatePeriod(days = 1)).atStartOfDayIn(tz).toEpochMilliseconds()
        val weekAgoStart = today.minus(DatePeriod(days = 6)).atStartOfDayIn(tz).toEpochMilliseconds()

        // Sucursales accesibles para este usuario.
        val branchesAsync = async { branchRepository.getBranches(companyId) }

        // Lanzamos en paralelo todo lo que no depende de las sucursales accesibles.
        val salesTodayAsync = async {
            saleRepository.aggregateSalesInRange(
                companyId = companyId,
                branchIds = null, // filtramos después por accesibles
                fromMillis = todayStart,
                untilMillis = tomorrowStart
            )
        }
        val weeklySalesAsync = async {
            saleRepository.aggregateSalesInRange(
                companyId = companyId,
                branchIds = null,
                fromMillis = weekAgoStart,
                untilMillis = tomorrowStart
            )
        }
        val lotsTodayAsync = async {
            productionRepository.aggregateProductionInRange(
                companyId = companyId,
                producingBranchIds = null,
                fromMillis = todayStart,
                untilMillis = tomorrowStart
            )
        }

        // Datos para alertas — cada uno solo se carga si el user tiene el permiso correspondiente.
        val assetsAsync = async {
            if (user.hasPermission(Permission.VIEW_ASSETS)) fixedAssetRepository.getAssets(companyId)
            else emptyList()
        }
        val ingredientsAsync = async {
            if (user.hasPermission(Permission.VIEW_INGREDIENTS)) ingredientRepository.getIngredients(companyId)
            else emptyList()
        }
        val ordersAsync = async {
            if (user.hasPermission(Permission.MANAGE_PRODUCTION_LOTS))
                productionRepository.observeOrders(companyId).first()
            else emptyList()
        }
        val lotsAsync = async {
            if (user.hasPermission(Permission.MANAGE_PRODUCTION_LOTS)) {
                productionRepository.getLotsForBranchInRange(
                    companyId = companyId,
                    producingBranchId = "",
                    fromMillis = todayStart - 7L * 24 * 60 * 60 * 1000, // 7 días atrás
                    untilMillis = tomorrowStart
                )
            } else emptyList()
        }
        val dispatchAsync = async {
            if (user.hasPermission(Permission.MANAGE_DELIVERY_RUNS) ||
                user.hasPermission(Permission.DISPATCH_PACK)
            ) {
                dispatchRepository.getNotesInRange(
                    companyId = companyId,
                    fromMillis = todayStart - 3L * 24 * 60 * 60 * 1000,
                    untilMillis = tomorrowStart + 24L * 60 * 60 * 1000
                )
            } else emptyList()
        }
        // Facturas de proveedor: se lee la colección completa porque el vencimiento no está
        // acotado al período — una factura de hace tres meses sin pagar es justo la que urge.
        val purchasesAsync = async {
            if (user.hasPermission(Permission.MANAGE_PURCHASE_PAYMENTS) ||
                user.hasPermission(Permission.RECEIVE_STOCK)
            ) runCatching { purchaseRepository.getPurchases(companyId) }.getOrDefault(emptyList())
            else emptyList()
        }
        val openCashAsync = async {
            if (user.hasPermission(Permission.SPOT_CHECK_CASH) ||
                user.hasPermission(Permission.MANAGE_DAILY_CASH_CLOSE)
            ) cashSessionRepository.observeOpenSessions(companyId).first()
            else emptyList()
        }

        // Esperamos las primeras
        val branches = branchesAsync.await()
        val accessibleBranchIds = branches.filter { user.canAccessBranch(it.id) }.map { it.id }
        val branchById = branches.associateBy { it.id }

        val salesToday = salesTodayAsync.await()
        val weeklySales = weeklySalesAsync.await()
        val lotsToday = lotsTodayAsync.await()

        // Filtrar ventas de hoy por sucursales accesibles (las agregaciones nos dan byBranch).
        val accessibleSet = accessibleBranchIds.toSet()
        val accessibleSalesToday = salesToday.byBranch.filter { it.branchId in accessibleSet }
        val salesTodayTotal = accessibleSalesToday.sumOf { it.total }
        val salesTodayCount = accessibleSalesToday.sumOf { it.transactionCount }

        val accessibleLotsToday = run {
            // ProductionSummary no tiene byBranch; pero los lotes de hoy en sucursales accesibles
            // los podemos contar desde lotsAsync (los cargamos abajo). Para Today usamos la cifra total
            // si el user tiene VIEW_ALL_BRANCHES; si no, recargamos restringido.
            if (user.hasPermission(Permission.VIEW_ALL_BRANCHES) || user.role == UserRole.OWNER) {
                lotsToday.lotsCount
            } else {
                productionRepository.aggregateProductionInRange(
                    companyId = companyId,
                    producingBranchIds = accessibleBranchIds,
                    fromMillis = todayStart,
                    untilMillis = tomorrowStart
                ).lotsCount
            }
        }

        // Filtrar weeklySales también por sucursales accesibles (recalcular por día limitando branch).
        val weeklyAccessible = if (
            user.hasPermission(Permission.VIEW_ALL_BRANCHES) || user.role == UserRole.OWNER
        ) {
            weeklySales.byDay
        } else {
            saleRepository.aggregateSalesInRange(
                companyId = companyId,
                branchIds = accessibleBranchIds,
                fromMillis = weekAgoStart,
                untilMillis = tomorrowStart
            ).byDay
        }
        // Rellenar días sin ventas con 0 para que la barra se renderice consistente.
        val filledWeekly = fillMissingDays(weeklyAccessible, today, tz)

        // Construir alertas
        val alerts = mutableListOf<HomeAlert>()
        val now = Clock.System.now().toEpochMilliseconds()

        // 1. Mantenimiento próximo
        val assets = assetsAsync.await().filter {
            it.active && it.status == AssetStatus.ACTIVE && it.branchId in accessibleSet
        }
        val maintenanceSoon = assets.filter { a ->
            val due = a.nextMaintenanceDate ?: return@filter false
            due in now..(now + 7L * 24 * 60 * 60 * 1000)
        }
        if (maintenanceSoon.isNotEmpty()) {
            alerts += HomeAlert(
                id = "maint-${maintenanceSoon.size}",
                severity = AlertSeverity.WARNING,
                target = AlertTarget.UPCOMING_MAINTENANCE,
                title = "${maintenanceSoon.size} mantenimiento(s) próximo(s)",
                message = maintenanceSoon.take(3).joinToString(", ") { it.name },
                actionLabel = "Ver activos",
                createdAt = now
            )
        }

        // 2. Stock bajo de ingredientes
        val ingredients = ingredientsAsync.await().filter { it.active }
        val lowStockEntries = mutableListOf<Pair<String, String>>() // (ingredientName, branchName)
        for (branch in branches.filter { it.id in accessibleSet }) {
            val stockMap = runCatching {
                ingredientRepository.getBranchStocksForBranch(companyId, branch.id)
            }.getOrDefault(emptyMap())
            ingredients.forEach { ing ->
                val alert = ing.stockAlert ?: return@forEach
                if (alert <= 0) return@forEach
                val current = stockMap[ing.id] ?: 0.0
                if (current < alert) lowStockEntries += ing.name to branch.name
            }
        }
        if (lowStockEntries.isNotEmpty()) {
            alerts += HomeAlert(
                id = "low-stock-${lowStockEntries.size}",
                severity = if (lowStockEntries.size >= 3) AlertSeverity.CRITICAL else AlertSeverity.WARNING,
                target = AlertTarget.LOW_STOCK_INGREDIENTS,
                title = "${lowStockEntries.size} insumo(s) en stock bajo",
                message = lowStockEntries.take(3).joinToString(", ") { "${it.first} (${it.second})" },
                actionLabel = "Ver insumos",
                createdAt = now
            )
        }

        // 3. Despachos sin acuse (IN_TRANSIT > 12h)
        val dispatchNotes = dispatchAsync.await()
        val staleInTransit = dispatchNotes.filter {
            it.status == DispatchStatus.IN_TRANSIT &&
                it.originBranchId in accessibleSet &&
                (it.packedAt ?: it.dispatchedAt ?: now).let { ts -> now - ts > 12L * 60 * 60 * 1000 }
        }
        if (staleInTransit.isNotEmpty()) {
            alerts += HomeAlert(
                id = "in-transit-${staleInTransit.size}",
                severity = AlertSeverity.CRITICAL,
                target = AlertTarget.IN_TRANSIT_DISPATCH,
                title = "${staleInTransit.size} despacho(s) sin acuse",
                message = staleInTransit.take(2).joinToString(", ") {
                    "${it.destinationBranchName}"
                },
                actionLabel = "Ver despachos",
                createdAt = now
            )
        }

        // 4. Despachos con precio sin definir (DRAFT/READY con priceSource NONE)
        val priceMissing = dispatchNotes.filter {
            it.hasPriceMissing &&
                (it.status == DispatchStatus.DRAFT || it.status == DispatchStatus.READY) &&
                it.originBranchId in accessibleSet
        }
        if (priceMissing.isNotEmpty() && user.hasPermission(Permission.MANAGE_PRICE_LISTS)) {
            alerts += HomeAlert(
                id = "price-missing-${priceMissing.size}",
                severity = AlertSeverity.WARNING,
                target = AlertTarget.DISPATCH_PRICE_MISSING,
                title = "${priceMissing.size} despacho(s) sin precio",
                message = "Hay despachos que no se pueden cerrar por precio faltante",
                actionLabel = "Configurar precios",
                createdAt = now
            )
        }

        // 5. Lotes IN_PROGRESS estancados (más de 1 día calendario)
        val staleLots = lotsAsync.await().filter {
            it.status == ProductionLotStatus.IN_PROGRESS &&
                it.producingBranchId in accessibleSet &&
                it.productionDate < todayStart
        }
        if (staleLots.isNotEmpty()) {
            alerts += HomeAlert(
                id = "stale-lots-${staleLots.size}",
                severity = AlertSeverity.WARNING,
                target = AlertTarget.STALE_PRODUCTION_LOT,
                title = "${staleLots.size} lote(s) sin completar",
                message = staleLots.take(2).joinToString(", ") {
                    "${it.producingBranchName} (${it.shiftName.ifEmpty { "sin turno" }})"
                },
                actionLabel = "Ver producción",
                createdAt = now
            )
        }

        // 6. Pedidos tardíos sin aprobar
        val orders = ordersAsync.await()
        val lateOrders = orders.filter {
            it.isLateOrder && it.status == ProductionOrderStatus.PENDING &&
                it.producingBranchId in accessibleSet
        }
        if (lateOrders.isNotEmpty()) {
            alerts += HomeAlert(
                id = "late-orders-${lateOrders.size}",
                severity = AlertSeverity.CRITICAL,
                target = AlertTarget.LATE_PRODUCTION_ORDER,
                title = "${lateOrders.size} pedido(s) tardío(s) pendiente(s)",
                message = lateOrders.take(2).joinToString(", ") {
                    "${it.sellingBranchName} → ${it.producingBranchName}"
                },
                actionLabel = "Aprobar",
                createdAt = now
            )
        }

        // 7. Cajas abiertas hace más de 14h
        val openSessions = openCashAsync.await().filter {
            it.status == CashSessionStatus.OPEN &&
                it.branchId in accessibleSet &&
                now - it.openedAt > 14L * 60 * 60 * 1000
        }
        if (openSessions.isNotEmpty()) {
            alerts += HomeAlert(
                id = "open-cash-${openSessions.size}",
                severity = AlertSeverity.WARNING,
                target = AlertTarget.OVERDUE_CASH_SESSION,
                title = "${openSessions.size} caja(s) sin cerrar",
                message = openSessions.take(2).joinToString(", ") {
                    "${it.registerName} (${it.branchName})"
                },
                actionLabel = "Cuadre / cierre",
                createdAt = now
            )
        }

        // 8. Facturas de proveedor vencidas
        val purchases = purchasesAsync.await().filter { it.branchId in accessibleSet }
        val overdue = purchases.filter { it.displayStatus(now) == PurchaseDisplayStatus.OVERDUE }
        if (overdue.isNotEmpty()) {
            val owed = overdue.sumOf { it.balanceDue }
            alerts += HomeAlert(
                id = "overdue-purchase-${overdue.size}",
                // Un proveedor con la factura vencida deja de despachar harina, y sin harina
                // no hay producción: por eso escala a CRITICAL antes que las demás alertas.
                severity = if (overdue.size >= 3) AlertSeverity.CRITICAL else AlertSeverity.WARNING,
                target = AlertTarget.OVERDUE_PURCHASE,
                title = "${overdue.size} factura(s) vencida(s) · Q${owed.fmtMoney()}",
                message = overdue.sortedBy { it.dueDate }.take(3).joinToString(", ") {
                    "${it.supplierName} (Q${it.balanceDue.fmtMoney()})"
                },
                actionLabel = "Ver compras",
                createdAt = now
            )
        }

        // Y las que vencen dentro de 3 días — el aviso sirve cuando todavía se puede pagar.
        val dueSoon = purchases.filter {
            it.displayStatus(now) != PurchaseDisplayStatus.OVERDUE &&
                it.balanceDue > 0.01 &&
                (it.daysUntilDue(now) ?: 99) in 0..3
        }
        if (overdue.isEmpty() && dueSoon.isNotEmpty()) {
            alerts += HomeAlert(
                id = "due-soon-purchase-${dueSoon.size}",
                severity = AlertSeverity.INFO,
                target = AlertTarget.OVERDUE_PURCHASE,
                title = "${dueSoon.size} factura(s) por vencer",
                message = dueSoon.sortedBy { it.dueDate }.take(3).joinToString(", ") {
                    "${it.supplierName} (${formatPurchaseDate(it.dueDate)})"
                },
                actionLabel = "Ver compras",
                createdAt = now
            )
        }

        // 9. Lotes de insumos perecederos por caducar
        //
        // Se mira el LOTE y no la compra: un saco de levadura que ya se consumió tiene el
        // lote agotado y deja de avisar solo. Antes esto colgaba del encabezado de la compra
        // y seguía alertando por mercadería que ya no estaba en bodega.
        val expiringLots = if (user.hasPermission(Permission.VIEW_INGREDIENTS)) {
            runCatching { ingredientRepository.getActiveStockLots(companyId, accessibleBranchIds) }
                .getOrDefault(emptyList())
                .expiringBefore(now + 15L * 24 * 60 * 60 * 1000)
        } else emptyList()
        if (expiringLots.isNotEmpty()) {
            val vencidos = expiringLots.count { it.expiryDate < now }
            alerts += HomeAlert(
                id = "expiring-lots-${expiringLots.size}-$vencidos",
                severity = if (vencidos > 0) AlertSeverity.CRITICAL else AlertSeverity.WARNING,
                target = AlertTarget.EXPIRING_INGREDIENT,
                title = if (vencidos > 0) "$vencidos lote(s) de insumo vencido(s)"
                        else "${expiringLots.size} insumo(s) por vencer",
                message = expiringLots.take(3).joinToString(", ") {
                    "${it.ingredientName} (${formatPurchaseDate(it.expiryDate)}, ${it.branchName})"
                },
                actionLabel = "Ver insumos",
                createdAt = now
            )
        }

        // Orden: CRITICAL primero
        val sortedAlerts = alerts.sortedBy { it.severity.ordinal }.reversed()

        _uiState.update {
            it.copy(
                isLoading = false,
                salesTodayTotal = salesTodayTotal,
                salesTodayCount = salesTodayCount,
                lotsTodayCount = accessibleLotsToday,
                lowStockCount = lowStockEntries.size,
                maintenanceDueCount = maintenanceSoon.size,
                weeklySales = filledWeekly,
                alerts = sortedAlerts,
                error = null
            )
        }
    }

    private fun fillMissingDays(
        points: List<DailySalesPoint>,
        today: LocalDate,
        tz: TimeZone
    ): List<DailySalesPoint> {
        val byKey = points.associateBy { it.dateLabel }
        return (6 downTo 0).map { offset ->
            val date = today.minus(DatePeriod(days = offset))
            val key = date.toString()
            byKey[key] ?: DailySalesPoint(
                dateMillis = date.atStartOfDayIn(tz).toEpochMilliseconds(),
                dateLabel = key,
                revenue = 0.0,
                transactionCount = 0
            )
        }
    }
}
