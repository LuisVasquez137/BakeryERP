package com.bakery.erp.presentation.production

import com.bakery.erp.ui.BrandTopAppBar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterDrama
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.Umbrella
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bakery.erp.domain.ForecastPeriod
import com.bakery.erp.domain.WeatherCondition
import com.bakery.erp.model.Branch
import com.bakery.erp.model.DISPATCH_TRIP_BOTH
import com.bakery.erp.model.DISPATCH_TRIP_FIRST
import com.bakery.erp.model.Product
import com.bakery.erp.model.ProductionOrderLine
import com.bakery.erp.model.ProductionShift
import com.bakery.erp.model.isoWeekdayName
import com.bakery.erp.model.isoWeekdayOf
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.presentation.products.ProductViewModel
import com.bakery.erp.ui.AdaptiveContainer
import com.bakery.erp.ui.LoadingOverlay
import com.bakery.erp.ui.LocationResult
import com.bakery.erp.ui.rememberLocationRequester
import com.bakery.erp.util.formatBakerWeight
import bakeryerp.composeapp.generated.resources.Res
import bakeryerp.composeapp.generated.resources.rain_icon
import org.jetbrains.compose.resources.painterResource
import kotlin.time.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToInt
import com.bakery.erp.ui.theme.BrandColors
import com.bakery.erp.model.isPortion

/** Estado del permiso de ubicación al crear un pedido — ver
 *  [com.bakery.erp.model.Branch.latitude] / `LocationRequester`. Puramente de
 *  UI (no vive en el ViewModel): controla si se puede seguir con el
 *  formulario mientras se resuelve. */
