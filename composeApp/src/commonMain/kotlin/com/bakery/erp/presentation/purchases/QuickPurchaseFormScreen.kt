package com.bakery.erp.presentation.purchases

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.Branch
import com.bakery.erp.model.CashSession
import com.bakery.erp.model.ExpenseCategory
import com.bakery.erp.model.expectedCashNow
import com.bakery.erp.model.isQuick
import com.bakery.erp.model.OPERATIONAL_EXPENSE_CATEGORIES
import com.bakery.erp.model.Ingredient
import com.bakery.erp.model.IngredientUsage
import com.bakery.erp.model.Permission
import com.bakery.erp.model.PurchaseConcept
import com.bakery.erp.model.findByName
import com.bakery.erp.model.forSupplier
import com.bakery.erp.model.matchesSearch
import com.bakery.erp.model.normalizeForSearch
import com.bakery.erp.model.Supplier
import com.bakery.erp.model.hasPermission
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.ui.AdaptiveContainer
import com.bakery.erp.ui.ImagePickerButton
import com.bakery.erp.ui.LoadingOverlay
import com.bakery.erp.ui.theme.BrandColors
import com.bakery.erp.util.fmt
import kotlin.time.Clock

/**
 * Anotar que salió dinero: el punto de entrada único, desde Operaciones, desde Gastos y desde
 * la caja abierta del POS.
 *
 * **Primero pregunta QUÉ se está anotando** (`CaptureKindStep`) y recién después muestra
 * campos, porque la respuesta cambia cuáles hacen falta. Un taxi son tres datos; una entrega
 * de gaseosas es una factura con proveedor, catálogo, presentaciones e IVA. Hubo una versión
 * con un solo formulario para todo y no servía para ninguno de los dos extremos: el de la
 * factura no cabía —no había forma de recorrer el catálogo del proveedor— y el del taxi pedía
 * cantidad y unidad, que para un taxi no existen.
 *
 * - **Un gasto** → el editor de renglones de este archivo. La escoba, el taxi, el garrafón, el
 *   desinfectante. Varias cosas en un mismo desembolso, todas gasto.
 * - **Insumos** / **Reventa** → `PurchaseFormScreen` con la misma caja fijada. Es la pantalla
 *   de Compras tal cual, no una copia: proveedor, su catálogo, cantidades por presentación,
 *   IVA. Lo único distinto es de qué bolsa salió el dinero.
 *
 * Por eso este editor **ya no elige catálogo**. Tuvo chips de "Solo gasto / Insumo / Reventa" y
 * sobraban en cuanto la clase de cosa se decide arriba: volver a ofrecerla es preguntar dos
 * veces lo mismo. Lo único que queda del catálogo es **mostrar y conservar** lo que traiga una
 * compra rápida vieja al corregirla — si el editor lo ignorara, guardar la convertiría en gasto
 * puro y `applyStockDelta` le revertiría el inventario en silencio.
 *
 * **La caja no se elige: sale del contexto.** Abierta desde el Punto de Venta, todo se carga a
 * la sesión que se está operando y baja el efectivo esperado al cuadrar; abierta desde
 * Operaciones o desde Gastos, no sale de ninguna gaveta y queda como caja general. Hubo un
 * selector de caja y se quitó a propósito: quien anota suele ser el mismo que está cobrando,
 * con gente esperando, y un desplegable de cajas es una forma de marcar la equivocada sin
 * darse cuenta — el precio de ese error lo paga la cajera de la otra gaveta, que aparece con
 * un faltante que no es suyo. Un dato derivable del contexto no se pregunta.
 *
 * **Cada cosa se escribe UNA vez.** También hubo una versión con dos campos en pantalla y un
 * diálogo detrás para el resto, y el diálogo volvía a pedir qué compraste y cuánto costó:
 * aunque llegaban prellenados, leerlo dos veces con las mismas etiquetas se entiende como que
 * hay que escribirlo dos veces. El renglón crece en su lugar.
 *
 * Acá se pagó en el momento, así que la compra nace saldada y no genera cuenta por pagar. El
 * crédito, el vencimiento y los abonos son del módulo de Compras.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickPurchaseFormScreen(
    onNavigateBack: () -> Unit,
    authViewModel: AuthViewModel,
    viewModel: PurchaseViewModel,
    /** Compra a corregir. `null` = alta. */
    purchaseId: String? = null,
    /**
     * Caja a la que se carga el desembolso. La manda quien abre la pantalla —el POS pasa la
     * suya— y `null` significa caja general. Nunca se elige acá.
     */
    sessionId: String? = null,
    /**
     * Abre el formulario de Compras con esta misma caja fijada. Es a dónde van "Insumos" y
     * "Reventa": comprarle a un proveedor es la MISMA captura que una factura —proveedor, su
     * catálogo, cantidades por presentación, IVA— y lo único distinto es de qué bolsa salió
     * el dinero. Una versión propia acá sería una segunda implementación de esa pantalla, que
     * se quedaría atrás en cuanto Compras cambie.
     */
    onNavigateToSupplierPurchase: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val companyId = authState.user?.orgId ?: ""
    val currentUser = authState.user
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(companyId, currentUser, purchaseId) {
        if (companyId.isNotEmpty() && currentUser != null) {
            // Al corregir se usa el mismo cargador que la factura: trae la compra, sus
            // líneas y los catálogos, y deja `editingOriginalLines` puesto — que es la base
            // contra la que se corrige el inventario por diferencia al guardar.
            if (purchaseId != null) {
                viewModel.loadPurchaseForEdit(companyId, purchaseId, currentUser)
            } else {
                // El ViewModel es singleton: si antes se abrió una corrección y se salió sin
                // guardar, el rastro sigue puesto. Limpiarlo acá es lo que impide que una
                // compra nueva se confunda con aquella.
                viewModel.cancelEdit()
                viewModel.loadFormData(companyId, currentUser)
            }
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }
    LaunchedEffect(state.isSaved) {
        if (state.isSaved) { viewModel.resetForm(); onNavigateBack() }
    }

    // La sesión se lee una vez por id. Si no viene, el desembolso es de caja general y no
    // hay nada que cargar — el caso de Operaciones, que es el más común.
    LaunchedEffect(companyId, sessionId) {
        viewModel.loadPinnedSession(companyId, sessionId)
    }
    val session = state.pinnedSession?.takeIf { it.id == sessionId }

    // Qué se está anotando. Se pregunta ANTES de mostrar campos porque la respuesta cambia
    // qué hay que llenar: un taxi son tres datos, y una entrega de gaseosas es una factura
    // con proveedor y catálogo. Mientras fue un solo formulario para todo, el de la factura
    // no cabía y el del taxi pedía cosas que no existen.
    // Al corregir no se pregunta: la compra ya existe y es de esta clase.
    var chose by rememberSaveable(purchaseId) { mutableStateOf(purchaseId != null) }

    // Siempre hay al menos un renglón en pantalla, vacío y listo para teclear. Un formulario
    // que arranca sin nada obliga a tocar "agregar" antes de poder escribir, que es un toque
    // de más justo en el caso de una sola cosa, que es el más frecuente.
    var lines by remember { mutableStateOf(listOf(QuickPurchaseDraftLine(id = newDraftLineId()))) }
    var notes by remember { mutableStateOf("") }
    var photoBytes by remember { mutableStateOf<ByteArray?>(null) }
    var invoiceNumber by remember { mutableStateOf("") }
    // Proveedor del encabezado. Puede quedar en texto libre —el puesto del mercado, la
    // ferretería— o apuntar al catálogo cuando es alguien que vuelve, como el de gaseosas.
    var supplierName by remember { mutableStateOf("") }
    var supplierId by remember { mutableStateOf("") }
    // El vencimiento es lo único que sigue abriendo un selector desde el renglón, y solo
    // aparece al corregir una compra vieja que traía un perecedero.
    var expiryFor by remember { mutableStateOf<String?>(null) }
    val editingPurchase = state.editingPurchase
    // La fecha se puede mover: estas compras se anotan en la noche o al día siguiente, y
    // dejarla clavada en "ahora" corría el período del gasto y la fecha del Kardex hacia
    // el día de la captura. Mismo motivo por el que la factura ya la traía editable.
    var date by remember { mutableStateOf(Clock.System.now().toEpochMilliseconds()) }
    var showDatePicker by remember { mutableStateOf(false) }

    val branches = state.accessibleBranches
    var defaultBranch by remember(branches) {
        mutableStateOf(state.selectedBranch ?: branches.firstOrNull())
    }
    // Con caja fija, la sucursal es la de esa caja: elegir otra pondría el gasto en el
    // reporte de una sucursal y el faltante en la gaveta de otra.
    LaunchedEffect(session?.id, branches) {
        session?.let { s -> branches.firstOrNull { it.id == s.branchId }?.let { defaultBranch = it } }
    }

    // Se siembra una sola vez por compra —la clave es el id, no la lista— para no pisar lo
    // que el usuario esté editando cada vez que el state se recomponga.
    LaunchedEffect(editingPurchase?.id, state.editingOriginalLines) {
        val p = editingPurchase ?: return@LaunchedEffect
        if (state.editingOriginalLines.isEmpty()) return@LaunchedEffect
        lines = state.editingOriginalLines.map {
            it.toQuickDraft(
                state.allIngredients, state.allResaleProducts, branches, state.purchaseConcepts
            )
        }
        notes = p.notes.orEmpty()
        invoiceNumber = p.invoiceNumber.orEmpty()
        supplierName = p.supplierName
        supplierId = p.supplierId
        date = p.date
    }

    // Un renglón cuenta cuando tiene nombre y monto. Los vacíos se descartan al guardar en vez
    // de bloquear el botón: el renglón en blanco del final es parte del formulario, no un error.
    val filled = lines.filter { it.label.isNotBlank() && it.amount > 0.0 }
    val total = filled.sumOf { it.amount }
    // Lo que de verdad hay en la gaveta. Al corregir, lo que esta misma compra ya le descontó
    // vuelve a estar disponible: sin sumarlo, subir de Q50 a Q60 se compararía contra una caja
    // a la que ya se le quitaron los Q50.
    val cashAvailable = session?.let {
        it.expectedCashNow + (state.editingPurchase?.takeIf { p -> p.cashSessionId == it.id }?.totalAmount ?: 0.0)
    }
    val overCash = cashAvailable != null && total > cashAvailable + 0.01
    // Se llega acá desde los movimientos de la caja, que no distinguen qué clase de compra
    // pagó el efectivo. Una factura de proveedor NO se corrige con este formulario: no sabe de
    // presentaciones ni de IVA, y `saveQuickPurchase` la trataría como alta —creando una
    // compra nueva y dejando la vieja intacta, con el inventario contado dos veces.
    val wrongKind = state.editingPurchase?.let { !it.isQuick } == true

    fun update(line: QuickPurchaseDraftLine) {
        lines = lines.map { if (it.id == line.id) line else it }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (purchaseId == null) "Anotar gasto" else "Corregir",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BrandColors.Primary,
                    titleContentColor = androidx.compose.ui.graphics.Color.White,
                    navigationIconContentColor = androidx.compose.ui.graphics.Color.White
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            // El total va fijo abajo, como en la factura: es el número que se coteja contra
            // lo que se pagó de verdad, y arriba del pliegue no sirve. En el paso de elegir
            // qué se anota no hay nada que sumar todavía.
            if (chose) Surface(tonalElevation = 3.dp) {
                // `navigationBarsPadding` porque desde Android 15 la app dibuja de borde a
                // borde: sin esto el botón de guardar queda DEBAJO de los botones del
                // teléfono y no se puede tocar.
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "${filled.size} ${if (filled.size == 1) "cosa" else "cosas"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Q ${total.fmt(2)}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (wrongKind) {
                        Text(
                            "Esta es una factura de proveedor. Se corrige desde Compras, que es " +
                                "donde están sus presentaciones y su IVA.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    if (overCash && cashAvailable != null) {
                        Text(
                            "En ${session?.registerName?.ifEmpty { "esta caja" } ?: "esta caja"} " +
                                "hay Q ${cashAvailable.fmt(2)}. Si el dinero salió de otro lado, " +
                                "anotalo desde Operaciones.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val branch = defaultBranch ?: return@Button
                            val user = currentUser ?: return@Button
                            viewModel.saveQuickPurchase(
                                companyId = companyId,
                                currentUser = user,
                                defaultBranch = branch,
                                lines = filled,
                                date = date,
                                notes = notes.ifBlank { null },
                                photoBytes = photoBytes,
                                purchaseId = purchaseId,
                                session = session,
                                invoiceNumber = invoiceNumber.ifBlank { null },
                                supplierId = supplierId,
                                supplierName = supplierName.trim()
                            )
                        },
                        enabled = filled.isNotEmpty() && defaultBranch != null &&
                            !state.isLoading && !overCash && !wrongKind,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            when {
                                purchaseId != null -> "Guardar cambios"
                                total > 0.0 -> "Guardar Q ${total.fmt(2)}"
                                else -> "Guardar"
                            }
                        )
                    }
                }
            }
        }
    ) { padding ->
        // Corregir lee la compra y sus líneas de Firestore, y eso tarda un par de segundos.
        // Sin esto la pantalla se dibujaba vacía y el usuario veía un formulario en blanco
        // que se llenaba solo después — que se lee como que se perdieron los datos.
        if (purchaseId != null && state.editingPurchase == null) {
            LoadingOverlay(visible = true, message = "Abriendo el gasto…")
            return@Scaffold
        }
        AdaptiveContainer(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (!chose) CaptureKindStep(
                session = session,
                branchName = defaultBranch?.name.orEmpty(),
                // Comprar inventario es ingresar mercadería: quien no puede recibir stock
                // tampoco puede registrar una compra que lo mueve. Se esconden en vez de
                // fallar después, que es peor: ya habría capturado la factura entera.
                canBuyStock = currentUser?.hasPermission(Permission.RECEIVE_STOCK) == true,
                resaleEnabled = state.usesResale,
                flowsLoaded = state.purchaseFlowsLoaded,
                onExpense = { chose = true },
                onSupplierPurchase = onNavigateToSupplierPurchase
            ) else Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CashOriginNotice(session = session, branchName = defaultBranch?.name.orEmpty())

                lines.forEachIndexed { index, line ->
                    QuickPurchaseLineEditor(
                        line = line,
                        position = index + 1,
                        showPosition = lines.size > 1,
                        branches = branches,
                        defaultBranchName = defaultBranch?.name.orEmpty(),
                        canRemove = lines.size > 1,
                        onChange = ::update,
                        onRemove = { lines = lines.filterNot { it.id == line.id } },
                        onPickExpiry = { expiryFor = line.id }
                    )
                }

                OutlinedButton(
                    onClick = { lines = lines + QuickPurchaseDraftLine(id = newDraftLineId()) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Agregar otra cosa")
                }

                HorizontalDivider()

                // ── El papel y el resto ─────────────────────────────────────────
                // Va abajo porque casi siempre sobra: la ferretería no da factura y el
                // mercado tampoco. Cuando llega el de gaseosas sí, y entonces está acá.
                SupplierField(
                    name = supplierName,
                    suppliers = state.suppliers,
                    linked = supplierId.isNotEmpty(),
                    onNameChange = { typed ->
                        supplierName = typed
                        // Si lo tecleado ES un proveedor del catálogo, se engancha solo. Sin
                        // esto, escribir el nombre completo y correcto hacía DESAPARECER la
                        // sugerencia (solo se ofrecen coincidencias parciales) y la compra
                        // quedaba con el nombre suelto y sin id: no entraba a su ficha, que es
                        // justo lo que quien lo escribió estaba buscando.
                        supplierId = state.suppliers
                            .firstOrNull {
                                normalizeForSearch(it.company) == normalizeForSearch(typed.trim())
                            }
                            ?.id.orEmpty()
                    },
                    onPickSupplier = { supplierName = it.company; supplierId = it.id }
                )

                OutlinedTextField(
                    value = invoiceNumber,
                    onValueChange = { invoiceNumber = it },
                    label = { Text("Número de factura o recibo (opcional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Con caja fija el desembolso es de ahora: la fecha es la del turno abierto
                // y moverla desprendería el gasto del cuadre que lo explica.
                if (session == null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Fecha",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(onClick = { showDatePicker = true }) {
                            Icon(
                                Icons.Default.CalendarMonth,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(formatPurchaseDate(date))
                        }
                    }
                }

                // La sucursal del encabezado es solo el default de los renglones. Con una sola
                // accesible, o con caja fija, no hay nada que elegir y el selector sobra.
                if (branches.size > 1 && session == null) {
                    BranchDropdown(
                        label = "La mayoría va a",
                        branches = branches,
                        selected = defaultBranch,
                        onSelect = { defaultBranch = it }
                    )
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notas (opcional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )

                Text("Foto del comprobante (opcional)", style = MaterialTheme.typography.labelLarge)
                ImagePickerButton(
                    currentImageUrl = null,
                    selectedBytes = photoBytes,
                    onImageSelected = { photoBytes = it },
                    allowCamera = true
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    // Guardar escribe la compra, sus líneas, el inventario, los gastos del mes y el movimiento
    // de la caja: son varios segundos. El botón deshabilitado no alcanza como señal —la
    // pantalla se ve igual— y el segundo toque es lo que produce el registro doble.
    LoadingOverlay(
        visible = state.isLoading && chose,
        message = if (purchaseId == null) "Guardando el gasto…" else "Guardando los cambios…"
    )

    if (showDatePicker) {
        val dpState = rememberDatePickerState(initialSelectedDateMillis = date)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dpState.selectedDateMillis?.let { date = it }
                    showDatePicker = false
                }) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancelar") }
            }
        ) { DatePicker(state = dpState) }
    }

    // Elegir del catálogo YA NO se hace acá: la clase de cosa se decide en el primer paso, y
    // comprar inventario abre el formulario de Compras con su proveedor y sus presentaciones.
    expiryFor?.let { lineId ->
        val current = lines.firstOrNull { it.id == lineId }?.expiryDate ?: 0L
        val dpState = rememberDatePickerState(initialSelectedDateMillis = current.takeIf { it > 0L })
        DatePickerDialog(
            onDismissRequest = { expiryFor = null },
            confirmButton = {
                TextButton(onClick = {
                    dpState.selectedDateMillis?.let { picked ->
                        lines.firstOrNull { it.id == lineId }?.let { update(it.copy(expiryDate = picked)) }
                    }
                    expiryFor = null
                }) { Text("Aceptar") }
            },
            dismissButton = {
                Row {
                    if (current > 0L) {
                        TextButton(onClick = {
                            lines.firstOrNull { it.id == lineId }?.let { update(it.copy(expiryDate = 0L)) }
                            expiryFor = null
                        }) { Text("Quitar") }
                    }
                    TextButton(onClick = { expiryFor = null }) { Text("Cancelar") }
                }
            }
        ) { DatePicker(state = dpState) }
    }
}

/**
 * Primera pregunta: **qué se está anotando**.
 *
 * Se pregunta antes de mostrar un solo campo porque la respuesta cambia qué hay que llenar.
 * Un taxi son tres datos; una entrega de gaseosas es una factura con proveedor, catálogo,
 * cantidades por presentación e IVA. Mientras fue un solo formulario para todo, el de la
 * factura no cabía —no había forma de elegir el catálogo del proveedor— y el del taxi pedía
 * cantidad y unidad, que para un taxi no existen.
 *
 * Las dos opciones de inventario van a la MISMA pantalla (el formulario de Compras): la
 * factura del proveedor puede traer insumos y reventa a la vez, y ahí ambos catálogos ya
 * están. Se ofrecen por separado igual porque el usuario no piensa "voy a capturar una
 * factura", piensa "llegó el de las gaseosas" o "compré azúcar".
 */
@Composable
private fun CaptureKindStep(
    session: CashSession?,
    branchName: String,
    canBuyStock: Boolean,
    resaleEnabled: Boolean,
    flowsLoaded: Boolean,
    onExpense: () -> Unit,
    onSupplierPurchase: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CashOriginNotice(session = session, branchName = branchName)

        Text("¿Qué vas a anotar?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

        CaptureKindCard(
            title = "Un gasto",
            detail = "Escoba, taxi, garrafón, limpieza. Solo qué fue y cuánto costó. Podés " +
                "anotar varias cosas de una vez.",
            onClick = onExpense
        )
        // Las opciones de inventario salen JUNTAS o no salen: cuál hay depende de la
        // configuración de la empresa, y dibujar las que ya se saben mientras se lee el
        // resto hacía aparecer "Productos para revender" sola un par de segundos después,
        // como si el toque anterior la hubiera invocado. Con el ViewModel ya cargado —el
        // caso normal, porque es singleton— esto no llega a verse.
        if (canBuyStock && !flowsLoaded) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Text(
                    "Buscando qué más podés anotar…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (canBuyStock && flowsLoaded) {
            CaptureKindCard(
                title = "Insumos de un proveedor",
                detail = "Azúcar, harina, fruta. Se elige el proveedor y se captura de su " +
                    "catálogo, igual que una factura — pero pagada de una vez.",
                onClick = onSupplierPurchase
            )
            // La reventa se ofrece solo si la empresa la usa: sin el flujo encendido, el
            // catálogo de reventa del proveedor llega vacío y la opción lleva a una pantalla
            // donde no hay nada que elegir.
            if (resaleEnabled) {
                CaptureKindCard(
                    title = "Productos para revender",
                    detail = "Gaseosas, tostadas. Se elige el proveedor y aparecen sus " +
                        "productos; entran al inventario de la tienda.",
                    onClick = onSupplierPurchase
                )
            }
        }
    }
}

@Composable
private fun CaptureKindCard(title: String, detail: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Id de un renglón del borrador. Lleva un sufijo aleatorio además del reloj porque se agregan
 * de un toque: dos renglones creados dentro del mismo milisegundo compartirían id y editar uno
 * editaría los dos.
 */
private fun newDraftLineId(): String =
    "qp_${Clock.System.now().toEpochMilliseconds()}_${kotlin.random.Random.nextInt(100000)}"

/**
 * Un renglón de la compra, editable en su lugar.
 *
 * **Crece según lo que se elija, y nunca vuelve a pedir lo ya escrito.** Qué y cuánto están
 * siempre; el tipo de gasto aparece solo cuando el renglón se vuelve gasto; la cantidad y el
 * vencimiento, solo cuando entra a inventario. Los campos que casi nunca se tocan —sucursal
 * distinta, otro lugar— viven detrás de "Otra sucursal o lugar", porque pedirlos a todos
 * convierte anotar un taxi en llenar una factura.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickPurchaseLineEditor(
    line: QuickPurchaseDraftLine,
    position: Int,
    showPosition: Boolean,
    branches: List<Branch>,
    defaultBranchName: String,
    canRemove: Boolean,
    onChange: (QuickPurchaseDraftLine) -> Unit,
    onRemove: () -> Unit,
    onPickExpiry: () -> Unit
) {
    // Los campos numéricos guardan su texto aparte para poder escribir "12." o "0," sin que
    // el parseo los reescriba a mitad de la tecleada. La clave es el id del renglón: elegir un
    // insumo en el picker cambia el objeto pero no el id, así que lo tecleado no se pierde.
    var amountText by remember(line.id) {
        mutableStateOf(if (line.amount > 0.0) line.amount.fmt(2) else "")
    }
    var qtyText by remember(line.id) {
        mutableStateOf(if (line.quantity > 0.0) line.quantity.fmt(2) else "1")
    }
    var showMore by remember(line.id) { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (showPosition || canRemove) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (showPosition) "Cosa $position" else "",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    if (canRemove) {
                        IconButton(onClick = onRemove) {
                            Icon(Icons.Default.Close, contentDescription = "Quitar")
                        }
                    }
                }
            }

            OutlinedTextField(
                value = line.description,
                onValueChange = { onChange(line.copy(description = it)) },
                label = { Text("¿Qué compraste? *") },
                placeholder = { Text("Ej: garrafón, escoba, desinfectante") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = amountText,
                onValueChange = {
                    amountText = it
                    onChange(line.copy(amount = it.replace(',', '.').toDoubleOrNull() ?: 0.0))
                },
                label = { Text("Cuánto costó (Q) *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )

            // ── Renglón que YA venía con un concepto del catálogo ─────────
            // Solo puede pasar al corregir un gasto anterior. Acá no se elige catálogo —lo
            // que se compra seguido a un proveedor va por su factura, donde sale como fila
            // con su presentación— pero lo que ya estaba pegado se muestra y se conserva.
            if (!line.touchesStock && line.concept != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = qtyText,
                        onValueChange = {
                            qtyText = it
                            onChange(line.copy(quantity = it.replace(',', '.').toDoubleOrNull() ?: 0.0))
                        },
                        label = { Text("Cuántos") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    OutlinedTextField(
                        value = line.conceptUnit,
                        onValueChange = { onChange(line.copy(conceptUnit = it)) },
                        label = { Text("¿En qué?") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
                Text(
                    "Del catálogo: se suma al historial de «${line.concept.name}».",
                    style = MaterialTheme.typography.labelSmall,
                    color = BrandColors.Primary
                )
            }

            // ── Renglones que YA venían pegados al catálogo ─────────────────
            // Solo pueden existir al corregir una compra rápida anterior: acá ya no se elige
            // catálogo, porque la clase de cosa se decidió en el primer paso. Se muestran
            // como un hecho y no como una opción — pero se muestran, y sobre todo se
            // CONSERVAN: si el editor los ignorara, guardar convertiría el renglón en gasto
            // puro y `applyStockDelta` le revertiría el inventario en silencio.
            if (line.touchesStock) {
                Text(
                    when {
                        line.ingredient != null ->
                            "Entra al inventario como ${line.ingredient.name}. Para cambiarlo, " +
                                "registrá la compra desde Compras."
                        else ->
                            "Entra al inventario de tienda como ${line.product?.name.orEmpty()}. " +
                                "Para cambiarlo, registrá la compra desde Compras."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = BrandColors.Primary
                )
            }

            // La cantidad importa SOLO cuando hay inventario detrás: es lo que se descuenta
            // después. Sin catálogo, preguntar "cuántos" no tiene a dónde ir.
            if (line.touchesStock) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = qtyText,
                        onValueChange = {
                            qtyText = it
                            onChange(line.copy(quantity = it.replace(',', '.').toDoubleOrNull() ?: 0.0))
                        },
                        // Con unidad, porque "20" a secas no dice si son 20 libras o 20
                        // sandías, y el inventario se descuenta en la unidad del insumo.
                        label = {
                            Text(
                                if (line.stockUnit.isNotEmpty()) "Cuántos (${line.stockUnit})"
                                else "Cuántos"
                            )
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                }
            }

            // El tipo de gasto solo aplica a lo que se vuelve gasto. La materia prima y la
            // reventa son inventario: su costo entra al resultado cuando se venden, así que
            // pedir una categoría de gasto ahí sería preguntar algo que no se usa.
            if (!line.touchesStock || line.ingredient?.usage == IngredientUsage.SUPPLY) {
                CategoryDropdown(
                    selected = line.expenseCategory,
                    onSelect = { onChange(line.copy(expenseCategory = it)) }
                )
            }

            // El vencimiento solo se pide para lo que de verdad caduca. Sin este campo, la
            // fruta comprada acá creaba un lote sin fecha — y en FEFO los lotes sin fecha se
            // consumen al final, justo al revés de lo que la fruta necesita.
            if (line.ingredient?.isPerishable == true) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Caduca",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    AssistChip(
                        onClick = onPickExpiry,
                        label = {
                            Text(
                                if (line.expiryDate > 0L) formatPurchaseDate(line.expiryDate)
                                else "Anotar fecha"
                            )
                        }
                    )
                }
            }

            // ── Lo que casi nunca cambia ────────────────────────────────────
            if (showMore) {
                if (branches.size > 1) {
                    BranchDropdown(
                        label = "Va para",
                        branches = branches,
                        selected = line.branch,
                        onSelect = { onChange(line.copy(branch = it)) },
                        // Se nombra la sucursal en vez de decir "la de siempre": el usuario no
                        // tiene por qué acordarse de cuál eligió arriba, y una opción que no
                        // dice qué hace se lee como un error.
                        noneLabel = defaultBranchName.ifEmpty { "La del encabezado" }
                    )
                }
                OutlinedTextField(
                    value = line.placeName,
                    onValueChange = { onChange(line.copy(placeName = it)) },
                    label = { Text("Dónde lo compraste") },
                    placeholder = { Text("Ej: mercado, la tienda de la esquina") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            } else if (branches.size > 1 || line.placeName.isNotEmpty()) {
                TextButton(onClick = { showMore = true }) { Text("Otra sucursal o lugar") }
            }
        }
    }
}

/**
 * Dice de dónde va a salir la plata. Es una afirmación, no una pregunta: el dato ya está
 * resuelto por el contexto y lo único que falta es que quien anota lo vea antes de guardar.
 */
@Composable
private fun CashOriginNotice(session: CashSession?, branchName: String) {
    val (title, detail, icon) = if (session != null) {
        Triple(
            "Sale de ${session.registerName.ifEmpty { "esta caja" }}",
            "Se descuenta al cuadrarla. No hace falta restarlo a mano.",
            Icons.Default.PointOfSale
        )
    } else {
        Triple(
            "Caja general / del dueño",
            // El riesgo que queda: alguien que cobra anota acá algo que sí salió de su
            // gaveta, y al cuadrar le falta. Decirlo es lo único que lo evita.
            "No sale de ninguna gaveta. Si el dinero salió de una caja, anotalo desde el " +
                "Punto de Venta para que se descuente al cuadrar.",
            Icons.Default.AccountBalanceWallet
        )
    }
    Surface(
        color = BrandColors.PrimaryContainer,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(icon, contentDescription = null, tint = BrandColors.Primary, modifier = Modifier.size(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = BrandColors.Primary
                )
                Text(detail, style = MaterialTheme.typography.labelSmall, color = BrandColors.Primary)
                if (branchName.isNotEmpty()) {
                    Text(branchName, style = MaterialTheme.typography.labelSmall, color = BrandColors.Primary)
                }
            }
        }
    }
}

/**
 * A quién se le compró.
 *
 * Se escribe libre y, si coincide con alguien del catálogo, se ofrece para pegarlo: al de
 * gaseosas conviene atribuirle la compra —vuelve todas las semanas— y a la ferretería de una
 * sola vez no, porque el catálogo de proveedores termina lleno de nombres que nadie vuelve a
 * tocar. Es el mismo criterio del proveedor ocasional de la factura (`supplierId` vacío con
 * `supplierName` lleno).
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun SupplierField(
    name: String,
    suppliers: List<Supplier>,
    /** `true` si quedó pegado a una ficha del catálogo, y no como texto suelto. */
    linked: Boolean,
    onNameChange: (String) -> Unit,
    onPickSupplier: (Supplier) -> Unit
) {
    // Sin tildes: en el catálogo está "Plásticos" y nadie teclea la tilde con el teléfono en
    // una mano. Mientras la comparación las distinguió, escribir "Plasticos" no ofrecía nada
    // y el proveedor terminaba guardado como texto suelto sin que se viera por qué.
    val matches = remember(name, suppliers) {
        val q = normalizeForSearch(name.trim())
        if (q.isEmpty()) emptyList()
        else suppliers
            .filter { val n = normalizeForSearch(it.company); n.contains(q) && n != q }
            .take(4)
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text("¿A quién le compraste? (opcional)") },
            placeholder = { Text("Ej: Distribuidora de gaseosas, ferretería") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        // Chips y no texto azul: lo anterior SÍ era tocable, pero no lo PARECÍA — se lee como
        // una explicación, no como una opción, y el proveedor terminaba guardado como texto
        // suelto teniendo su ficha a un toque. Mismo patrón que las sugerencias de concepto.
        if (matches.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                matches.forEach { s ->
                    AssistChip(
                        onClick = { onPickSupplier(s) },
                        label = { Text(s.company, style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Business,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
            }
        }
        // La diferencia entre las dos cosas se DICE, porque decide algo que no se ve hasta
        // mucho después: solo lo que quedó pegado al catálogo entra al historial de esa
        // ficha. Con el nombre suelto el gasto se guarda igual y sale en el reporte del mes.
        if (name.isNotBlank()) {
            Text(
                if (linked) "Queda en el historial de este proveedor."
                else "Se guarda como texto: no va a aparecer en la ficha de ningún proveedor.",
                style = MaterialTheme.typography.labelSmall,
                color = if (linked) BrandColors.Primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Tipo de gasto del renglón.
 *
 * Ofrece [OPERATIONAL_EXPENSE_CATEGORIES] y no el enum entero: acá se anota lo que se compra
 * en el día, y "Alquiler", "Planilla" o "Depreciación de equipo" son compromisos mensuales
 * que se configuran en Costos Fijos. Mientras se ofreció la lista completa, quien anotaba una
 * escoba tenía que descartar cinco renglones que nunca le iban a servir para llegar a "Otros",
 * y el reporte terminaba con todo en esa bolsa.
 *
 * Si la categoría guardada quedó fuera de la lista —un gasto viejo, o uno reclasificado— se
 * ofrece igual, para poder verla y corregirla en vez de que la fila aparezca en blanco. Mismo
 * criterio que `usableInRecipes(keepIds)` con los insumos.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryDropdown(
    selected: ExpenseCategory,
    onSelect: (ExpenseCategory) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val options = remember(selected) {
        if (selected in OPERATIONAL_EXPENSE_CATEGORIES) OPERATIONAL_EXPENSE_CATEGORIES
        else OPERATIONAL_EXPENSE_CATEGORIES + selected
    }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected.label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Tipo de gasto") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { c ->
                DropdownMenuItem(
                    text = { Text(c.label) },
                    onClick = { onSelect(c); expanded = false }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BranchDropdown(
    label: String,
    branches: List<Branch>,
    selected: Branch?,
    onSelect: (Branch?) -> Unit,
    /** Texto de "sin elegir". Vacío = no se ofrece esa opción. */
    noneLabel: String = ""
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected?.name ?: noneLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (noneLabel.isNotEmpty()) {
                DropdownMenuItem(
                    text = { Text(noneLabel) },
                    onClick = { onSelect(null); expanded = false }
                )
            }
            branches.forEach { b ->
                DropdownMenuItem(
                    text = { Text(b.name) },
                    onClick = { onSelect(b); expanded = false }
                )
            }
        }
    }
}
