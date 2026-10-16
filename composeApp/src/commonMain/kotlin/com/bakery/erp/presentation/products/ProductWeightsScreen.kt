package com.bakery.erp.presentation.products

import com.bakery.erp.ui.BrandTopAppBar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.Product
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.presentation.production.ProductionViewModel
import com.bakery.erp.presentation.production.groupByMasa
import com.bakery.erp.ui.AdaptiveContainer
import com.bakery.erp.ui.LoadingOverlay
import com.bakery.erp.ui.LocalPreferences
import com.bakery.erp.ui.PreferenceKeys
import com.bakery.erp.util.fmt
import com.bakery.erp.ui.theme.BrandColors

private const val GRAMS_PER_OZ = 28.3495

/**
 * Unidad en la que se teclea. El modelo siempre guarda gramos
 * ([Product.rawWeightGrams]); esto es solo el borde de entrada.
 */
private enum class WeightInputUnit(val label: String, val suffix: String) {
    OUNCES("Onzas", "oz"),
    GRAMS("Gramos", "g");

    fun toGrams(value: Double): Double = if (this == OUNCES) value * GRAMS_PER_OZ else value
    fun fromGrams(grams: Double): Double = if (this == OUNCES) grams / GRAMS_PER_OZ else grams
    /** Onzas con 2 decimales, gramos con 1: la onza necesita más precisión. */
    fun format(grams: Double): String =
        fromGrams(grams).fmt(if (this == OUNCES) 2 else 1).trimEnd('0').trimEnd('.')

    val other: WeightInputUnit get() = if (this == OUNCES) GRAMS else OUNCES
}

