package com.bakery.erp.presentation.dispatch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.DispatchNoteLine
import com.bakery.erp.model.Permission
import com.bakery.erp.model.PriceSourceTag
import com.bakery.erp.model.hasPermission
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.presentation.production.ProductionViewModel
import com.bakery.erp.presentation.production.groupByMasa
import com.bakery.erp.ui.AdaptiveContainer
import com.bakery.erp.ui.LoadingOverlay
import com.bakery.erp.util.fmt
import com.bakery.erp.ui.theme.BrandColors.Primary as DeepSeaBlue
import com.bakery.erp.ui.theme.BrandColors.Ivory as WarmIvory
import com.bakery.erp.ui.theme.BrandColors.Accent as BurntOrange
import com.bakery.erp.ui.theme.BrandColors


private data class PackLineState(
    val original: DispatchNoteLine,
    val packedInput: String,
    val reason: String
) {
    fun toLine(): DispatchNoteLine {
        val qty = packedInput.toDoubleOrNull() ?: 0.0
        return original.copy(
            packedQuantity = qty,
            packedReason = if (qty != original.requestedQuantity && reason.isNotBlank()) reason.trim() else null
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DispatchNotePackScreen(
    noteId: String,
    onNavigateBack: () -> Unit,
    authViewModel: AuthViewModel,
    viewModel: DispatchViewModel,
    productionViewModel: ProductionViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val productionState by productionViewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val user = authState.user
    val companyId = user?.orgId ?: ""
    val canSeeValue = user?.hasPermission(Permission.VIEW_DISPATCH_VALUE) == true
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(noteId, companyId) {
        if (companyId.isNotEmpty()) viewModel.loadNoteDetail(companyId, noteId)
    }
    // Mapa producto → masa, para agrupar la lista de empaque igual que la
    // hoja de trabajo del panadero (mismo pan de la misma masa, junto).
    LaunchedEffect(companyId) {
        if (companyId.isNotEmpty()) productionViewModel.loadRecipeRefByProductIdIfNeeded(companyId)
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }
    LaunchedEffect(state.isSaved) {
        if (state.isSaved) { viewModel.clearSaved(); onNavigateBack() }
    }

    val note = state.selectedNote
    var lineStates by remember(note?.id) {
        mutableStateOf<List<PackLineState>>(emptyList())
    }
    LaunchedEffect(note?.id, note?.lines) {
        if (note != null) {
            lineStates = note.lines.map { line ->
                PackLineState(
                    original = line,
                    packedInput = (if (line.packedQuantity > 0) line.packedQuantity else line.requestedQuantity).fmt(0),
                    reason = line.packedReason ?: ""
                )
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Empacar despacho", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DeepSeaBlue,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = WarmIvory
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isDetailLoading || note == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                else -> AdaptiveContainer {
                    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Header(note.destinationBranchName, note.shiftName, formatDay(note.productionDate))

                        if (note.hasPriceMissing || note.lines.any { it.priceSource == PriceSourceTag.NONE }) {
                            Spacer(Modifier.height(8.dp))
                            PriceWarningCard()
                        }

                        Spacer(Modifier.height(12.dp))

                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                "Productos del pedido",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = DeepSeaBlue,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedButton(onClick = {
                                lineStates = lineStates.map {
                                    it.copy(packedInput = it.original.requestedQuantity.fmt(0), reason = "")
                                }
                            }) {
                                Icon(Icons.Default.DoneAll, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Igual a lo pedido")
                            }
                        }

                        Spacer(Modifier.height(6.dp))

                        val recipeRefByProductId = productionState.recipeRefByProductId
                        val groupedLines = remember(lineStates, recipeRefByProductId) {
                            groupByMasa(
                                items = lineStates,
                                recipeIdOf = { dispatchLineRecipeId(it.original, recipeRefByProductId) },
                                recipeNameOf = { dispatchLineRecipeName(it.original, recipeRefByProductId) },
                                nameOf = { it.original.productName }
                            )
                        }

                        LazyColumn(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            groupedLines.forEach { group ->
                                item(key = "header_${group.key}") {
                                    Text(
                                        group.title,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = DeepSeaBlue,
                                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                                    )
                                }
                                items(group.items, key = { it.original.id }) { ls ->
                                    PackLineCard(
                                        state = ls,
                                        canSeeValue = canSeeValue,
                                        onChange = { updated ->
                                            lineStates = lineStates.map {
                                                if (it.original.id == updated.original.id) updated else it
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        val totalLines = lineStates.size
                        val withReasonsNeeded = lineStates.filter {
                            val v = it.packedInput.toDoubleOrNull() ?: 0.0
                            v != it.original.requestedQuantity && it.reason.isBlank()
                        }
                        val hasUnpriced = lineStates.any {
                            it.original.priceSource == PriceSourceTag.NONE &&
                                (it.packedInput.toDoubleOrNull() ?: 0.0) > 0
                        }
                        val canSubmit = withReasonsNeeded.isEmpty() && !hasUnpriced

                        if (withReasonsNeeded.isNotEmpty()) {
                            Text(
                                "Falta justificar ${withReasonsNeeded.size} línea(s) con cantidad distinta a lo pedido.",
                                style = MaterialTheme.typography.bodySmall,
                                color = BrandColors.AccentDeep,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                        if (hasUnpriced) {
                            Text(
                                "Hay producto(s) sin precio en la lista \"${note.priceListName}\". Configura el precio o ponle un override antes de cerrar.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFB00020),
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }

                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {
                                user ?: return@Button
                                viewModel.confirmPacking(
                                    companyId, noteId,
                                    lineStates.map { it.toLine() },
                                    user
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = canSubmit && !state.isLoading
                        ) {
                            if (state.isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp),
                                    color = Color.White, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.CheckCircle, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Marcar como lista para envío", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
    LoadingOverlay(visible = state.isLoading)
    }
}

@Composable
private fun Header(destination: String, shift: String, day: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DeepSeaBlue)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                "Hacia $destination",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            Text(
                "Turno: ${shift.ifEmpty { "—" }} · Producción del $day",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.85f)
            )
        }
    }
}

@Composable
private fun PriceWarningCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.WarningAmber, null, tint = Color(0xFFB00020))
            Spacer(Modifier.width(8.dp))
            Text(
                "Algunas líneas no tienen precio resoluble. Configura un factor en la lista o un override en el producto antes de cerrar la nota.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFB00020)
            )
        }
    }
}

@Composable
private fun PackLineCard(
    state: PackLineState,
    canSeeValue: Boolean,
    onChange: (PackLineState) -> Unit
) {
    val packed = state.packedInput.toDoubleOrNull() ?: 0.0
    val diff = packed != state.original.requestedQuantity
    val noPrice = state.original.priceSource == PriceSourceTag.NONE
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (noPrice) Color(0xFFFFEBEE) else Color.White
        ),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Inventory, null, tint = DeepSeaBlue, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(dispatchLineLabel(
                            state.original.productName,
                            state.original.isSpecial,
                            state.original.specialRawWeightGrams
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = DeepSeaBlue)
                    val priceTag = if (canSeeValue) {
                        if (state.original.unitWholesalePrice > 0) "Q${state.original.unitWholesalePrice.fmt(2)} c/u"
                        else "sin precio"
                    } else ""
                    Text(
                        "Pedido: ${state.original.requestedQuantity.fmt(0)} ${state.original.productUnit}" +
                            if (priceTag.isNotEmpty()) " · $priceTag" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedTextField(
                    value = state.packedInput,
                    onValueChange = { onChange(state.copy(packedInput = it.filter { c -> c.isDigit() || c == '.' })) },
                    label = { Text("Empacado") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.widthIn(min = 110.dp, max = 140.dp)
                )
            }
            if (diff) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.reason,
                    onValueChange = { onChange(state.copy(reason = it)) },
                    label = { Text("Razón del ajuste *") },
                    placeholder = { Text("ej. No alcanzó la producción") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = state.reason.isBlank()
                )
            }
            if (noPrice && packed > 0) {
                Text(
                    "Sin precio definido — bloquea el cierre.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFB00020),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
