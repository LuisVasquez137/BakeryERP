package com.bakery.erp.presentation.production

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import kotlin.time.Clock as KotlinClock
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bakery.erp.domain.BluetoothPrinterManager
import com.bakery.erp.domain.DocumentPrinter
import com.bakery.erp.model.CustomerLineSpec
import com.bakery.erp.model.CustomerOrderType
import com.bakery.erp.model.PreparationBatch
import com.bakery.erp.model.Permission
import com.bakery.erp.model.ProductionLot
import com.bakery.erp.model.ProductionLotLine
import com.bakery.erp.model.ProductionLotStatus
import com.bakery.erp.model.ProductionOrder
import com.bakery.erp.model.PrintMasaGroup
import com.bakery.erp.model.PrintProductLine
import com.bakery.erp.model.ProductionOrderLine
import com.bakery.erp.model.ProductionOrderPrintData
import com.bakery.erp.model.hasPermission
import com.bakery.erp.model.toBaseUnit
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.presentation.plantissues.PlantIssueDialog
import com.bakery.erp.presentation.plantissues.PlantIssueViewModel
import com.bakery.erp.presentation.products.ProductViewModel
import com.bakery.erp.ui.ProductImage
import com.bakery.erp.util.formatBakerWeight
import androidx.compose.ui.window.Dialog
import com.bakery.erp.ui.KeepScreenOn
import com.bakery.erp.ui.LoadingOverlay
import com.bakery.erp.ui.startKneadingAlarmSound
import com.bakery.erp.ui.stopKneadingAlarmSound
import com.bakery.erp.util.fmt
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import com.bakery.erp.ui.theme.BrandColors

private const val GRAMS_PER_LB_DETAIL = 453.592
private fun Double.gramsToLbDetail(): Double = this / GRAMS_PER_LB_DETAIL

/** "4 band. + 8 sueltos" bajo la cantidad de un producto — null si el producto
 *  no tiene bandeja configurada (no aplica el desglose). */
private fun formatTrayBreakdown(qty: Int, unitsPerTray: Int): String? {
    if (unitsPerTray <= 0 || qty <= 0) return null
    val trays = qty / unitsPerTray
    val loose = qty % unitsPerTray
    return when {
        trays > 0 && loose > 0 -> "$trays band. + $loose sueltos"
        trays > 0 -> "$trays band."
        else -> "$loose sueltos"
    }
}

/**
 * Estado en vivo del timer de amasado de una masa (`recipeId`). `isRinging`
 * pasa a true cuando `remainingSeconds` llega a 0; desde ahí
 * [ringRemainingSeconds] cuenta los 10s de alarma hacia atrás (o el panadero
 * la corta antes con el botón "Detener").
 */
internal data class KneadingTimerState(
    val recipeId: String,
    val recipeName: String,
    val totalSeconds: Int,
    val remainingSeconds: Int,
    val isRinging: Boolean,
    val ringRemainingSeconds: Int = 10
)