private enum class LocationGateState { CHECKING, REQUESTING, PASSED, DENIED }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductionOrderFormScreen(
    orderId: String? = null,
    onNavigateBack: () -> Unit,
    onNavigateToEditOrder: (String) -> Unit = {},
    /** Se llama si la encargada niega el permiso de ubicación al crear un
     *  pedido para una sucursal sin coordenadas — ver [LocationGateState]. */
    onLocationDenied: () -> Unit = {},
    authViewModel: AuthViewModel,
    viewModel: ProductionViewModel,
    productViewModel: ProductViewModel
) {
    val isEdit = !orderId.isNullOrEmpty()
    val state by viewModel.uiState.collectAsState()
    val productState by productViewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val user = authState.user
    val companyId = user?.orgId ?: ""

    // Ubicación de la sucursal (alimenta el ajuste de pedido por clima): se
    // pide una sola vez por sucursal, la primera vez que alguien crea un
    // pedido para ella. En edición no aplica — la sucursal ya pasó por esto.
    var locationGateState by remember(isEdit) {
        mutableStateOf(if (isEdit) LocationGateState.PASSED else LocationGateState.CHECKING)
    }
    val locationRequester = rememberLocationRequester()

    // En creación: dos pasos. En edición saltamos directo al paso 2.
    var step by rememberSaveable(isEdit) { mutableIntStateOf(if (isEdit) 2 else 1) }
    // Persistimos solo los IDs (sobreviven rotación); las instancias Branch/Shift se
    // derivan del state para no perder selección al rotar.
    var selectedProducingBranchId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedSellingBranchId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedShiftId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedProducingBranch: Branch? = remember(state.producingBranches, selectedProducingBranchId) {
        state.producingBranches.find { it.id == selectedProducingBranchId }
    }
    val selectedSellingBranch: Branch? = remember(state.sellingBranches, selectedSellingBranchId) {
        state.sellingBranches.find { it.id == selectedSellingBranchId }
    }
    val selectedShift: ProductionShift? =
        remember(state.shiftsForSelectedProducingBranch, selectedShiftId) {
            state.shiftsForSelectedProducingBranch.find { it.id == selectedShiftId }
        }
    // Viaje único en que la sucursal solicitante recibe (0 = los dos). En edición
    // el id seleccionado queda nulo, así que se resuelve desde el pedido cargado.
    val sellingBranchTripRestriction: Int = remember(state.sellingBranches, selectedSellingBranch, state.selectedOrder) {
        (selectedSellingBranch
            ?: state.sellingBranches.find { it.id == state.selectedOrder?.sellingBranchId })
            ?.receivesOnlyInTrip ?: DISPATCH_TRIP_BOTH
    }
    var branchDropdownExpanded by remember { mutableStateOf(false) }
    var sellingBranchDropdownExpanded by remember { mutableStateOf(false) }
    // Pedido especial abierto en el diálogo: null = cerrado, línea vacía = alta.
    var editingSpecialLine by remember { mutableStateOf<ProductionOrderLine?>(null) }
    var specialDialogOpen by remember { mutableStateOf(false) }
    var targetDate by rememberSaveable { mutableStateOf(startOfNextDay()) }
    var notesText by rememberSaveable { mutableStateOf("") }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }

    // Sugerencias del historial + pronóstico de lluvia: ocupaban dos tarjetas
    // fijas arriba del formulario, empujando los productos —que es a lo que se
    // viene— fuera de la primera pantalla. Ahora se dicen UNA vez, en un
    // diálogo al entrar al paso de productos, y después el ícono de nube del
    // TopAppBar los vuelve a abrir si hacen falta. La recomendación por lluvia
    // de cada producto sigue en su fila: ésa sí es accionable donde está.
    // Llaveado por (sucursal, fecha): si la encargada vuelve al paso 1, cambia
    // la fecha y regresa, el pronóstico es otro y merece decirse de nuevo.
    var insightsAcknowledged by rememberSaveable(state.rainForecastKey) { mutableStateOf(false) }
    var showInsightsDialog by remember { mutableStateOf(false) }
    val hasInsights = state.suggestionApplied || state.rainProbabilityPercent > 0 ||
        state.rainPeriods.isNotEmpty()

    // Día de descanso de la planta productora: bloquea crear pedido ese día
    // (ver Branch.closedWeekdays). Solo aplica en creación — en edición la
    // fecha no se toca desde este formulario.
    val isClosedDay = !isEdit && selectedProducingBranch != null &&
        isoWeekdayOf(targetDate) in selectedProducingBranch.closedWeekdays
    var showClosedDayDialog by remember { mutableStateOf(false) }
    // Se re-arma cada vez que cambian planta o fecha, así si la encargada la
    // cierra y prueba con otra fecha que también cae en descanso, vuelve a avisar.
    LaunchedEffect(selectedProducingBranchId, targetDate) {
        showClosedDayDialog = isClosedDay
    }

    // El ViewModel es singleton (sobrevive navegación). Si la encargada sale sin
    // guardar, `draftOrderLines`/`suggestionPatternId` quedaban pegados: al volver
    // a "Nuevo pedido" con la misma combinación sucursal/planta/turno/día, el guard
    // "ya resolvimos esta combinación" de `applySuggestedQuantities` cortaba antes
    // de repoblar y el form quedaba vacío para siempre en esa combinación. Limpiar
    // al salir de la pantalla (por cualquier camino) evita que la sugerencia quede huérfana.
    DisposableEffect(Unit) {
        onDispose { viewModel.resetOrderForm() }
    }

    LaunchedEffect(companyId, orderId) {
        if (companyId.isEmpty()) return@LaunchedEffect
        if (isEdit) {
            viewModel.loadOrderForEdit(companyId, orderId!!)
            productViewModel.loadProducts(companyId)
        } else {
            viewModel.loadOrderFormData(companyId, user)
            productViewModel.loadProducts(companyId)
        }
    }

    // Prefijar selecciones desde el pedido cargado (modo edición).
    LaunchedEffect(state.selectedOrder, isEdit) {
        if (!isEdit) return@LaunchedEffect
        val order = state.selectedOrder ?: return@LaunchedEffect
        targetDate = order.requestedDate
        notesText = order.notes ?: ""
    }

    // Resolver el turno seleccionado contra los turnos cargados (puede llegar después).
    //
    // Con exactamente 2 turnos activos, la planta reparte automático entre ambos
    // (ver ProductionShiftSplitScreen) — la sucursal ya no elige turno, el pedido
    // se guarda con shiftId="" como las plantas sin turnos. Con 0 turnos (sin
    // cambios) o 1 turno (auto-selección de siempre) o 3+ turnos (selector
    // obligatorio, sin cambios) el comportamiento es el de siempre.
    LaunchedEffect(state.shiftsForSelectedProducingBranch, state.selectedOrder, isEdit) {
        val shifts = state.shiftsForSelectedProducingBranch
        if (shifts.isEmpty() || shifts.size == 2) {
            selectedShiftId = null
            return@LaunchedEffect
        }
        if (isEdit) {
            val orderShiftId = state.selectedOrder?.shiftId.orEmpty()
            selectedShiftId = shifts.find { it.id == orderShiftId }?.id ?: shifts.firstOrNull()?.id
        } else if (shifts.size == 1) {
            // Auto-seleccionar el único turno disponible.
            if (selectedShiftId == null || shifts.none { it.id == selectedShiftId }) {
                selectedShiftId = shifts.firstOrNull()?.id
            }
        } else {
            // 3+ turnos: selector obligatorio, sin auto-selección — como siempre.
            if (shifts.none { it.id == selectedShiftId }) selectedShiftId = null
        }
    }

    // Cuando el usuario elige la planta productora en creación: cargar sus turnos.
    LaunchedEffect(selectedProducingBranchId, isEdit) {
        if (isEdit) return@LaunchedEffect
        viewModel.loadShiftsForProducingBranch(companyId, selectedProducingBranchId ?: "")
    }

    LaunchedEffect(state.producingBranches, isEdit) {
        if (isEdit) return@LaunchedEffect
        if (selectedProducingBranchId == null && state.producingBranches.size == 1) {
            selectedProducingBranchId = state.producingBranches.first().id
        }
    }
    LaunchedEffect(state.sellingBranches, isEdit) {
        if (isEdit) return@LaunchedEffect
        if (selectedSellingBranchId == null && state.sellingBranches.size == 1) {
            selectedSellingBranchId = state.sellingBranches.first().id
        }
    }

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) { viewModel.resetOrderForm(); onNavigateBack() }
    }

    // Cargar pedidos de revendedor pendientes cuando cambie la sucursal vendedora o la fecha.
    LaunchedEffect(selectedSellingBranchId, targetDate, isEdit) {
        if (!isEdit && !selectedSellingBranchId.isNullOrEmpty()) {
            viewModel.loadPendingCustomerOrdersForSelling(
                companyId, selectedSellingBranchId!!, targetDate
            )
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }

    // Para mostrar la lista de productos en step 2, filtrar por productos asignados a la
    // planta productora (en edición resolvemos por id del pedido cargado).
    val producingBranchIdForFilter = if (isEdit) state.selectedOrder?.producingBranchId.orEmpty()
                                     else selectedProducingBranch?.id.orEmpty()
    val producingBranchForFilter = state.producingBranches.find { it.id == producingBranchIdForFilter }
        ?: selectedProducingBranch
    val displayProducts = remember(productState.productList, searchQuery, producingBranchForFilter) {
        val base = producingBranchForFilter
            ?.assignedProductIds
            ?.takeIf { it.isNotEmpty() }
            ?.let { ids -> productState.productList.filter { it.id in ids } }
            ?: productState.productList
        // La porción de pastel se corta en la tienda; a planta se le pide el entero.
        base.filter {
            it.active && !it.isPortion && (searchQuery.isEmpty() || it.name.contains(searchQuery, ignoreCase = true))
        }.sortedBy { it.name }
    }

    // Sucursal sin coordenadas guardadas: pedir el permiso de ubicación del
    // sistema UNA vez y guardarlas (ver Branch.latitude) antes de dejar seguir
    // con el formulario. Ya resuelta (o en edición), pasa directo sin demora.
    LaunchedEffect(selectedSellingBranch?.id, isEdit) {
        if (isEdit) {
            locationGateState = LocationGateState.PASSED
            return@LaunchedEffect
        }
        val branch = selectedSellingBranch ?: return@LaunchedEffect
        if (branch.latitude != null && branch.longitude != null) {
            locationGateState = LocationGateState.PASSED
            return@LaunchedEffect
        }
        locationGateState = LocationGateState.REQUESTING
        when (val result = locationRequester.request()) {
            is LocationResult.Granted -> {
                viewModel.saveBranchLocation(companyId, branch.id, result.latitude, result.longitude)
                locationGateState = LocationGateState.PASSED
            }
            LocationResult.Denied, LocationResult.Unavailable -> {
                locationGateState = LocationGateState.DENIED
            }
        }
    }

    // Pronóstico de lluvia: aparte de la sugerencia de cantidades, a propósito.
    // Depende solo de sucursal + fecha (no de planta/turno/búsqueda), así que
    // se actualiza SIEMPRE que cualquiera de esas dos cambie — incluso después
    // de que ya se prellenaron cantidades. Si viviera dentro del mismo efecto
    // que la sugerencia, cambiar de fecha después de la primera sugerencia
    // habría dejado pegado el pronóstico del primer día que se probó.
    LaunchedEffect(selectedSellingBranchId, targetDate, locationGateState) {
        if (isEdit || locationGateState != LocationGateState.PASSED) return@LaunchedEffect
        viewModel.refreshRainForecast(
            sellingBranchId = selectedSellingBranchId.orEmpty(),
            requestedDate = targetDate,
            sellingBranchLatitude = selectedSellingBranch?.latitude,
            sellingBranchLongitude = selectedSellingBranch?.longitude
        )
    }

    // Cantidades sugeridas: al entrar al paso de productos (solo en creación y
    // solo si la encargada aún no escribió nada) se prellenan con lo que esta
    // sucursal suele pedir a esta planta ese día de la semana — y, si hay
    // lluvia probable ese día, se suben un poco (ver applySuggestedQuantities).
    LaunchedEffect(
        step, isEdit, displayProducts.size,
        selectedSellingBranchId, selectedProducingBranchId, selectedShiftId, targetDate,
        locationGateState
    ) {
        if (isEdit || step != 2 || locationGateState != LocationGateState.PASSED) return@LaunchedEffect
        viewModel.applySuggestedQuantities(
            companyId = companyId,
            sellingBranchId = selectedSellingBranchId.orEmpty(),
            producingBranchId = selectedProducingBranchId.orEmpty(),
            shiftId = selectedShiftId.orEmpty(),
            requestedDate = targetDate,
            products = displayProducts
        )
    }

    // Se abre una sola vez al llegar al paso de productos, cuando ya hay algo
    // que decir (sugerencia resuelta y/o pronóstico). Espera a que la carga
    // termine para no mostrarse vacío y volver a aparecer con datos.
    LaunchedEffect(step, hasInsights, state.isSuggestionLoading, insightsAcknowledged) {
        if (isEdit || step != 2 || insightsAcknowledged) return@LaunchedEffect
        if (state.isSuggestionLoading || !hasInsights) return@LaunchedEffect
        showInsightsDialog = true
    }

    if (showInsightsDialog) {
        OrderInsightsDialog(
            suggestionApplied = state.suggestionApplied,
            suggestionWeekday = state.suggestionWeekday,
            suggestionSampleCount = state.suggestionSampleCount,
            rainProbabilityPercent = state.rainProbabilityPercent,
            rainExtraUnits = state.rainExtraUnits,
            rainPeriods = state.rainPeriods,
            onClearSuggestion = {
                viewModel.clearSuggestedQuantities()
                insightsAcknowledged = true
                showInsightsDialog = false
            },
            onAccept = {
                insightsAcknowledged = true
                showInsightsDialog = false
            }
        )
    }

    // Alta / edición de un pedido especial (pan fuera de catálogo, misma masa).
    if (specialDialogOpen) {
        SpecialOrderDialog(
            // Solo las masas que ESTA planta hace, deducidas de los productos que
            // tiene asignados — el mismo criterio con el que ya se filtra la lista
            // de productos del paso 2 (`displayProducts`). Antes se ofrecían todas
            // las masas de la empresa, incluidas las de otras plantas: elegir una
            // de esas mandaba el encargo a una amasada que en esa planta no existe.
            //
            // El filtro NO usa `displayProducts` porque ése lleva encima la
            // búsqueda del paso 2: las masas se achicarían mientras la encargada
            // teclea el nombre de un producto.
            //
            // Si la planta no tiene productos asignados (o ninguno de ellos sale de
            // una receta) se ofrecen todas, igual que hace la lista de productos:
            // más vale ruido que dejar a la encargada sin poder anotar el encargo.
            masas = remember(
                state.recipeRefByProductId, productState.productList, producingBranchForFilter
            ) {
                val assigned = producingBranchForFilter?.assignedProductIds?.takeIf { it.isNotEmpty() }
                val plantProductIds = productState.productList
                    .filter { it.active && (assigned == null || it.id in assigned) }
                    .map { it.id }
                    .toSet()
                val all = state.recipeRefByProductId.values.distinctBy { it.id }.sortedBy { it.name }
                state.recipeRefByProductId
                    .filterKeys { it in plantProductIds }
                    .values.distinctBy { it.id }.sortedBy { it.name }
                    .ifEmpty { all }
            },
            existing = editingSpecialLine,
            isSaving = state.isSavingSpecialLine,
            onDismiss = { specialDialogOpen = false; editingSpecialLine = null },
            onConfirm = { line, photoBytes ->
                viewModel.saveSpecialDraftLine(companyId, line, photoBytes)
                specialDialogOpen = false
                editingSpecialLine = null
            }
        )
    }

    // Dialog de día de descanso: la planta productora no recibe pedidos ese día.
    if (showClosedDayDialog && selectedProducingBranch != null) {
        AlertDialog(
            onDismissRequest = { showClosedDayDialog = false },
            title = { Text("Este día no se puede pedir") },
            text = {
                Text(
                    "${selectedProducingBranch.name} no trabaja los " +
                        "${isoWeekdayName(isoWeekdayOf(targetDate))}s — es su día de descanso. " +
                        "Revisa bien la fecha del pedido (${formatDate(targetDate)}) antes de continuar."
                )
            },
            confirmButton = {
                TextButton(onClick = { showClosedDayDialog = false }) {
                    Text("Entendido", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Dialog de conflicto de pedido pendiente duplicado.
    // No se permite crear duplicados; el usuario solo puede editar el existente o cancelar.
    state.pendingOrderConflict?.let { existing ->
        AlertDialog(
            onDismissRequest = { viewModel.clearPendingOrderConflict() },
            title = { Text("Ya existe un pedido pendiente") },
            text = {
                Text(
                    "Ya tienes un pedido pendiente para ${existing.sellingBranchName} → " +
                        "${existing.producingBranchName} el ${formatDate(existing.requestedDate)}" +
                        (if (existing.shiftName.isNotBlank()) " (turno ${existing.shiftName})" else "") +
                        ". No puedes crear otro para la misma combinación: edita el existente o " +
                        "cancélalo primero."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val id = existing.id
                    viewModel.clearPendingOrderConflict()
                    viewModel.resetOrderForm()
                    onNavigateToEditOrder(id)
                }) { Text("Editar el existente", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.clearPendingOrderConflict() }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Ubicación rechazada: sin ella no se puede armar el pedido (alimenta el
    // ajuste por clima) — se explica y se manda de vuelta a Inicio.
    if (locationGateState == LocationGateState.DENIED) {
        AlertDialog(
            onDismissRequest = { /* no se puede cerrar sin elegir */ },
            title = { Text("Se necesita tu ubicación") },
            text = {
                Text(
                    "Para crear pedidos desde esta sucursal necesitamos su ubicación una " +
                        "sola vez — nos ayuda a sugerir mejor según el clima. Actívala en los " +
                        "ajustes de ubicación del dispositivo e inténtalo de nuevo."
                )
            },
            confirmButton = {
                TextButton(onClick = onLocationDenied) {
                    Text("Volver a Inicio", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            BrandTopAppBar(
                title = {
                    Text(
                        when {
                            isEdit && step == 2 -> "Editar Pedido (1/2)"
                            isEdit && step == 3 -> "Revisar Cambios (2/2)"
                            step == 1 -> "Nuevo Pedido (1/3)"
                            step == 2 -> "Agregar Productos (2/3)"
                            else -> "Revisar Pedido (3/3)"
                        },
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        when {
                            step == 3 -> step = 2
                            !isEdit && step == 2 -> step = 1
                            else -> onNavigateBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                    }
                },
                actions = {
                    // Reabrir el pronóstico y el aviso de sugerencias sin robarle
                    // espacio permanente al formulario.
                    if (!isEdit && step == 2 && hasInsights) {
                        IconButton(onClick = { showInsightsDialog = true }) {
                            Icon(Icons.Default.Cloud, contentDescription = "Ver clima y sugerencias")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BrandColors.Primary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        containerColor = BrandColors.Ivory
    ) { padding ->
        if (isEdit && state.selectedOrder == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        // Paso 1 (elegir sucursal) nunca se bloquea: sin sucursal elegida
        // todavía no hay nada que verificar. La compuerta solo aplica desde el
        // paso 2 en adelante — para entonces ya se sabe cuál sucursal es.
        if (!isEdit && step != 1 && locationGateState != LocationGateState.PASSED) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                // DENIED no dibuja nada acá: el diálogo de arriba es lo que manda.
                if (locationGateState != LocationGateState.DENIED) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text("Verificando ubicación de la sucursal…", color = Color.Gray)
                    }
                }
            }
            return@Scaffold
        }

        when {
            !isEdit && step == 1 -> OrderFormStep1(
                modifier = Modifier.padding(padding),
                producingBranches = state.producingBranches,
                selectedProducingBranch = selectedProducingBranch,
                branchDropdownExpanded = branchDropdownExpanded,
                onBranchDropdownToggle = { branchDropdownExpanded = it },
                onBranchSelected = { branch ->
                    if (branch.id != selectedProducingBranchId) {
                        viewModel.clearDraftOrderLines()
                        selectedShiftId = null
                    }
                    selectedProducingBranchId = branch.id
                    branchDropdownExpanded = false
                },
                sellingBranches = state.sellingBranches,
                selectedSellingBranch = selectedSellingBranch,
                sellingBranchDropdownExpanded = sellingBranchDropdownExpanded,
                onSellingBranchDropdownToggle = { sellingBranchDropdownExpanded = it },
                onSellingBranchSelected = { selectedSellingBranchId = it.id; sellingBranchDropdownExpanded = false },
                shifts = state.shiftsForSelectedProducingBranch,
                selectedShift = selectedShift,
                onShiftSelected = { selectedShiftId = it.id },
                targetDate = targetDate,
                onDateChange = { targetDate = it },
                notes = notesText,
                onNotesChange = { notesText = it },
                isClosedDay = isClosedDay,
                onNext = {
                    if (selectedProducingBranch == null || selectedSellingBranch == null) return@OrderFormStep1
                    val branchShifts = state.shiftsForSelectedProducingBranch
                    if (branchShifts.isNotEmpty() && branchShifts.size != 2 && selectedShift == null) return@OrderFormStep1
                    if (isClosedDay) { showClosedDayDialog = true; return@OrderFormStep1 }
                    step = 2
                },
                singleSellingBranch = state.sellingBranches.size == 1
            )
            step == 2 -> OrderFormStep2(
                modifier = Modifier.padding(padding),
                isEdit = isEdit,
                headerSellingBranchName = if (isEdit) state.selectedOrder?.sellingBranchName.orEmpty()
                                          else selectedSellingBranch?.name.orEmpty(),
                headerProducingBranchName = if (isEdit) state.selectedOrder?.producingBranchName.orEmpty()
                                            else selectedProducingBranch?.name.orEmpty(),
                headerDateMillis = targetDate,
                shifts = state.shiftsForSelectedProducingBranch,
                selectedShift = selectedShift,
                onShiftSelected = { selectedShiftId = it.id },
                sellingBranchTripRestriction = sellingBranchTripRestriction,
                notes = notesText,
                onNotesChange = { notesText = it },
                products = displayProducts,
                recipeRefByProductId = state.recipeRefByProductId,
                searchQuery = searchQuery,
                onSearchChange = { searchQuery = it },
                draftLines = state.draftOrderLines,
                onLineChanged = { viewModel.updateDraftOrderLine(it) },
                isLoading = state.isLoading,
                rainExtraUnitsByProduct = state.rainExtraUnitsByProduct,
                expectedQuantityByProduct = state.expectedQuantityByProduct,
                onAddSpecial = { editingSpecialLine = null; specialDialogOpen = true },
                onEditSpecial = { line -> editingSpecialLine = line; specialDialogOpen = true },
                onRemoveSpecial = { productId -> viewModel.removeDraftOrderLine(productId) },
                onConfirm = {
                    if (state.draftOrderLines.isEmpty()) return@OrderFormStep2
                    step = 3
                },
                resellerPanel = {
                    if (state.pendingCustomerOrdersForSelling.isNotEmpty()) {
                        ResellerOrdersPanel(
                            orders = state.pendingCustomerOrdersForSelling,
                            linesByOrder = state.pendingCustomerOrderLinesByOrder,
                            selectedLineIds = state.selectedCustomerLineIds,
                            producingBranch = selectedProducingBranch,
                            allProducingBranches = state.producingBranches,
                            onSetSelectedLines = { coId, newSelectedLineIds ->
                                viewModel.setSelectedCustomerLinesForOrder(coId, newSelectedLineIds)
                            }
                        )
                    }
                }
            )
            else -> {
                // Merge en vivo: para el review necesitamos ver las líneas combinadas
                // (cantidad manual + cantidades de revendedores seleccionados) con sus
                // customerLineRefs. El merge real se hace dentro de saveOrder, pero acá
                // hacemos un preview idéntico para que la encargada vea el desglose.
                val mergedReviewLines = remember(
                    state.draftOrderLines,
                    state.selectedCustomerLineIds,
                    state.pendingCustomerOrderLinesByOrder,
                    state.pendingCustomerOrdersForSelling
                ) {
                    buildMergedReviewLines(state)
                }
                OrderReviewStep(
                modifier = Modifier.padding(padding),
                isEdit = isEdit,
                sellingBranchName = if (isEdit) state.selectedOrder?.sellingBranchName.orEmpty()
                                    else selectedSellingBranch?.name.orEmpty(),
                producingBranchName = if (isEdit) state.selectedOrder?.producingBranchName.orEmpty()
                                      else selectedProducingBranch?.name.orEmpty(),
                shiftName = selectedShift?.name.orEmpty(),
                dateMillis = targetDate,
                notes = notesText,
                lines = mergedReviewLines,
                recipeRefByProductId = state.recipeRefByProductId,
                isLoading = state.isLoading,
                onBack = { step = 2 },
                onSend = {
                    if (isEdit) {
                        val id = orderId ?: return@OrderReviewStep
                        viewModel.updateExistingOrder(companyId, id, selectedShift, notesText)
                    } else {
                        val producingBranch = selectedProducingBranch ?: return@OrderReviewStep
                        val sellingBranch = selectedSellingBranch ?: return@OrderReviewStep
                        val currentUser = user ?: return@OrderReviewStep
                        viewModel.saveOrder(
                            companyId, currentUser, sellingBranch, producingBranch,
                            selectedShift, targetDate, notesText
                        )
                    }
                }
            )
            }
        }
    }
    LoadingOverlay(visible = state.isLoading)
    }
}

/**
 * Construye las líneas combinadas para mostrar en el step 3 (review): toma las
 * cantidades manuales de la encargada (`draftOrderLines`) y les suma las líneas
 * de revendedor seleccionadas, agrupando por productId y persistiendo el
 * `customerLineRefs` por desglose. Es el MISMO algoritmo que usa `saveOrder`.
 */
private fun buildMergedReviewLines(
    state: ProductionUiState
): List<ProductionOrderLine> {
    val coLinesById = state.pendingCustomerOrderLinesByOrder.values.flatten()
        .associateBy { it.id }
    val coNamesById = state.pendingCustomerOrdersForSelling
        .associate { it.id to it.customerName }
    val coIdByLineId: Map<String, String> = buildMap {
        state.pendingCustomerOrderLinesByOrder.forEach { (coId, ls) ->
            ls.forEach { l -> put(l.id, coId) }
        }
    }

    val merged = state.draftOrderLines.toMutableList()
    state.selectedCustomerLineIds.forEach { coLineId ->
        val coLine = coLinesById[coLineId] ?: return@forEach
        if (!coLine.requiresProduction) return@forEach
        val coId = coIdByLineId[coLineId] ?: return@forEach
        val ref = com.bakery.erp.model.CustomerLineRef(
            customerOrderId = coId,
            customerName = coNamesById[coId] ?: "",
            customerOrderLineId = coLineId,
            quantity = coLine.requestedQuantity,
            customSpec = coLine.customSpec
        )
        val idx = merged.indexOfFirst { it.productId == coLine.productId }
        if (idx >= 0) {
            val existing = merged[idx]
            merged[idx] = existing.copy(
                requestedQuantity = existing.requestedQuantity + coLine.requestedQuantity,
                customerLineRefs = existing.customerLineRefs + ref
            )
        } else {
            merged += ProductionOrderLine(
                id = "",
                productId = coLine.productId,
                productName = coLine.productName,
                requestedQuantity = coLine.requestedQuantity,
                productUnit = coLine.productUnit,
                customerLineRefs = listOf(ref)
            )
        }
    }
    return merged
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OrderFormStep1(
    modifier: Modifier,
    producingBranches: List<Branch>,
    selectedProducingBranch: Branch?,
    branchDropdownExpanded: Boolean,
    onBranchDropdownToggle: (Boolean) -> Unit,
    onBranchSelected: (Branch) -> Unit,
    sellingBranches: List<Branch>,
    selectedSellingBranch: Branch?,
    sellingBranchDropdownExpanded: Boolean,
    onSellingBranchDropdownToggle: (Boolean) -> Unit,
    onSellingBranchSelected: (Branch) -> Unit,
    shifts: List<ProductionShift>,
    selectedShift: ProductionShift?,
    onShiftSelected: (ProductionShift) -> Unit,
    targetDate: Long,
    onDateChange: (Long) -> Unit,
    notes: String,
    onNotesChange: (String) -> Unit,
    isClosedDay: Boolean = false,
    onNext: () -> Unit,
    singleSellingBranch: Boolean = false
) {
    AdaptiveContainer(modifier = modifier) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        when {
            singleSellingBranch -> {
                Text("Pedido desde", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = BrandColors.Primary)
                Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            selectedSellingBranch?.name ?: sellingBranches.firstOrNull()?.name ?: "",
                            fontWeight = FontWeight.SemiBold,
                            color = BrandColors.Primary
                        )
                    }
                }
            }
            // Sin este caso la sección quedaba COMPLETAMENTE en blanco mientras cargaban las
            // sucursales —ni etiqueta ni indicador—, y la espera se leía como que la pantalla
            // estaba colgada. Cubre también el caso de no tener ninguna asignada, que antes
            // tampoco decía nada.
            sellingBranches.isEmpty() -> {
                Text(
                    "¿Desde qué sucursal?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = BrandColors.Primary
                )
                Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = BrandColors.Primary
                        )
                        Text(
                            "Cargando sucursales…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            sellingBranches.size > 1 -> {
                Text("¿Desde qué sucursal?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = BrandColors.Primary)
                ExposedDropdownMenuBox(
                    expanded = sellingBranchDropdownExpanded,
                    onExpandedChange = onSellingBranchDropdownToggle
                ) {
                    OutlinedTextField(
                        value = selectedSellingBranch?.name ?: "Seleccionar mi sucursal",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Sucursal solicitante *") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(sellingBranchDropdownExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = sellingBranchDropdownExpanded,
                        onDismissRequest = { onSellingBranchDropdownToggle(false) }
                    ) {
                        sellingBranches.forEach { branch ->
                            DropdownMenuItem(
                                text = { Text(branch.name) },
                                onClick = { onSellingBranchSelected(branch) }
                            )
                        }
                    }
                }
            }
        }

        Text("¿A qué planta va el pedido?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = BrandColors.Primary)

        ExposedDropdownMenuBox(
            expanded = branchDropdownExpanded,
            onExpandedChange = onBranchDropdownToggle
        ) {
            OutlinedTextField(
                value = selectedProducingBranch?.name ?: "Seleccionar planta productora",
                onValueChange = {},
                readOnly = true,
                label = { Text("Planta productora *") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(branchDropdownExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = branchDropdownExpanded,
                onDismissRequest = { onBranchDropdownToggle(false) }
            ) {
                producingBranches.forEach { branch ->
                    DropdownMenuItem(
                        text = { Text(branch.name) },
                        onClick = { onBranchSelected(branch) }
                    )
                }
            }
        }

        // Selector de turno cuando la planta productora tiene turnos configurados.
        // Con exactamente 2 turnos activos la planta reparte automático — no hay
        // nada que elegir acá, solo un aviso.
        if (selectedProducingBranch != null && shifts.size == 2) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFEAF3F8), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${selectedProducingBranch.name} reparte tu pedido automáticamente entre sus " +
                        "${shifts.size} turnos.",
                    style = MaterialTheme.typography.bodySmall,
                    color = BrandColors.Primary
                )
            }
        } else {
            ShiftSelector(
                shifts = shifts,
                selectedShift = selectedShift,
                onShiftSelected = onShiftSelected,
                visible = selectedProducingBranch != null
            )
        }

        Spacer(Modifier.height(8.dp))
        Text("¿Para qué fecha?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = BrandColors.Primary)

        Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { onDateChange(targetDate - 86_400_000L) }) {
                    Icon(Icons.Default.ChevronLeft, "Día anterior")
                }
                Text(
                    formatDate(targetDate),
                    fontWeight = FontWeight.SemiBold,
                    color = BrandColors.Primary,
                    style = MaterialTheme.typography.titleMedium
                )
                IconButton(onClick = { onDateChange(targetDate + 86_400_000L) }) {
                    Icon(Icons.Default.ChevronRight, "Día siguiente")
                }
            }
        }

        if (isClosedDay) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFFBE9E7), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Día de descanso — no se puede pedir para esta fecha.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFC62828),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        OutlinedTextField(
            value = notes,
            onValueChange = onNotesChange,
            label = { Text("Notas (opcional)") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3
        )

        Spacer(Modifier.height(24.dp))

        // Con 2 turnos activos no hay nada que elegir — el reparto es automático.
        val shiftRequired = shifts.isNotEmpty() && shifts.size != 2 && selectedShift == null
        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BrandColors.Primary),
            enabled = selectedProducingBranch != null && selectedSellingBranch != null &&
                !shiftRequired && !isClosedDay
        ) {
            Text(
                when {
                    isClosedDay -> "Día de descanso — elige otra fecha"
                    shiftRequired -> "Selecciona un turno"
                    else -> "Continuar → Agregar productos"
                },
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(8.dp))
    }
    }
}

@Composable
private fun ShiftSelector(
    shifts: List<ProductionShift>,
    selectedShift: ProductionShift?,
    onShiftSelected: (ProductionShift) -> Unit,
    visible: Boolean
) {
    if (!visible) return
    if (shifts.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("¿Para qué turno?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = BrandColors.Primary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            shifts.forEach { shift ->
                val selected = selectedShift?.id == shift.id
                FilterChip(
                    selected = selected,
                    onClick = { onShiftSelected(shift) },
                    label = {
                        Column {
                            Text(shift.name, fontWeight = FontWeight.SemiBold)
                            if (shift.startTime.isNotBlank() && shift.endTime.isNotBlank()) {
                                Text(
                                    "${shift.startTime} – ${shift.endTime}",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = BrandColors.Primary,
                        selectedLabelColor = Color.White
                    )
                )
            }
        }
    }
}

@Composable
private fun OrderFormStep2(
    modifier: Modifier,
    isEdit: Boolean,
    headerSellingBranchName: String,
    headerProducingBranchName: String,
    headerDateMillis: Long,
    shifts: List<ProductionShift>,
    selectedShift: ProductionShift?,
    onShiftSelected: (ProductionShift) -> Unit,
    /** Viaje único en que esta sucursal recibe ([Branch.receivesOnlyInTrip]); 0 = los dos. */
    sellingBranchTripRestriction: Int = DISPATCH_TRIP_BOTH,
    notes: String,
    onNotesChange: (String) -> Unit,
    products: List<Product>,
    /** `productId` → masa, para agrupar la lista por tipo de masa. */
    recipeRefByProductId: Map<String, RecipeRef>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    draftLines: List<ProductionOrderLine>,
    onLineChanged: (ProductionOrderLine) -> Unit,
    isLoading: Boolean,
    /** `productId` → unidades de más por lluvia, para mostrarlo en la fila del producto. */
    rainExtraUnitsByProduct: Map<String, Int> = emptyMap(),
    /** `productId` → cantidad típica histórica, para el aviso de cantidad inusual por línea. */
    expectedQuantityByProduct: Map<String, Double> = emptyMap(),
    /** Abre el diálogo de alta de un pedido especial (pan fuera de catálogo). */
    onAddSpecial: () -> Unit = {},
    onEditSpecial: (ProductionOrderLine) -> Unit = {},
    onRemoveSpecial: (productId: String) -> Unit = {},
    onConfirm: () -> Unit,
    /** Panel opcional renderizado encima del editor de líneas. Usado para mostrar
     *  pedidos de revendedores pendientes que la encargada puede incluir. */
    resellerPanel: @Composable () -> Unit = {}
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // En edición + pantalla ancha: dos columnas (info izquierda, productos derecha).
        // En crear o pantalla angosta: una sola columna con el container adaptativo.
        val useTwoColumns = isEdit && maxWidth >= 800.dp

        @Composable
        fun EditHeaderSection(modifier: Modifier = Modifier) {
            Column(
                modifier = modifier,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "$headerSellingBranchName → $headerProducingBranchName",
                            fontWeight = FontWeight.SemiBold,
                            color = BrandColors.Primary
                        )
                        Text(
                            "Para el ${formatDate(headerDateMillis)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Sucursal origen, planta y fecha no se editan. Si te equivocaste, " +
                                "cancela y crea uno nuevo.",
                            style = MaterialTheme.typography.labelSmall,
                            color = BrandColors.AccentDeep
                        )
                    }
                }
                if (shifts.isNotEmpty() && shifts.size != 2) {
                    ShiftSelector(
                        shifts = shifts,
                        selectedShift = selectedShift,
                        onShiftSelected = onShiftSelected,
                        visible = true
                    )
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = onNotesChange,
                    label = { Text("Notas (opcional)") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
            }
        }

        @Composable
        fun ProductsSection(modifier: Modifier = Modifier) {
            Column(modifier = modifier) {
                // La planta reparte el día en dos viajes, pero esta sucursal solo
                // recibe en uno: se dice acá para que quien pide sepa a qué hora
                // le llega, sin tener que preguntarlo en la planta.
                if (shifts.size == 2 && sellingBranchTripRestriction != DISPATCH_TRIP_BOTH) {
                    val tripName = if (sellingBranchTripRestriction == DISPATCH_TRIP_FIRST)
                        shifts.first().name else shifts[1].name
                    Surface(
                        color = Color(0xFFEAF2F8),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    ) {
                        Text(
                            "Esta sucursal recibe todo su pedido en $tripName. No se reparte " +
                                "entre los dos viajes.",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = BrandColors.Primary
                        )
                    }
                }
                // Panel de pedidos de revendedores pendientes (si los hay).
                resellerPanel()
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    label = { Text("Buscar producto") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    singleLine = true
                )
                val totalItems = draftLines.sumOf { it.requestedQuantity }.toInt()
                if (totalItems > 0) {
                    Text(
                        "$totalItems unidades en el pedido",
                        style = MaterialTheme.typography.labelMedium,
                        color = BrandColors.AccentDeep,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                val focusManager = LocalFocusManager.current
                val groups = remember(products, recipeRefByProductId) {
                    groupProductsByRecipe(products, recipeRefByProductId)
                }
                // Se guardan las masas PLEGADAS (no las abiertas): así una masa
                // nueva aparece abierta por defecto sin tener que tocar nada.
                var collapsedGroups by remember { mutableStateOf(emptySet<String>()) }
                val lastProductId = groups.lastOrNull()?.items?.lastOrNull()?.id

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Los especiales van DENTRO de la lista, no en el encabezado
                    // fijo: son lo primero que se ve al entrar (que es cuando la
                    // encargada los tiene frescos) pero se van con el scroll en
                    // vez de comerse pantalla mientras escribe 40 cantidades.
                    item(key = "__pedidos_especiales__") {
                        SpecialOrdersSection(
                            lines = draftLines.filter { it.isSpecial },
                            onAdd = onAddSpecial,
                            onEdit = onEditSpecial,
                            onRemove = onRemoveSpecial
                        )
                    }
                    groups.forEach { group ->
                        val collapsed = group.key in collapsedGroups
                        item(key = "header_${group.key}") {
                            RecipeGroupHeader(
                                title = group.title,
                                productCount = group.items.size,
                                // Los especiales de esta masa suman acá aunque no
                                // tengan fila propia en la lista: es el mismo número
                                // que después decide si la amasada llega al mínimo,
                                // y dejarlos fuera lo haría mentir por lo bajo.
                                unitsInOrder = (
                                    group.items.sumOf { p ->
                                        draftLines.find { it.productId == p.id }?.requestedQuantity ?: 0.0
                                    } + draftLines
                                        .filter { it.isSpecial && it.specialRecipeId == group.key }
                                        .sumOf { it.requestedQuantity }
                                    ).toInt(),
                                collapsed = collapsed,
                                onToggle = {
                                    collapsedGroups = if (collapsed) collapsedGroups - group.key
                                                      else collapsedGroups + group.key
                                }
                            )
                        }
                        if (collapsed) return@forEach
                        items(group.items, key = { it.id }) { product ->
                            val existingLine = draftLines.find { it.productId == product.id }
                            ProductOrderRow(
                                product = product,
                                quantity = existingLine?.requestedQuantity ?: 0.0,
                                expectedQuantity = expectedQuantityByProduct[product.id],
                                rainExtraUnits = rainExtraUnitsByProduct[product.id] ?: 0,
                                isLast = product.id == lastProductId,
                                onImeNext = {
                                    // El teclado avanza al campo de abajo en vez de cerrarse.
                                    if (!focusManager.moveFocus(FocusDirection.Down)) {
                                        focusManager.clearFocus()
                                    }
                                },
                                onImeDone = { focusManager.clearFocus() },
                                onQuantityChange = { qty ->
                                    onLineChanged(
                                        ProductionOrderLine(
                                            id = existingLine?.id ?: "",
                                            productId = product.id,
                                            productName = product.name,
                                            requestedQuantity = qty,
                                            productUnit = product.unit,
                                            // Bajar la cantidad total no puede dejar un
                                            // encargo mayor que lo pedido.
                                            earlyShiftQuantity = existingLine?.earlyShiftQuantity
                                                ?.coerceAtMost(qty),
                                            earlyShiftNote = existingLine?.earlyShiftNote ?: ""
                                        )
                                    )
                                },
                                // El encargo por viaje solo aplica cuando la planta tiene
                                // 2 turnos: son los pedidos que pasan por el reparto. Y no
                                // aplica si esta sucursal solo recibe en un viaje — ahí no
                                // hay nada que repartir, y ofrecerlo invitaría a declarar un
                                // encargo para una hora en que está cerrada.
                                earlyShiftEnabled = shifts.size == 2 &&
                                    sellingBranchTripRestriction == DISPATCH_TRIP_BOTH,
                                earlyShiftName = shifts.firstOrNull()?.name ?: "Primer viaje",
                                lateShiftName = shifts.getOrNull(1)?.name ?: "Segundo viaje",
                                earlyShiftQuantity = existingLine?.earlyShiftQuantity,
                                earlyShiftNote = existingLine?.earlyShiftNote ?: "",
                                onEarlyShiftChange = { earlyQty, note ->
                                    val base = existingLine ?: return@ProductOrderRow
                                    onLineChanged(
                                        base.copy(
                                            earlyShiftQuantity = earlyQty
                                                ?.coerceIn(0.0, base.requestedQuantity),
                                            earlyShiftNote = note
                                        )
                                    )
                                }
                            )
                        }
                    }
                }
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandColors.Primary),
                    enabled = draftLines.isNotEmpty() && !isLoading
                ) {
                    Text(
                        if (isEdit) "Revisar cambios (${draftLines.size} productos) →"
                        else "Revisar pedido (${draftLines.size} productos) →",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (useTwoColumns) {
            Row(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(end = 12.dp)
                ) {
                    EditHeaderSection(modifier = Modifier.fillMaxWidth())
                }
                VerticalDivider(color = Color(0xFFE0E0E0))
                ProductsSection(
                    modifier = Modifier
                        .weight(1.2f)
                        .fillMaxHeight()
                        .padding(start = 12.dp)
                )
            }
        } else {
            AdaptiveContainer {
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    if (isEdit) {
                        EditHeaderSection(modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(12.dp))
                    }
                    ProductsSection(modifier = Modifier.weight(1f).fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun OrderReviewStep(
    modifier: Modifier,
    isEdit: Boolean,
    sellingBranchName: String,
    producingBranchName: String,
    shiftName: String,
    dateMillis: Long,
    notes: String,
    lines: List<ProductionOrderLine>,
    /** `productId` → masa, para agrupar el resumen igual que el paso anterior. */
    recipeRefByProductId: Map<String, RecipeRef>,
    isLoading: Boolean,
    onBack: () -> Unit,
    onSend: () -> Unit
) {
    AdaptiveContainer(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Aviso de revisión.
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFFFFF8E1),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "⚠",
                        style = MaterialTheme.typography.titleLarge,
                        color = BrandColors.AccentDeep
                    )
                    Text(
                        "Revisa bien tu pedido antes de enviarlo. Una vez enviado, " +
                            "la planta productora podrá tomarlo y modificarlo será limitado.",
                        style = MaterialTheme.typography.bodySmall,
                        color = BrandColors.AccentDeep,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Datos generales del pedido.
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Resumen del pedido",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = BrandColors.Primary
                    )
                    HorizontalDivider()
                    ReviewRow("Sucursal solicitante", sellingBranchName.ifBlank { "—" })
                    ReviewRow("Planta productora", producingBranchName.ifBlank { "—" })
                    if (shiftName.isNotBlank()) ReviewRow("Turno", shiftName)
                    ReviewRow("Para el día", formatDate(dateMillis))
                    if (notes.isNotBlank()) ReviewRow("Notas", notes)
                }
            }

            // Productos solicitados.
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Productos (${lines.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = BrandColors.Primary
                        )
                        val totalUnits = lines.sumOf { it.requestedQuantity }.toInt()
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        ) {
                            Text(
                                "$totalUnits unidades total",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = BrandColors.Primary
                            )
                        }
                    }
                    HorizontalDivider()
                    // Mismo agrupado por masa que el paso de captura: permite
                    // chequear de un vistazo "¿pedí suficiente hojaldre?" antes
                    // de enviar, que es la pregunta que decide si la amasada
                    // llega al mínimo.
                    val groups = remember(lines, recipeRefByProductId) {
                        groupReviewLinesByRecipe(lines, recipeRefByProductId)
                    }
                    groups.forEach { group ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                group.title.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF7A8B96)
                            )
                            Text(
                                "${group.items.sumOf { it.requestedQuantity }.toInt()} u.",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF7A8B96)
                            )
                        }
                        group.items.forEachIndexed { index, line ->
                            val resellerQty = line.customerLineRefs.sumOf { it.quantity }
                            val ownQty = (line.requestedQuantity - resellerQty).coerceAtLeast(0.0)
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
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
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                        Text(
                                            line.productName,
                                            fontWeight = FontWeight.SemiBold,
                                            color = BrandColors.Primary,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                    if (line.isSpecial) {
                                        Text(
                                            "Especial · ${formatBakerWeight(line.specialRawWeightGrams)} c/u",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = BrandColors.AccentDeep,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    if (line.productUnit.isNotBlank()) {
                                        Text(
                                            line.productUnit,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.Gray
                                        )
                                    }
                                    if (line.customerLineRefs.isNotEmpty()) {
                                        Text(
                                            "Tuyo: ${ownQty.toInt()}  +  Revendedores: ${resellerQty.toInt()}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = BrandColors.AccentDeep,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                                Text(
                                    "${line.requestedQuantity.toInt()}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = BrandColors.AccentDeep
                                )
                            }
                            if (index < group.items.lastIndex) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                )
                            }
                        }
                    }
                }
            }

            // Desglose por revendedor (si hay).
            val resellerRefs = lines.flatMap { line ->
                line.customerLineRefs.map { ref -> ref to line }
            }
            if (resellerRefs.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1)),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFFFFE0B2))
                ) {
                    Column(
                        Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            "Sumado de revendedores",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = BrandColors.AccentDeep
                        )
                        HorizontalDivider()
                        val byCustomer = resellerRefs.groupBy { it.first.customerName }
                        byCustomer.forEach { (name, refs) ->
                            Text(
                                name.ifEmpty { "Revendedor" },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = BrandColors.Primary,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            refs.forEach { (ref, line) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .padding(start = 12.dp, top = 2.dp, bottom = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        line.productName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF424242)
                                    )
                                    Text(
                                        "${ref.quantity.toInt()} ${line.productUnit}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = BrandColors.AccentDeep,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                if (ref.customSpec.isNotEmpty()) {
                                    Text(
                                        "✱ ${ref.customSpec}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = BrandColors.AccentDeep,
                                        modifier = Modifier.padding(start = 12.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Botones: Editar (izquierda) + Enviar (derecha, azul tema).
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onBack,
                    enabled = !isLoading,
                    modifier = Modifier.weight(1f).height(52.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandColors.Primary)
                ) {
                    Text(
                        if (isEdit) "Editar cambios" else "Editar pedido",
                        fontWeight = FontWeight.Bold
                    )
                }
                Button(
                    onClick = onSend,
                    enabled = !isLoading && lines.isNotEmpty(),
                    modifier = Modifier.weight(1.3f).height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandColors.Primary)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = Color.White,
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        Text(
                            if (isEdit) "Enviar cambios" else "Enviar pedido",
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ReviewRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = BrandColors.Primary
        )
    }
}

private fun groupProductsByRecipe(
    products: List<Product>,
    recipeRefByProductId: Map<String, RecipeRef>
): List<MasaGroup<Product>> = groupByMasa(
    items = products,
    recipeIdOf = { recipeRefByProductId[it.id]?.id },
    recipeNameOf = { recipeRefByProductId[it.id]?.name },
    nameOf = { it.name }
)

private fun groupReviewLinesByRecipe(
    lines: List<ProductionOrderLine>,
    recipeRefByProductId: Map<String, RecipeRef>
): List<MasaGroup<ProductionOrderLine>> = groupByMasa(
    items = lines,
    // Un pedido especial no tiene entrada en el mapa producto→masa (su productId
    // es sintético): su masa viene declarada en la propia línea, y es la que lo
    // pone junto a los panes con los que se va a amasar.
    recipeIdOf = { line ->
        if (line.isSpecial) line.specialRecipeId.takeIf { it.isNotEmpty() }
        else recipeRefByProductId[line.productId]?.id
    },
    recipeNameOf = { line ->
        if (line.isSpecial) line.specialRecipeName.takeIf { it.isNotBlank() }
        else recipeRefByProductId[line.productId]?.name
    },
    nameOf = { it.productName }
)

/**
 * Encabezado de una masa. Muestra cuántas unidades de esa masa ya lleva el
 * pedido para que la encargada vea el peso de cada masa sin sumar de cabeza —
 * es justo el número que después decide si la amasada llega al mínimo.
 */
@Composable
private fun RecipeGroupHeader(
    title: String,
    productCount: Int,
    unitsInOrder: Int,
    collapsed: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        color = Color(0xFFEAF3F8),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (collapsed) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                contentDescription = if (collapsed) "Mostrar $title" else "Ocultar $title",
                tint = BrandColors.Primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    fontWeight = FontWeight.Bold,
                    color = BrandColors.Primary,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    if (productCount == 1) "1 producto" else "$productCount productos",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (unitsInOrder > 0) {
                Surface(shape = RoundedCornerShape(20.dp), color = BrandColors.Accent) {
                    Text(
                        "$unitsInOrder u.",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = BrandColors.OnAccent
                    )
                }
            }
        }
    }
}

/**
 * Qué tan lejos tiene que estar una cantidad de lo típico para avisar.
 *
 * Un solo % no funciona en todo el rango: con lo típico en 6, pasar a 8 ya es
 * +33% pero son 2 piezas — nada raro. Por eso exige **las dos** condiciones:
 * un % mínimo Y una diferencia absoluta mínima. La absoluta filtra el ruido
 * en cantidades chicas; el % filtra que en cantidades grandes no avise por
 * variaciones normales del día a día (ajustado a ojo, ver feedback del dueño).
 */
private const val UNUSUAL_QUANTITY_RATIO = 0.40
private const val UNUSUAL_QUANTITY_MIN_DIFF = 5.0

private fun unusualQuantityMessage(quantity: Double, expected: Double?): String? {
    if (expected == null || expected <= 0.0 || quantity <= 0.0) return null
    val diff = quantity - expected
    if (kotlin.math.abs(diff) < UNUSUAL_QUANTITY_MIN_DIFF) return null
    val ratio = diff / expected
    if (ratio > UNUSUAL_QUANTITY_RATIO) {
        return "Estás pidiendo ${quantity.toInt()} — lo habitual es ~${expected.toInt()}. Revisa que sea el producto correcto."
    }
    if (ratio < -UNUSUAL_QUANTITY_RATIO) {
        return "Estás pidiendo ${quantity.toInt()} — bastante menos de lo habitual (~${expected.toInt()}). Revisa que sea el producto correcto."
    }
    return null
}

@Composable
private fun ProductOrderRow(
    product: Product,
    quantity: Double,
    onQuantityChange: (Double) -> Unit,
    /** Cantidad típica histórica de este producto ese día (null = sin historial suficiente). */
    expectedQuantity: Double? = null,
    /** Unidades de más (de las ya sugeridas) por el ajuste de lluvia — 0 = no aplica a este producto. */
    rainExtraUnits: Int = 0,
    /** Último producto de la lista: el teclado muestra "Listo" en vez de "Siguiente". */
    isLast: Boolean = false,
    onImeNext: () -> Unit = {},
    onImeDone: () -> Unit = {},
    /** La planta tiene 2 turnos, así que este pedido se reparte entre dos viajes. */
    earlyShiftEnabled: Boolean = false,
    /** Nombres de los turnos, en orden de salida del camión, como los nombra la planta. */
    earlyShiftName: String = "",
    lateShiftName: String = "",
    earlyShiftQuantity: Double? = null,
    earlyShiftNote: String = "",
    onEarlyShiftChange: (Double?, String) -> Unit = { _, _ -> }
) {
    var qtyText by remember(quantity) {
        mutableStateOf(if (quantity > 0) quantity.toInt().toString() else "")
    }
    val unusualMessage = remember(quantity, expectedQuantity) {
        unusualQuantityMessage(quantity, expectedQuantity)
    }
    // % real sobre la cantidad ya sugerida (no siempre calza exacto en el
    // redondeo con RAIN_BUMP_FRACTION) — más honesto que mostrar un 15% fijo.
    val rainExtraPercent = remember(rainExtraUnits, expectedQuantity) {
        expectedQuantity?.takeIf { it > 0 }
            ?.let { (rainExtraUnits / it * 100).roundToInt() }
    }

    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(10.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(product.name, fontWeight = FontWeight.SemiBold, color = BrandColors.Primary)
                    Text(product.unit, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    if (rainExtraUnits > 0) {
                        Text(
                            "Te recomendamos aumentar $rainExtraUnits" +
                                (rainExtraPercent?.let { " (+$it%)" } ?: "") +
                                " por pronóstico de lluvia",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF0D6EAA)
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = qtyText,
                    onValueChange = { text ->
                        qtyText = text
                        onQuantityChange(text.toDoubleOrNull() ?: 0.0)
                    },
                    label = { Text("Cant.") },
                    isError = unusualMessage != null,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = if (isLast) ImeAction.Done else ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { onImeNext() },
                        onDone = { onImeDone() }
                    ),
                    modifier = Modifier.width(90.dp),
                    singleLine = true
                )
            }
            if (unusualMessage != null) {
                Text(
                    "⚠ $unusualMessage",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFC62828),
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            if (earlyShiftEnabled && quantity > 0) {
                EarlyShiftField(
                    totalQuantity = quantity,
                    shiftName = earlyShiftName,
                    lateShiftName = lateShiftName,
                    earlyQuantity = earlyShiftQuantity,
                    note = earlyShiftNote,
                    onChange = onEarlyShiftChange
                )
            }
        }
    }
}

/**
 * Reparto por viaje que declara la **sucursal** en su propio pedido.
 *
 * Los **dos viajes se editan**, no solo el primero: pedir más al mediodía es un
 * caso tan real como pedir más temprano, y con un único campo "en el primer viaje"
 * había que resolverlo de cabeza restando. Se guarda uno solo
 * ([ProductionOrderLine.earlyShiftQuantity]) y el otro se deriva, así los dos nunca
 * pueden sumar algo distinto de lo pedido.
 *
 * Colapsado por defecto — la enorme mayoría de las líneas no tiene encargo y no
 * debe pagar ningún costo de pantalla por esta función. Solo se abre solo cuando
 * ya hay algo declarado.
 */
@Composable
private fun EarlyShiftField(
    totalQuantity: Double,
    shiftName: String,
    lateShiftName: String,
    earlyQuantity: Double?,
    note: String,
    onChange: (Double?, String) -> Unit
) {
    var expanded by rememberSaveable(earlyQuantity != null) { mutableStateOf(earlyQuantity != null) }

    if (!expanded) {
        TextButton(
            onClick = { expanded = true },
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
        ) {
            Text(
                "¿Necesitás más en un viaje que en el otro?",
                style = MaterialTheme.typography.labelSmall,
                color = BrandColors.AccentDeep,
                fontWeight = FontWeight.SemiBold
            )
        }
        return
    }

    // Sin declarar, el default es mitad y mitad — el mismo que aplica la planta.
    val early = earlyQuantity ?: (totalQuantity / 2).roundToInt().toDouble()
    val late = (totalQuantity - early).coerceAtLeast(0.0)

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFFFBF1DC),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Reparto por viaje · ${totalQuantity.toInt()} en total",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = BrandColors.AccentDeep
                )
                TextButton(onClick = {
                    expanded = false
                    onChange(null, "")
                }) {
                    Text("Quitar", style = MaterialTheme.typography.labelSmall)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TripQuantityField(
                    label = shiftName,
                    value = early,
                    onValueChange = { onChange(it, note) },
                    modifier = Modifier.weight(1f)
                )
                TripQuantityField(
                    label = lateShiftName,
                    value = late,
                    // Escribir el segundo viaje define el primero por resta: el dato
                    // guardado sigue siendo uno solo.
                    onValueChange = { onChange((totalQuantity - it).coerceAtLeast(0.0), note) },
                    modifier = Modifier.weight(1f)
                )
            }
            OutlinedTextField(
                value = note,
                onValueChange = { onChange(earlyQuantity ?: early, it) },
                label = { Text("¿Por qué? (opcional)") },
                placeholder = { Text("Encargo doña María 7am") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** Caja de cantidad de un viaje. Al tipear se propaga; el otro viaje se recalcula. */
@Composable
private fun TripQuantityField(
    label: String,
    value: Double,
    onValueChange: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    var text by remember(value) { mutableStateOf(value.toInt().toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { newText ->
            text = newText
            newText.toDoubleOrNull()?.let(onValueChange)
        },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = modifier
    )
}

/**
 * Sugerencias del historial + pronóstico de lluvia, dichos UNA vez al entrar al
 * paso de productos.
 *
 * Antes vivían como dos tarjetas fijas encima del formulario y se llevaban la
 * primera pantalla completa: lo que la encargada viene a hacer —escribir
 * cantidades— quedaba abajo del pliegue todos los días, para leer algo que solo
 * importa la primera vez. Como diálogo se lee una vez, se acepta y se sale del
 * camino; el ícono de nube del TopAppBar lo vuelve a abrir.
 *
 * Lo que NO se movió acá es la recomendación por lluvia de cada producto: ésa es
 * accionable justo donde está la caja de cantidad, y sacarla obligaría a
 * recordar un número por producto.
 */
@Composable
private fun OrderInsightsDialog(
    suggestionApplied: Boolean,
    suggestionWeekday: Int,
    suggestionSampleCount: Int,
    rainProbabilityPercent: Int,
    rainExtraUnits: Int,
    rainPeriods: List<ForecastPeriod>,
    onClearSuggestion: () -> Unit,
    onAccept: () -> Unit
) {
    val showRain = rainProbabilityPercent > 0 || rainPeriods.isNotEmpty()
    AlertDialog(
        onDismissRequest = onAccept,
        containerColor = BrandColors.Ivory,
        title = {
            Text(
                "Antes de escribir cantidades",
                fontWeight = FontWeight.Bold,
                color = BrandColors.Primary
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (suggestionApplied) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFFFF8E1),
                        border = BorderStroke(1.dp, Color(0xFFFFE0B2)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("⚠", style = MaterialTheme.typography.titleMedium, color = BrandColors.AccentDeep)
                                Text(
                                    "Cantidades sugeridas automáticamente",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF6B4C00)
                                )
                            }
                            Text(
                                "Se prellenaron con lo que esta sucursal suele pedir los " +
                                    "${isoWeekdayName(suggestionWeekday)}" +
                                    (if (suggestionSampleCount > 0) " (últimos $suggestionSampleCount pedidos de ese día)" else "") +
                                    ". Revisa bien producto por producto antes de enviar: " +
                                    "el clima, feriados o eventos cambian la demanda.",
                                style = MaterialTheme.typography.bodySmall,
                                color = BrandColors.AccentDeep,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                    }
                }
                if (showRain) {
                    RainForecastCard(
                        rainProbabilityPercent = rainProbabilityPercent,
                        extraUnits = rainExtraUnits,
                        periods = rainPeriods
                    )
                }
            }
        },
        confirmButton = {
            // Relleno y en azul de marca: es la salida esperada del diálogo y la que
            // deja escribir cantidades. "Empezar de cero" queda como texto, que es lo
            // que corresponde a la acción destructiva de las dos.
            Button(
                onClick = onAccept,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrandColors.Primary,
                    contentColor = Color.White
                )
            ) {
                Text("Aceptar", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = if (suggestionApplied) {
            {
                TextButton(onClick = onClearSuggestion) {
                    Text("Empezar de cero", color = BrandColors.AccentDeep)
                }
            }
        } else null
    )
}

/**
 * Pedidos especiales del pedido en curso.
 *
 * Va arriba de la lista de productos —y no escondida al final— porque un
 * especial es justamente lo que la encargada tiene fresco cuando entra a hacer
 * el pedido: se lo acaban de encargar. Si estuviera al final, se anotaría
 * después de haber escrito 40 cantidades, o no se anotaría.
 */
@Composable
private fun SpecialOrdersSection(
    lines: List<ProductionOrderLine>,
    onAdd: () -> Unit,
    onEdit: (ProductionOrderLine) -> Unit,
    onRemove: (productId: String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        lines.forEach { line ->
            SpecialOrderCard(
                line = line,
                onEdit = { onEdit(line) },
                onRemove = { onRemove(line.productId) }
            )
        }
        OutlinedButton(
            onClick = onAdd,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, BrandColors.Accent)
        ) {
            Icon(
                Icons.Default.Star,
                contentDescription = null,
                tint = BrandColors.AccentDeep,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (lines.isEmpty()) "Agregar pedido especial"
                else "Agregar otro pedido especial",
                color = BrandColors.AccentDeep,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.labelLarge
            )
        }
        if (lines.isEmpty()) {
            Text(
                "Un pan que no está en la lista: la misma masa, en otra figura o tamaño.",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF9E9E9E)
            )
        }
    }
}

