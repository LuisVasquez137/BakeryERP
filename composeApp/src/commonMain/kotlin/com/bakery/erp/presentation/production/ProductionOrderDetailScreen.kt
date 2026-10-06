package com.bakery.erp.presentation.production

import com.bakery.erp.presentation.reports.branchsupply.BranchSupplyTable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bakery.erp.ui.ProductImage
import com.bakery.erp.util.formatBakerWeight
import androidx.compose.ui.draw.clip
import com.bakery.erp.model.Permission
import com.bakery.erp.model.ProductionLotStatus
import com.bakery.erp.model.ProductionOrderStatus
import com.bakery.erp.model.UserRole
import com.bakery.erp.model.canAccessBranch
import com.bakery.erp.model.hasPermission
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.presentation.products.ProductViewModel
import com.bakery.erp.ui.theme.BrandChip
import com.bakery.erp.ui.theme.BrandColors
import com.bakery.erp.ui.theme.ChipTone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductionOrderDetailScreen(
    orderId: String,
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (String) -> Unit = {},
    authViewModel: AuthViewModel,
    viewModel: ProductionViewModel,
    /** Para ofrecer en el ajuste un pan que la sucursal no había pedido. */
    productViewModel: ProductViewModel? = null
) {
    val snackbarHostState = remember { SnackbarHostState() }
    if (productViewModel != null) {
        val productState by productViewModel.uiState.collectAsState()
        val authState by authViewModel.uiState.collectAsState()
        val companyId = authState.user?.orgId ?: ""
        LaunchedEffect(companyId) {
            if (companyId.isNotEmpty() && productState.productList.isEmpty()) productViewModel.loadProducts(companyId)
        }
        LaunchedEffect(productState.productList) {
            if (productState.productList.isNotEmpty()) viewModel.updateProductsCache(productState.productList)
        }
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Detalle del Pedido", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BrandColors.Primary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = BrandColors.Ivory
    ) { padding ->
        ProductionOrderDetailContent(
            orderId = orderId,
            authViewModel = authViewModel,
            viewModel = viewModel,
            onNavigateToEdit = onNavigateToEdit,
            onAfterCancel = onNavigateBack,
            snackbarHostState = snackbarHostState,
            modifier = Modifier.fillMaxSize().padding(padding)
        )
    }
}

/**
 * Cuerpo del detalle de pedido — sin Scaffold ni TopAppBar — para reutilizar como panel
 * embebido en pantallas anchas (al lado de la lista) o dentro del Scaffold de su screen
 * propia en pantallas pequeñas.
 */