private fun formatKneadingTime(totalSeconds: Int): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "$m:${s.toString().padStart(2, '0')}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductionLotDetailScreen(
    lotId: String,
    onNavigateBack: () -> Unit,
    onNavigateToComplete: (lotId: String) -> Unit,
    authViewModel: AuthViewModel,
    viewModel: ProductionViewModel,
    productViewModel: ProductViewModel,
    documentPrinter: DocumentPrinter,
    bluetoothPrinterManager: BluetoothPrinterManager,
    /**
     * Vales de salida de material. Llega como parámetro —igual que `productViewModel`—
     * porque el panadero saca la harina de la mesa desde acá y no desde Insumos: es
     * la pantalla que ya tiene abierta con las manos en la masa.
     */
    plantIssueViewModel: PlantIssueViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val productState by productViewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val user = authState.user
    val companyId = user?.orgId ?: ""
    val canManage = user?.hasPermission(Permission.MANAGE_PRODUCTION_LOTS) == true
    val canIssueMaterial = user?.hasPermission(Permission.ISSUE_PLANT_MATERIAL) == true
    var showPlantIssueDialog by remember { mutableStateOf(false) }
    val plantIssueState by plantIssueViewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // El vale se confirma con un snackbar y no con un diálogo: el panadero ya cerró
    // el suyo y volver a interrumpirlo para decirle que sí pasó lo que acaba de hacer
    // le cuesta un toque más en cada saco.
    LaunchedEffect(plantIssueState.message) {
        plantIssueState.message?.let {
            snackbarHostState.showSnackbar(it)
            plantIssueViewModel.consumeMessage()
        }
    }
    LaunchedEffect(plantIssueState.error) {
        plantIssueState.error?.let {
            snackbarHostState.showSnackbar(it)
            plantIssueViewModel.clearError()
        }
    }

    if (showPlantIssueDialog && user != null) {
        PlantIssueDialog(
            companyId = companyId,
            user = user,
            viewModel = plantIssueViewModel,
            onDismiss = { showPlantIssueDialog = false },
            presetBranchId = state.selectedLot?.producingBranchId ?: "",
            lotId = lotId,
            shiftId = state.selectedLot?.shiftId ?: "",
            shiftName = state.selectedLot?.shiftName ?: ""
        )
    }

    // Mantener pantalla encendida mientras los panaderos tengan la lista abierta.
    KeepScreenOn()

    LaunchedEffect(lotId) {
        if (companyId.isNotEmpty()) viewModel.observeLotDetail(companyId, lotId)
    }
    DisposableEffect(Unit) {
        onDispose { viewModel.stopObservingLotDetail() }
    }
    LaunchedEffect(companyId) {
        if (companyId.isNotEmpty()) productViewModel.loadProducts(companyId)
    }
    // Mapa producto→masa para que el diálogo de orden agrupe por receta (ver
    // punto 2 del rediseño: mover una masa mueve con ella todos sus productos).
    LaunchedEffect(companyId) {
        if (companyId.isNotEmpty()) viewModel.loadRecipeRefByProductIdIfNeeded(companyId)
    }
    // Refrescar el caché de productos del ViewModel cada vez que cambie la lista cargada
    // (resuelve fallback de rawWeightGrams en líneas legacy con campo null).
    LaunchedEffect(productState.productList) {
        viewModel.updateProductsCache(productState.productList)
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }

    val lot = state.selectedLot
    // Usamos editableLotLines para que los cambios de esponja del panadero se reflejen
    // inmediatamente en el cálculo de la receta escalada.
    // El orden es el que configuró el panadero de esta planta (secuencia del
    // horno), independiente del orden del consolidado de la encargada. Se aplica
    // acá para que la tabla, las tarjetas y el sheet de registro coincidan.
    val productionSortOrder = state.lotBranch?.productionSortOrder.orEmpty()
    val lines = remember(state.editableLotLines, productionSortOrder) {
        applyProductSortOrder(
            items = state.editableLotLines,
            sortOrder = productionSortOrder,
            productIdOf = { it.productId },
            nameOf = { it.productName }
        )
    }
    var showSortDialog by remember { mutableStateOf(false) }

    // La orden se trabaja por MASA, no por producto: la amasada es una sola para
    // todos los panes que salen de la misma receta. Cada fila es una masa y
    // adentro se listan sus productos con las unidades a sacar de cada uno.
    val masaGroups = remember(lines, productionSortOrder) {
        val itemIndex = productionSortOrder.withIndex().associate { (i, id) -> id to i }
        val recipeIndex = recipeOrderIndexFrom(productionSortOrder) { productId ->
            lines.find { it.productId == productId }?.recipeId
        }
        val ownGroups = groupByMasa(
            items = lines,
            recipeIdOf = { it.recipeId },
            recipeNameOf = { it.recipeName },
            nameOf = { it.productName },
            groupOrderIndex = { recipeIndex[it] ?: Int.MAX_VALUE },
            itemOrderIndex = { itemIndex[it.productId] ?: Int.MAX_VALUE }
        )
        // Masas que nadie pidió directamente pero que hay que amasar igual
        // porque otro producto las lleva de relleno (Pan de Capa → Suave, ver
        // DoughFilling): sin esto, esa masa no aparecía en la lista y el
        // panadero no tenía forma de saber que tenía que hacerla.
        val ownRecipeIds = ownGroups.map { it.key }.toSet()
        val fillingOnlyGroups = lines
            .flatMap { it.doughFillingsSnapshot }
            .map { it.recipeId to it.recipeName }
            .distinct()
            .filter { (recipeId, _) -> recipeId.isNotEmpty() && recipeId !in ownRecipeIds }
            .map { (recipeId, recipeName) ->
                MasaGroup<ProductionLotLine>(key = recipeId, title = recipeName, items = emptyList())
            }
        (ownGroups + fillingOnlyGroups)
    }

    // Masas con al menos un producto sin conteo confirmado — el prellenado
    // (aunque ya sea el número correcto) no cuenta como confirmado mientras
    // el panadero no le dé "Guardar" en el sheet de registro. Las sintéticas
    // (solo relleno, sin productos propios) no tienen nada que contar.
    val unconfirmedMasaGroups = remember(masaGroups) {
        masaGroups.filter { group ->
            group.items.isNotEmpty() && group.items.any { !it.producedCountConfirmed }
        }
    }

    // ── Timer de amasado ─────────────────────────────────────────────────────
    // Por masa (recipeId), no por producto: la amasada es una sola. Vive acá
    // (no en el ViewModel) porque es puramente de sesión — nadie más necesita
    // saber que el panadero está amasando, y así no hay que persistir ni
    // sincronizar nada por un timer.
    var kneadingTimers by remember { mutableStateOf<Map<String, KneadingTimerState>>(emptyMap()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            if (kneadingTimers.isEmpty()) continue
            kneadingTimers = kneadingTimers.mapNotNull { (key, timer) ->
                when {
                    timer.isRinging -> {
                        val nextRing = timer.ringRemainingSeconds - 1
                        if (nextRing <= 0) null // se acabaron los 10s de alarma: se quita solo
                        else key to timer.copy(ringRemainingSeconds = nextRing)
                    }
                    timer.remainingSeconds > 1 -> key to timer.copy(remainingSeconds = timer.remainingSeconds - 1)
                    else -> key to timer.copy(remainingSeconds = 0, isRinging = true, ringRemainingSeconds = 10)
                }
            }.toMap()
        }
    }
    // El audio suena en loop mientras haya AL MENOS un timer sonando, y se
    // corta solo cuando ninguno queda sonando — no un beep por tick.
    val anyTimerRinging = kneadingTimers.values.any { it.isRinging }
    LaunchedEffect(anyTimerRinging) {
        if (anyTimerRinging) startKneadingAlarmSound() else stopKneadingAlarmSound()
    }
    // Por si el panadero sale de la pantalla mientras suena, o toca "Detener".
    DisposableEffect(Unit) { onDispose { stopKneadingAlarmSound() } }

    var selectedGroupKey by remember { mutableStateOf<String?>(null) }
    val selectedGroup = masaGroups.find { it.key == selectedGroupKey }
    // Masas "ya preparé" — al abrir la receta la marcamos para que el panadero
    // recuerde qué amasadas ya hizo. Solo en sesión.
    var visitedGroupKeys by remember { mutableStateOf(emptySet<String>()) }
    // Con producción extra declarada hay DOS recetas válidas y el sistema no puede
    // elegir por su cuenta cuál mostrar: la del total sirve si todavía no se amasó
    // nada, y la del incremento sirve si ya se amasó lo que pedían. No existe un
    // estado "ya amasé" que lo resuelva, así que lo decide el panadero con un toque.
    var showOnlyExtra by remember(selectedGroupKey) { mutableStateOf(false) }
    val extraUnits = selectedGroup?.let {
        viewModel.extraUnitsInGroup(it.items, lines, it.key)
    } ?: 0.0
    val hasExtraToMake = extraUnits > 0.0
    val scaledView = selectedGroup?.let {
        if (showOnlyExtra && hasExtraToMake) {
            viewModel.buildExtraOnlyRecipeForGroup(
                groupLines = it.items,
                allLines = lines,
                fallbackRecipeId = it.key,
                fallbackRecipeName = it.title
            )
        } else {
            viewModel.buildScaledRecipeViewForGroup(
                it.items,
                extraDoughGrams = viewModel.extraDoughGramsForRecipe(lines, it.key),
                fallbackRecipeId = it.key,
                fallbackRecipeName = it.title
            )
        }
    }
    val scaledIssue = selectedGroup?.let {
        viewModel.diagnoseScaledRecipeForGroup(
            it.items,
            extraDoughGrams = viewModel.extraDoughGramsForRecipe(lines, it.key),
            fallbackRecipeId = it.key,
            fallbackRecipeName = it.title
        )
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val registerSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val adjustSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Masa sobre la que se abrió "¿qué pasó?" (se quemó / hay que hacer más).
    var adjustSheetTarget by remember { mutableStateOf<String?>(null) }
    val canEditSponge = lot?.status == ProductionLotStatus.OPEN ||
        lot?.status == ProductionLotStatus.IN_PROGRESS

    // Pedido tardío pendiente de confirmación (cuando lote está IN_PROGRESS).
    var pendingLateOrderConfirm by remember { mutableStateOf<ProductionOrder?>(null) }

    // Dialog de inicio: se muestra cuando el lote sigue OPEN. Bloquea el detalle
    // hasta que el panadero acuse el inicio del turno para que el cronómetro
    // arranque y quede registro auditable de la hora de inicio.
    var isStartingLot by remember { mutableStateOf(false) }

    // Confirmación de cierre temprano (<1h de trabajo) para evitar cierres accidentales.
    var pendingEarlyComplete by remember { mutableStateOf<Int?>(null) }

    // Bottom sheet con cantidades reales por producto, por masa. null = cerrado;
    // el `key` de una masa puntual (ícono en su tarjeta) abre solo esa — ya no
    // hay botón para registrar todas las masas de un tirón.
    var registerSheetTarget by remember { mutableStateOf<String?>(null) }
    val unitsPerTrayByProduct = remember(productState.productList) {
        productState.productList.associate { it.id to it.unitsPerTray }
    }

    state.lotBranch?.let { branch ->
        if (showSortDialog) {
            ProductSortOrderDialog(
                title = "Orden de producción · ${branch.name}",
                description = "Define en qué orden quieres ver los productos mientras horneas. " +
                    "Se guarda en la sucursal, así que queda igual para todos los panaderos " +
                    "y en todos los dispositivos. Es independiente del orden que la encargada " +
                    "usa en el consolidado de pedidos.",
                products = productState.productList,
                assignedProductIds = branch.assignedProductIds,
                currentOrder = branch.productionSortOrder,
                onDismiss = { showSortDialog = false },
                onSave = { ids ->
                    viewModel.saveProductionSortOrder(companyId, branch.id, ids)
                    showSortDialog = false
                },
                recipeIdOf = { productId -> state.recipeRefByProductId[productId]?.id },
                recipeNameOf = { productId -> state.recipeRefByProductId[productId]?.name }
            )
        }
    }

    pendingLateOrderConfirm?.let { lateOrder ->
        if (lot != null) {
            AlertDialog(
                onDismissRequest = { pendingLateOrderConfirm = null },
                title = { Text("Lote ya iniciado") },
                text = {
                    Text(
                        "Este lote ya está en producción. Antes de agregar el pedido de " +
                            "${lateOrder.sellingBranchName}, coordina con el panadero para que sepa " +
                            "que tiene que pesar un extra. ¿Continuar?"
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.addLateOrderToCurrentLot(companyId, lot, lateOrder)
                        pendingLateOrderConfirm = null
                    }) { Text("Sí, agregar al lote", fontWeight = FontWeight.Bold) }
                },
                dismissButton = {
                    TextButton(onClick = { pendingLateOrderConfirm = null }) { Text("Cancelar") }
                }
            )
        }
    }

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    // Compactamos toda la info del lote en el header (sucursal,
                    // turno y estado) para no gastar espacio en una card encima
                    // del listado de productos.
                    Column {
                        Text(
                            buildString {
                                append("Orden de producción")
                                if (lot != null && lot.producingBranchName.isNotBlank()) {
                                    append(" - ").append(lot.producingBranchName)
                                }
                            },
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1
                        )
                        if (lot != null) {
                            val turnoTxt = if (lot.shiftName.isNotBlank()) "Turno: ${lot.shiftName}" else ""
                            val estadoTxt = ProductionLotStatus.label(lot.status)
                            Text(
                                listOf(turnoTxt, estadoTxt).filter { it.isNotEmpty() }
                                    .joinToString("  ·  "),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFFFD166),
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                    }
                },
                actions = {
                    // Anotar el material que se usó fuera de la receta: la harina de
                    // la mesa, la manteca de las bandejas. Vive acá y no en Insumos
                    // porque es el panadero quien lo usa y ésta es la pantalla que ya
                    // tiene abierta — mandarlo a otro módulo significa que no lo anota.
                    // El verbo es "anotar" y no "sacar" a propósito: lo que se nombra
                    // es el registro, no la extracción, para que quien deja constancia
                    // no quede como sujeto de sospecha.
                    if (canIssueMaterial) {
                        TextButton(
                            onClick = { showPlantIssueDialog = true },
                            colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
                        ) {
                            Icon(Icons.Default.Inventory2, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "Anotar material",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                    // Imprimir la orden: el panadero trabaja con las manos en la
                    // masa y la tablet lejos, así que el papel sigue siendo el
                    // formato de consulta. Ver ProductionOrderPrintButton.
                    ProductionOrderPrintButton(
                        documentPrinter = documentPrinter,
                        bluetoothPrinterManager = bluetoothPrinterManager,
                        snackbarHostState = snackbarHostState,
                        printData = {
                            lot?.let {
                                buildProductionOrderPrintData(
                                    lot = it,
                                    masaGroups = masaGroups,
                                    lines = lines,
                                    viewModel = viewModel,
                                    unitsPerTrayByProduct = unitsPerTrayByProduct,
                                    showProduced = it.status == ProductionLotStatus.COMPLETED
                                )
                            }
                        }
                    )
                    // El propio panadero define la secuencia de su lista: no se
                    // pide permiso extra porque es su herramienta de trabajo y
                    // no altera ningún dato del lote, solo cómo se muestra.
                    if (state.lotBranch != null) {
                        TextButton(
                            onClick = { showSortDialog = true },
                            colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
                        ) {
                            Icon(Icons.Default.SwapVert, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "Ordenar",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
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
        if (state.isDetailLoading || lot == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        // Antes de mostrar la orden, un panadero con lote OPEN debe pulsar
        // "Iniciar orden de producción". Bloqueamos el detalle con un fondo
        // neutro y un dialog centrado para que el acuse sea explícito y el
        // cronómetro arranque desde aquí.
        if (canManage && lot.status == ProductionLotStatus.OPEN) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(BrandColors.Ivory)
            ) {
                StartShiftDialog(
                    lot = lot,
                    isStarting = isStartingLot,
                    onStart = {
                        isStartingLot = true
                        viewModel.startLot(companyId, lot.id, user)
                    }
                )
            }
            // Al pasar a IN_PROGRESS el dialog se desmonta automáticamente.
            LaunchedEffect(lot.status) {
                if (lot.status != ProductionLotStatus.OPEN) isStartingLot = false
            }
            return@Scaffold
        }

        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Cronómetro del turno: visible mientras el lote esté en producción.
            if (lot.status == ProductionLotStatus.IN_PROGRESS && lot.startedAt != null) {
                ElapsedTimeBanner(startedAtMillis = lot.startedAt)
            }
            // Fijo arriba (no dentro del scroll): si el panadero está viendo otra
            // masa cuando suena, igual sabe cuál es sin tener que buscarla.
            val ringingTimers = kneadingTimers.values.filter { it.isRinging }
            if (ringingTimers.isNotEmpty()) {
                KneadingRingingBanner(
                    timers = ringingTimers,
                    onStop = { recipeId ->
                        kneadingTimers = kneadingTimers - recipeId
                        stopKneadingAlarmSound()
                    }
                )
            }
            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            // 840dp = breakpoint "medium" de Material 3 — el que usan la mayoría
            // de tablets en horizontal (antes 1000dp dejaba tablets de 11" caer
            // al bottom sheet, que debería ser solo para vertical/celular).
            val isWide = maxWidth >= 840.dp

            @Composable
            fun LotBodyContent(modifier: Modifier) {
                LazyColumn(
                    modifier = modifier,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Sin header card ni texto introductorio — la info de sucursal/
                    // turno/estado ya vive en el TopAppBar. Maximiza el espacio para
                    // la orden de producción.

                    items(masaGroups, key = { it.key }) { group ->
                        MasaProductionCard(
                            group = group,
                            showProduced = lot.status == ProductionLotStatus.COMPLETED,
                            countConfirmed = group.items.isNotEmpty() &&
                                group.items.all { it.producedCountConfirmed },
                            isSelected = selectedGroupKey == group.key,
                            isVisited = group.key in visitedGroupKeys,
                            doughGramsOf = { viewModel.doughGramsOf(it) },
                            missingRawWeight = viewModel.linesMissingRawWeight(group.items)
                                .map { it.productId }.toSet(),
                            spongeGrams = viewModel.spongeGramsOfGroup(group.items),
                            extraDoughGrams = viewModel.extraDoughGramsForRecipe(lines, group.key),
                            extraDoughContributions = viewModel.extraDoughContributionsForRecipe(lines, group.key),
                            kneadingTimeSeconds = state.recipesById[group.key]?.kneadingTimeSeconds ?: 0,
                            kneadingTimer = kneadingTimers[group.key],
                            unitsPerTrayByProduct = unitsPerTrayByProduct,
                            onRegisterCount = if (canManage && lot.status == ProductionLotStatus.IN_PROGRESS &&
                                group.items.isNotEmpty()
                            ) {
                                { registerSheetTarget = group.key }
                            } else null,
                            // Se ofrece desde que el lote está ABIERTO, no solo en
                            // producción: el encargo puede llegar antes de que el
                            // panadero toque "Iniciar", y ahí sumarlo a la amasada es
                            // justo lo más barato. Con el lote ya cerrado no — ese
                            // consumo ya se descontó.
                            onAdjustProduction = if (canManage && group.items.isNotEmpty() &&
                                (lot.status == ProductionLotStatus.OPEN ||
                                    lot.status == ProductionLotStatus.IN_PROGRESS)
                            ) {
                                { adjustSheetTarget = group.key }
                            } else null,
                            onStartKneadingTimer = { seconds ->
                                kneadingTimers = kneadingTimers + (group.key to KneadingTimerState(
                                    recipeId = group.key,
                                    recipeName = group.title,
                                    totalSeconds = seconds,
                                    remainingSeconds = seconds,
                                    isRinging = false
                                ))
                            },
                            onStopKneadingTimer = {
                                kneadingTimers = kneadingTimers - group.key
                                stopKneadingAlarmSound()
                            },
                            onClick = {
                                selectedGroupKey = group.key
                                visitedGroupKeys = visitedGroupKeys + group.key
                            }
                        )
                    }

                    // ── Pedidos tardíos pendientes de incorporar ─────────────────
                    val lateOrders = state.lateOrdersForLot
                    if (canManage && lateOrders.isNotEmpty() &&
                        lot.status != ProductionLotStatus.COMPLETED &&
                        lot.status != ProductionLotStatus.CANCELLED) {
                        item {
                            Spacer(Modifier.height(8.dp))
                            LateOrdersPanelHeader(
                                count = lateOrders.size,
                                isInProgress = lot.status == ProductionLotStatus.IN_PROGRESS
                            )
                        }
                        items(lateOrders, key = { it.id }) { lateOrder ->
                            LateOrderCard(
                                order = lateOrder,
                                lines = state.lateOrderLinesMap[lateOrder.id].orEmpty(),
                                lineCountFallback = lateOrder.lineCount,
                                isLoading = state.isAddingLateOrder,
                                onAdd = {
                                    if (lot.status == ProductionLotStatus.IN_PROGRESS) {
                                        pendingLateOrderConfirm = lateOrder
                                    } else {
                                        viewModel.addLateOrderToCurrentLot(companyId, lot, lateOrder)
                                    }
                                }
                            )
                        }
                    }

                    // ── Acciones ─────────────────────────────────────────────────
                    if (canManage && lot.status == ProductionLotStatus.IN_PROGRESS) {
                        item {
                            Spacer(Modifier.height(8.dp))
                            if (unconfirmedMasaGroups.isNotEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFFFFEBEE),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        "Falta confirmar el conteo de: " +
                                            unconfirmedMasaGroups.joinToString(", ") { it.title } +
                                            ". Usa el lápiz de cada masa y presiona \"Guardar todo\".",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color(0xFFC62828),
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                            // Espacio extra al fondo para que el FAB no tape el botón.
                            Button(
                                onClick = {
                                    val started = lot.startedAt
                                    if (started != null) {
                                        val elapsedMin = ((KotlinClock.System.now()
                                            .toEpochMilliseconds() - started) / 60_000L).toInt()
                                        if (elapsedMin < 60) {
                                            pendingEarlyComplete = elapsedMin
                                            return@Button
                                        }
                                    }
                                    onNavigateToComplete(lot.id)
                                },
                                enabled = unconfirmedMasaGroups.isEmpty(),
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                            ) { Text("Terminar lote y descontar stock", fontWeight = FontWeight.Bold) }
                            Spacer(Modifier.height(80.dp))
                        }
                    }
                }
            }

            if (isWide) {
                Row(modifier = Modifier.fillMaxSize()) {
                    // Proporciones: panel de receta ~58% (1.4f) — necesita más espacio
                    // que la lista porque muestra tabla con ingredientes y conversiones.
                    LotBodyContent(modifier = Modifier.weight(1f).fillMaxHeight())
                    VerticalDivider(color = Color(0xFFE0E0E0))
                    Box(modifier = Modifier.weight(1.4f).fillMaxHeight()) {
                        when {
                            scaledView != null -> ScaledRecipePanel(
                                view = scaledView,
                                modifier = Modifier.fillMaxSize(),
                                onClose = { selectedGroupKey = null },
                                headerExtra = {
                                    if (hasExtraToMake) {
                                        RecipeScopeSelector(
                                            showOnlyExtra = showOnlyExtra,
                                            onChange = { showOnlyExtra = it }
                                        )
                                    }
                                    if (canEditSponge && selectedGroup != null) {
                                        SpongeControlsForMasa(
                                            lines = selectedGroup.items,
                                            batches = state.availableSpongeBatches,
                                            spongeConsumed = viewModel.spongeConsumedOfGroup(selectedGroup.items),
                                            spongeBatchName = viewModel.spongeBatchNameOfGroup(selectedGroup.items),
                                            spongeGramsUsedTotal = viewModel.spongeGramsOfGroup(selectedGroup.items),
                                            onConfirm = { batch, totalGrams ->
                                                viewModel.confirmSpongeForGroup(
                                                    companyId, lot.id, selectedGroup.items, batch, totalGrams
                                                )
                                            },
                                            onRemove = {
                                                viewModel.removeSpongeFromGroup(companyId, lot.id, selectedGroup.items)
                                            }
                                        )
                                    }
                                }
                            )
                            scaledIssue != null -> RecipeIssuePanel(
                                issue = scaledIssue,
                                modifier = Modifier.fillMaxSize(),
                                onClose = { selectedGroupKey = null }
                            )
                            else -> EmptyDetailRecipePlaceholder(modifier = Modifier.fillMaxSize())
                        }
                    }
                }
            } else {
                LotBodyContent(modifier = Modifier.fillMaxSize())
                if (selectedGroup != null) {
                    ModalBottomSheet(
                        onDismissRequest = { selectedGroupKey = null },
                        sheetState = sheetState,
                        containerColor = BrandColors.Ivory
                    ) {
                        when {
                            scaledView != null -> ScaledRecipePanel(
                                view = scaledView,
                                onClose = { selectedGroupKey = null },
                                headerExtra = {
                                    if (hasExtraToMake) {
                                        RecipeScopeSelector(
                                            showOnlyExtra = showOnlyExtra,
                                            onChange = { showOnlyExtra = it }
                                        )
                                    }
                                    if (canEditSponge && selectedGroup != null) {
                                        SpongeControlsForMasa(
                                            lines = selectedGroup.items,
                                            batches = state.availableSpongeBatches,
                                            spongeConsumed = viewModel.spongeConsumedOfGroup(selectedGroup.items),
                                            spongeBatchName = viewModel.spongeBatchNameOfGroup(selectedGroup.items),
                                            spongeGramsUsedTotal = viewModel.spongeGramsOfGroup(selectedGroup.items),
                                            onConfirm = { batch, totalGrams ->
                                                viewModel.confirmSpongeForGroup(
                                                    companyId, lot.id, selectedGroup.items, batch, totalGrams
                                                )
                                            },
                                            onRemove = {
                                                viewModel.removeSpongeFromGroup(companyId, lot.id, selectedGroup.items)
                                            }
                                        )
                                    }
                                }
                            )
                            scaledIssue != null -> RecipeIssuePanel(
                                issue = scaledIssue,
                                onClose = { selectedGroupKey = null }
                            )
                        }
                    }
                }
            }
            }  // BoxWithConstraints
        }  // Column wrapping the timer banner + body

        // Bottom sheet "Registrar producción" — el panadero puede registrar
        // cantidades reales en cualquier momento del turno (no solo al final).
        // Desde el TopAppBar cubre todas las masas; desde el ícono de una
        // tarjeta, solo esa masa (mismo sheet, lista filtrada).
        registerSheetTarget?.let { target ->
            if (lot.status == ProductionLotStatus.IN_PROGRESS) {
                // Las masas sintéticas (solo relleno, sin productos propios) no
                // tienen nada que contar acá — el relleno se cuenta con el
                // producto que lo lleva (Pan de Capa), no aparte.
                val targetGroups = masaGroups.filter { group ->
                    group.items.isNotEmpty() && group.key == target
                }
                RegisterProductionSheet(
                    masaGroups = targetGroups,
                    unitsPerTrayByProduct = unitsPerTrayByProduct,
                    sheetState = registerSheetState,
                    onDismiss = { registerSheetTarget = null },
                    onSave = { quantities ->
                        val confirmedIds = targetGroups.flatMap { it.items }.map { it.id }.toSet()
                        viewModel.saveProducedQuantities(companyId, lot.id, quantities, confirmedIds)
                        registerSheetTarget = null
                    }
                )
            }
        }

        // "¿Qué pasó?": se quemó una tanda, o llegó un encargo a media mañana.
        // Vive por masa y no por lote porque lo que se quema es una masa concreta,
        // y porque la receta que hay que mostrar es la de esa receta.
        adjustSheetTarget?.let { target ->
            val targetGroup = masaGroups.firstOrNull { it.key == target && it.items.isNotEmpty() }
            if (targetGroup != null) {
                ProductionAdjustmentSheet(
                    masaTitle = targetGroup.title,
                    lines = targetGroup.items,
                    sheetState = adjustSheetState,
                    scaledRecipeFor = { quantities ->
                        viewModel.buildScaledRecipeForQuantities(targetGroup.items, quantities)
                    },
                    fillingRecipesFor = { quantities ->
                        viewModel.buildFillingRecipesForQuantities(targetGroup.items, quantities)
                    },
                    hasConfirmedSponge = viewModel.groupHasConfirmedSponge(targetGroup.items),
                    isSaving = state.isSavingAdjustment,
                    onDismiss = { adjustSheetTarget = null },
                    onConfirm = { adjustment ->
                        when (adjustment.kind) {
                            AdjustmentKind.WASTE -> viewModel.registerProductionAdjustment(
                                companyId = companyId,
                                lotId = lot.id,
                                wasteByLine = adjustment.quantityByLineId,
                                wasteReason = adjustment.reason,
                                wasteNotes = adjustment.notes
                            )
                            AdjustmentKind.EXTRA -> viewModel.registerProductionAdjustment(
                                companyId = companyId,
                                lotId = lot.id,
                                extraByLine = adjustment.quantityByLineId,
                                extraReason = adjustment.reason,
                                extraNotes = adjustment.notes
                            )
                        }
                        adjustSheetTarget = null
                    }
                )
            }
        }

        // Confirmación de cierre temprano (<1h de producción): evita cierres
        // accidentales que dañen el reporte y el descuento de stock.
        pendingEarlyComplete?.let { minutes ->
            AlertDialog(
                onDismissRequest = { pendingEarlyComplete = null },
                title = { Text("¿Terminar lote ahora?") },
                text = {
                    Text(
                        "Solo llevas $minutes minuto${if (minutes == 1) "" else "s"} de producción, " +
                            "parece muy poco tiempo para terminar este lote. ¿Estás seguro?"
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            pendingEarlyComplete = null
                            onNavigateToComplete(lot.id)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                    ) { Text("Sí, completar producción", fontWeight = FontWeight.Bold) }
                },
                dismissButton = {
                    TextButton(onClick = { pendingEarlyComplete = null }) { Text("Cancelar") }
                }
            )
        }
    }
    LoadingOverlay(visible = state.isLoading)
    }
}

/**
 * Traduce lo que la pantalla ya calculó a [ProductionOrderPrintData]. Se arma acá y no
 * en el ViewModel a propósito: el papel debe decir exactamente lo mismo que la tarjeta
 * de cada masa —mismo agrupado, mismo total, mismas libras— y esos números salen de los
 * helpers del ViewModel combinados justo como los combina [MasaProductionCard]. Con un
 * segundo cálculo paralelo, cualquier arreglo en pantalla dejaría el papel mintiendo.
 */
private fun buildProductionOrderPrintData(
    lot: ProductionLot,
    masaGroups: List<MasaGroup<ProductionLotLine>>,
    lines: List<ProductionLotLine>,
    viewModel: ProductionViewModel,
    unitsPerTrayByProduct: Map<String, Int>,
    showProduced: Boolean
): ProductionOrderPrintData {
    val groups = masaGroups.map { group ->
        val extraDoughGrams = viewModel.extraDoughGramsForRecipe(lines, group.key)
        val extraContributions = viewModel.extraDoughContributionsForRecipe(lines, group.key)
        val missing = viewModel.linesMissingRawWeight(group.items).map { it.productId }.toSet()
        val totalDough = group.items.sumOf { viewModel.doughGramsOf(it) } + extraDoughGrams

        PrintMasaGroup(
            title = group.title,
            totalUnits = group.items.sumOf { qtyToShow(it, showProduced) }.toInt(),
            doughLb = totalDough.gramsToLbDetail(),
            hasSponge = viewModel.spongeGramsOfGroup(group.items) > 0.0,
            extraDoughLb = extraDoughGrams.gramsToLbDetail(),
            extraDoughFor = extraContributions.joinToString(", ") { (name, _) -> name },
            isFillingOnly = group.items.isEmpty(),
            items = group.items.map { line ->
                val qty = qtyToShow(line, showProduced).toInt()
                PrintProductLine(
                    name = line.productName,
                    quantity = qty,
                    trayBreakdown = formatTrayBreakdown(
                        qty, unitsPerTrayByProduct[line.productId] ?: 0
                    ).orEmpty(),
                    isSpecial = line.isSpecial,
                    missingWeight = line.productId in missing,
                    customerNotes = line.customerSpecs.map { spec ->
                        buildString {
                            append(spec.customerName.ifBlank { "Cliente" })
                            append(": ").append(spec.quantity.toInt())
                            if (spec.customSpec.isNotBlank()) append(" - ").append(spec.customSpec)
                        }
                    }
                )
            }
        )
    }

    return ProductionOrderPrintData(
        branchName = lot.producingBranchName,
        shiftName = lot.shiftName,
        dateLabel = formatDate(lot.productionDate),
        statusLabel = ProductionLotStatus.label(lot.status),
        printedAtLabel = formatDateTimeNow(),
        groups = groups
    )
}

/** Misma regla que la tarjeta de masa: lo producido manda una vez que existe. */
private fun qtyToShow(line: ProductionLotLine, showProduced: Boolean): Double =
    if (showProduced && line.producedQuantity > 0.0) line.producedQuantity else line.plannedQuantity

private fun formatDateTimeNow(): String = runCatching {
    val ldt = Instant.fromEpochMilliseconds(KotlinClock.System.now().toEpochMilliseconds())
        .toLocalDateTime(TimeZone.currentSystemDefault())
    "${ldt.dayOfMonth.toString().padStart(2, '0')}/" +
        "${ldt.monthNumber.toString().padStart(2, '0')}/${ldt.year} " +
        "${ldt.hour.toString().padStart(2, '0')}:${ldt.minute.toString().padStart(2, '0')}"
}.getOrElse { "" }

@Composable
private fun LotHeaderCard(lot: com.bakery.erp.model.ProductionLot) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(lot.producingBranchName, fontWeight = FontWeight.Bold, color = BrandColors.Primary, style = MaterialTheme.typography.titleMedium)
                    Text(lot.shiftName, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                    Text(formatProductionDate(lot.productionDate), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
                LotStatusChip(lot.status)
            }
            if (!lot.notes.isNullOrBlank()) {
                HorizontalDivider()
                Text(lot.notes, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            if (!lot.stockDeducted && lot.status == ProductionLotStatus.COMPLETED) {
                HorizontalDivider()
                Surface(color = Color(0xFFFFF3E0), shape = RoundedCornerShape(6.dp)) {
                    Text(
                        "⚠ Stock pendiente de descontar",
                        modifier = Modifier.padding(8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFF57C00),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun RecipeIssuePanel(
    issue: String,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .background(Color(0xFFFFF8E1))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Receta no disponible",
                    style = MaterialTheme.typography.labelMedium,
                    color = BrandColors.AccentDeep,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "No se puede mostrar la receta escalada",
                    style = MaterialTheme.typography.titleMedium,
                    color = BrandColors.AccentDeep,
                    fontWeight = FontWeight.Bold
                )
            }
            if (onClose != null) {
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Cerrar",
                        tint = BrandColors.AccentDeep
                    )
                }
            }
        }
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color.White,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                issue,
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF333333)
            )
        }
    }
}