/**
 * Tarjeta del pronóstico dentro del diálogo de [OrderInsightsDialog] — a
 * propósito NO es un renglón más del aviso ámbar de sugerencias: el clima es un
 * cálculo distinto al historial de pedidos y tiene que notarse de un vistazo.
 * Paleta azul "de tormenta", deliberadamente distinta del ámbar de las
 * sugerencias, y el "+N" a la derecha es lo primero que se ve.
 */
private fun conditionIcon(condition: WeatherCondition): ImageVector = when (condition) {
    WeatherCondition.CLEAR -> Icons.Default.WbSunny
    WeatherCondition.PARTLY_CLOUDY -> Icons.Default.FilterDrama
    WeatherCondition.CLOUDY -> Icons.Default.Cloud
    WeatherCondition.DRIZZLE -> Icons.Default.Grain
    WeatherCondition.RAIN -> Icons.Default.Umbrella
    WeatherCondition.STORM -> Icons.Default.Thunderstorm
}

private fun conditionLabel(condition: WeatherCondition): String = when (condition) {
    WeatherCondition.CLEAR -> "Despejado"
    WeatherCondition.PARTLY_CLOUDY -> "Parc. nublado"
    WeatherCondition.CLOUDY -> "Nublado"
    WeatherCondition.DRIZZLE -> "Llovizna"
    WeatherCondition.RAIN -> "Lluvia"
    WeatherCondition.STORM -> "Tormenta"
}

