package com.bakery.erp.presentation.purchases

import com.bakery.erp.util.fmt
import com.bakery.erp.util.fmtMoney
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bakery.erp.ui.isBarcodeScanSupported
import com.bakery.erp.ui.rememberBarcodeScanner
import com.bakery.erp.model.Branch
import com.bakery.erp.model.DEFAULT_TAX_RATE_PERCENT
import com.bakery.erp.model.TaxBreakdown
import com.bakery.erp.model.Ingredient
import com.bakery.erp.model.Product
import com.bakery.erp.model.PurchaseLine
import com.bakery.erp.model.PurchaseLineKind
import com.bakery.erp.model.PurchaseStatus
import com.bakery.erp.model.Supplier
import com.bakery.erp.model.convertUnits
import com.bakery.erp.model.largestUnit
import com.bakery.erp.model.purchaseTotal
import com.bakery.erp.model.taxBreakdownFromGross
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.ui.AdaptiveContainer
import com.bakery.erp.ui.ImagePickerButton
import com.bakery.erp.ui.LoadingOverlay
import com.bakery.erp.ui.theme.BrandColors
import kotlin.time.Clock

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchaseFormScreen(
    onNavigateBack: () -> Unit,
    onNavigateToSuppliers: () -> Unit,
    authViewModel: AuthViewModel,
    viewModel: PurchaseViewModel,
    /** null = alta. Con id, la pantalla edita esa compra. */
    purchaseId: String? = null
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val companyId = authState.user?.orgId ?: ""
    val currentUser = authState.user
    val snackbarHostState = remember { SnackbarHostState() }
    var invoiceNumber by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var purchaseStatus by rememberSaveable { mutableStateOf(PurchaseStatus.RECEIVED_PENDING_PAYMENT) }
    var ingredientSearch by rememberSaveable { mutableStateOf("") }
    // Borrador del proveedor ocasional. Vive local hasta que se confirma con "Usar": si
    // fuera al ViewModel en cada tecla, cada letra reiniciaría el catálogo del formulario.
    var occasionalDraft by rememberSaveable { mutableStateOf("") }
    var showReview by rememberSaveable { mutableStateOf(false) }
    // La fecha ya no es `now` fijo: las facturas se capturan en tandas, y con la fecha de hoy
    // el vencimiento de una factura de la semana pasada nacía corrido.
    var purchaseDate by rememberSaveable { mutableStateOf(Clock.System.now().toEpochMilliseconds()) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var creditDaysStr by rememberSaveable { mutableStateOf("") }
    var creditTouched by rememberSaveable { mutableStateOf(false) }
    var applyTax by rememberSaveable { mutableStateOf(true) }
    var declaredTotalStr by rememberSaveable { mutableStateOf("") }
    var showOnlySelected by rememberSaveable { mutableStateOf(false) }
    var showAddIngredient by rememberSaveable { mutableStateOf(false) }
    // ByteArray no es Parcelable: `remember`, no `rememberSaveable` (regla del proyecto).
    var invoicePhotoBytes by remember { mutableStateOf<ByteArray?>(null) }

    // Los días de crédito los propone el proveedor y el usuario puede pisarlos para esta
    // factura; `creditTouched` evita que cambiar de proveedor borre lo que ya escribió.
    LaunchedEffect(state.selectedSupplier?.id) {
        if (!creditTouched) creditDaysStr = (state.selectedSupplier?.paymentTermsDays ?: 0).toString()
    }
    val creditDays = creditDaysStr.toIntOrNull()?.coerceIn(0, 365) ?: 0
    val dueDate = if (creditDays > 0) purchaseDate + creditDays * 86_400_000L else 0L

    val grandTotal = state.draftLines.purchaseTotal()
    val taxRate = if (applyTax) DEFAULT_TAX_RATE_PERCENT else 0.0
    val breakdown = taxBreakdownFromGross(grandTotal, taxRate)
    val declaredTotal = declaredTotalStr.replace(",", ".").toDoubleOrNull() ?: 0.0

    // Insumos perecederos ya capturados: son los únicos a los que se les pide vencimiento.
    val perishableLines = remember(state.draftLines, state.supplierIngredients) {
        val perishableIds = state.supplierIngredients.filter { it.isPerishable }.mapTo(HashSet()) { it.id }
        state.draftLines.filter { it.ingredientId in perishableIds && it.quantityReceived > 0 }
            .sortedBy { it.ingredientName.lowercase() }
    }

    val isEditing = purchaseId != null
    // Vacío en alta (estable), y pasa a ser el id de la compra en cuanto la edición termina
    // de cargar — ese cambio es el que hace que las filas se re-inicialicen con sus valores.
    val formKey = state.editingPurchase?.id.orEmpty()

    LaunchedEffect(companyId, currentUser, purchaseId) {
        if (companyId.isEmpty() || currentUser == null) return@LaunchedEffect
        // En edición NO se llama loadFormData: pisaría la sucursal y el proveedor de la
        // compra con los valores por defecto del alta.
        if (purchaseId == null) viewModel.loadFormData(companyId, currentUser)
        else viewModel.loadPurchaseForEdit(companyId, purchaseId, currentUser)
    }

    // Siembra los campos del encabezado una sola vez, cuando la compra termina de cargar.
    LaunchedEffect(state.editingPurchase?.id) {
        val editing = state.editingPurchase ?: return@LaunchedEffect
        invoiceNumber = editing.invoiceNumber.orEmpty()
        notes = editing.notes.orEmpty()
        purchaseStatus = editing.status
        purchaseDate = editing.date
        creditDaysStr = editing.paymentTermsDays.toString()
        creditTouched = true
        applyTax = editing.taxRatePercent > 0.0
        declaredTotalStr = if (editing.declaredTotal > 0.0) editing.declaredTotal.fmt(2) else ""
    }
    LaunchedEffect(state.isSaved) {
        if (state.isSaved) { viewModel.cancelEdit(); viewModel.resetForm(); onNavigateBack() }
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }
    // En edición el proveedor viene fijado por la compra: limpiar la búsqueda al "cambiarlo"
    // no aplica, y reaccionar al objeto entero borraría el filtro en cada recomposición.
    LaunchedEffect(state.selectedSupplier?.id) { if (!isEditing) ingredientSearch = "" }

    // Escanear = buscar. El código encuentra el renglón y lo deja solo en pantalla; la
    // cantidad se sigue tecleando, porque la factura dice cuántos vinieron, no el empaque.
    var scanMessage by remember { mutableStateOf<String?>(null) }
    val scanBarcode = rememberBarcodeScanner { code ->
        when (val match = viewModel.findByBarcode(code)) {
            is BarcodeMatch.OfIngredient -> {
                ingredientSearch = match.ingredient.name
                showOnlySelected = false
            }
            is BarcodeMatch.OfProduct -> {
                ingredientSearch = ""
                scanMessage = "${match.product.name} está en la sección de reventa, más abajo."
            }
            null -> scanMessage = "Ningún insumo ni producto tiene el código $code."
        }
    }
    LaunchedEffect(scanMessage) {
        scanMessage?.let { snackbarHostState.showSnackbar(it); scanMessage = null }
    }

    val filteredIngredients = remember(
        state.supplierIngredients, ingredientSearch, showOnlySelected, state.draftLines
    ) {
        val chosen = state.draftLines.mapTo(HashSet()) { it.ingredientId }
        state.supplierIngredients
            .filter { ingredientSearch.isBlank() || it.name.contains(ingredientSearch.trim(), ignoreCase = true) }
            .filter { !showOnlySelected || it.id in chosen }
    }

    fun doSave() {
        if (currentUser == null) return
        if (isEditing) {
            viewModel.updatePurchase(
                companyId = companyId,
                currentUser = currentUser,
                invoiceNumber = invoiceNumber.trim().ifEmpty { null },
                notes = notes.trim().ifEmpty { null },
                date = purchaseDate,
                status = purchaseStatus,
                paymentTermsDays = creditDays,
                dueDate = dueDate,
                taxRatePercent = taxRate,
                declaredTotal = declaredTotal,
                invoicePhotoBytes = invoicePhotoBytes
            )
            return
        }
        run {
            viewModel.savePurchase(
                companyId = companyId,
                currentUser = currentUser,
                invoiceNumber = invoiceNumber.trim().ifEmpty { null },
                notes = notes.trim().ifEmpty { null },
                date = purchaseDate,
                status = purchaseStatus,
                paymentTermsDaysOverride = creditDays,
                dueDateOverride = dueDate,
                taxRatePercent = taxRate,
                declaredTotal = declaredTotal,
                invoicePhotoBytes = invoicePhotoBytes
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            showReview -> "Revisar compra"
                            isEditing -> "Editar compra"
                            else -> "Nueva Compra"
                        },
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { if (showReview) showReview = false else onNavigateBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        AdaptiveContainer(modifier = Modifier.padding(padding)) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val isWide = maxWidth >= 600.dp

                if (showReview && state.selectedBranch != null && state.selectedSupplier != null) {
                    PurchaseReviewContent(
                        branch = state.selectedBranch!!,
                        supplier = state.selectedSupplier!!,
                        invoiceNumber = invoiceNumber,
                        notes = notes,
                        purchaseStatus = purchaseStatus,
                        draftLines = state.draftLines,
                        isLoading = state.isLoading,
                        onBack = { showReview = false },
                        onConfirm = ::doSave
                    )
                } else {
                  Column(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // ── PASO 1: Sucursal ──────────────────────────────────────────
                        // Con una sola sucursal accesible el ViewModel ya la eligió: mostrarla
                        // como "paso" gastaba el espacio más caro de la pantalla en algo que
                        // nadie puede cambiar.
                        val singleBranch = state.accessibleBranches.size == 1 && state.selectedBranch != null
                        if (!singleBranch) {
                            StepHeader("1. Sucursal destino")
                        }
                        when {
                            state.isLoadingBranches -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            state.accessibleBranches.isEmpty() -> InfoCard(
                                "No tenés acceso a ninguna sucursal productora o de almacén. " +
                                "Contactá al administrador para que te asigne una sucursal."
                            )
                            state.selectedBranch == null -> BranchSelector(
                                branches = state.accessibleBranches,
                                onSelect = { viewModel.selectBranch(it) }
                            )
                            singleBranch -> Text(
                                "Ingresa a ${state.selectedBranch!!.name}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            else -> SelectedItem(
                                label = state.selectedBranch!!.name,
                                onClear = { viewModel.selectBranch(null) }
                            )
                        }

                        if (isEditing) {
                            EditPurchaseNotice(
                                status = state.editingPurchase?.status ?: purchaseStatus,
                                isLoading = state.isLoadingEdit
                            )
                        }

                        // ── PASO 2: Proveedor ─────────────────────────────────────────
                        if (state.selectedBranch != null) {
                            if (!singleBranch) HorizontalDivider()
                            StepHeader(if (singleBranch) "1. Proveedor" else "2. Proveedor")
                            val occasional = state.occasionalSupplierName
                            val supplierChosen =
                                state.selectedSupplier != null || occasional.isNotBlank()
                            when {
                                !supplierChosen -> {
                                    SupplierSelector(
                                        suppliers = state.suppliers,
                                        onSelect = { viewModel.selectSupplier(companyId, it) },
                                        onNavigateToSuppliers = onNavigateToSuppliers
                                    )
                                    // Proveedor ocasional: la tienda de la esquina, el puesto
                                    // del mercado. No se da de alta como proveedor a propósito
                                    // —al que le comprás una vez no es un proveedor— y sin esta
                                    // salida había que inventar una ficha para poder capturar.
                                    Spacer(Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = occasionalDraft,
                                        onValueChange = { occasionalDraft = it },
                                        label = { Text("O escribí dónde compraste") },
                                        placeholder = { Text("Ej: la tienda de la esquina") },
                                        supportingText = {
                                            Text("No se guarda como proveedor. Para una compra que no se repite.")
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        trailingIcon = if (occasionalDraft.isNotBlank()) ({
                                            TextButton(onClick = {
                                                viewModel.setOccasionalSupplier(companyId, occasionalDraft)
                                            }) { Text("Usar") }
                                        }) else null
                                    )
                                }
                                else -> {
                                    SelectedItem(
                                        label = state.selectedSupplier?.company ?: occasional,
                                        onClear = {
                                            occasionalDraft = ""
                                            viewModel.setOccasionalSupplier(companyId, "")
                                            viewModel.selectSupplier(companyId, null)
                                        }
                                    )

                                    InvoiceHeaderFields(
                                        invoiceNumber = invoiceNumber,
                                        onInvoiceNumberChange = { invoiceNumber = it },
                                        date = purchaseDate,
                                        onPickDate = { showDatePicker = true },
                                        creditDaysStr = creditDaysStr,
                                        onCreditDaysChange = { creditTouched = true; creditDaysStr = it },
                                        dueDate = dueDate,
                                        supplierDefaultDays = state.selectedSupplier?.paymentTermsDays ?: 0
                                    )

                                    // ── PASO 3: Insumos ───────────────────────────────
                                    HorizontalDivider()
                                    StepHeader(if (singleBranch) "2. Insumos recibidos" else "3. Insumos recibidos")

                                    if (currentUser != null) {
                                        Text(
                                            text = "Registrando como: ${currentUser.displayName.ifEmpty { currentUser.email }}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }

                                    if (state.isLoadingIngredients) {
                                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                    } else if (
                                        // Con servicios encendidos SIEMPRE hay algo que capturar:
                                        // una factura de mantenimiento no necesita catálogo. Sin
                                        // esta condición, el aviso de "proveedor sin insumos"
                                        // tapaba la única sección que servía para esa factura.
                                        // Un proveedor ocasional tampoco tiene catálogo, y ahí
                                        // el camino es la línea suelta, no un aviso.
                                        !state.usesServices &&
                                        state.selectedSupplier != null &&
                                        state.supplierIngredients.isEmpty() &&
                                        state.supplierSupplies.isEmpty() &&
                                        state.supplierResaleProducts.isEmpty()
                                    ) {
                                        InfoCard(
                                            "Este proveedor no tiene insumos ni productos de reventa asociados. " +
                                            "Agregá una presentación de compra en el detalle de un insumo, o " +
                                            "asígnale este proveedor a un producto de reventa."
                                        )
                                    } else {
                                      if (state.selectedSupplier == null) {
                                          // Proveedor ocasional: no hay catálogo que recorrer,
                                          // se agregan líneas sueltas una por una.
                                          Text(
                                              "Compra sin proveedor registrado: agregá cada insumo " +
                                                  "con su cantidad y lo que costó.",
                                              style = MaterialTheme.typography.bodySmall,
                                              color = MaterialTheme.colorScheme.onSurfaceVariant
                                          )
                                          LineToolbar(
                                              lineCount = state.draftLines.size,
                                              showOnlySelected = showOnlySelected,
                                              onToggleOnlySelected = { showOnlySelected = !showOnlySelected },
                                              onAddIngredient = { showAddIngredient = true },
                                              onScanBarcode = if (isBarcodeScanSupported()) scanBarcode else null
                                          )
                                          LooseLinesList(
                                              lines = state.draftLines.filter {
                                                  it.lineKind != PurchaseLineKind.SERVICE
                                              },
                                              onRemove = { viewModel.removeDraftLine(it) }
                                          )
                                      }

                                      if (state.supplierIngredients.isNotEmpty()) {
                                        if (state.supplierSupplies.isNotEmpty() && state.usesSupplies) {
                                            StepHeader("Materia prima")
                                        }
                                        // La pantalla muestra el catálogo del proveedor, no la
                                        // factura: sin decirlo, no se entiende que hay que ir
                                        // tocando "Agregar" en cada renglón que sí venga.
                                        Text(
                                            "Tocá \"Agregar\" en cada producto que traiga esta " +
                                                "factura y anotá la cantidad. Solo lo que agregues " +
                                                "cuenta en el total.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        LineToolbar(
                                            lineCount = state.draftLines.size,
                                            showOnlySelected = showOnlySelected,
                                            onToggleOnlySelected = { showOnlySelected = !showOnlySelected },
                                            onAddIngredient = { showAddIngredient = true },
                                            onScanBarcode = if (isBarcodeScanSupported()) scanBarcode else null
                                        )
                                        if (state.supplierIngredients.size > 4) {
                                            OutlinedTextField(
                                                value = ingredientSearch,
                                                onValueChange = { ingredientSearch = it },
                                                label = { Text("Buscar insumo") },
                                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                                trailingIcon = if (ingredientSearch.isNotEmpty()) ({
                                                    IconButton(onClick = { ingredientSearch = "" }) {
                                                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    }
                                                }) else null,
                                                modifier = Modifier.fillMaxWidth(),
                                                singleLine = true
                                            )
                                        }

                                        if (filteredIngredients.isEmpty()) {
                                            Text(
                                                "Sin resultados para \"$ingredientSearch\"",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                        } else if (isWide) {
                                            IngredientTable(
                                                ingredients = filteredIngredients,
                                                supplierId = state.selectedSupplier!!.id,
                                                draftLines = state.draftLines,
                                                formKey = formKey,
                                                onLineChanged = { viewModel.updateDraftLine(it) }
                                            )
                                        } else {
                                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                filteredIngredients.forEach { ingredient ->
                                                    val existingLine = state.draftLines.find { it.ingredientId == ingredient.id }
                                                    IngredientPurchaseRow(
                                                        ingredient = ingredient,
                                                        supplierId = state.selectedSupplier!!.id,
                                                        existingLine = existingLine,
                                                        formKey = formKey,
                                                        onLineChanged = { viewModel.updateDraftLine(it) }
                                                    )
                                                }
                                            }
                                        }
                                      } // fin sección insumos

                                      // ── Suministros de operación ──────────
                                      // Reusan la MISMA fila que la materia prima: un
                                      // suministro se compra por presentación, se guarda en
                                      // la sucursal y lleva Kardex igual. Lo único distinto
                                      // —que además se gasta— pasa al guardar, no acá.
                                      if (state.usesSupplies && state.supplierSupplies.isNotEmpty()) {
                                          Spacer(Modifier.height(8.dp))
                                          StepHeader("Suministros de operación")
                                          Text(
                                              "Entran al inventario de la sucursal y se registran " +
                                                  "como gasto del mes.",
                                              style = MaterialTheme.typography.bodySmall,
                                              color = MaterialTheme.colorScheme.onSurfaceVariant
                                          )
                                          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                              state.supplierSupplies.forEach { supply ->
                                                  IngredientPurchaseRow(
                                                      ingredient = supply,
                                                      supplierId = state.selectedSupplier!!.id,
                                                      existingLine = state.draftLines.find {
                                                          it.ingredientId == supply.id
                                                      },
                                                      formKey = formKey,
                                                      onLineChanged = { viewModel.updateDraftLine(it) }
                                                  )
                                              }
                                          }
                                      }

                                      if (state.usesResale && state.supplierResaleProducts.isNotEmpty()) {
                                          Spacer(Modifier.height(8.dp))
                                          StepHeader("Productos de reventa")
                                          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                              state.supplierResaleProducts.forEach { product ->
                                                  val existing = state.draftLines.find {
                                                      it.lineKind == PurchaseLineKind.RESALE_PRODUCT &&
                                                      it.productId == product.id
                                                  }
                                                  ResaleProductRow(
                                                      product = product,
                                                      existingLine = existing,
                                                      formKey = formKey,
                                                      onLineChanged = { viewModel.updateDraftLine(it) }
                                                  )
                                              }
                                          }
                                      }

                                        // ── Servicios y gastos ────────────
                                        // Único tipo SIN catálogo: la ferretería vende algo
                                        // distinto cada vez, así que la línea es texto libre.
                                        // Dar de alta un ítem por cada reparación llenaría el
                                        // catálogo de cosas que no se vuelven a comprar.
                                        if (state.usesServices) {
                                            ServiceLinesSection(
                                                lines = state.draftLines.filter {
                                                    it.lineKind == PurchaseLineKind.SERVICE
                                                },
                                                assets = state.assets,
                                                onUpsert = { viewModel.upsertServiceLine(it) },
                                                onRemove = { viewModel.removeServiceLine(it) }
                                            )
                                        }

                                        if (perishableLines.isNotEmpty()) {
                                            ExpirySection(
                                                lines = perishableLines,
                                                onLineChanged = { viewModel.updateDraftLine(it) }
                                            )
                                        }

                                        Spacer(Modifier.height(4.dp))

                                        Text(
                                            text = "Estado de la compra",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            listOf(
                                                PurchaseStatus.RECEIVED_PENDING_PAYMENT to "Recibido",
                                                PurchaseStatus.PAID to "Pagado",
                                                PurchaseStatus.PENDING_RECEIPT to "Por recibir"
                                            ).forEach { (status, label) ->
                                                if (purchaseStatus == status) {
                                                    Button(
                                                        onClick = { purchaseStatus = status },
                                                        modifier = Modifier.weight(1f),
                                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                                                    ) {
                                                        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                                    }
                                                } else {
                                                    OutlinedButton(
                                                        onClick = { purchaseStatus = status },
                                                        modifier = Modifier.weight(1f),
                                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                                                    ) {
                                                        Text(label, style = MaterialTheme.typography.labelSmall)
                                                    }
                                                }
                                            }
                                        }

                                        // El estado ya NO es solo una etiqueta: "Por recibir"
                                        // deja el inventario intacto hasta que alguien confirme
                                        // la llegada desde el detalle de la compra.
                                        Text(
                                            text = when (purchaseStatus) {
                                                PurchaseStatus.PENDING_RECEIPT ->
                                                    "El stock NO entrará todavía. Se ingresa cuando marques la compra como recibida."
                                                PurchaseStatus.PAID ->
                                                    "Entra al stock y queda saldada, sin saldo por pagar."
                                                else ->
                                                    "Entra al stock y queda pendiente de pago."
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        OutlinedTextField(
                                            value = notes,
                                            onValueChange = { notes = it },
                                            label = { Text("Observaciones (opcional)") },
                                            modifier = Modifier.fillMaxWidth(),
                                            minLines = 2,
                                            maxLines = 4
                                        )

                                        // El respaldo del papel: cuando el total no cuadra o
                                        // el proveedor reclama, lo que resuelve la discusión
                                        // es la factura física, no el documento de Firestore.
                                        Text(
                                            "Foto de la factura (opcional)",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        ImagePickerButton(
                                            currentImageUrl = state.editingPurchase?.invoicePhotoUrl
                                                ?.ifEmpty { null },
                                            selectedBytes = invoicePhotoBytes,
                                            onImageSelected = { invoicePhotoBytes = it },
                                            allowCamera = true
                                        )

                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(24.dp))
                    }

                    // El total va fijo abajo, no dentro del scroll: es el número que se coteja
                    // contra el papel mientras se teclea, y antes solo existía en el paso de
                    // revisión — que en tablet ni siquiera se muestra.
                    if (state.selectedSupplier != null) {
                        PurchaseTotalsBar(
                            lineCount = state.draftLines.size,
                            breakdown = breakdown,
                            applyTax = applyTax,
                            onToggleTax = { applyTax = it },
                            declaredTotalStr = declaredTotalStr,
                            onDeclaredTotalChange = { v ->
                                declaredTotalStr = v.filter { it.isDigit() || it == '.' || it == ',' }
                            },
                            isLoading = state.isLoading,
                            confirmLabel = when {
                                isEditing -> "Guardar cambios"
                                isWide -> "Confirmar compra"
                                else -> "Revisar"
                            },
                            onConfirm = { if (isWide || isEditing) doSave() else showReview = true }
                        )
                    }
                  }
                }
            }
        }
    }
    LoadingOverlay(visible = state.isLoading)
    }

    if (showDatePicker) {
        val dpState = rememberDatePickerState(initialSelectedDateMillis = purchaseDate)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    purchaseDate = dpState.selectedDateMillis ?: purchaseDate
                    showDatePicker = false
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancelar") } }
        ) { DatePicker(state = dpState) }
    }

    // Sin proveedor del catálogo no hay presentación de compra que dar de alta: la línea se
    // captura suelta (insumo + cantidad + lo que costó) y no deja nada permanente detrás.
    // Es el mismo criterio de la línea de servicio: no ensuciar el catálogo por una compra
    // que no se repite.
    if (showAddIngredient && state.selectedSupplier == null) {
        LooseIngredientLineDialog(
            candidates = remember(state.allIngredients) {
                state.allIngredients.sortedBy { it.name.lowercase() }
            },
            onDismiss = { showAddIngredient = false },
            onConfirm = { line ->
                showAddIngredient = false
                viewModel.updateDraftLine(line)
            }
        )
    }

    if (showAddIngredient && state.selectedSupplier != null) {
        AddSupplierIngredientDialog(
            supplierName = state.selectedSupplier!!.company,
            candidates = remember(state.allIngredients, state.supplierIngredients) {
                val already = state.supplierIngredients.mapTo(HashSet()) { it.id }
                state.allIngredients.filter { it.id !in already }.sortedBy { it.name.lowercase() }
            },
            isSaving = state.isProcessing,
            onDismiss = { showAddIngredient = false },
            onConfirm = { ingredient, description, unit, unitsPerPackage, packageCost ->
                showAddIngredient = false
                viewModel.addSupplierFormat(
                    companyId, ingredient, description, unit, unitsPerPackage, packageCost
                )
            }
        )
    }
}

// ── Tabla de insumos (pantallas anchas) ──────────────────────────────────────

@Composable
private fun IngredientTable(
    ingredients: List<Ingredient>,
    supplierId: String,
    draftLines: List<PurchaseLine>,
    /**
     * Cambia cuando el formulario se rellena con una compra existente. Va en las claves de
     * `rememberSaveable` de cada fila: sin él, los campos se inicializan vacíos en la primera
     * composición y NO se vuelven a inicializar cuando las líneas terminan de cargar, así que
     * editar una compra mostraría todas las cantidades en blanco.
     */
    formKey: String,
    onLineChanged: (PurchaseLine) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Insumo",
                        modifier = Modifier.weight(2.5f),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Precio / pres.",
                        modifier = Modifier.weight(1.5f),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.End
                    )
                    Text(
                        "Cantidad",
                        modifier = Modifier.weight(1.5f),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        "Subtotal",
                        modifier = Modifier.weight(1.2f),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.End
                    )
                }
            }
            HorizontalDivider()
            ingredients.forEachIndexed { index, ingredient ->
                val existingLine = draftLines.find { it.ingredientId == ingredient.id }
                IngredientTableRow(
                    ingredient = ingredient,
                    supplierId = supplierId,
                    existingLine = existingLine,
                    formKey = formKey,
                    onLineChanged = onLineChanged
                )
                if (index < ingredients.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun IngredientTableRow(
    ingredient: Ingredient,
    supplierId: String,
    existingLine: PurchaseLine?,
    formKey: String,
    onLineChanged: (PurchaseLine) -> Unit
) {
    val defaultFormat = ingredient.purchaseFormats.firstOrNull { it.isDefault && it.supplierId == supplierId }
        ?: ingredient.purchaseFormats.firstOrNull { it.supplierId == supplierId }
        ?: ingredient.purchaseFormats.firstOrNull()

    val toBaseUnitFactor = if (defaultFormat != null) {
        convertUnits(defaultFormat.unitsPerPackage, defaultFormat.unit, ingredient.effectiveBaseUnit)
    } else {
        convertUnits(1.0, largestUnit(ingredient.allowedUnits), ingredient.effectiveBaseUnit)
    }

    val originalPackageCost = defaultFormat?.packageCost ?: 0.0
    val unitLabel = defaultFormat?.description?.split(" ")?.firstOrNull()?.lowercase()
        ?: ingredient.effectiveBaseUnit

    var editingPrice by remember(ingredient.id, formKey) { mutableStateOf(false) }
    var packageCostStr by rememberSaveable(ingredient.id, formKey) {
        // Se prefiere el precio de la línea: una compra vieja pudo registrarse a un precio
        // distinto del que hoy tiene el formato, y reabrirla mostrando el precio del catálogo
        // se lo cambiaría en silencio al guardar.
        val seed = existingLine?.packageCost?.takeIf { it > 0 } ?: originalPackageCost
        mutableStateOf(if (seed > 0) seed.fmt(2) else "")
    }
    var showQtyDialog by remember { mutableStateOf(false) }

    val currentPackageCost = packageCostStr.toDoubleOrNull() ?: originalPackageCost
    val currentQty = existingLine?.packagesReceived?.takeIf { it > 0 }
        ?: existingLine?.let { if (toBaseUnitFactor > 0) it.quantityReceived / toBaseUnitFactor else it.quantityReceived }
        ?: 0.0
    val lineTotal = currentQty * currentPackageCost
    val isSelected = existingLine != null && currentQty > 0

    fun buildLine(packages: Double): PurchaseLine {
        val cost = packageCostStr.toDoubleOrNull() ?: originalPackageCost
        return PurchaseLine(
            ingredientId = ingredient.id,
            ingredientName = ingredient.name,
            ingredientUnit = ingredient.effectiveBaseUnit,
            quantityReceived = packages * toBaseUnitFactor,
            packagesReceived = if (defaultFormat != null) packages else 0.0,
            packageDescription = defaultFormat?.description ?: largestUnit(ingredient.allowedUnits),
            unitCost = if (defaultFormat != null && defaultFormat.unitsPerPackage > 0)
                cost / defaultFormat.unitsPerPackage else 0.0,
            packageCost = cost,
            formatId = defaultFormat?.id ?: ""
        )
    }

    Surface(color = if (isSelected) Color(0xFFFFF3DC) else MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Insumo
            Column(modifier = Modifier.weight(2.5f)) {
                Text(ingredient.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                if (defaultFormat != null) {
                    Text(
                        defaultFormat.description,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Precio
            if (editingPrice) {
                Row(
                    modifier = Modifier.weight(1.5f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedTextField(
                        value = packageCostStr,
                        onValueChange = { packageCostStr = it },
                        prefix = { Text("Q", style = MaterialTheme.typography.labelSmall) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                    IconButton(
                        onClick = {
                            editingPrice = false
                            if (existingLine != null) onLineChanged(buildLine(currentQty))
                        },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Listo",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.weight(1.5f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = if (currentPackageCost > 0) "Q ${currentPackageCost.fmt(2)}" else "—",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.End
                    )
                    IconButton(
                        onClick = { editingPrice = true },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Editar precio",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            // Cantidad
            Box(modifier = Modifier.weight(1.5f), contentAlignment = Alignment.Center) {
                if (isSelected) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            "${formatPurchaseQty(currentQty)} $unitLabel",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        IconButton(
                            onClick = { showQtyDialog = true },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Editar cantidad",
                                modifier = Modifier.size(13.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                } else {
                    // Lleno y en el azul de la marca: en outline se leía como un borde vacío
                    // más de la tabla y no se entendía que ahí es donde se agrega el producto.
                    Button(
                        onClick = { showQtyDialog = true },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BrandColors.Primary,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Agregar", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Subtotal
            Text(
                text = if (lineTotal > 0) "Q ${lineTotal.fmt(2)}" else "—",
                modifier = Modifier.weight(1.2f),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (lineTotal > 0) FontWeight.SemiBold else FontWeight.Normal,
                color = if (lineTotal > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.End
            )
        }
    }

    if (showQtyDialog) {
        QuantityInputDialog(
            ingredientName = ingredient.name,
            unitLabel = unitLabel,
            initialQty = if (currentQty > 0) formatPurchaseQty(currentQty) else "",
            onDismiss = { showQtyDialog = false },
            onConfirm = { qtyStr ->
                showQtyDialog = false
                onLineChanged(buildLine(qtyStr.toDoubleOrNull() ?: 0.0))
            }
        )
    }
}

@Composable
private fun QuantityInputDialog(
    ingredientName: String,
    unitLabel: String,
    initialQty: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    // Se abre con todo seleccionado para poder teclear encima sin borrar primero.
    var qty by remember {
        mutableStateOf(
            TextFieldValue(initialQty, selection = TextRange(0, initialQty.length))
        )
    }
    val focusRequester = remember { FocusRequester() }
    // El diálogo se abre para escribir un número y nada más: pedirle al usuario un toque
    // extra sobre el campo para que aparezca el teclado es un toque por cada producto de la
    // factura. El `runCatching` cubre la carrera de pedir foco antes de que el diálogo
    // termine de componerse.
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    fun confirmIfValid() {
        if (qty.text.toDoubleOrNull()?.let { it > 0 } == true) onConfirm(qty.text)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(ingredientName, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
        },
        text = {
            OutlinedTextField(
                value = qty,
                onValueChange = { qty = it },
                label = { Text("Cantidad") },
                suffix = { Text(unitLabel, style = MaterialTheme.typography.labelSmall) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { confirmIfValid() }),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
            )
        },
        confirmButton = {
            Button(
                onClick = { confirmIfValid() },
                enabled = qty.text.toDoubleOrNull()?.let { it > 0 } == true
            ) { Text("Confirmar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

// ── Pantalla de revisión (móvil) ─────────────────────────────────────────────

@Composable
private fun PurchaseReviewContent(
    branch: Branch,
    supplier: Supplier,
    invoiceNumber: String,
    notes: String,
    purchaseStatus: String,
    draftLines: List<PurchaseLine>,
    isLoading: Boolean,
    onBack: () -> Unit,
    onConfirm: () -> Unit
) {
    // `purchaseTotal()` es la misma fórmula del encabezado y del script de backfill. La
    // versión anterior filtraba por `packageCost > 0`, así que las líneas de reventa
    // —que se costean por unidad— sumaban cero en la revisión.
    val grandTotal = draftLines.purchaseTotal()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Encabezado del pedido
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ReviewDetailRow("Sucursal", branch.name)
                ReviewDetailRow("Proveedor", supplier.company)
                if (invoiceNumber.isNotBlank()) ReviewDetailRow("Nº Factura", invoiceNumber)
                ReviewDetailRow("Estado", PurchaseStatus.label(purchaseStatus))
            }
        }

        // Lista de insumos
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "ÍTEMS A RECIBIR",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                        Text(
                            "${draftLines.size}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text("Insumo", modifier = Modifier.weight(2f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    Text("Cant.", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, textAlign = TextAlign.End)
                    Text("Total", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, textAlign = TextAlign.End)
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                draftLines.forEachIndexed { index, line ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(2f)) {
                            Text(line.ingredientName.ifEmpty { line.productName }, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                            if (line.packageDescription.isNotEmpty()) {
                                Text(line.packageDescription, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        val qtyText = if (line.packagesReceived > 0 && line.packageDescription.isNotEmpty()) {
                            val u = line.packageDescription.split(" ").firstOrNull()?.lowercase() ?: ""
                            "${formatPurchaseQty(line.packagesReceived)} $u"
                        } else {
                            "${formatPurchaseQty(line.quantityReceived)} ${line.ingredientUnit}"
                        }
                        Text(
                            qtyText,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.End
                        )
                        val lineTotal = if (line.packageCost > 0) line.packageCost * line.packagesReceived.coerceAtLeast(1.0) else 0.0
                        Text(
                            if (lineTotal > 0) "Q ${lineTotal.fmt(2)}" else "—",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (lineTotal > 0) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (lineTotal > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            textAlign = TextAlign.End
                        )
                    }
                    if (index < draftLines.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }

        // Total
        if (grandTotal > 0) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("TOTAL", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "Q ${grandTotal.fmt(2)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Observaciones
        if (notes.isNotBlank()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("OBSERVACIONES", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(notes, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                Text("Editar")
            }
            Button(
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Guardar compra", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun ReviewDetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.width(90.dp)
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

// ── Sub-composables ───────────────────────────────────────────────────────────

@Composable
private fun StepHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun InfoCard(text: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(18.dp).padding(top = 1.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun SelectedItem(label: String, onClear: (() -> Unit)?) {
    FilterChip(
        selected = true,
        onClick = {},
        label = { Text(label) },
        trailingIcon = if (onClear != null) ({
            IconButton(onClick = onClear, modifier = Modifier.size(18.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Limpiar", modifier = Modifier.size(16.dp))
            }
        }) else null
    )
}

@Composable
private fun BranchSelector(branches: List<Branch>, onSelect: (Branch) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        branches.forEach { branch ->
            Card(
                onClick = { onSelect(branch) },
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                ListItem(
                    headlineContent = { Text(branch.name, fontWeight = FontWeight.Medium) },
                    supportingContent = if (branch.location.isNotEmpty()) ({ Text(branch.location) }) else null,
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
                )
            }
        }
    }
}

@Composable
private fun IngredientPurchaseRow(
    ingredient: Ingredient,
    supplierId: String,
    existingLine: PurchaseLine?,
    formKey: String,
    onLineChanged: (PurchaseLine) -> Unit
) {
    val defaultFormat = ingredient.purchaseFormats.firstOrNull { it.isDefault && it.supplierId == supplierId }
        ?: ingredient.purchaseFormats.firstOrNull { it.supplierId == supplierId }
        ?: ingredient.purchaseFormats.firstOrNull()

    val toBaseUnitFactor = if (defaultFormat != null) {
        convertUnits(defaultFormat.unitsPerPackage, defaultFormat.unit, ingredient.effectiveBaseUnit)
    } else {
        convertUnits(1.0, largestUnit(ingredient.allowedUnits), ingredient.effectiveBaseUnit)
    }

    val originalPackageCost = defaultFormat?.packageCost ?: 0.0

    var qty by rememberSaveable(ingredient.id, formKey) {
        mutableStateOf(
            existingLine?.let {
                val displayed = if (it.packagesReceived > 0) it.packagesReceived
                               else if (toBaseUnitFactor > 0) it.quantityReceived / toBaseUnitFactor
                               else it.quantityReceived
                if (displayed > 0) displayed.fmt(2).trimEnd('0').trimEnd('.') else ""
            } ?: ""
        )
    }

    var editingPrice by remember(ingredient.id, formKey) { mutableStateOf(false) }
    var packageCostStr by rememberSaveable(ingredient.id, formKey) {
        // Se prefiere el precio de la línea: una compra vieja pudo registrarse a un precio
        // distinto del que hoy tiene el formato, y reabrirla mostrando el precio del catálogo
        // se lo cambiaría en silencio al guardar.
        val seed = existingLine?.packageCost?.takeIf { it > 0 } ?: originalPackageCost
        mutableStateOf(if (seed > 0) seed.fmt(2) else "")
    }

    val currentPackageCost = packageCostStr.toDoubleOrNull() ?: originalPackageCost
    val currentQty = qty.toDoubleOrNull() ?: 0.0
    val lineTotal = currentQty * currentPackageCost
    val priceChanged = currentPackageCost > 0 && currentPackageCost != originalPackageCost

    fun buildLine(): PurchaseLine {
        val packages = qty.toDoubleOrNull() ?: 0.0
        return PurchaseLine(
            ingredientId = ingredient.id,
            ingredientName = ingredient.name,
            ingredientUnit = ingredient.effectiveBaseUnit,
            quantityReceived = packages * toBaseUnitFactor,
            packagesReceived = if (defaultFormat != null) packages else 0.0,
            packageDescription = defaultFormat?.description ?: largestUnit(ingredient.allowedUnits),
            unitCost = if (defaultFormat != null && defaultFormat.unitsPerPackage > 0)
                currentPackageCost / defaultFormat.unitsPerPackage else 0.0,
            packageCost = currentPackageCost,
            formatId = defaultFormat?.id ?: ""
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (existingLine != null) Color(0xFFFFF3DC) else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(ingredient.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    if (defaultFormat != null) {
                        Text(
                            text = defaultFormat.description,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                OutlinedTextField(
                    value = qty,
                    onValueChange = { raw -> qty = raw; onLineChanged(buildLine()) },
                    label = { Text("Cantidad") },
                    suffix = { Text(
                        defaultFormat?.description?.split(" ")?.firstOrNull()?.lowercase()
                            ?: ingredient.effectiveBaseUnit,
                        style = MaterialTheme.typography.labelSmall
                    ) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.width(115.dp)
                )
            }

            if (defaultFormat != null || originalPackageCost > 0 || currentQty > 0) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                if (editingPrice) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = packageCostStr,
                            onValueChange = { raw -> packageCostStr = raw; onLineChanged(buildLine()) },
                            label = { Text("Precio / presentación (Q)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { editingPrice = false }) {
                            Icon(Icons.Default.Check, contentDescription = "Listo", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (currentPackageCost > 0)
                                    "Q ${currentPackageCost.fmt(2)} / ${defaultFormat?.description ?: "paquete"}"
                                else "Sin precio",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (priceChanged) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                fontWeight = if (priceChanged) FontWeight.SemiBold else FontWeight.Normal
                            )
                            if (priceChanged) {
                                Text(
                                    text = "Antes: Q ${originalPackageCost.fmt(2)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                        if (currentQty > 0 && currentPackageCost > 0) {
                            Text(
                                text = "Q ${lineTotal.fmt(2)}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                        IconButton(
                            onClick = { editingPrice = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Editar precio",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Fila de producto de reventa ──────────────────────────────────────────────

@Composable
private fun ResaleProductRow(
    product: Product,
    existingLine: PurchaseLine?,
    formKey: String,
    onLineChanged: (PurchaseLine) -> Unit
) {
    var qty by rememberSaveable(product.id, formKey) {
        mutableStateOf(
            existingLine?.quantityReceived?.takeIf { it > 0 }
                ?.let { it.fmt(2).trimEnd('0').trimEnd('.') } ?: ""
        )
    }
    var costStr by rememberSaveable(product.id, formKey) {
        mutableStateOf(
            (existingLine?.unitCost ?: product.purchaseCost ?: 0.0).takeIf { it > 0 }?.fmt(2) ?: ""
        )
    }
    val currentQty = qty.toDoubleOrNull() ?: 0.0
    val currentCost = costStr.toDoubleOrNull() ?: 0.0
    val lineTotal = currentQty * currentCost
    val isSelected = currentQty > 0

    fun buildLine() = PurchaseLine(
        lineKind = PurchaseLineKind.RESALE_PRODUCT,
        productId = product.id,
        productName = product.name,
        quantityReceived = qty.toDoubleOrNull() ?: 0.0,
        packagesReceived = qty.toDoubleOrNull() ?: 0.0,
        packageDescription = product.unit,
        unitCost = costStr.toDoubleOrNull() ?: 0.0,
        packageCost = costStr.toDoubleOrNull() ?: 0.0
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFFFFF3DC) else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(product.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    if (product.unit.isNotEmpty()) {
                        Text(
                            text = product.unit,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                OutlinedTextField(
                    value = qty,
                    onValueChange = { raw -> qty = raw; onLineChanged(buildLine()) },
                    label = { Text("Cantidad") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.width(115.dp)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = costStr,
                    onValueChange = { raw -> costStr = raw; onLineChanged(buildLine()) },
                    label = { Text("Costo por unidad") },
                    prefix = { Text("Q ") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                if (lineTotal > 0) {
                    Text(
                        text = "Q ${lineTotal.fmt(2)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