@Composable
private fun EmptyDetailRecipePlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(BrandColors.Ivory).padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Receta escalada",
                style = MaterialTheme.typography.labelMedium,
                color = BrandColors.AccentDeep,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Selecciona un producto de la tabla para ver cuánto pesar de cada ingrediente.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )
        }
    }
}

/** Pasos del flujo de confirmación de esponja — uno por diálogo. */
private sealed class SpongeDialogStep {
    data object PickBatch : SpongeDialogStep()
    data class PickAmount(val batch: PreparationBatch) : SpongeDialogStep()
    data class Confirm(val batch: PreparationBatch, val totalGrams: Double, val isAll: Boolean) : SpongeDialogStep()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpongeControlsForMasa(
    lines: List<ProductionLotLine>,
    batches: List<PreparationBatch>,
    spongeConsumed: Boolean,
    spongeBatchName: String,
    spongeGramsUsedTotal: Double,
    onConfirm: (PreparationBatch, Double) -> Unit,
    onRemove: () -> Unit
) {
    val groupKey = lines.firstOrNull()?.recipeId.orEmpty()
    var step by remember(groupKey) { mutableStateOf<SpongeDialogStep?>(null) }
    var confirmRemove by remember(groupKey) { mutableStateOf(false) }

    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        color = Color(0xFFFFF8E1),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (spongeConsumed) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "✓ Esponja usada",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF2E7D32)
                        )
                        Text(
                            "$spongeBatchName — ${spongeGramsUsedTotal.gramsToLbDetail().fmt(2)} lb",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF2E7D32)
                        )
                    }
                    TextButton(onClick = { confirmRemove = true }) { Text("Quitar") }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = false,
                        onCheckedChange = { checked -> if (checked) step = SpongeDialogStep.PickBatch },
                        enabled = batches.isNotEmpty(),
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "¿Esponja para esta masa?",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = BrandColors.AccentDeep
                        )
                        Text(
                            "Se declara para toda la amasada; se reparte sola entre los productos.",
                            style = MaterialTheme.typography.labelSmall,
                            color = BrandColors.AccentDeep
                        )
                        if (batches.isEmpty()) {
                            Text(
                                "Sin lotes activos de esponja.",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFD32F2F)
                            )
                        }
                    }
                }
            }
        }
    }

    when (val s = step) {
        SpongeDialogStep.PickBatch -> SpongeBatchPickerDialog(
            batches = batches,
            onSelect = { batch -> step = SpongeDialogStep.PickAmount(batch) },
            onDismiss = { step = null }
        )
        is SpongeDialogStep.PickAmount -> SpongeAmountDialog(
            batch = s.batch,
            onContinue = { totalGrams, isAll -> step = SpongeDialogStep.Confirm(s.batch, totalGrams, isAll) },
            onBack = { step = SpongeDialogStep.PickBatch },
            onDismiss = { step = null }
        )
        is SpongeDialogStep.Confirm -> SpongeConfirmDialog(
            batch = s.batch,
            totalGrams = s.totalGrams,
            isAll = s.isAll,
            onConfirm = {
                onConfirm(s.batch, s.totalGrams)
                step = null
            },
            onDismiss = { step = null }
        )
        null -> {}
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("¿Quitar esponja?") },
            text = {
                Text(
                    "Se devuelven ${spongeGramsUsedTotal.gramsToLbDetail().fmt(2)} lb a " +
                        "$spongeBatchName.",
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            confirmButton = {
                TextButton(onClick = { onRemove(); confirmRemove = false }) { Text("Quitar") }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancelar") } }
        )
    }
}

