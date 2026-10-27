package com.bakery.erp.presentation.transfers

import com.bakery.erp.ui.BrandTopAppBar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.BranchCapability
import com.bakery.erp.model.DISPATCH_TRIP_FIRST
import com.bakery.erp.model.DISPATCH_TRIP_SECOND
import com.bakery.erp.model.Product
import com.bakery.erp.model.StockTransfer
import com.bakery.erp.model.StockTransferItemKind
import com.bakery.erp.model.StockTransferLine
import com.bakery.erp.model.canAccessBranch
import com.bakery.erp.model.tripTitle
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.presentation.pos.ProductPickerDialog
import com.bakery.erp.ui.AdaptiveContainer
import com.bakery.erp.ui.LoadingOverlay
import com.bakery.erp.ui.theme.BrandColors
import com.bakery.erp.util.fmtTrim

/**
 * Pedir a otra sucursal lo que se hace para existencia (pan molde, rosca, pan tostado) para un día
 * y un viaje del camión. No mueve stock: quien lo tiene lo envía cuando lo prepara. Ver "Pedido de
 * la sucursal" en `model/StockTransfer.kt`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockTransferRequestFormScreen(
    onNavigateBack: () -> Unit,
    onSent: (transferId: String) -> Unit,
    authViewModel: AuthViewModel,
    viewModel: StockTransferViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val user = authState.user
    val companyId = user?.orgId.orEmpty()
    val snackbar = remember { SnackbarHostState() }

    val mine = remember(state.branches, user?.id) { state.branches.filter { user?.canAccessBranch(it.id) == true } }
    var destinationId by rememberSaveable { mutableStateOf("") }
    var originId by rememberSaveable { mutableStateOf("") }
    var forTomorrow by rememberSaveable { mutableStateOf(true) }
    var trip by rememberSaveable { mutableStateOf(DISPATCH_TRIP_FIRST) }
    var note by rememberSaveable { mutableStateOf("") }
    /** productId → cantidad escrita. */
    var quantities by remember { mutableStateOf(mapOf<String, String>()) }
    /** Productos agregados a mano (fuera de lo que la sucursal guarda para existencia). */
    var extraIds by remember { mutableStateOf(listOf<String>()) }
    var picking by remember { mutableStateOf(false) }

    LaunchedEffect(companyId) { if (companyId.isNotEmpty()) viewModel.loadBranches(companyId) }
    LaunchedEffect(mine) { if (destinationId.isEmpty() && mine.size == 1) destinationId = mine.first().id }
    LaunchedEffect(companyId, originId) { if (companyId.isNotEmpty()) viewModel.prepareForm(companyId, originId) }
    LaunchedEffect(state.sentId) { state.sentId?.let { viewModel.consumeSentId(); onSent(it) } }
    LaunchedEffect(state.error) { state.error?.let { viewModel.clearError(); snackbar.showSnackbar(it) } }

    // Las plantas primero: casi siempre se le pide a quien hornea.
    val origins = remember(state.branches, destinationId) {
        state.branches.filter { it.id != destinationId }
            .sortedWith(compareByDescending<com.bakery.erp.model.Branch> { it.capabilities.contains(BranchCapability.CAN_PRODUCE) }.thenBy { it.name })
    }
    val origin = state.branches.firstOrNull { it.id == originId }
    val destination = state.branches.firstOrNull { it.id == destinationId }
    val productsById = remember(state.products) { state.products.associateBy { it.id } }
    // Lo que ese origen guarda para existencia sale de una vez, sin buscarlo.
    val keptHere = remember(origin, state.products) {
        val assigned = origin?.assignedProductIds.orEmpty().toSet()
        state.products.filter { it.isMadeForStock && it.id in assigned }
    }
    val shown = keptHere + extraIds.mapNotNull { id -> productsById[id] }.filter { it !in keptHere }

    fun parsed(id: String): Double? = quantities[id]?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it > 0.0 }

    fun buildDraft() = StockTransfer(
        originBranchId = originId,
        originBranchName = origin?.name.orEmpty(),
        destinationBranchId = destinationId,
        destinationBranchName = destination?.name.orEmpty(),
        note = note,
        trip = trip,
        forDate = startOfDayMillis(plusDays = if (forTomorrow) 1 else 0),
        lines = shown.mapNotNull { p ->
            val q = parsed(p.id) ?: return@mapNotNull null
            StockTransferLine(
                kind = StockTransferItemKind.PRODUCT, itemId = p.id, itemName = p.name,
                unit = p.unit.ifBlank { "u" }, requestedQuantity = q, unitCost = p.purchaseCost ?: 0.0
            )
        }
    )

    val canRequest = destinationId.isNotEmpty() && originId.isNotEmpty() &&
        shown.any { parsed(it.id) != null } && !state.isBusy

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                BrandTopAppBar(
                    title = { Text("Pedir a otra sucursal", fontWeight = FontWeight.SemiBold) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = BrandColors.Primary,
                        titleContentColor = BrandColors.Ivory,
                        navigationIconContentColor = BrandColors.Ivory
                    )
                )
            },
            bottomBar = {
                Surface(color = BrandColors.Ivory, shadowElevation = 8.dp) {
                    AdaptiveContainer {
                        Button(
                            onClick = { user?.let { viewModel.request(companyId, buildDraft(), it) } },
                            enabled = canRequest,
                            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BrandColors.Primary)
                        ) {
                            Text(if (origin != null) "Pedir a ${origin.name}" else "Pedir", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbar) },
            containerColor = BrandColors.Ivory
        ) { padding ->
            AdaptiveContainer(modifier = Modifier.padding(padding)) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (mine.size > 1) {
                                Label("Es para")
                                ChipRow {
                                    mine.forEach { b ->
                                        BranchChip(b.name, selected = destinationId == b.id) {
                                            destinationId = b.id
                                            if (originId == b.id) originId = ""
                                        }
                                    }
                                }
                            }
                            Label("Se lo pides a")
                            ChipRow {
                                origins.forEach { b ->
                                    BranchChip(b.name, selected = originId == b.id) {
                                        if (originId != b.id) { originId = b.id; quantities = emptyMap(); extraIds = emptyList() }
                                    }
                                }
                            }
                            Label("¿Cuándo lo necesitas?")
                            ChipRow {
                                BranchChip("Hoy", selected = !forTomorrow) { forTomorrow = false }
                                BranchChip("Mañana", selected = forTomorrow) { forTomorrow = true }
                                Spacer(Modifier.width(8.dp))
                                listOf(DISPATCH_TRIP_FIRST, DISPATCH_TRIP_SECOND).forEach { t ->
                                    BranchChip(tripTitle(t), selected = trip == t) { trip = t }
                                }
                            }
                        }
                    }
                    if (originId.isEmpty()) item {
                        Text("Elige a qué sucursal se lo pides.", color = BrandColors.MutedSlate, modifier = Modifier.padding(vertical = 12.dp))
                    } else {
                        if (keptHere.isEmpty() && extraIds.isEmpty()) item {
                            Text(
                                "${origin?.name} no tiene productos marcados \"Se hace para existencia\". " +
                                    "Puedes agregar cualquier producto con el botón de abajo.",
                                color = BrandColors.MutedSlate, style = MaterialTheme.typography.bodySmall
                            )
                        }
                        items(shown, key = { it.id }) { p ->
                            RequestLine(
                                product = p,
                                available = state.originProductStock[p.id]?.currentStock ?: 0.0,
                                originName = origin?.name.orEmpty(),
                                text = quantities[p.id].orEmpty(),
                                removable = p.id in extraIds,
                                onChange = { v -> quantities = quantities + (p.id to v.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(7)) },
                                onRemove = { extraIds = extraIds - p.id; quantities = quantities - p.id }
                            )
                        }
                        item {
                            OutlinedButton(onClick = { picking = true }) {
                                Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text("Otro producto")
                            }
                        }
                    }
                    item {
                        OutlinedTextField(
                            value = note,
                            onValueChange = { note = it.take(300) },
                            label = { Text("Nota (opcional)") },
                            placeholder = { Text("Ej.: el tostado en bolsa grande") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item { Spacer(Modifier.padding(12.dp)) }
                }
            }
        }
        LoadingOverlay(visible = state.isBusy || state.isFormLoading, message = if (state.isBusy) "Enviando pedido…" else "Cargando…")
    }

    if (picking) {
        val taken = shown.map { it.id }.toSet()
        ProductPickerDialog(
            title = "¿Qué más pides?",
            products = state.products.filter { it.id !in taken }
                .sortedByDescending { (state.originProductStock[it.id]?.currentStock ?: 0.0) > 0.0 },
            stockOf = { id -> state.originProductStock[id]?.currentStock ?: 0.0 },
            showSearch = true,
            onDismiss = { picking = false },
            onSelect = { p: Product -> extraIds = extraIds + p.id; picking = false }
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = BrandColors.Primary)
}

@Composable
private fun RequestLine(
    product: Product,
    available: Double,
    originName: String,
    text: String,
    removable: Boolean,
    onChange: (String) -> Unit,
    onRemove: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(product.name, fontWeight = FontWeight.SemiBold, color = BrandColors.TextSlate)
                Text(
                    "Hay ${available.fmtTrim()} en $originName",
                    style = MaterialTheme.typography.bodySmall, color = BrandColors.MutedSlate
                )
            }
            OutlinedTextField(
                value = text,
                onValueChange = onChange,
                label = { Text("Pido") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.widthIn(max = 110.dp)
            )
            if (removable) IconButton(onClick = onRemove) { Icon(Icons.Default.Close, contentDescription = "Quitar") }
        }
    }
}