@Composable
fun ProductionOrderDetailContent(
    orderId: String,
    authViewModel: AuthViewModel,
    viewModel: ProductionViewModel,
    onNavigateToEdit: (String) -> Unit,
    onAfterCancel: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    onClosePanel: (() -> Unit)? = null
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val user = authState.user
    val companyId = user?.orgId ?: ""
    val canSend = user?.hasPermission(Permission.SEND_PRODUCTION_ORDER) == true
    val canManageLots = user?.hasPermission(Permission.MANAGE_PRODUCTION_LOTS) == true
    // El borrado de raíz es del propietario y de nadie más: no es una acción de la
    // operación diaria (para eso está cancelar), es la salida para los pedidos de prueba.
    val isOwner = user?.role == UserRole.OWNER
    var showCancelDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(orderId) {
        if (companyId.isNotEmpty()) {
            viewModel.loadOrderDetail(companyId, orderId)
            viewModel.loadOrderAdjustments(companyId, orderId)
        }
    }
    LaunchedEffect(state.orderAdjustmentDone) {
        state.orderAdjustmentDone?.let {
            snackbarHostState.showSnackbar("Ajuste guardado — $it")
            viewModel.clearOrderAdjustmentDone()
        }
    }
    state.orderAdjustmentContext?.takeIf { it.order.id == orderId }?.let { ctx ->
        OrderAdjustmentDialog(
            context = ctx,
            productsById = state.productsById,
            isSaving = state.isOrderAdjustmentSaving,
            onDismiss = { viewModel.clearOrderAdjustment() },
            onConfirm = { lotId, productId, productName, productUnit, delta, verified ->
                user?.let {
                    viewModel.applyOrderAdjustment(
                        companyId, it, orderId, lotId, productId, productName, productUnit, delta, verified
                    )
                }
            }
        )
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }

    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = { Text("Cancelar pedido") },
            text = { Text("¿Estás seguro de que deseas cancelar este pedido?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.cancelOrder(companyId, orderId)
                    showCancelDialog = false
                    onAfterCancel()
                }) { Text("Cancelar pedido", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showCancelDialog = false }) { Text("No, mantener") } }
        )
    }

    if (showDeleteDialog) {
        // Los lotes que este pedido alimenta y que quedarían sin ninguna otra fuente:
        // el propietario tiene que saber que se van con él antes de confirmar.
        val lotsGoingAway = state.lotsForSelectedOrder.filter {
            it.sourceOrderIds.all { id -> id == orderId } && it.customerOrderIds.isEmpty()
        }
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Borrar el pedido de raíz") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Esto NO es cancelar. El pedido desaparece por completo, junto con:"
                    )
                    Text("• sus productos y las notas de despacho que salieron de él")
                    if (lotsGoingAway.isNotEmpty()) {
                        Text(
                            "• el lote de " +
                                lotsGoingAway.joinToString(" y ") { it.shiftName.ifEmpty { "la planta" } } +
                                ", que se queda sin ningún otro pedido"
                        )
                    }
                    Text("• lo que aporta a las cantidades sugeridas de ese día de la semana")
                    Text(
                        "Sirve para sacar pedidos de prueba. Un pedido real conviene cancelarlo, " +
                            "para que quede el rastro de que existió.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    Text(
                        "No se puede deshacer.",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    user?.let { current ->
                        viewModel.deleteOrderPermanently(companyId, orderId, current) {
                            onAfterCancel()
                        }
                    }
                }) { Text("Borrar definitivamente", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("No, mantener") } }
        )
    }

    val order = state.selectedOrder
    val lines = state.selectedOrderLines

    LaunchedEffect(order?.id, order?.status) {
        if (order != null && order.id == orderId && companyId.isNotEmpty()) {
            viewModel.loadOrderSupply(companyId, order)
            viewModel.loadRecipeRefByProductIdIfNeeded(companyId)
        }
    }

    if (state.isDetailLoading || order == null || order.id != orderId) {
        Box(modifier, contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Pedido",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = BrandColors.Primary
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusChip(order.status, state.lotForSelectedOrder?.status, order.isLateOrder)
                            if (onClosePanel != null) {
                                Spacer(Modifier.width(6.dp))
                                IconButton(onClick = onClosePanel) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Cerrar",
                                        tint = Color.Gray
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider()
                    InfoRow("Sucursal solicitante", order.sellingBranchName)
                    InfoRow("Planta destino", order.producingBranchName)
                    InfoRow("Turno", order.shiftName.ifBlank { "—" })
                    InfoRow("Para el día", formatProductionDate(order.requestedDate))
                    InfoRow(
                        "Creado por",
                        "${order.createdByUserName.ifBlank { "—" }} · ${formatHourMinute(order.createdAt)}"
                    )
                    if (!order.notes.isNullOrBlank()) InfoRow("Notas", order.notes)
                }
            }
        }

        // Aviso si el pedido ya está en un lote y el usuario no es de la planta productora.
        val lotForOrder = state.lotForSelectedOrder
        val isProducingStaff = user?.canAccessBranch(order.producingBranchId) == true
        if (lotForOrder != null && order.status == ProductionOrderStatus.PENDING && !isProducingStaff) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            tint = BrandColors.AccentDeep
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Este pedido ya está en un lote (${ProductionLotStatus.label(lotForOrder.status)}).",
                                fontWeight = FontWeight.SemiBold,
                                color = BrandColors.AccentDeep,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "Contacta al supervisor de sucursal de la planta productora " +
                                    "(${order.producingBranchName}) para modificar o cancelar este pedido.",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF7A3300)
                            )
                        }
                    }
                }
            }
        }

        item {
            Text(
                "Productos solicitados (${lines.size})",
                fontWeight = FontWeight.Bold,
                color = BrandColors.Primary
            )
        }

        items(lines) { line ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // La foto del especial va acá aunque el detalle sea read-only:
                    // es la pantalla donde la planta revisa el pedido antes de
                    // consolidarlo, y "pirujo del doble" sin imagen es una discusión
                    // por teléfono que se puede evitar.
                    if (line.isSpecial && line.specialPhotoUrl.isNotEmpty()) {
                        ProductImage(
                            imageUrl = line.specialPhotoUrl,
                            contentDescription = null,
                            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp))
                        )
                        Spacer(Modifier.width(10.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (line.isSpecial) {
                                Icon(
                                    Icons.Default.Star,
                                    contentDescription = "Pedido especial",
                                    tint = BrandColors.AccentDeep,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                            Text(line.productName, fontWeight = FontWeight.SemiBold, color = BrandColors.Primary)
                        }
                        if (line.isSpecial) {
                            Text(
                                listOfNotNull(
                                    "Especial",
                                    line.specialRecipeName.takeIf { it.isNotBlank() },
                                    formatBakerWeight(line.specialRawWeightGrams)
                                        .takeIf { it != "—" }?.let { "$it c/u" }
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall,
                                color = BrandColors.AccentDeep,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Text(line.productUnit, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                    Text(
                        "${line.requestedQuantity.toInt()} uds.",
                        fontWeight = FontWeight.Bold,
                        color = BrandColors.AccentDeep
                    )
                }
            }
        }

        val adjustments = state.selectedOrderAdjustments.filter { it.orderId == order.id }
        if (adjustments.isNotEmpty()) {
            item {
                Text("Ajustes después del reparto", fontWeight = FontWeight.Bold, color = BrandColors.Primary)
            }
            items(adjustments) { adj ->
                Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(10.dp)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        val sign = if (adj.delta > 0) "+" else "−"
                        Text(
                            "$sign${kotlin.math.abs(adj.delta).toInt()} ${adj.productName} · ${adj.shiftName}",
                            fontWeight = FontWeight.SemiBold,
                            color = if (adj.delta > 0) BrandColors.Primary else BrandColors.AccentDeep
                        )
                        Text(
                            listOfNotNull(
                                adj.createdByUserName.ifBlank { null },
                                formatHourMinute(adj.createdAt),
                                if (adj.verifiedNotMade) "verificado con el panadero" else null
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                    }
                }
            }
        }

        // Seguimiento: aparece en cuanto la planta arranca el lote y nacen las notas.
        // Antes de eso no hay nada que seguir más allá de lo pedido, que ya está arriba.
        val supply = state.orderSupply?.takeIf { state.orderSupplyForId == order.id }
        if (supply != null && supply.columns.isNotEmpty()) {
            item {
                Text(
                    "Seguimiento",
                    fontWeight = FontWeight.Bold,
                    color = BrandColors.Primary
                )
                Text(
                    "Lo que la planta asignó en cada turno, lo que se empacó y lo que contó la sucursal.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    BranchSupplyTable(
                        report = supply,
                        recipeRefByProductId = state.recipeRefByProductId,
                        showSold = false,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        if (order.status == ProductionOrderStatus.PENDING) {
            val lotStatus = state.lotForSelectedOrder?.status
            // Editar permitido si:
            //   - sin lote: el usuario solicitante con permiso SEND_PRODUCTION_ORDER.
            //   - lote OPEN: solo la planta productora (acceso a la sucursal productora
            //     + permiso MANAGE_PRODUCTION_LOTS). Esto evita que la sucursal origen
            //     cambie unilateralmente algo que la planta ya está consolidando.
            //   - lote IN_PROGRESS o COMPLETED: nadie edita.
            // Con algún turno ya en producción no se edita el pedido entero: el
            // recálculo repartiría el cambio solo y lo cargaría al turno abierto
            // aunque ese turno no haga ese pan. Para eso está "Ajustar pedido",
            // donde la planta dice a qué turno va.
            val anyLotStarted = state.lotsForSelectedOrder.any { it.status != ProductionLotStatus.OPEN }
            val editable = when (lotStatus) {
                null -> canSend
                ProductionLotStatus.OPEN -> isProducingStaff && canManageLots && !anyLotStarted
                else -> false
            }
            val canAdjust = isProducingStaff && canManageLots && state.lotsForSelectedOrder.any {
                it.status == ProductionLotStatus.OPEN || it.status == ProductionLotStatus.IN_PROGRESS
            }
            // Cancelar permitido si: no hay lote (la sucursal solicitante puede), o el
            // lote está OPEN (solo la planta productora puede; la sucursal origen ya no
            // tiene control unilateral una vez consolidado). Bloqueado en IN_PROGRESS /
            // COMPLETED.
            val cancellable = when (lotStatus) {
                null -> canSend
                ProductionLotStatus.OPEN -> isProducingStaff && canManageLots
                else -> false
            }
            // Planta de 2 turnos con un lote ya en producción y el otro abierto: se
            // puede editar, pero el cambio solo puede caer en el turno que sigue
            // abierto. Decirlo evita que la encargada crea que movió pan que ya
            // está en el horno.
            val lockedShifts = state.lotsForSelectedOrder
                .filter { it.status != ProductionLotStatus.OPEN }
                .mapNotNull { it.shiftName.ifEmpty { null } }
            if (canAdjust && lockedShifts.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        color = Color(0xFFFBF1DC),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "${lockedShifts.joinToString(" y ")} ya está en producción: para cambiar este " +
                                "pedido usá \"Ajustar pedido\" y elegí a qué turno va el cambio. Si es un turno " +
                                "en producción, verificá antes con el panadero que esa masa no se haya hecho.",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = BrandColors.AccentDeep
                        )
                    }
                }
            }
            if (canAdjust) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { viewModel.loadOrderAdjustmentContext(companyId, order.id) },
                        enabled = !state.isOrderAdjustmentLoading,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandColors.Accent, contentColor = BrandColors.OnAccent)
                    ) {
                        if (state.isOrderAdjustmentLoading) {
                            CircularProgressIndicator(Modifier.size(18.dp), color = BrandColors.OnAccent, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Tune, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Ajustar pedido", fontWeight = FontWeight.Bold)
                        }
                    }
                    Text(
                        "Ponerle o quitarle pan a ${order.sellingBranchName} con los turnos ya repartidos.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 4.dp, start = 8.dp)
                    )
                }
            }
            if (editable || canSend) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { onNavigateToEdit(order.id) },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        enabled = editable,
                        colors = ButtonDefaults.buttonColors(containerColor = BrandColors.Primary)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Editar pedido", fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (cancellable || canSend) {
                item {
                    OutlinedButton(
                        onClick = { showCancelDialog = true },
                        enabled = cancellable,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Text("Cancelar pedido") }
                    if (!cancellable && lotStatus != null && lotStatus != ProductionLotStatus.OPEN) {
                        Text(
                            "El lote ya está en producción o cerrado. La cancelación queda " +
                                "bloqueada para no descuadrar el stock.",
                            style = MaterialTheme.typography.labelSmall,
                            color = BrandColors.AccentDeep,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
            // Borrado de raíz — solo el propietario. Va al final y como texto, no como
            // botón lleno: es la salida para los pedidos de prueba, no una acción que
            // deba competir visualmente con editar o cancelar.
            if (isOwner) {
                item {
                    Spacer(Modifier.height(4.dp))
                    TextButton(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Borrar pedido de prueba")
                    }
                    Text(
                        if (lotStatus == ProductionLotStatus.COMPLETED)
                            "El lote ya cerró y descontó insumos: este pedido ya no se puede borrar, " +
                                "solo ajustar el stock a mano."
                        else
                            "Solo vos ves esta opción. Borra el pedido y su lote sin dejar rastro; " +
                                "un pedido real es mejor cancelarlo.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusChip(status: String, lotStatus: String? = null, isLateOrder: Boolean = false) {
    val (label, tone) = effectiveOrderDisplay(status, lotStatus, isLateOrder)
    BrandChip(label, tone)
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = BrandColors.Primary)
    }
}