/** Diálogo 1: cuál lote de esponja (preparación) usar. */
@Composable
private fun SpongeBatchPickerDialog(
    batches: List<PreparationBatch>,
    onSelect: (PreparationBatch) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("¿Qué esponja vas a usar?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                batches.forEach { batch ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFF5F5F5),
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(batch) }
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                batch.preparationName,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Saldo: ${batch.remainingWeightGrams.gramsToLbDetail().fmt(2)} lb",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

/**
 * Diálogo 2: toda la masa o cantidad parcial. El campo de libras es estado
 * local puro de este diálogo (una instancia nueva cada vez que se abre) — no
 * se sincroniza con ningún valor derivado del propio tecleo, que era la causa
 * de que el punto decimal "se corriera" en el campo anterior.
 */
@Composable
private fun SpongeAmountDialog(
    batch: PreparationBatch,
    onContinue: (totalGrams: Double, isAll: Boolean) -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit
) {
    var useAll by remember { mutableStateOf(true) }
    var libras by remember { mutableStateOf("") }
    val saldoLb = batch.remainingWeightGrams.gramsToLbDetail()
    // El teclado numérico en español mete coma decimal, no punto.
    val partialLb = libras.replace(',', '.').toDoubleOrNull()
    val partialValid = partialLb != null && partialLb > 0.0 && partialLb <= saldoLb + 0.01

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("¿Cuánto vas a usar de ${batch.preparationName}?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { useAll = true }
                ) {
                    RadioButton(selected = useAll, onClick = { useAll = true })
                    Text(
                        "Toda la masa (saldo: ${saldoLb.fmt(2)} lb)",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { useAll = false }
                ) {
                    RadioButton(selected = !useAll, onClick = { useAll = false })
                    Text("Cantidad parcial", style = MaterialTheme.typography.bodyLarge)
                }
                if (!useAll) {
                    OutlinedTextField(
                        value = libras,
                        onValueChange = { libras = it },
                        label = { Text("Libras") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        suffix = { Text("lb") },
                        isError = libras.isNotEmpty() && !partialValid,
                        supportingText = {
                            if (libras.isNotEmpty() && !partialValid) {
                                Text(
                                    "Máximo ${saldoLb.fmt(2)} lb",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = useAll || partialValid,
                onClick = {
                    val totalGrams = if (useAll) batch.remainingWeightGrams
                                     else toBaseUnit(partialLb ?: 0.0, "lb")
                    onContinue(totalGrams, useAll)
                }
            ) { Text("Continuar") }
        },
        dismissButton = { TextButton(onClick = onBack) { Text("Atrás") } }
    )
}

/** Diálogo 3: confirmación final antes de descontar. */
@Composable
private fun SpongeConfirmDialog(
    batch: PreparationBatch,
    totalGrams: Double,
    isAll: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val lb = totalGrams.gramsToLbDetail().fmt(2)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirmar uso de esponja") },
        text = {
            Text(
                "¿Confirmas usar ${if (isAll) "toda la masa ($lb lb)" else "$lb lb"} de " +
                    "${batch.preparationName}? Se descuenta del lote de preparación de inmediato.",
                style = MaterialTheme.typography.bodyLarge
            )
        },
        confirmButton = { Button(onClick = onConfirm) { Text("Confirmar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun LateOrdersPanelHeader(count: Int, isInProgress: Boolean) {
    val bg = if (isInProgress) Color(0xFFFFF3E0) else Color(0xFFFFFDE7)
    val accent = if (isInProgress) BrandColors.AccentDeep else Color(0xFFF9A825)
    Surface(
        color = bg,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                Icons.Default.NotificationsActive,
                contentDescription = null,
                tint = accent
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Pedidos tardíos pendientes de aprobación ($count)",
                    fontWeight = FontWeight.Bold,
                    color = accent,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    if (isInProgress)
                        "El lote ya está en producción. Habla con el panadero antes de aprobar."
                    else "Pedidos enviados después de creado el lote. Aprueba para añadirlos.",
                    style = MaterialTheme.typography.labelSmall,
                    color = BrandColors.AccentDeep
                )
            }
        }
    }
}

@Composable
private fun LateOrderCard(
    order: ProductionOrder,
    lines: List<ProductionOrderLine>,
    lineCountFallback: Int,
    isLoading: Boolean,
    onAdd: () -> Unit
) {
    val displayCount = if (lines.isNotEmpty()) lines.size else lineCountFallback
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Color(0xFFFFE0B2)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        order.sellingBranchName.ifBlank { "Sucursal —" },
                        fontWeight = FontWeight.SemiBold,
                        color = BrandColors.Primary
                    )
                    Text(
                        "$displayCount productos · enviado ${formatHourMinute(order.createdAt)}" +
                            (if (order.createdByUserName.isNotBlank()) " por ${order.createdByUserName}" else ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    if (!order.notes.isNullOrBlank()) {
                        Text(
                            "Nota: ${order.notes}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF555555)
                        )
                    }
                }
                Button(
                    onClick = onAdd,
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Aprobar pedido tardío", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Desglose de productos del pedido tardío — el panadero debe ver qué se está
            // pidiendo extra antes de aprobar.
            if (lines.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Surface(
                    color = Color(0xFFFFF8E1),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
                        Text(
                            "Productos pedidos:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = BrandColors.AccentDeep
                        )
                        Spacer(Modifier.height(4.dp))
                        lines.forEach { line ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    line.productName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF333333),
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    "${line.requestedQuantity.toInt()} ${line.productUnit}".trim(),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = BrandColors.AccentDeep
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StartShiftDialog(
    lot: ProductionLot,
    isStarting: Boolean,
    onStart: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { /* No se puede cerrar — solo iniciar o volver con back. */ },
        title = {
            Text(
                "Iniciar orden de producción",
                fontWeight = FontWeight.Bold,
                color = BrandColors.Primary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    lot.shiftName.ifBlank { "Turno no especificado" },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = BrandColors.Primary
                )
                Text(
                    lot.producingBranchName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF555555)
                )
                Text(
                    formatProductionDate(lot.productionDate),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                Spacer(Modifier.height(8.dp))
                Surface(
                    color = Color(0xFFFFF8E1),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Schedule,
                            contentDescription = null,
                            tint = BrandColors.AccentDeep
                        )
                        Text(
                            "Al iniciar se registra la hora de inicio del turno trabajado. " +
                                "El tiempo se contará automáticamente hasta el cierre del lote.",
                            style = MaterialTheme.typography.bodySmall,
                            color = BrandColors.AccentDeep
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onStart,
                enabled = !isStarting,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))
            ) {
                if (isStarting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Iniciar orden de producción", fontWeight = FontWeight.Bold)
                }
            }
        }
    )
}

@Composable
private fun ElapsedTimeBanner(startedAtMillis: Long) {
    var nowMillis by remember { mutableStateOf(KotlinClock.System.now().toEpochMilliseconds()) }
    LaunchedEffect(startedAtMillis) {
        while (true) {
            nowMillis = KotlinClock.System.now().toEpochMilliseconds()
            delay(1000L)
        }
    }
    val totalSec = ((nowMillis - startedAtMillis) / 1000L).coerceAtLeast(0L)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    val text = "${h.toString().padStart(2, '0')}:" +
        "${m.toString().padStart(2, '0')}:" +
        s.toString().padStart(2, '0')

    Surface(
        color = Color(0xFFFFF3E0),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.Schedule,
                contentDescription = null,
                tint = BrandColors.AccentDeep,
                modifier = Modifier.size(16.dp)
            )
            Text(
                "Tiempo trabajado",
                style = MaterialTheme.typography.labelMedium,
                color = BrandColors.AccentDeep,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text,
                style = MaterialTheme.typography.titleMedium,
                color = BrandColors.AccentDeep,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

/**
 * "Todo" / "Solo lo que falta": cuál de las dos recetas de esta masa se muestra.
 *
 * Aparece solo cuando hay producción extra declarada. Las etiquetas dicen QUÉ cubre
 * cada una y no "total"/"parcial", porque lo que el panadero necesita decidir es si ya
 * amasó o no — y leer "parcial" no contesta esa pregunta.
 */
@Composable
private fun RecipeScopeSelector(showOnlyExtra: Boolean, onChange: (Boolean) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !showOnlyExtra,
                onClick = { onChange(false) },
                label = { Text("Todo el día") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = BrandColors.PrimaryContainer,
                    selectedLabelColor = BrandColors.Primary
                )
            )
            FilterChip(
                selected = showOnlyExtra,
                onClick = { onChange(true) },
                label = { Text("Solo lo extra") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = BrandColors.PrimaryContainer,
                    selectedLabelColor = BrandColors.Primary
                )
            )
        }
        Text(
            if (showOnlyExtra)
                "Receta solo del pan que se agregó después. Usala si ya amasaste lo que pedían."
            else
                "Receta de todo lo del día, incluido lo que se agregó después. " +
                    "Usala si todavía no has amasado esta masa.",
            style = MaterialTheme.typography.labelSmall,
            color = BrandColors.MutedSlate
        )
    }
}

/**
 * Una masa de la orden de producción.
 *
 * Encabeza el nombre de la RECETA (no el del producto) porque la amasada es una
 * sola, y debajo lista los productos con las unidades a sacar de cada uno. Ese
 * desglose va acá adentro —y no en una pantalla aparte— porque el panadero
 * necesita ver de un vistazo cuánto sale de qué sin perder la masa de vista, y
 * en la tablet de la planta el scroll cuesta.
 */
@Composable
private fun MasaProductionCard(
    group: MasaGroup<ProductionLotLine>,
    showProduced: Boolean,
    /** true si TODOS los productos de esta masa ya tienen conteo confirmado —
     *  ver `producedCountConfirmed`. Cambia el lápiz por un check verde. */
    countConfirmed: Boolean,
    isSelected: Boolean,
    isVisited: Boolean,
    doughGramsOf: (ProductionLotLine) -> Double,
    missingRawWeight: Set<String>,
    spongeGrams: Double,
    /** Masa extra que otros productos (de otra masa) necesitan como relleno de
     *  ESTA receta — ver [DoughFilling]. Ya sumada al total mostrado. */
    extraDoughGrams: Double = 0.0,
    /** Detalle de quién aporta esa masa extra: (nombre del producto, gramos). */
    extraDoughContributions: List<Pair<String, Double>> = emptyList(),
    /** Tiempo de amasado de la receta, en segundos. 0 = esta masa no tiene timer. */
    kneadingTimeSeconds: Int,
    kneadingTimer: KneadingTimerState?,
    /** `productId` → unidades por bandeja, para el desglose bajo cada cantidad. */
    unitsPerTrayByProduct: Map<String, Int> = emptyMap(),
    /** null = oculta el ícono. Abre el registro de conteo real filtrado a
     *  SOLO esta masa (mismo sheet que "Registrar conteo" del TopAppBar). */
    onRegisterCount: (() -> Unit)? = null,
    /** null = oculta el ícono. Abre la hoja de "¿qué pasó?" — pan perdido o pan
     *  que hay que hacer de más — para SOLO esta masa. Va en la tarjeta y no en
     *  el TopAppBar porque lo que se quema es una masa concreta, no el lote. */
    onAdjustProduction: (() -> Unit)? = null,
    onStartKneadingTimer: (seconds: Int) -> Unit,
    onStopKneadingTimer: () -> Unit,
    onClick: () -> Unit
) {
    val totalUnits = group.items.sumOf {
        // El pan que hay que hacer de más SÍ entra en el total: todavía no se amasó,
        // así que el panadero tiene que verlo en la lista. Lo perdido no — ya se amasó
        // una vez, y sumarlo mostraría una amasada que nunca se hace de un solo golpe.
        val good = if (showProduced && it.producedQuantity > 0.0) it.producedQuantity
                   else it.plannedQuantity
        good + it.extraQuantity
    }
    val wasteUnits = group.items.sumOf { it.wasteQuantity }
    val extraUnits = group.items.sumOf { it.extraQuantity }
    val hasAdjustments = wasteUnits > 0.0 || extraUnits > 0.0
    val totalDough = group.items.sumOf(doughGramsOf) + extraDoughGrams
    val isRinging = kneadingTimer?.isRinging == true

    val isFillingOnly = group.items.isEmpty()

    // Pulso sutil en el borde de la masa seleccionada: comunica "esto es lo que
    // se está preparando" sin ser llamativo — solo corre mientras isSelected.
    val selectedBorderAlpha = if (isSelected) {
        val infiniteTransition = rememberInfiniteTransition(label = "masaSelectedPulse")
        val alpha by infiniteTransition.animateFloat(
            initialValue = 0.35f,
            targetValue = 0.9f,
            animationSpec = infiniteRepeatable(tween(1300), RepeatMode.Reverse),
            label = "masaSelectedPulseAlpha"
        )
        alpha
    } else 1f

    // La banda del encabezado lleva su propio color por masa (en vez de un
    // título liso sobre fondo blanco): así una tarjeta se distingue de la
    // siguiente de un vistazo al bajar la lista, no solo por el espaciado.
    val headerBg = when {
        isRinging -> Color(0xFFFFEBEE)
        isSelected -> BrandColors.Primary
        isFillingOnly -> Color(0xFFFFF8E1)
        isVisited -> Color(0xFFFDF6E7)
        else -> Color(0xFFEAF3F8)
    }
    val headerFg = if (isSelected) Color.White else BrandColors.Primary
    val headerMetaColor = if (isSelected) Color.White.copy(alpha = 0.82f) else BrandColors.PrimaryDark

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = when {
            isRinging -> BorderStroke(2.dp, Color(0xFFC62828))
            isSelected -> BorderStroke(1.5.dp, BrandColors.Primary.copy(alpha = selectedBorderAlpha))
            isFillingOnly -> BorderStroke(1.dp, BrandColors.Accent)
            else -> null
        }
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().background(headerBg).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        group.title,
                        fontWeight = FontWeight.Bold,
                        color = headerFg,
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (group.items.isEmpty()) {
                        // Masa sintética: nadie la pidió directamente, solo existe
                        // porque otro producto la lleva de relleno.
                        Text(
                            "No pedida por ninguna sucursal — se amasa solo por el relleno de " +
                                extraDoughContributions.joinToString(", ") { (name, _) -> name },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = BrandColors.AccentDeep
                        )
                        if (totalDough > 0) {
                            Text(
                                "${(totalDough / GRAMS_PER_LB_DETAIL).fmt(1)} lb de masa a amasar",
                                style = MaterialTheme.typography.labelSmall,
                                color = headerMetaColor
                            )
                        }
                    } else {
                        Text(
                            buildString {
                                append("${totalUnits.toInt()} unidades")
                                if (totalDough > 0) append(" · ${(totalDough / GRAMS_PER_LB_DETAIL).fmt(1)} lb de masa")
                                if (spongeGrams > 0) append(" · con esponja")
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = headerMetaColor
                        )
                        if (extraDoughContributions.isNotEmpty()) {
                            Text(
                                "+ ${(extraDoughGrams / GRAMS_PER_LB_DETAIL).fmt(1)} lb para relleno de " +
                                    extraDoughContributions.joinToString(", ") { (name, _) -> name },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = if (isSelected) Color.White else BrandColors.AccentDeep
                            )
                        }
                        // Lo declarado en esta masa se dice en la tarjeta y no solo dentro
                        // de la hoja: es lo que explica por qué el total subió, o por qué
                        // al cerrar el lote se va a descontar más masa de la que salió.
                        if (hasAdjustments) {
                            Text(
                                buildString {
                                    if (extraUnits > 0.0) append("+${extraUnits.toInt()} de más")
                                    if (wasteUnits > 0.0) {
                                        if (isNotEmpty()) append("  ·  ")
                                        append("${wasteUnits.toInt()} perdidas")
                                    }
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.White else BrandColors.AccentDeep
                            )
                        }
                    }
                }
                if (onAdjustProduction != null) {
                    IconButton(onClick = onAdjustProduction, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.ReportProblem,
                            contentDescription = "Se perdió pan o hay que hacer más de ${group.title}",
                            // Naranja = "esto necesita tus ojos": hay merma o producción
                            // extra anotada en esta masa. Sin nada anotado va en el color
                            // del encabezado, como un botón más.
                            tint = if (hasAdjustments) BrandColors.Accent else headerFg,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                if (onRegisterCount != null) {
                    IconButton(onClick = onRegisterCount, modifier = Modifier.size(32.dp)) {
                        Icon(
                            if (countConfirmed) Icons.Default.CheckCircle else Icons.Default.Edit,
                            contentDescription = if (countConfirmed)
                                "Conteo confirmado de ${group.title} — toca para ajustar"
                            else "Registrar conteo de ${group.title}",
                            tint = if (countConfirmed) Color(0xFF66BB6A) else headerFg,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.width(2.dp))
                }
                if (kneadingTimeSeconds > 0) {
                    KneadingTimerChip(
                        totalSeconds = kneadingTimeSeconds,
                        timer = kneadingTimer,
                        onStart = { onStartKneadingTimer(kneadingTimeSeconds) },
                        onStop = onStopKneadingTimer
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = "Ver receta de ${group.title}",
                    tint = headerFg
                )
            }

            // Productos de esta masa en flujo horizontal: si dos caben en la
            // misma línea (p. ej. Capa y Gusanito, de la misma amasada) van
            // juntos en vez de uno por fila, para minimizar el scroll.
            if (group.items.isNotEmpty()) {
                // Foto de referencia de un especial abierta en grande. El chip es
                // demasiado chico para juzgar una figura, y el panadero necesita
                // verla antes de bolear, no adivinarla de la descripción.
                var zoomedPhoto by remember { mutableStateOf<Pair<String, String>?>(null) }
                zoomedPhoto?.let { (title, url) ->
                    SpecialPhotoDialog(title = title, photoUrl = url) { zoomedPhoto = null }
                }
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    group.items.forEach { line ->
                        val qty = if (showProduced && line.producedQuantity > 0.0) line.producedQuantity
                                  else line.plannedQuantity
                        val missing = line.productId in missingRawWeight
                        Surface(
                            shape = RoundedCornerShape(50),
                            // El especial se pinta distinto (crema + borde dorado):
                            // no es el pan de siempre y el panadero tiene que notarlo
                            // antes de contar piezas, no después.
                            color = if (line.isSpecial) Color(0xFFFFF5DF) else Color(0xFFF6F6F4),
                            border = if (line.isSpecial) BorderStroke(1.dp, BrandColors.Accent) else null,
                            onClick = {
                                if (line.isSpecial && line.specialPhotoUrl.isNotEmpty()) {
                                    zoomedPhoto = line.productName to line.specialPhotoUrl
                                }
                            },
                            enabled = line.isSpecial && line.specialPhotoUrl.isNotEmpty()
                        ) {
                            Row(
                                modifier = Modifier.padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Placa sólida (antes: fondo durazno + número dorado, muy
                                // poco contraste) — número blanco sobre azul, se lee de lejos.
                                // El desglose de bandejas va DEBAJO DEL NOMBRE, no dentro de
                                // la placa: meterlo en la placa la ensanchaba tanto que dejaban
                                // de caber 2-3 productos por línea.
                                val trayLabel = formatTrayBreakdown(
                                    qty.toInt(), unitsPerTrayByProduct[line.productId] ?: 0
                                )
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (missing) Color(0xFFC62828) else BrandColors.Primary
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            "${qty.toInt()}",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color.White,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier
                                                .defaultMinSize(minWidth = 30.dp)
                                                .padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                                if (line.isSpecial && line.specialPhotoUrl.isNotEmpty()) {
                                    ProductImage(
                                        imageUrl = line.specialPhotoUrl,
                                        contentDescription = null,
                                        modifier = Modifier.size(28.dp).clip(RoundedCornerShape(6.dp))
                                    )
                                }
                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
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
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            color = if (missing) Color(0xFFC62828) else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    if (line.isSpecial && !missing) {
                                        Text(
                                            "Especial · ${formatBakerWeight(line.rawWeightGrams ?: 0.0)} c/u" +
                                                (if (line.specialPhotoUrl.isNotEmpty()) " · toca para ver la foto" else ""),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = BrandColors.AccentDeep
                                        )
                                    }
                                    if (missing) {
                                        Text(
                                            "sin peso",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFFC62828)
                                        )
                                    } else if (trayLabel != null) {
                                        Text(
                                            trayLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.Gray
                                        )
                                    }
                                    // Ajuste de la planta con el lote ya armado: si el
                                    // panadero imprimió la orden, el papel dice el número
                                    // viejo y esto es lo único que se lo avisa.
                                    if (line.adjustmentNote.isNotBlank()) {
                                        Text(
                                            "Cambió: ${line.adjustmentNote}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = BrandColors.AccentDeep
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (missingRawWeight.isNotEmpty()) {
                Text(
                    "Los productos en rojo no tienen peso en crudo configurado, así que no " +
                        "entran en el cálculo de la masa.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFC62828),
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp)
                )
            }
        }
    }
}

/**
 * Icono/chip del timer de amasado de una masa. Tres estados: sin arrancar
 * (toca para iniciar), corriendo (cuenta regresiva, toca para cancelar) y
 * sonando (toca para detener la alarma).
 */
@Composable
private fun KneadingTimerChip(
    totalSeconds: Int,
    timer: KneadingTimerState?,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    when {
        timer == null -> Surface(
            onClick = onStart,
            shape = RoundedCornerShape(50),
            color = Color(0xFFEAF3F8),
            border = BorderStroke(1.dp, BrandColors.Primary.copy(alpha = 0.35f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Schedule,
                    contentDescription = "Iniciar timer de amasado (${formatKneadingTime(totalSeconds)})",
                    tint = BrandColors.Primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    formatKneadingTime(totalSeconds),
                    style = MaterialTheme.typography.labelMedium,
                    color = BrandColors.Primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        timer.isRinging -> {
            val infiniteTransition = rememberInfiniteTransition(label = "kneadingRing")
            val scale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.2f,
                animationSpec = infiniteRepeatable(tween(400), RepeatMode.Reverse),
                label = "kneadingRingScale"
            )
            Surface(
                onClick = onStop,
                shape = RoundedCornerShape(50),
                color = Color(0xFFC62828),
                modifier = Modifier.scale(scale)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.NotificationsActive,
                        contentDescription = "Detener alarma de ${timer.recipeName}",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Detener", style = MaterialTheme.typography.labelMedium, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
        else -> Surface(
            onClick = onStop,
            shape = RoundedCornerShape(50),
            color = Color(0xFFFFF3E0),
            border = BorderStroke(1.dp, BrandColors.Accent)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Schedule,
                    contentDescription = "Timer de amasado corriendo, toca para cancelar",
                    tint = BrandColors.AccentDeep,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    formatKneadingTime(timer.remainingSeconds),
                    style = MaterialTheme.typography.labelMedium,
                    color = BrandColors.AccentDeep,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Aviso fijo (fuera del scroll) de qué masa(s) terminaron de amasar. Existe
 * para que, si el panadero está viendo otra masa cuando suena, sepa cuál es
 * sin tener que buscarla entre las tarjetas — la tarjeta en sí también se
 * resalta, pero puede estar fuera de la pantalla visible.
 */
@Composable
private fun KneadingRingingBanner(
    timers: List<KneadingTimerState>,
    onStop: (recipeId: String) -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "kneadingBanner")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.65f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
        label = "kneadingBannerAlpha"
    )
    Column(
        modifier = Modifier.fillMaxWidth()
            .background(Color(0xFFC62828).copy(alpha = alpha))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        timers.forEach { timer ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text(
                    "¡Listo el amasado de ${timer.recipeName}!",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { onStop(timer.recipeId) }) {
                    Text("Detener", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * "+3 sobre lo pedido" / "faltan 5" / "✓ coincide" bajo cada campo de conteo,
 * para que el panadero vea de un vistazo si le sobró o le faltó sin tener que
 * restar mentalmente contra el "Pedido: X" de arriba.
 */
@Composable
private fun DeltaVsPlannedLabel(actual: Int, planned: Int) {
    if (planned <= 0) return
    val delta = actual - planned
    val (text, color) = when {
        delta == 0 -> "✓ coincide con lo pedido" to Color(0xFF2E7D32)
        delta > 0 -> "+$delta sobre lo pedido" to Color(0xFF2E7D32)
        else -> "faltan ${-delta}" to Color(0xFFC62828)
    }
    Text(text, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Medium)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RegisterProductionSheet(
    masaGroups: List<MasaGroup<ProductionLotLine>>,
    /** `productId` → unidades por bandeja (0 = sin configurar, conteo solo por unidad neta). */
    unitsPerTrayByProduct: Map<String, Int>,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onSave: (Map<String, Double>) -> Unit
) {
    val lines = remember(masaGroups) { masaGroups.flatMap { it.items } }
    val lineIdsKey = remember(lines) { lines.joinToString(",") { it.id } }

    // Dos modos de captura por línea, según si el producto tiene bandeja
    // configurada: neto directo (como antes), o bandejas+sueltos (más rápido
    // de contar físicamente — full trays + lo que sobra en la última).
    // Pre-llenamos descomponiendo el producedQuantity actual si > 0.
    var netInputs by remember(lineIdsKey) {
        mutableStateOf(
            lines.filter { (unitsPerTrayByProduct[it.productId] ?: 0) <= 0 }
                .associate { line ->
                    line.id to (line.producedQuantity.takeIf { it > 0.0 }?.toInt()?.toString() ?: "")
                }
        )
    }
    var trayInputs by remember(lineIdsKey) {
        mutableStateOf(
            lines.mapNotNull { line ->
                val perTray = unitsPerTrayByProduct[line.productId] ?: 0
                if (perTray <= 0 || line.producedQuantity <= 0.0) return@mapNotNull null
                line.id to (line.producedQuantity.toInt() / perTray).toString()
            }.toMap()
        )
    }
    var looseInputs by remember(lineIdsKey) {
        mutableStateOf(
            lines.mapNotNull { line ->
                val perTray = unitsPerTrayByProduct[line.productId] ?: 0
                if (perTray <= 0 || line.producedQuantity <= 0.0) return@mapNotNull null
                line.id to (line.producedQuantity.toInt() % perTray).toString()
            }.toMap()
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = BrandColors.Ivory
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
            Text(
                "Registrar producción",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge,
                color = BrandColors.Primary
            )
            Text(
                "Anota las unidades reales que han salido. Lo que registres aquí se guarda " +
                    "al instante. Deja en blanco lo que aún no salga.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )

            LazyColumn(
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                masaGroups.forEach { group ->
                    item(key = "header_${group.key}") {
                        Text(
                            group.title,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = BrandColors.Primary,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                    }
                    items(group.items, key = { it.id }) { line ->
                        val perTray = unitsPerTrayByProduct[line.productId] ?: 0
                        val planned = line.plannedQuantity.toInt()
                        Surface(
                            color = Color.White,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            line.productName,
                                            fontWeight = FontWeight.SemiBold,
                                            color = BrandColors.Primary
                                        )
                                        Text(
                                            "Pedido: $planned ${line.productUnit}".trim(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.Gray
                                        )
                                    }
                                    if (perTray <= 0) {
                                        Column(horizontalAlignment = Alignment.End) {
                                            OutlinedTextField(
                                                value = netInputs[line.id].orEmpty(),
                                                onValueChange = { txt ->
                                                    val filtered = txt.filter { it.isDigit() }
                                                    netInputs = netInputs.toMutableMap().also { it[line.id] = filtered }
                                                },
                                                placeholder = { Text("0") },
                                                suffix = { Text("/$planned") },
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                singleLine = true,
                                                modifier = Modifier.width(130.dp),
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedBorderColor = BrandColors.Accent,
                                                    focusedLabelColor = BrandColors.AccentDeep
                                                )
                                            )
                                            netInputs[line.id]?.toIntOrNull()?.let { actual ->
                                                Spacer(Modifier.height(2.dp))
                                                DeltaVsPlannedLabel(actual = actual, planned = planned)
                                            }
                                        }
                                    }
                                }
                                if (perTray > 0) {
                                    Spacer(Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = trayInputs[line.id].orEmpty(),
                                            onValueChange = { txt ->
                                                val filtered = txt.filter { it.isDigit() }
                                                trayInputs = trayInputs.toMutableMap().also { it[line.id] = filtered }
                                            },
                                            label = { Text("Bandejas") },
                                            placeholder = { Text("0") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            singleLine = true,
                                            modifier = Modifier.width(84.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = BrandColors.Accent,
                                                focusedLabelColor = BrandColors.AccentDeep
                                            )
                                        )
                                        OutlinedTextField(
                                            value = looseInputs[line.id].orEmpty(),
                                            onValueChange = { txt ->
                                                val filtered = txt.filter { it.isDigit() }
                                                looseInputs = looseInputs.toMutableMap().also { it[line.id] = filtered }
                                            },
                                            label = { Text("Sueltos") },
                                            placeholder = { Text("0") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            singleLine = true,
                                            modifier = Modifier.width(84.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = BrandColors.Accent,
                                                focusedLabelColor = BrandColors.AccentDeep
                                            )
                                        )
                                        val netTotal = (trayInputs[line.id]?.toIntOrNull() ?: 0) * perTray +
                                            (looseInputs[line.id]?.toIntOrNull() ?: 0)
                                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                            Row(verticalAlignment = Alignment.Bottom) {
                                                Text(
                                                    "$netTotal",
                                                    style = MaterialTheme.typography.headlineSmall,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = BrandColors.AccentDeep
                                                )
                                                Text(
                                                    " / $planned u.",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    color = Color.Gray
                                                )
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(2.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "$perTray u. por bandeja",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.Gray
                                        )
                                        val hasTrayInput = !trayInputs[line.id].isNullOrBlank() ||
                                            !looseInputs[line.id].isNullOrBlank()
                                        if (hasTrayInput) {
                                            val netTotal = (trayInputs[line.id]?.toIntOrNull() ?: 0) * perTray +
                                                (looseInputs[line.id]?.toIntOrNull() ?: 0)
                                            DeltaVsPlannedLabel(actual = netTotal, planned = planned)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).height(48.dp)
                ) { Text("Cancelar") }
                Button(
                    onClick = {
                        // Solo enviamos las líneas donde se escribió algo — las que quedan
                        // en blanco no se tocan (conservan su producedQuantity actual).
                        val fromNet = netInputs.mapNotNull { (id, txt) ->
                            txt.toDoubleOrNull()?.let { id to it }
                        }
                        val fromTrays = lines.mapNotNull { line ->
                            val perTray = unitsPerTrayByProduct[line.productId] ?: 0
                            if (perTray <= 0) return@mapNotNull null
                            val trayTxt = trayInputs[line.id].orEmpty()
                            val looseTxt = looseInputs[line.id].orEmpty()
                            if (trayTxt.isBlank() && looseTxt.isBlank()) return@mapNotNull null
                            val net = (trayTxt.toIntOrNull() ?: 0) * perTray + (looseTxt.toIntOrNull() ?: 0)
                            line.id to net.toDouble()
                        }
                        onSave((fromNet + fromTrays).toMap())
                    },
                    modifier = Modifier.weight(1.5f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandColors.AccentDeep)
                ) { Text("Guardar todo", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun LotStatusChip(status: String) {
    val (color, bg) = when (status) {
        ProductionLotStatus.OPEN        -> Color(0xFF1565C0) to Color(0xFFE3F2FD)
        ProductionLotStatus.IN_PROGRESS -> Color(0xFFF57C00) to Color(0xFFFFF3E0)
        ProductionLotStatus.COMPLETED   -> Color(0xFF2E7D32) to Color(0xFFE8F5E9)
        else                            -> Color.Gray to Color(0xFFF5F5F5)
    }
    Surface(shape = RoundedCornerShape(20.dp), color = bg) {
        Text(
            ProductionLotStatus.label(status),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Lista horizontal con badges por cada [CustomerLineSpec] de la línea. Cada chip
 * muestra el tipo de cliente (Revendedor / Encargo / Pastel) y el nombre. Si la
 * línea tiene customSpec, lo agrega abajo en cursiva-ish para el panadero.
 *
 * Se renderiza solo si la línea tiene specs — no afecta líneas que vienen 100%
 * de sucursales.
 */
@Composable
private fun CustomerSpecsRow(specs: List<CustomerLineSpec>) {
    if (specs.isEmpty()) return
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        FlowRowSimple(items = specs) { spec ->
            CustomerSpecChip(spec)
        }
        val customSpecs = specs.filter { it.customSpec.isNotEmpty() }
        if (customSpecs.isNotEmpty()) {
            customSpecs.forEach { spec ->
                Text(
                    "✱ ${spec.customerName}: ${spec.customSpec}",
                    style = MaterialTheme.typography.labelSmall,
                    color = BrandColors.AccentDeep,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun CustomerSpecChip(spec: CustomerLineSpec) {
    val (bg, fg, label) = when (spec.customerType) {
        CustomerOrderType.RESELLER -> Triple(
            BrandColors.Accent.copy(alpha = 0.18f),
            BrandColors.AccentDeep,
            "Revendedor"
        )
        CustomerOrderType.CAKE -> Triple(
            Color(0xFFAD1457).copy(alpha = 0.18f),
            Color(0xFFAD1457),
            "Pastel"
        )
        CustomerOrderType.RETAIL_SPECIAL -> Triple(
            Color(0xFF1565C0).copy(alpha = 0.18f),
            Color(0xFF1565C0),
            "Encargo"
        )
        else -> Triple(Color(0x11000000), Color(0xFF424242), "Cliente")
    }
    Surface(
        shape = RoundedCornerShape(50),
        color = bg,
        modifier = Modifier.padding(end = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "$label · ${spec.customerName} (${spec.quantity.toInt()})",
                style = MaterialTheme.typography.labelSmall,
                color = fg,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * FlowRow simple (sin la API experimental de Material3). Lista horizontal que
 * salta a la siguiente línea cuando no cabe. Suficiente para pintar 1-5 chips.
 */
@Composable
private fun <T> FlowRowSimple(
    items: List<T>,
    item: @Composable (T) -> Unit
) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items.forEach { item(it) }
    }
}

/**
 * Foto de referencia de un pedido especial, en grande.
 *
 * Existe porque la descripción sola no alcanza: "pirujo del doble" puede ser
 * más largo o más gordo, y esa diferencia se decide al bolear. Verla en el
 * chiquito del chip no sirve; verla al tamaño de la tablet, sí.
 */
@Composable
private fun SpecialPhotoDialog(
    title: String,
    photoUrl: String,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), color = Color.White) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = BrandColors.AccentDeep)
                    Text(
                        title,
                        fontWeight = FontWeight.Bold,
                        color = BrandColors.Primary,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                ProductImage(
                    imageUrl = photoUrl,
                    contentDescription = "Foto de referencia de $title",
                    modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
                TextButton(onClick = onDismiss) {
                    Text("Cerrar", fontWeight = FontWeight.Bold, color = BrandColors.Primary)
                }
            }
        }
    }
}