private fun conditionColor(condition: WeatherCondition): Color = when (condition) {
    WeatherCondition.CLEAR -> BrandColors.AccentDeep
    WeatherCondition.PARTLY_CLOUDY -> Color(0xFF6B8CAE)
    WeatherCondition.CLOUDY -> Color(0xFF5A7A99)
    WeatherCondition.DRIZZLE -> Color(0xFF3B8EC2)
    WeatherCondition.RAIN -> Color(0xFF0D6EAA)
    WeatherCondition.STORM -> Color(0xFF0D3B70)
}

@Composable
private fun WeatherPeriodChip(period: ForecastPeriod, modifier: Modifier = Modifier) {
    val color = conditionColor(period.condition)
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color.White,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                period.label,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF3B6E96),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(
                conditionIcon(period.condition),
                contentDescription = conditionLabel(period.condition),
                tint = color,
                modifier = Modifier.size(20.dp)
            )
            // Cuatro chips repartidos a partes iguales dejan muy poco ancho a cada uno:
            // sin esto, un "100%" se parte y se apila igual que pasaba en el encabezado.
            Text(
                "${period.rainProbabilityPercent}%",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = color,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun RainForecastCard(
    rainProbabilityPercent: Int,
    extraUnits: Int,
    periods: List<ForecastPeriod> = emptyList()
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Color(0xFFA9CDEC)),
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.linearGradient(listOf(BrandColors.PrimaryContainer, Color(0xFFAED9F5)))
                )
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier.size(48.dp).clip(CircleShape).background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painterResource(Res.drawable.rain_icon),
                        contentDescription = null,
                        modifier = Modifier.size(34.dp)
                    )
                }
                Column(Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // El título lleva el weight y el porcentaje NO: así el que cede
                        // espacio y se parte en dos renglones es el texto largo. Al revés
                        // —que es como estaba— el "100%" se quedaba sin ancho y Compose lo
                        // rompía carácter por carácter, dejándolo en vertical.
                        Text(
                            "Lluvia probable",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0D47A1),
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Surface(shape = RoundedCornerShape(50), color = Color.White) {
                            Text(
                                "$rainProbabilityPercent%",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF0D6EAA),
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        "El pedido suele subir cuando llueve — revisa la recomendación en cada producto de abajo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF3B6E96)
                    )
                }
                if (extraUnits > 0) {
                    Surface(shape = RoundedCornerShape(50), color = Color(0xFF0D6EAA)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "+$extraUnits",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                            Text(
                                "sugerido",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
            // Por tramos del día — más honesto que una sola hora de inicio:
            // la lluvia real puede ir y venir, esto lo muestra de un vistazo.
            if (periods.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    periods.forEach { period ->
                        WeatherPeriodChip(period, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

internal fun startOfNextDay(): Long {
    val tz = TimeZone.currentSystemDefault()
    val today = Clock.System.now().toLocalDateTime(tz).date
    val tomorrow = today.plus(1, DateTimeUnit.DAY)
    return kotlinx.datetime.LocalDateTime(tomorrow, kotlinx.datetime.LocalTime(0, 0, 0))
        .toInstant(tz).toEpochMilliseconds()
}

internal fun formatDate(millis: Long): String {
    if (millis == 0L) return "—"
    return runCatching {
        val ldt = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault())
        val dayName = when (ldt.date.dayOfWeek) {
            kotlinx.datetime.DayOfWeek.MONDAY    -> "Lunes"
            kotlinx.datetime.DayOfWeek.TUESDAY   -> "Martes"
            kotlinx.datetime.DayOfWeek.WEDNESDAY -> "Miércoles"
            kotlinx.datetime.DayOfWeek.THURSDAY  -> "Jueves"
            kotlinx.datetime.DayOfWeek.FRIDAY    -> "Viernes"
            kotlinx.datetime.DayOfWeek.SATURDAY  -> "Sábado"
            kotlinx.datetime.DayOfWeek.SUNDAY    -> "Domingo"
            else                                 -> ""
        }
        "$dayName ${ldt.dayOfMonth.toString().padStart(2,'0')}/${ldt.monthNumber.toString().padStart(2,'0')}/${ldt.year}"
    }.getOrElse { "—" }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ResellerOrdersPanel(
    orders: List<com.bakery.erp.model.CustomerOrder>,
    linesByOrder: Map<String, List<com.bakery.erp.model.CustomerOrderLine>>,
    selectedLineIds: Set<String>,
    producingBranch: com.bakery.erp.model.Branch?,
    allProducingBranches: List<com.bakery.erp.model.Branch>,
    onSetSelectedLines: (orderId: String, newSelectedLineIds: Set<String>) -> Unit
) {
    val primary = BrandColors.AccentDeep
    val border = Color(0xFFFFE0B2)
    var openOrderId by remember { mutableStateOf<String?>(null) }

    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1)),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, border)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "Pedidos pendientes de revendedores (${orders.size})",
                style = MaterialTheme.typography.titleSmall,
                color = primary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Tus revendedores ya pidieron para esta fecha. " +
                    "Toca cada uno para elegir qué productos sumar a este pedido.",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF6E6E6E)
            )
            Spacer(Modifier.height(8.dp))
            orders.forEach { order ->
                val lines = linesByOrder[order.id].orEmpty()
                val selectedCount = lines.count { it.id in selectedLineIds }
                val totalAssignable = lines.count { it.requiresProduction }

                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(
                        1.dp,
                        if (selectedCount > 0) primary else Color(0xFFE0E0E0)
                    ),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "🏪 ${order.customerName.ifEmpty { "Revendedor" }}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = BrandColors.Primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "$totalAssignable productos pendientes" +
                                        (order.deliveryTime.takeIf { it.isNotEmpty() }
                                            ?.let { " · $it" } ?: ""),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF6E6E6E)
                                )
                            }
                            if (selectedCount > 0) {
                                Surface(
                                    color = primary,
                                    shape = RoundedCornerShape(50),
                                    modifier = Modifier
                                ) {
                                    Text(
                                        "$selectedCount/$totalAssignable",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { openOrderId = order.id },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                if (selectedCount > 0)
                                    "Ver / editar productos asignados"
                                else "Ver productos para asignar a esta planta",
                                style = MaterialTheme.typography.labelMedium,
                                color = primary
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal de líneas del CO abierto
    val openOrder = openOrderId?.let { id -> orders.firstOrNull { it.id == id } }
    if (openOrder != null) {
        val lines = linesByOrder[openOrder.id].orEmpty()
        CustomerOrderLinesBottomSheet(
            customerName = openOrder.customerName,
            deliveryBranchName = openOrder.deliveryBranchName,
            deliveryDate = openOrder.deliveryDate,
            deliveryTime = openOrder.deliveryTime,
            lines = lines,
            producingBranch = producingBranch,
            allProducingBranches = allProducingBranches,
            initialSelectedLineIds = lines.map { it.id }.filter { it in selectedLineIds }.toSet(),
            onDismiss = { openOrderId = null },
            onConfirm = { newSel ->
                onSetSelectedLines(openOrder.id, newSel)
                openOrderId = null
            }
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun CustomerOrderLinesBottomSheet(
    customerName: String,
    deliveryBranchName: String,
    deliveryDate: Long,
    deliveryTime: String,
    lines: List<com.bakery.erp.model.CustomerOrderLine>,
    /** Planta de destino del PO (donde se va a producir lo seleccionado). */
    producingBranch: com.bakery.erp.model.Branch?,
    /** TODAS las plantas con CAN_PRODUCE, para mostrar "Se produce en: Calvario, Bombero". */
    allProducingBranches: List<com.bakery.erp.model.Branch>,
    initialSelectedLineIds: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (newSelectedLineIds: Set<String>) -> Unit
) {
    val primary = BrandColors.Accent
    var localSelected by remember(initialSelectedLineIds) {
        mutableStateOf(initialSelectedLineIds)
    }
    // Una línea es "compatible" si la planta de destino tiene el producto en su
    // `assignedProductIds` (configurado desde el módulo de Sucursales).
    //  - Si la planta tiene el producto asignado → compatible.
    //  - Si la planta tiene `assignedProductIds` vacío → compatible (sin restricción).
    //  - Si la planta tiene productos pero NO este → incompatible.
    fun isCompatible(line: com.bakery.erp.model.CustomerOrderLine): Boolean {
        if (!line.requiresProduction) return false
        val branch = producingBranch ?: return true
        return branch.assignedProductIds.isEmpty() ||
            line.productId in branch.assignedProductIds
    }

    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                "Pedido de $customerName",
                style = MaterialTheme.typography.titleMedium,
                color = BrandColors.Primary,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Para ${deliveryBranchName.ifEmpty { "—" }} · " +
                    formatRequestedDay(deliveryDate) +
                    (deliveryTime.takeIf { it.isNotEmpty() }?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF6E6E6E)
            )
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            // Línea por línea
            lines.forEach { line ->
                val compatible = isCompatible(line)
                val checked = compatible && line.id in localSelected
                // Plantas que TIENEN este producto en su assignedProductIds.
                val plantasQueLoHacen = allProducingBranches
                    .filter { line.productId in it.assignedProductIds }
                // Plantas con assignedProductIds VACÍO (no tienen restricción configurada).
                val plantasSinRestriccion = allProducingBranches
                    .filter { it.assignedProductIds.isEmpty() }
                Surface(
                    color = if (checked) primary.copy(alpha = 0.08f) else Color.Transparent,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = {
                                    if (!compatible) return@Checkbox
                                    localSelected = if (it) localSelected + line.id
                                    else localSelected - line.id
                                },
                                enabled = compatible
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    line.productName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (compatible) BrandColors.Primary else Color(0xFF9E9E9E),
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    "${line.requestedQuantity.toInt()} ${line.productUnit}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (compatible) Color(0xFF6E6E6E) else Color(0xFFBDBDBD)
                                )
                            }
                        }
                        // Mensaje según situación
                        when {
                            !line.requiresProduction -> Text(
                                "Producto de reventa — no entra al lote de producción.",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF9E9E9E),
                                modifier = Modifier.padding(start = 48.dp, end = 8.dp, bottom = 4.dp)
                            )
                            !compatible -> {
                                val nombres = plantasQueLoHacen
                                    .joinToString(", ") { it.name }
                                    .ifEmpty { "Ninguna planta lo tiene asignado" }
                                Text(
                                    "Este producto no se hace en esta planta productora, " +
                                        "pendiente de pedir. Se produce en: $nombres",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF9E9E9E),
                                    modifier = Modifier.padding(start = 48.dp, end = 8.dp, bottom = 4.dp)
                                )
                            }
                            // Compatible porque la planta destino tiene assignedProductIds vacío
                            // (sin restricción). Avisar al usuario.
                            producingBranch != null &&
                                producingBranch.assignedProductIds.isEmpty() -> Text(
                                "⚠ Esta planta no tiene productos configurados (compatible con " +
                                    "todo por default). Edítala en Sucursales para limitar qué hace.",
                                style = MaterialTheme.typography.labelSmall,
                                color = BrandColors.AccentDeep,
                                modifier = Modifier.padding(start = 48.dp, end = 8.dp, bottom = 4.dp)
                            )
                            else -> {
                                val nombres = plantasQueLoHacen.joinToString(", ") { it.name }
                                if (nombres.isNotEmpty()) {
                                    Text(
                                        "Se produce en: $nombres",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF6E6E6E),
                                        modifier = Modifier.padding(start = 48.dp, end = 8.dp, bottom = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) { Text("Cancelar") }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { onConfirm(localSelected) },
                    colors = ButtonDefaults.buttonColors(containerColor = primary)
                ) { Text("Aceptar") }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

private fun formatRequestedDay(millis: Long): String {
    if (millis <= 0L) return "—"
    return kotlin.runCatching {
        val tz = TimeZone.currentSystemDefault()
        val ldt = kotlin.time.Instant.fromEpochMilliseconds(millis).toLocalDateTime(tz)
        "${ldt.dayOfMonth.toString().padStart(2,'0')}/${ldt.monthNumber.toString().padStart(2,'0')}/${ldt.year}"
    }.getOrElse { "—" }
}
