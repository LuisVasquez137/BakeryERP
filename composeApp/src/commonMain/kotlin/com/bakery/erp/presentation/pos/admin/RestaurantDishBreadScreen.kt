package com.bakery.erp.presentation.pos.admin

import com.bakery.erp.ui.BrandTopAppBar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.Product
import com.bakery.erp.model.RestaurantDish
import com.bakery.erp.model.RestaurantDishBread
import com.bakery.erp.model.breadSummary
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.ui.AdaptiveContainer
import com.bakery.erp.ui.LoadingOverlay
import com.bakery.erp.ui.theme.BrandColors
import com.bakery.erp.util.fmtTrim

/**
 * Qué pan del mostrador lleva cada platillo del restaurante. Al llegar la cuenta de ComandaYa,
 * ese pan se descuenta solo del inventario de la sucursal: la cocina no anota nada.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestaurantDishBreadScreen(
    onNavigateBack: () -> Unit,
    authViewModel: AuthViewModel,
    viewModel: RestaurantDishBreadViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val companyId = authState.user?.orgId.orEmpty()
    val snackbar = remember { SnackbarHostState() }
    var editing by remember { mutableStateOf<RestaurantDish?>(null) }
    var openedAt by remember { mutableStateOf(0L) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(companyId) { if (companyId.isNotEmpty()) viewModel.load(companyId) }
    LaunchedEffect(state.error) {
        if (editing == null) state.error?.let { snackbar.showSnackbar(it); viewModel.clearError() }
    }
    LaunchedEffect(state.savedAt) { if (state.savedAt > openedAt) editing = null }

    val shown = remember(state.dishes, query) {
        val q = query.trim()
        if (q.isEmpty()) state.dishes else state.dishes.filter { it.dishName.contains(q, ignoreCase = true) }
    }

    Scaffold(
        topBar = {
            BrandTopAppBar(
                title = { Text("Pan en platillos", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        AdaptiveContainer(modifier = Modifier.padding(padding)) {
            when {
                state.isLoading && state.dishes.isEmpty() ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                else -> LazyColumn(
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item { Intro(empty = state.dishes.isEmpty()) }
                    if (state.dishes.isNotEmpty()) item {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text("Buscar platillo") },
                            leadingIcon = { Icon(Icons.Default.Search, null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    items(shown, key = { it.externalItemId }) { dish ->
                        DishCard(dish, onClick = { openedAt = state.savedAt; editing = dish })
                    }
                }
            }
        }
    }

    editing?.let { dish ->
        DishBreadDialog(
            dish = dish,
            products = state.products,
            error = state.error,
            isSaving = state.isSaving,
            onDismiss = { editing = null; viewModel.clearError() },
            onSave = { breads -> viewModel.save(companyId, dish, breads) }
        )
    }
    LoadingOverlay(visible = state.isSaving, message = "Guardando…")
}

@Composable
private fun Intro(empty: Boolean) {
    Surface(color = BrandColors.PrimaryContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(
                "El pan que la cocina toma del mostrador para un platillo.",
                fontWeight = FontWeight.SemiBold, color = BrandColors.Primary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Cuando llega la cuenta del restaurante, ese pan se descuenta solo del inventario de " +
                    "la sucursal (a costo, como «Usado en restaurante»), y el conteo de cierre ya no lo " +
                    "ve como faltante. Si la cuenta se corrige o se cancela antes de recibirse, se ajusta solo." +
                    if (empty) "\n\nTodavía no ha llegado ninguna cuenta de ComandaYa: los platillos " +
                        "aparecen aquí solos con la primera." else "",
                style = MaterialTheme.typography.bodySmall,
                color = BrandColors.TextSlate
            )
        }
    }
}

@Composable
private fun DishCard(dish: RestaurantDish, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).background(BrandColors.PrimaryContainer, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Restaurant, null, tint = BrandColors.Primary)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(dish.dishName.ifBlank { "(sin nombre)" }, fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleSmall)
                Text(
                    if (dish.hasBread) dish.breadSummary() else "Sin pan",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (dish.hasBread) BrandColors.TextSlate else BrandColors.MutedSlate
                )
            }
        }
    }
}

@Composable
private fun DishBreadDialog(
    dish: RestaurantDish,
    products: List<Product>,
    error: String?,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (List<RestaurantDishBread>) -> Unit
) {
    // productId → panes por platillo, en el orden en que se agregaron.
    var breads by remember(dish.externalItemId) {
        mutableStateOf(dish.breads.filter { it.quantity > 0.0 }.associate { it.productId to it })
    }
    var query by remember { mutableStateOf("") }
    val matches = remember(products, query, breads) {
        val q = query.trim()
        if (q.isEmpty()) emptyList()
        else products.filter { it.id !in breads && it.name.contains(q, ignoreCase = true) }.take(6)
    }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text(dish.dishName.ifBlank { "Platillo" }) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("¿Qué pan lleva cada uno?", style = MaterialTheme.typography.bodyMedium)
                if (breads.isEmpty()) {
                    Text("Sin pan: no descuenta nada.", style = MaterialTheme.typography.bodySmall,
                        color = BrandColors.MutedSlate)
                }
                breads.values.forEach { b ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(b.productName, Modifier.weight(1f), fontWeight = FontWeight.Medium)
                        IconButton(onClick = {
                            breads = if (b.quantity <= 1.0) breads - b.productId
                            else breads + (b.productId to b.copy(quantity = b.quantity - 1.0))
                        }, enabled = !isSaving) { Icon(Icons.Default.Remove, "Uno menos") }
                        Text(b.quantity.fmtTrim(), fontWeight = FontWeight.Bold, modifier = Modifier.width(32.dp))
                        IconButton(onClick = {
                            breads = breads + (b.productId to b.copy(quantity = b.quantity + 1.0))
                        }, enabled = !isSaving) { Icon(Icons.Default.Add, "Uno más") }
                    }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Agregar pan: buscar") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    singleLine = true,
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth()
                )
                matches.forEach { p ->
                    Card(
                        onClick = {
                            breads = breads + (p.id to RestaurantDishBread(p.id, p.name, 1.0))
                            query = ""
                        },
                        colors = CardDefaults.cardColors(containerColor = BrandColors.Sand),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(p.name, Modifier.padding(horizontal = 12.dp, vertical = 10.dp), color = BrandColors.TextSlate)
                    }
                }
                if (error != null) {
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(breads.values.toList()) }, enabled = !isSaving) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) { Text("Cancelar") }
        }
    )
}
