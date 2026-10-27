package com.bakery.erp.presentation.finishedStock

import com.bakery.erp.ui.BrandTopAppBar

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import com.bakery.erp.model.FinishedMovementType
import com.bakery.erp.model.FinishedProductStock
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.ui.AdaptiveContainer
import com.bakery.erp.ui.LoadingOverlay
import com.bakery.erp.util.fmt
import com.bakery.erp.ui.theme.BrandColors.Primary as DeepSeaBlue
import com.bakery.erp.ui.theme.BrandColors.Ivory as WarmIvory
import com.bakery.erp.ui.theme.BrandColors.Accent as BurntOrange
import com.bakery.erp.ui.theme.BrandColors


private data class MovementOption(val type: String, val signFactor: Int, val label: String, val hint: String)

private val MOVEMENT_OPTIONS = listOf(
    MovementOption(FinishedMovementType.MERMA, -1, "Merma",
        "Producto dañado, vencido o desperdiciado. Sale del inventario."),
    MovementOption(FinishedMovementType.AJUSTE, -1, "Ajuste por menos",
        "Conteo manual encontró menos producto del que registra el sistema."),
    MovementOption(FinishedMovementType.AJUSTE, 1, "Ajuste por más",
        "Conteo manual encontró más producto del que registra el sistema, o conteo inicial de un producto nuevo en la sucursal."),
    MovementOption(FinishedMovementType.VENTA, -1, "Venta directa",
        "Venta puntual de mostrador sin pasar por POS. Sale del inventario.")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinishedStockAdjustScreen(
    branchId: String,
    productId: String?,
    onNavigateBack: () -> Unit,
    authViewModel: AuthViewModel,
    viewModel: FinishedStockViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val user = authState.user
    val companyId = user?.orgId ?: ""
    val snackbarHostState = remember { SnackbarHostState() }

    val branch = remember(state.branches, branchId) {
        state.branches.firstOrNull { it.id == branchId }
    }

    var selectedProductId by rememberSaveable { mutableStateOf(productId ?: "") }
    // Desde "Agregar producto" casi siempre es cargar lo que ya estaba en el estante.
    var selectedOption by rememberSaveable {
        mutableStateOf(if (productId == null) "Ajuste por más" else MOVEMENT_OPTIONS.first().label)
    }
    var productSearch by rememberSaveable { mutableStateOf("") }
    var quantityInput by rememberSaveable { mutableStateOf("") }
    var reason by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }
    LaunchedEffect(state.isSaved) {
        if (state.isSaved) { viewModel.clearSaved(); onNavigateBack() }
    }

    val productStock: FinishedProductStock? = remember(state.stocks, selectedProductId) {
        state.stocks.firstOrNull { it.productId == selectedProductId }
    }
    val selectedProduct = remember(state.catalog, selectedProductId) {
        state.catalog.firstOrNull { it.id == selectedProductId }
    }
    val productName = productStock?.productName ?: selectedProduct?.name
    val option = remember(selectedOption) {
        MOVEMENT_OPTIONS.firstOrNull { it.label == selectedOption } ?: MOVEMENT_OPTIONS.first()
    }
    val qty = quantityInput.toDoubleOrNull() ?: 0.0
    val signedDelta = qty * option.signFactor
    val newStock = (productStock?.currentStock ?: 0.0) + signedDelta
    val exceedsStock = option.signFactor < 0 && qty > (productStock?.currentStock ?: 0.0)

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            BrandTopAppBar(
                title = { Text("Registrar movimiento", fontWeight = FontWeight.SemiBold) },
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
            AdaptiveContainer {
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = DeepSeaBlue)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("Sucursal", style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.85f))
                            Text(branch?.name ?: "(no encontrada)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold, color = Color.White)
                        }
                    }

                    // Selector de producto (solo si no vino preseleccionado). Busca en todo el
                    // catálogo, no solo en lo que ya tiene stock: así entra el conteo inicial de un
                    // pan que todavía no ha llegado en ningún despacho.
                    if (productId == null) {
                        if (productName != null) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                elevation = CardDefaults.cardElevation(0.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Producto", style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(productName, style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold, color = DeepSeaBlue)
                                        Text(
                                            "Stock actual: ${(productStock?.currentStock ?: 0.0).fmt(0)} " +
                                                (selectedProduct?.unit?.takeIf { it.isNotBlank() } ?: "unidades"),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    TextButton(onClick = { selectedProductId = ""; productSearch = "" }) {
                                        Text("Cambiar")
                                    }
                                }
                            }
                        } else {
                            OutlinedTextField(
                                value = productSearch,
                                onValueChange = { productSearch = it },
                                label = { Text("Buscar producto *") },
                                leadingIcon = { Icon(Icons.Default.Search, null) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            val stockById = remember(state.stocks) {
                                state.stocks.associate { it.productId to it.currentStock }
                            }
                            val matches = remember(state.catalog, productSearch) {
                                if (productSearch.isBlank()) emptyList()
                                else state.catalog
                                    .filter { it.name.contains(productSearch.trim(), ignoreCase = true) }
                                    .take(12)
                            }
                            if (productSearch.isNotBlank() && matches.isEmpty()) {
                                Text(
                                    "No hay productos activos con ese nombre. Créalo primero en Productos.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            matches.forEach { p ->
                                Card(
                                    onClick = { selectedProductId = p.id },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    elevation = CardDefaults.cardElevation(0.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(p.name, style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.weight(1f))
                                        Text(
                                            stockById[p.id]?.let { "${it.fmt(0)} en tienda" } ?: "Sin stock aún",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(0.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Producto", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(productStock?.productName ?: "(no encontrado)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold, color = DeepSeaBlue)
                                Text(
                                    "Stock actual: ${productStock?.currentStock?.fmt(0) ?: "—"} unidades",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Tipo de movimiento
                    var typeExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = typeExpanded,
                        onExpandedChange = { typeExpanded = !typeExpanded }
                    ) {
                        OutlinedTextField(
                            readOnly = true,
                            value = option.label,
                            onValueChange = {},
                            label = { Text("Tipo de movimiento *") },
                            supportingText = { Text(option.hint, style = MaterialTheme.typography.labelSmall) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = typeExpanded,
                            onDismissRequest = { typeExpanded = false }
                        ) {
                            MOVEMENT_OPTIONS.forEach { opt ->
                                DropdownMenuItem(
                                    text = { Text(opt.label) },
                                    onClick = { selectedOption = opt.label; typeExpanded = false }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = quantityInput,
                        onValueChange = { quantityInput = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Cantidad *") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        isError = exceedsStock,
                        supportingText = {
                            when {
                                qty <= 0 -> {}
                                exceedsStock -> Text(
                                    "Excede el stock actual (${productStock?.currentStock?.fmt(0)} unidades).",
                                    color = Color(0xFFB00020)
                                )
                                else -> Text(
                                    "Nuevo stock: ${newStock.fmt(0)} unidades",
                                    color = BrandColors.AccentDeep
                                )
                            }
                        }
                    )

                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("Razón / detalle *") },
                        placeholder = { Text("ej. Pan del día anterior, producto contaminado") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4,
                        isError = reason.isBlank()
                    )

                    val canSave = productName != null && qty > 0 &&
                        !exceedsStock && reason.isNotBlank() && !state.isLoading

                    Button(
                        onClick = {
                            val u = user ?: return@Button
                            val name = productName ?: return@Button
                            // Entra al promedio que ya tenía; si nunca tuvo, al costo de compra
                            // (reventa). El pan sin historia entra a 0, como antes.
                            val unitCost = productStock?.avgUnitCost?.takeIf { it > 0.0 }
                                ?: selectedProduct?.purchaseCost ?: 0.0
                            viewModel.registerAdjustment(
                                companyId = companyId,
                                productId = selectedProductId,
                                productName = name,
                                branchId = branchId,
                                branchName = productStock?.branchName ?: branch?.name.orEmpty(),
                                signedDelta = signedDelta,
                                type = option.type,
                                reason = reason,
                                currentUser = u,
                                unitCost = unitCost
                            )
                        },
                        enabled = canSave,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (state.isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp),
                                color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Save, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Registrar movimiento", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
    LoadingOverlay(visible = state.isLoading)
    }
}
