package com.bakery.erp.presentation.fel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.Branch
import com.bakery.erp.model.FelBranchAssignment
import com.bakery.erp.model.FelCredentialsInput
import com.bakery.erp.model.FelEnvironment
import com.bakery.erp.model.FelProfile
import com.bakery.erp.model.FelVatAffiliation
import com.bakery.erp.model.cleanNit
import com.bakery.erp.model.felAssignmentProblems
import com.bakery.erp.model.isValidGuatemalaNit
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.ui.AdaptiveContainer
import com.bakery.erp.ui.LoadingOverlay
import com.bakery.erp.ui.theme.BrandColors

@Composable
fun FelProfileFormScreen(
    profileId: String?,
    onNavigateBack: () -> Unit,
    authViewModel: AuthViewModel,
    viewModel: FelViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val companyId = authState.user?.orgId ?: ""

    LaunchedEffect(companyId) {
        // Entrar directo al formulario (sin pasar por la lista) no trae nada cargado.
        if (state.branches.isEmpty()) viewModel.load(companyId)
    }
    LaunchedEffect(state.isSaved) {
        if (state.isSaved) { viewModel.consumeSaved(); onNavigateBack() }
    }

    val existing = state.profile(profileId)
    if (profileId != null && existing == null) {
        Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = BrandColors.Primary) }
        return
    }
    FelProfileFormContent(
        existing = existing,
        state = state,
        onNavigateBack = onNavigateBack,
        onSave = { profile, credentials, assignments ->
            viewModel.save(companyId, profile, credentials, assignments)
        },
        onErrorShown = viewModel::clearError
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FelProfileFormContent(
    existing: FelProfile?,
    state: FelUiState,
    onNavigateBack: () -> Unit,
    onSave: (FelProfile, FelCredentialsInput?, List<FelBranchAssignment>) -> Unit,
    onErrorShown: () -> Unit
) {
    val key = existing?.id ?: "nuevo"
    var nit by rememberSaveable(key) { mutableStateOf(existing?.nit ?: "") }
    var nombreEmisor by rememberSaveable(key) { mutableStateOf(existing?.nombreEmisor ?: "") }
    var nombreComercial by rememberSaveable(key) { mutableStateOf(existing?.nombreComercial ?: "") }
    var correo by rememberSaveable(key) { mutableStateOf(existing?.correo ?: "") }
    var direccion by rememberSaveable(key) { mutableStateOf(existing?.direccion ?: "") }
    var municipio by rememberSaveable(key) { mutableStateOf(existing?.municipio ?: "") }
    var departamento by rememberSaveable(key) { mutableStateOf(existing?.departamento ?: "") }
    var codigoPostal by rememberSaveable(key) { mutableStateOf(existing?.codigoPostal ?: "01001") }
    var afiliacion by rememberSaveable(key) { mutableStateOf(existing?.afiliacionIVA?.name ?: FelVatAffiliation.GEN.name) }
    var regimenIsr by rememberSaveable(key) { mutableStateOf(existing?.regimenISR ?: "OPCIONAL") }
    var active by rememberSaveable(key) { mutableStateOf(existing?.active ?: true) }
    var apiUser by rememberSaveable(key) { mutableStateOf("") }
    // La contraseña no se guarda en el estado que sobrevive a la rotación a propósito:
    // rememberSaveable la escribiría al Bundle del sistema.
    var apiPassword by remember(key) { mutableStateOf("") }
    var environment by rememberSaveable(key) { mutableStateOf(existing?.environment?.name ?: FelEnvironment.PRODUCCION.name) }
    var showPassword by remember { mutableStateOf(false) }
    var attempted by remember { mutableStateOf(false) }

    // branchId → (código, factura por defecto). Solo las marcadas están en el mapa.
    val selected = remember(key, state.branches) {
        mutableStateMapOf<String, Pair<String, Boolean>>().apply {
            state.branches.filter { existing != null && it.felProfileId == existing.id }
                .forEach { put(it.id, it.felEstablishmentCode to it.invoiceByDefault) }
        }
    }

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it); onErrorShown() }
    }

    val branchName: (String) -> String = { id -> state.branches.firstOrNull { it.id == id }?.name ?: id }
    val assignments = selected.map { (id, v) -> FelBranchAssignment(id, v.first, v.second) }
    val nitOk = isValidGuatemalaNit(nit)
    val problems = buildList {
        if (!nitOk) add("El NIT no es válido: revisá el dígito verificador.")
        if (nombreEmisor.isBlank()) add("Falta la razón social.")
        if (direccion.isBlank() || municipio.isBlank() || departamento.isBlank()) add("Falta la dirección fiscal completa.")
        addAll(felAssignmentProblems(assignments, branchName))
    }
    val sendsCredentials = apiPassword.isNotBlank()

    Scaffold(
        containerColor = BrandColors.Ivory,
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "Nuevo NIT" else "NIT emisor", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
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
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
                    if (attempted && problems.isNotEmpty()) {
                        problems.forEach {
                            Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    Button(
                        onClick = {
                            attempted = true
                            if (problems.isNotEmpty()) return@Button
                            val profile = FelProfile(
                                id = existing?.id ?: "",
                                nit = cleanNit(nit),
                                nombreEmisor = nombreEmisor,
                                nombreComercial = nombreComercial.ifBlank { nombreEmisor },
                                correo = correo,
                                direccion = direccion,
                                municipio = municipio,
                                departamento = departamento,
                                codigoPostal = codigoPostal.ifBlank { "01001" },
                                afiliacionIVA = FelVatAffiliation.valueOf(afiliacion),
                                regimenISR = regimenIsr,
                                active = active
                            )
                            val credentials = if (sendsCredentials) FelCredentialsInput(
                                user = apiUser.ifBlank { cleanNit(nit) },
                                password = apiPassword,
                                environment = FelEnvironment.valueOf(environment)
                            ) else null
                            onSave(profile, credentials, assignments)
                        },
                        enabled = !state.isSaving,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandColors.Primary)
                    ) {
                        Text(if (sendsCredentials) "Probar credenciales y guardar" else "Guardar")
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            AdaptiveContainer(Modifier.fillMaxSize()) {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Section("Datos del RTU") {
                        OutlinedTextField(
                            value = nit, onValueChange = { nit = it },
                            label = { Text("NIT") }, placeholder = { Text("5932521-6") },
                            singleLine = true, isError = nit.isNotBlank() && !nitOk,
                            supportingText = if (nit.isNotBlank() && !nitOk) {
                                { Text("El dígito verificador no cuadra") }
                            } else null,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Field("Razón social (como sale en el RTU)", nombreEmisor) { nombreEmisor = it }
                        Field("Nombre comercial", nombreComercial) { nombreComercial = it }
                        Field("Correo del emisor (opcional)", correo, KeyboardType.Email) { correo = it }
                        Field("Dirección fiscal", direccion) { direccion = it }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Field("Municipio", municipio, modifier = Modifier.weight(1f)) { municipio = it }
                            Field("Departamento", departamento, modifier = Modifier.weight(1f)) { departamento = it }
                        }
                        Field("Código postal", codigoPostal, KeyboardType.Number) { codigoPostal = it }
                    }

                    Section("Tipo de contribuyente") {
                        Hint("Aparece en tu constancia del RTU y decide qué documento se emite. Si no es el tuyo, la SAT rechaza la factura.")
                        FelVatAffiliation.entries.forEach { a ->
                            Choice(a.label, a.detail, afiliacion == a.name) { afiliacion = a.name }
                        }
                        if (afiliacion == FelVatAffiliation.GEN.name) {
                            Text("Régimen de ISR", fontWeight = FontWeight.SemiBold, color = BrandColors.TextSlate)
                            Choice("Opcional simplificado", "Retención definitiva sobre ingresos", regimenIsr != "UTILIDADES") { regimenIsr = "OPCIONAL" }
                            Choice("Sobre utilidades", "Pagos trimestrales", regimenIsr == "UTILIDADES") { regimenIsr = "UTILIDADES" }
                        }
                    }

                    Section("Credenciales de Digifact") {
                        Hint("Las que Digifact entregó para este NIT. Se prueban contra Digifact antes de guardarse y nunca vuelven a mostrarse.")
                        if (existing?.hasCredentials == true) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.Info, null, tint = BrandColors.Primary, modifier = Modifier.size(16.dp))
                                Text(
                                    "Ya hay credenciales de ${existing.environment.label.lowercase()} guardadas. Llená estos campos solo para reemplazarlas.",
                                    style = MaterialTheme.typography.bodySmall, color = BrandColors.MutedSlate
                                )
                            }
                        } else {
                            Text(
                                "Sin credenciales este NIT no puede facturar.",
                                style = MaterialTheme.typography.bodySmall, color = BrandColors.AccentDeep
                            )
                        }
                        OutlinedTextField(
                            value = apiUser, onValueChange = { apiUser = it },
                            label = { Text("Usuario") },
                            placeholder = { Text(cleanNit(nit).ifBlank { "Normalmente es el NIT" }) },
                            singleLine = true, modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = apiPassword, onValueChange = { apiPassword = it },
                            label = { Text("Contraseña") }, singleLine = true,
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            trailingIcon = {
                                TextButton(onClick = { showPassword = !showPassword }) { Text(if (showPassword) "Ocultar" else "Ver") }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (sendsCredentials) {
                            FelEnvironment.entries.forEach { e ->
                                Choice(
                                    e.label,
                                    if (e == FelEnvironment.PRODUCCION) "Facturas reales ante la SAT" else "Solo si Digifact te dio un usuario de pruebas",
                                    environment == e.name
                                ) { environment = e.name }
                            }
                        }
                    }

                    Section("Sucursales que facturan con este NIT") {
                        Hint("Marcá cada sucursal y escribí el código de establecimiento que la SAT le dio dentro de este NIT.")
                        if (state.branches.isEmpty()) {
                            Text("No hay sucursales activas.", color = BrandColors.MutedSlate)
                        }
                        state.branches.forEach { b ->
                            BranchRow(
                                branch = b,
                                otherProfileName = b.felProfileId
                                    .takeIf { it.isNotBlank() && it != existing?.id }
                                    ?.let { pid -> state.profile(pid)?.let { it.nombreComercial.ifBlank { it.nit } } ?: "otro NIT" },
                                value = selected[b.id],
                                onToggle = { on ->
                                    if (on) selected[b.id] = (b.felEstablishmentCode.takeIf { b.felProfileId == existing?.id } ?: "") to b.invoiceByDefault
                                    else selected.remove(b.id)
                                },
                                onCode = { code -> selected[b.id]?.let { selected[b.id] = code to it.second } },
                                onDefault = { d -> selected[b.id]?.let { selected[b.id] = it.first to d } }
                            )
                        }
                    }

                    if (existing != null) {
                        Section("Estado") {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Facturación activa", fontWeight = FontWeight.SemiBold, color = BrandColors.TextSlate)
                                    Hint("Apagado, ninguna sucursal de este NIT factura. Las facturas ya emitidas no cambian.")
                                }
                                Switch(checked = active, onCheckedChange = { active = it })
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            LoadingOverlay(
                visible = state.isSaving,
                message = if (sendsCredentials) "Probando las credenciales con Digifact…" else "Guardando…"
            )
        }
    }
}

@Composable
private fun BranchRow(
    branch: Branch,
    otherProfileName: String?,
    value: Pair<String, Boolean>?,
    onToggle: (Boolean) -> Unit,
    onCode: (String) -> Unit,
    onDefault: (Boolean) -> Unit
) {
    val checked = value != null
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = checked, onCheckedChange = onToggle)
            Column(Modifier.weight(1f)) {
                Text(branch.name, fontWeight = FontWeight.SemiBold, color = BrandColors.TextSlate)
                if (otherProfileName != null) {
                    // Reasignar es válido (se puede estar corrigiendo), pero tiene que ser a
                    // sabiendas: al guardar deja de facturar con el otro NIT.
                    Text(
                        if (checked) "Factura con $otherProfileName. Al guardar pasa a este NIT."
                        else "Factura con $otherProfileName.",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (checked) MaterialTheme.colorScheme.error else BrandColors.MutedSlate
                    )
                }
            }
        }
        if (value != null) {
            Row(
                Modifier.fillMaxWidth().padding(start = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = value.first, onValueChange = onCode,
                    label = { Text("Establecimiento") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(140.dp)
                )
                Column(Modifier.weight(1f)) {
                    Text("Facturar por defecto", style = MaterialTheme.typography.bodyMedium, color = BrandColors.TextSlate)
                    Text("El cobro sale con Facturar marcado", style = MaterialTheme.typography.bodySmall, color = BrandColors.MutedSlate)
                }
                Switch(checked = value.second, onCheckedChange = onDefault)
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = BrandColors.TextSlate)
            content()
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = BrandColors.MutedSlate)
}

@Composable
private fun Field(
    label: String,
    value: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType), modifier = modifier
    )
}

@Composable
private fun Choice(title: String, detail: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, onClick = onClick).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column {
            Text(title, color = BrandColors.TextSlate)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = BrandColors.MutedSlate)
        }
    }
}