/**
 * Carga rápida del peso en crudo de los productos.
 *
 * El peso en crudo es lo que pesa una pieza de masa **antes de hornear**, y es el
 * dato del que cuelga todo el cálculo de producción: sin él no se puede escalar
 * la receta, ni saber si la amasada llega al mínimo, ni descontar bien los
 * insumos. Cargarlo producto por producto desde el formulario completo es
 * inviable cuando faltan decenas.
 *
 * Va agrupado por masa (igual que el resto del flujo) porque los productos de una
 * misma receta tienen pesos parecidos y se llenan de corrido; además así se ve de
 * un golpe qué masas quedan todavía sin poder calcularse.
 *
 * Se ingresa en **onzas**, que es como pesa el panadero. El modelo guarda gramos
 * ([Product.rawWeightGrams]) porque es la unidad en la que trabajan las recetas;
 * la conversión ocurre acá, en el borde, y no se propaga al resto del sistema.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductWeightsScreen(
    onNavigateBack: () -> Unit,
    authViewModel: AuthViewModel,
    viewModel: ProductViewModel,
    productionViewModel: ProductionViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val productionState by productionViewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val companyId = authState.user?.orgId ?: ""
    val snackbarHostState = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(companyId) {
        if (companyId.isNotEmpty()) {
            viewModel.loadProducts(companyId)
            // Trae el mapa producto → masa (se cachea en el ViewModel).
            productionViewModel.loadOrderFormData(companyId, authState.user)
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }
    LaunchedEffect(state.isSaved) {
        if (state.isSaved) {
            snackbarHostState.showSnackbar("Pesos guardados")
            viewModel.clearSaved()
        }
    }

    // Texto editado por producto, en la unidad activa. Solo lo que el usuario
    // tocó entra al guardado.
    var edits by remember { mutableStateOf(emptyMap<String, String>()) }
    var onlyMissing by rememberSaveable { mutableStateOf(true) }
    // Se recuerda entre sesiones: el panadero pesa siempre en la misma unidad y
    // no tiene por qué re-elegirla cada vez que abre la pantalla.
    var unit by rememberSaveable {
        mutableStateOf(
            runCatching {
                WeightInputUnit.valueOf(
                    LocalPreferences.getString(
                        PreferenceKeys.WEIGHT_INPUT_UNIT,
                        WeightInputUnit.OUNCES.name
                    )
                )
            }.getOrDefault(WeightInputUnit.OUNCES)
        )
    }

    /**
     * Cambia la unidad convirtiendo lo ya tecleado, para que el peso real no se
     * mueva. Si solo se cambiara la etiqueta, un "1" escrito en onzas pasaría a
     * significar 1 gramo y se guardarían pesos absurdos sin que se note.
     */
    fun switchUnit(target: WeightInputUnit) {
        if (target == unit) return
        val from = unit
        edits = edits.mapValues { (_, text) ->
            val parsed = text.replace(',', '.').toDoubleOrNull()
            if (parsed == null || parsed <= 0.0) text
            else target.format(from.toGrams(parsed))
        }
        unit = target
        LocalPreferences.putString(PreferenceKeys.WEIGHT_INPUT_UNIT, target.name)
    }

    val activeProducts = remember(state.productList) { state.productList.filter { it.active } }
    val missingCount = remember(activeProducts) {
        activeProducts.count { (it.rawWeightGrams ?: 0.0) <= 0.0 }
    }

    val visibleProducts = remember(activeProducts, onlyMissing) {
        if (onlyMissing) activeProducts.filter { (it.rawWeightGrams ?: 0.0) <= 0.0 }
        else activeProducts
    }
    val groups = remember(visibleProducts, productionState.recipeRefByProductId) {
        groupByMasa(
            items = visibleProducts,
            recipeIdOf = { productionState.recipeRefByProductId[it.id]?.id },
            recipeNameOf = { productionState.recipeRefByProductId[it.id]?.name },
            nameOf = { it.name }
        )
    }
    val lastVisibleId = groups.lastOrNull()?.items?.lastOrNull()?.id

    /** Lo tecleado está en la unidad activa; al modelo van gramos. */
    fun parsedGrams(product: Product): Double? {
        val text = edits[product.id] ?: return null
        return text.replace(',', '.').toDoubleOrNull()
            ?.takeIf { it > 0.0 }
            ?.let { unit.toGrams(it) }
    }
    val pendingSave = remember(edits, state.productList) {
        activeProducts.mapNotNull { p -> parsedGrams(p)?.let { p.id to it } }
            .filter { (id, grams) ->
                activeProducts.find { it.id == id }?.rawWeightGrams != grams
            }
            .toMap()
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                BrandTopAppBar(
                    title = {
                        Column {
                            Text("Pesos en crudo", fontWeight = FontWeight.SemiBold)
                            Text(
                                if (missingCount == 0) "Todos los productos tienen peso"
                                else "$missingCount sin peso de ${activeProducts.size}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFFFD166)
                            )
                        }
                    },
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
            bottomBar = {
                Surface(shadowElevation = 8.dp, color = Color.White) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (pendingSave.isEmpty()) "Sin cambios"
                            else "${pendingSave.size} por guardar",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                viewModel.saveRawWeights(companyId, pendingSave, unit.suffix)
                                edits = emptyMap()
                            },
                            enabled = pendingSave.isNotEmpty() && !state.isLoading,
                            colors = ButtonDefaults.buttonColors(containerColor = BrandColors.Primary),
                            modifier = Modifier.height(48.dp)
                        ) { Text("Guardar", fontWeight = FontWeight.Bold) }
                    }
                }
            },
            containerColor = BrandColors.Ivory
        ) { padding ->
            AdaptiveContainer(modifier = Modifier.padding(padding)) {
                Column(Modifier.fillMaxSize()) {
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
                        Text(
                            "El peso en crudo es lo que pesa una pieza de masa antes de hornear. " +
                                "Sin él no se puede escalar la receta ni descontar insumos. " +
                                "Elegí en qué unidad escribir; debajo de cada producto se ve " +
                                "la equivalencia en la otra.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = onlyMissing,
                            onClick = { onlyMissing = !onlyMissing },
                            label = { Text(if (onlyMissing) "Solo los que faltan" else "Todos") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = BrandColors.Primary,
                                selectedLabelColor = Color.White
                            )
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            "Ingresar en",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        WeightInputUnit.entries.forEach { option ->
                            FilterChip(
                                selected = unit == option,
                                onClick = { switchUnit(option) },
                                label = { Text(option.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = BrandColors.Accent,
                                    selectedLabelColor = BrandColors.OnAccent
                                )
                            )
                        }
                    }

                    if (visibleProducts.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF2E7D32),
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Todos los productos activos ya tienen peso en crudo",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        return@Column
                    }

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        groups.forEach { group ->
                            item(key = "h_${group.key}") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFFEAF3F8), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        group.title.uppercase(),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = BrandColors.Primary
                                    )
                                    Text(
                                        "${group.items.size}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = BrandColors.Primary
                                    )
                                }
                            }
                            items(group.items, key = { it.id }) { product ->
                                val text = edits[product.id]
                                    ?: product.rawWeightGrams?.takeIf { it > 0.0 }
                                        ?.let { unit.format(it) }
                                    ?: ""
                                WeightRow(
                                    product = product,
                                    text = text,
                                    unit = unit,
                                    isLast = product.id == lastVisibleId,
                                    onTextChange = { edits = edits + (product.id to it) },
                                    onImeNext = {
                                        if (!focusManager.moveFocus(FocusDirection.Down)) {
                                            focusManager.clearFocus()
                                        }
                                    },
                                    onImeDone = { focusManager.clearFocus() }
                                )
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
private fun WeightRow(
    product: Product,
    text: String,
    unit: WeightInputUnit,
    isLast: Boolean,
    onTextChange: (String) -> Unit,
    onImeNext: () -> Unit,
    onImeDone: () -> Unit
) {
    val typed = text.replace(',', '.').toDoubleOrNull()
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    product.name,
                    fontWeight = FontWeight.SemiBold,
                    color = BrandColors.Primary,
                    style = MaterialTheme.typography.bodyMedium
                )
                // Siempre se muestra la equivalencia en la OTRA unidad, para
                // poder verificar sin cambiar el selector.
                Text(
                    if (typed != null && typed > 0.0) {
                        val grams = unit.toGrams(typed)
                        "= ${unit.other.format(grams)} ${unit.other.suffix}"
                    } else "sin peso",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (typed != null && typed > 0.0) MaterialTheme.colorScheme.onSurfaceVariant
                            else Color(0xFFC62828)
                )
            }
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { txt ->
                    if (txt.isEmpty() || txt.matches(Regex("^\\d*[.,]?\\d*$"))) onTextChange(txt)
                },
                label = { Text(unit.suffix) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = if (isLast) ImeAction.Done else ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { onImeNext() },
                    onDone = { onImeDone() }
                ),
                singleLine = true,
                modifier = Modifier.width(110.dp)
            )
        }
    }
}
