package com.bakery.erp.presentation.home

import com.bakery.erp.model.openChargeList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bakery.erp.domain.AttendanceRepository
import com.bakery.erp.domain.AuthRepository
import com.bakery.erp.domain.BranchRepository
import com.bakery.erp.domain.CashSessionRepository
import com.bakery.erp.domain.CustomerOrderRepository
import com.bakery.erp.domain.DispatchRepository
import com.bakery.erp.domain.EmployeeRepository
import com.bakery.erp.domain.HomeShortcutRepository
import com.bakery.erp.domain.homeShortcutOwnerKey
import com.bakery.erp.domain.FixedAssetRepository
import com.bakery.erp.domain.IngredientRepository
import com.bakery.erp.domain.ProductionRepository
import com.bakery.erp.domain.PurchaseRepository
import com.bakery.erp.domain.SaleRepository
import com.bakery.erp.domain.report.AlertSeverity
import com.bakery.erp.domain.report.AlertTarget
import com.bakery.erp.domain.report.SalesSummary
import com.bakery.erp.domain.report.HomeAlert
import com.bakery.erp.model.AssetStatus
import com.bakery.erp.model.CashSessionStatus
import com.bakery.erp.model.DispatchStatus
import com.bakery.erp.model.Employee
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
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
    val lowStockCount: Int = 0,
    val maintenanceDueCount: Int = 0,
    /** Gráficas del inicio; null = sin permiso o todavía sin datos (ver [dashboardLoading]). */
    val dashboard: HomeDashboard? = null,
    val dashboardLoading: Boolean = false,
    /** Lo que le toca hacer a la persona, con botón (ver HomePersonal.kt). */
    val pendingTasks: List<PendingTask> = emptyList(),
    val birthdays: Birthdays? = null,
    /** Cuándo terminó la última carga; el Home dice "actualizado hace 5 min". */
    val lastUpdatedAt: Long = 0L,
    /** Solo el jalón de "deslizar para actualizar", no la primera carga. */
    val isRefreshing: Boolean = false,
    val alerts: List<HomeAlert> = emptyList(),
    /**
     * Ficha de la persona que está en el Home: la que se identificó con PIN o, con cuenta
     * propia, la ficha ligada a su usuario (`Employee.userId`). Null = no tiene ficha.
     */
    val profileEmployee: Employee? = null,
    val isSavingProfile: Boolean = false,
    val profileError: String? = null,
    /** Sube cada vez que el perfil se guarda bien: el diálogo lo usa para cerrarse. */
    val profileSavedVersion: Int = 0,
    /** Títulos de módulos que la persona escogió; HomeScreen los filtra por permisos. */
    val shortcuts: List<String> = emptyList(),
    val shortcutsLoaded: Boolean = false
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
    private val purchaseRepository: PurchaseRepository,
    /** Aviso de créditos con saldo viejo. */
    private val creditRepository: com.bakery.erp.domain.CreditRepository,
    private val employeeRepository: EmployeeRepository,
    private val shortcutRepository: HomeShortcutRepository,
    private val customerOrderRepository: CustomerOrderRepository,
    private val attendanceRepository: AttendanceRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var lastLoadedKey: String? = null
    private var shortcutOwnerKey: String? = null
    private var shortcutCompanyId: String = ""
    private var dashboardJob: Job? = null
    /** Sucursales del tablero del dueño; null = todas (no hace falta filtrar). */
    private var ownerBranchFilter: List<String>? = null
    private var dashboardCompanyId: String = ""

    /**
     * Llamado por HomeScreen cuando obtiene el [User] desde AuthViewModel. La llave no es
     * solo el id: en un equipo de sucursal el id es el de la cuenta y no cambia al pasar de
     * una persona a otra, pero los permisos, las alertas y la ficha sí.
     */
    fun ensureLoadedFor(user: User) {
        val key = "${user.id}|${user.operatorEmployeeId}|${user.permissions.sorted().joinToString(",")}"
        if (lastLoadedKey == key) return
        lastLoadedKey = key
        // Lo de la persona anterior no se queda pintado mientras carga lo de la nueva.
        _uiState.update {
            it.copy(profileEmployee = null, shortcuts = emptyList(), shortcutsLoaded = false, profileError = null)
        }
        refresh(user)
        loadPerson(user)
    }

    private fun loadPerson(user: User) {
        viewModelScope.launch {
            val employee = runCatching {
                if (user.operatorEmployeeId.isNotBlank()) {
                    employeeRepository.getEmployee(user.orgId, user.operatorEmployeeId)
                } else {
                    employeeRepository.getEmployees(user.orgId).firstOrNull { it.userId == user.id }
                }
            }.getOrNull()
            val ownerKey = homeShortcutOwnerKey(employee?.id ?: user.operatorEmployeeId, user.id)
            shortcutOwnerKey = ownerKey
            shortcutCompanyId = user.orgId
            val shortcuts = shortcutRepository.getShortcuts(user.orgId, ownerKey)
            _uiState.update {
                it.copy(profileEmployee = employee, shortcuts = shortcuts, shortcutsLoaded = true)
            }
            loadPersonal(user, employee)
        }
    }

    /** Jalón de "deslizar para actualizar": todo de nuevo, incluidos pendientes y gráficas. */
    fun pullToRefresh(user: User) {
        _uiState.update { it.copy(isRefreshing = true) }
        refresh(user)
        viewModelScope.launch {
            val employee = _uiState.value.profileEmployee
            loadPersonal(user, employee)
        }
    }

    /**
     * Pendientes y cumpleaños. Cada fuente va en su propio `runCatching`: que falle la
     * consulta de cajas no debe dejar sin cumpleaños ni sin el aviso de asistencia.
     */
    private suspend fun loadPersonal(user: User, employee: Employee?) = withContext(Dispatchers.Default) {
        coroutineScope {
            val companyId = user.orgId
            val tz = TimeZone.currentSystemDefault()
            val now = Clock.System.now().toLocalDateTime(tz)
            val today = now.date
            val todayStart = today.atStartOfDayIn(tz).toEpochMilliseconds()
            val tomorrowStart = today.plus(DatePeriod(days = 1)).atStartOfDayIn(tz).toEpochMilliseconds()
            val seesAll = user.role == UserRole.OWNER || user.hasPermission(Permission.VIEW_ALL_BRANCHES)

            val employeesAsync = async { runCatching { employeeRepository.getEmployees(companyId) }.getOrDefault(emptyList()) }
            val branchesAsync = async { runCatching { branchRepository.getBranches(companyId) }.getOrDefault(emptyList()) }
            val attendanceAsync = async {
                if (employee == null) null
                else runCatching { attendanceRepository.getTodayRecords(companyId, employee.id) }.getOrNull()
            }
            val cashAsync = async {
                if (!user.hasPermission(Permission.OPERATE_POS)) emptyList()
                else runCatching { cashSessionRepository.observeOpenSessions(companyId).first() }.getOrDefault(emptyList())
                    .filter { s ->
                        s.openedAt in 1 until todayStart &&
                            if (user.operatorEmployeeId.isNotBlank()) s.openedByEmployeeId == user.operatorEmployeeId
                            else s.cashierId == user.id
                    }
            }

            // El pedido de planta y los pedidos de clientes son de la sucursal: solo para
            // quien trabaja en una o pocas (al dueño le llegan como alertas generales).
            val myBranches = if (seesAll) emptyList()
                             else branchesAsync.await().filter { user.canAccessBranch(it.id) }.map { it.id }.take(3)
            val notesAsync = async {
                if (!user.hasPermission(Permission.RECEIVE_DISPATCH)) emptyList()
                else myBranches.flatMap { b ->
                    runCatching {
                        dispatchRepository.getNotesForDestination(companyId, b, todayStart - 24L * 60 * 60 * 1000, tomorrowStart)
                    }.getOrDefault(emptyList())
                }
            }
            val ordersAsync = async {
                val canSee = user.hasPermission(Permission.VIEW_CUSTOMER_ORDERS) ||
                    user.hasPermission(Permission.MANAGE_CUSTOMER_ORDERS)
                if (!canSee) emptyList()
                else myBranches.flatMap { b ->
                    runCatching {
                        customerOrderRepository.getOrdersForDeliveryBranch(companyId, b, todayStart, tomorrowStart)
                    }.getOrDefault(emptyList())
                }
            }

            val tasks = buildPendingTasks(
                employee = employee,
                today = today,
                hour = now.hour,
                minute = now.minute,
                todayAttendance = attendanceAsync.await(),
                canOpenAttendance = user.hasPermission(Permission.MANAGE_EMPLOYEES),
                staleCash = cashAsync.await(),
                notes = notesAsync.await(),
                ordersToday = ordersAsync.await()
            )
            val birthdays = buildBirthdays(employeesAsync.await(), today)
            _uiState.update { it.copy(pendingTasks = tasks, birthdays = birthdays) }
        }
    }

    /** Guarda los accesos directos de la persona actual. Se pinta de una vez (optimista). */
    fun saveShortcuts(modules: List<String>) {
        val ownerKey = shortcutOwnerKey ?: return
        val previous = _uiState.value.shortcuts
        _uiState.update { it.copy(shortcuts = modules) }
        viewModelScope.launch {
            runCatching { shortcutRepository.saveShortcuts(shortcutCompanyId, ownerKey, modules) }
                .onFailure { e ->
                    _uiState.update { it.copy(shortcuts = previous, error = e.message) }
                }
        }
    }

    /**
     * La persona corrige su nombre y contacto. [onSaved] recibe el nombre guardado para que
     * la sesión del equipo lo use de una vez.
     */
    fun saveProfile(name: String, phone: String, phoneAux: String, email: String, onSaved: (String) -> Unit) {
        val employee = _uiState.value.profileEmployee ?: return
        val cleanName = name.trim()
        if (cleanName.isBlank()) {
            _uiState.update { it.copy(profileError = "El nombre no puede quedar vacío") }
            return
        }
        val cleanEmail = email.trim()
        if (cleanEmail.isNotEmpty() && !EMAIL_REGEX.matches(cleanEmail)) {
            _uiState.update { it.copy(profileError = "El correo no parece válido") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingProfile = true, profileError = null) }
            runCatching {
                employeeRepository.updateEmployeeContact(
                    companyId = shortcutCompanyId,
                    employeeId = employee.id,
                    name = cleanName,
                    phone = phone.trim(),
                    phoneAux = phoneAux.trim(),
                    email = cleanEmail
                )
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        isSavingProfile = false,
                        profileEmployee = employee.copy(
                            name = cleanName, phone = phone.trim(), phoneAux = phoneAux.trim(), email = cleanEmail
                        ),
                        profileSavedVersion = it.profileSavedVersion + 1
                    )
                }
                onSaved(cleanName)
            }.onFailure { e ->
                _uiState.update { it.copy(isSavingProfile = false, profileError = e.message ?: "No se pudo guardar") }
            }
        }
    }

    fun clearProfileError() {
        _uiState.update { it.copy(profileError = null) }
    }

    fun refresh(user: User) {
        loadDashboard(user)
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            // Fuera del hilo de UI: loadAll lee colecciones enteras (ventas de la
            // semana, pedidos, compras, insumos) y decodificar esos documentos en
            // Main trababa el scroll de Home los primeros segundos.
            runCatching { withContext(Dispatchers.Default) { loadAll(user) } }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, isRefreshing = false, error = e.message) }
                }
        }
    }

    private suspend fun loadAll(user: User) = coroutineScope {
        val companyId = user.orgId
        val tz = TimeZone.currentSystemDefault()
        val today: LocalDate = Clock.System.now().toLocalDateTime(tz).date
        val todayStart = today.atStartOfDayIn(tz).toEpochMilliseconds()
        val tomorrowStart = today.plus(DatePeriod(days = 1)).atStartOfDayIn(tz).toEpochMilliseconds()

        // Sucursales accesibles para este usuario.
        val branchesAsync = async { branchRepository.getBranches(companyId) }

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

        val accessibleSet = accessibleBranchIds.toSet()

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

        // 10. Créditos con compras sin pagar de hace más de 30 días. Sale de los cargos
        // abiertos que ya viven en cada cuenta: una sola lectura de la colección, sin abrir
        // el estado de cuenta de nadie.
        if (user.hasPermission(Permission.MANAGE_CREDIT_ACCOUNTS)) {
            val late = runCatching { creditRepository.getAccounts(companyId) }.getOrDefault(emptyList())
                .map { it to com.bakery.erp.model.creditAgingOf(it.openChargeList, now) }
                .filter { (_, aging) -> aging.overdue30 > 0.004 }
                .sortedByDescending { (_, aging) -> aging.overdue30 }
            if (late.isNotEmpty()) {
                val owed = late.sumOf { (_, aging) -> aging.overdue30 }
                alerts += HomeAlert(
                    id = "credit-overdue-${late.size}",
                    severity = if (late.any { (_, a) -> a.over90 > 0.004 }) AlertSeverity.CRITICAL else AlertSeverity.WARNING,
                    target = AlertTarget.CREDIT_OVERDUE,
                    title = "${late.size} crédito(s) con más de 30 días · Q${owed.fmtMoney()}",
                    message = late.take(3).joinToString(", ") { (acc, a) -> "${acc.name} (Q${a.overdue30.fmtMoney()})" },
                    actionLabel = "Ver créditos",
                    createdAt = now
                )
            }
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
                lowStockCount = lowStockEntries.size,
                maintenanceDueCount = maintenanceSoon.size,
                alerts = sortedAlerts,
                lastUpdatedAt = Clock.System.now().toEpochMilliseconds(),
                isRefreshing = false,
                error = null
            )
        }
    }

    // ── Gráficas del inicio ──────────────────────────────────────────────────

    /**
     * Carga el tablero aparte de las alertas: las gráficas no esperan a que se lean las
     * compras o los insumos, ni las alertas a las gráficas. El Home se pinta de una vez y
     * cada parte llega cuando llega.
     */
    private fun loadDashboard(user: User) {
        dashboardJob?.cancel()
        if (!user.hasPermission(Permission.VIEW_OPERATIONS_REPORT)) {
            _uiState.update { it.copy(dashboard = null, dashboardLoading = false) }
            return
        }
        _uiState.update { it.copy(dashboard = null, dashboardLoading = true) }
        dashboardJob = viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.Default) { buildDashboard(user) }
            }.onSuccess { d ->
                _uiState.update { it.copy(dashboard = d, dashboardLoading = false) }
            }.onFailure { e ->
                if (e is CancellationException) throw e
                _uiState.update { it.copy(dashboard = null, dashboardLoading = false) }
            }
        }
    }

    private suspend fun buildDashboard(user: User): HomeDashboard? {
        val companyId = user.orgId
        dashboardCompanyId = companyId
        val branches = branchRepository.getBranches(companyId)
        val seesAll = user.role == UserRole.OWNER || user.hasPermission(Permission.VIEW_ALL_BRANCHES)
        val accessible = if (seesAll) branches else branches.filter { user.canAccessBranch(it.id) }
        if (accessible.isEmpty()) return null

        // Una sola sucursal = el tablero de la sucursal (sin montos).
        if (!seesAll && accessible.size == 1) {
            val branch = accessible.first()
            return HomeDashboard.Branch(loadBranchToday(companyId, branch.id, branch.name))
        }

        ownerBranchFilter = if (seesAll) null else accessible.map { it.id }
        val week = loadOwnerPeriod(DashboardPeriod.WEEK)
        return HomeDashboard.Owner(
            showMoney = user.hasPermission(Permission.VIEW_SALES_VALUE),
            branchCount = accessible.size,
            selected = DashboardPeriod.WEEK,
            periods = mapOf(DashboardPeriod.WEEK to week)
        )
    }

    /** El dueño cambia Hoy / Semana / Mes. Cada período se pide una vez y se guarda. */
    fun selectDashboardPeriod(period: DashboardPeriod) {
        val owner = _uiState.value.dashboard as? HomeDashboard.Owner ?: return
        _uiState.update { it.copy(dashboard = owner.copy(selected = period)) }
        if (owner.periods[period] != null) return
        viewModelScope.launch {
            val data = runCatching {
                withContext(Dispatchers.Default) { loadOwnerPeriod(period) }
            }.getOrNull() ?: return@launch
            _uiState.update { st ->
                val current = st.dashboard as? HomeDashboard.Owner ?: return@update st
                st.copy(dashboard = current.copy(periods = current.periods + (period to data)))
            }
        }
    }

    private suspend fun loadOwnerPeriod(period: DashboardPeriod): OwnerPeriodData = coroutineScope {
        val companyId = dashboardCompanyId
        val filter = ownerBranchFilter
        val tz = TimeZone.currentSystemDefault()
        val now = Clock.System.now().toLocalDateTime(tz)
        val today = now.date
        val n = period.days()
        val shift = period.compareShiftDays()
        fun startOf(d: LocalDate) = d.atStartOfDayIn(tz).toEpochMilliseconds()
        val from = today.minus(DatePeriod(days = n - 1))
        val until = today.plus(DatePeriod(days = 1))
        val prevFrom = from.minus(DatePeriod(days = shift))
        val prevUntil = until.minus(DatePeriod(days = shift))
        val prevLastDay = today.minus(DatePeriod(days = shift))

        val current = async { saleRepository.aggregateSalesInRange(companyId, filter, startOf(from), startOf(until)) }
        val previous = async { saleRepository.aggregateSalesInRange(companyId, filter, startOf(prevFrom), startOf(prevUntil)) }
        val lastDay = if (period == DashboardPeriod.TODAY) previous else async {
            saleRepository.aggregateSalesInRange(
                companyId, filter, startOf(prevLastDay), startOf(prevLastDay.plus(DatePeriod(days = 1)))
            )
        }
        buildOwnerPeriod(
            period = period,
            today = today,
            hour = now.hour,
            minute = now.minute,
            current = current.await(),
            previous = previous.await(),
            previousLastDay = lastDay.await()
        )
    }

    private suspend fun loadBranchToday(companyId: String, branchId: String, branchName: String): BranchToday =
        coroutineScope {
            val tz = TimeZone.currentSystemDefault()
            val now = Clock.System.now().toLocalDateTime(tz)
            val today = now.date
            fun startOf(d: LocalDate) = d.atStartOfDayIn(tz).toEpochMilliseconds()
            val ids = listOf(branchId)
            val todayStart = startOf(today)
            val tomorrowStart = startOf(today.plus(DatePeriod(days = 1)))

            val todaySales = async { saleRepository.aggregateSalesInRange(companyId, ids, todayStart, tomorrowStart) }
            // Los últimos cuatro días iguales a hoy: con eso se arma "un jueves normal".
            val sameWeekdays = (1..4).map { w ->
                val d = today.minus(DatePeriod(days = 7 * w))
                async {
                    runCatching {
                        saleRepository.aggregateSalesInRange(companyId, ids, startOf(d), startOf(d.plus(DatePeriod(days = 1))))
                    }.getOrDefault(SalesSummary())
                }
            }
            val notes = async {
                runCatching {
                    dispatchRepository.getNotesForDestination(companyId, branchId, todayStart, tomorrowStart)
                }.getOrDefault(emptyList())
            }
            val orders = async {
                runCatching {
                    customerOrderRepository.getOrdersForDeliveryBranch(
                        companyId, branchId, todayStart, startOf(today.plus(DatePeriod(days = 2)))
                    )
                }.getOrDefault(emptyList())
            }
            buildBranchToday(
                branchName = branchName,
                today = today,
                hour = now.hour,
                minute = now.minute,
                todaySales = todaySales.await(),
                sameWeekdays = sameWeekdays.map { it.await() },
                notes = notes.await(),
                customerOrders = orders.await(),
                todayStartMillis = todayStart,
                tomorrowStartMillis = tomorrowStart
            )
        }
}

private val EMAIL_REGEX = Regex("""^[^@\s]+@[^@\s]+\.[^@\s]+$""")
