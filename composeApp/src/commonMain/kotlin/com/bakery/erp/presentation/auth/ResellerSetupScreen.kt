package com.bakery.erp.presentation.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.ExternalCustomer
import com.bakery.erp.model.ResellerDeliveryMode
import com.bakery.erp.ui.AdaptiveContainer
import com.bakery.erp.ui.TimePickerDialog
import com.bakery.erp.ui.formatHHmm
import com.bakery.erp.ui.parseHHmm
import com.bakery.erp.ui.theme.BrandColors.Primary as PrimaryBlue
import com.bakery.erp.ui.theme.BrandColors.Ivory as WarmIvory
import com.bakery.erp.ui.theme.BrandColors.Accent as BurntOrange
import com.bakery.erp.ui.theme.BrandColors

private val MutedText = Color(0xFF6E6E6E)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResellerSetupScreen(
    authViewModel: AuthViewModel,
    setupViewModel: ResellerSetupViewModel
) {
    val authState by authViewModel.uiState.collectAsState()
    val setupState by setupViewModel.uiState.collectAsState()
    val orgId = authState.user?.orgId ?: ""
    val displayName = authState.user?.displayName ?: ""
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(orgId) { setupViewModel.load(orgId) }
    LaunchedEffect(authState.error) {
        authState.error?.let { snackbarHostState.showSnackbar(it); authViewModel.clearError() }
    }
    LaunchedEffect(setupState.error) {
        setupState.error?.let { snackbarHostState.showSnackbar(it); setupViewModel.clearError() }
    }

    var businessName by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var phoneAlt by rememberSaveable { mutableStateOf("") }
    var taxId by rememberSaveable { mutableStateOf("") }
    var deliveryMode by rememberSaveable { mutableStateOf(ResellerDeliveryMode.PICKUP) }
    var preferredBranchId by rememberSaveable { mutableStateOf("") }
    var preferredPickupTime by rememberSaveable { mutableStateOf("") }
    var address by rememberSaveable { mutableStateOf("") }
    var addressRef by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var showTimePicker by remember { mutableStateOf(false) }
    var branchPickerExpanded by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = WarmIvory,
        topBar = {
            TopAppBar(
                title = { Text("Configurar mi cuenta", fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PrimaryBlue,
                    titleContentColor = Color.White
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        AdaptiveContainer(modifier = Modifier.padding(padding).fillMaxSize().imePadding()) {
            when {
                setupState.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = PrimaryBlue)
                }
                else -> {
                    Column(
                        modifier = Modifier.fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // ── Bienvenida ───────────────────────────────────
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = BurntOrange.copy(alpha = 0.08f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Text(
                                    "¡Bienvenido, $displayName!",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = BrandColors.AccentDeep,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.size(4.dp))
                                Text(
                                    "Te uniste a ${setupState.orgName}. Solo nos falta saber cómo prefieres recibir tu pedido. " +
                                        "Estos datos quedan guardados — no los pediremos otra vez.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MutedText
                                )
                            }
                        }

                        // ── Datos del negocio ────────────────────────────
                        SectionTitle("Datos de tu negocio")
                        OutlinedTextField(
                            value = businessName, onValueChange = { businessName = it },
                            label = { Text("Nombre del negocio (opcional)") },
                            singleLine = true, modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = phone,
                            onValueChange = { phone = it.filter { c -> c.isDigit() }.take(8) },
                            label = { Text("Teléfono (+502) *") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true, modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = phoneAlt,
                            onValueChange = { phoneAlt = it.filter { c -> c.isDigit() }.take(8) },
                            label = { Text("Teléfono adicional (+502)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true, modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = taxId,
                            onValueChange = { taxId = it.filter { c -> c.isDigit() || c == '-' } },
                            label = { Text("NIT (opcional)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true, modifier = Modifier.fillMaxWidth()
                        )

                        // ── Modalidad ────────────────────────────────────
                        SectionTitle("¿Cómo recibes tu pedido?")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = deliveryMode == ResellerDeliveryMode.PICKUP,
                                onClick = { deliveryMode = ResellerDeliveryMode.PICKUP },
                                label = { Text("Paso a recoger") },
                                leadingIcon = { Icon(Icons.Default.Storefront, null) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PrimaryBlue,
                                    selectedLabelColor = Color.White,
                                    selectedLeadingIconColor = Color.White
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = deliveryMode == ResellerDeliveryMode.DELIVERY,
                                onClick = {
                                    if (setupState.acceptsResellerDelivery)
                                        deliveryMode = ResellerDeliveryMode.DELIVERY
                                },
                                label = { Text("Me lo entregan") },
                                leadingIcon = { Icon(Icons.Default.LocalShipping, null) },
                                enabled = setupState.acceptsResellerDelivery,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PrimaryBlue,
                                    selectedLabelColor = Color.White,
                                    selectedLeadingIconColor = Color.White
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (!setupState.acceptsResellerDelivery) {
                            Text(
                                "Esta panadería no ofrece entregas a domicilio a revendedores.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MutedText
                            )
                        }

                        // ── Datos según modalidad ────────────────────────
                        if (deliveryMode == ResellerDeliveryMode.PICKUP) {
                            // Branch picker
                            ExposedDropdownMenuBox(
                                expanded = branchPickerExpanded,
                                onExpandedChange = { branchPickerExpanded = !branchPickerExpanded }
                            ) {
                                val current = setupState.branches.firstOrNull { it.id == preferredBranchId }
                                OutlinedTextField(
                                    value = current?.name ?: "",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Sucursal de retiro *") },
                                    trailingIcon = {
                                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = branchPickerExpanded)
                                    },
                                    modifier = Modifier
                                        .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                                        .fillMaxWidth()
                                )
                                ExposedDropdownMenu(
                                    expanded = branchPickerExpanded,
                                    onDismissRequest = { branchPickerExpanded = false }
                                ) {
                                    if (setupState.branches.isEmpty()) {
                                        DropdownMenuItem(
                                            text = { Text("Sin sucursales disponibles") },
                                            onClick = {},
                                            enabled = false
                                        )
                                    }
                                    setupState.branches.forEach { b ->
                                        DropdownMenuItem(
                                            text = {
                                                Column {
                                                    Text(b.name)
                                                    if (b.location.isNotEmpty()) {
                                                        Text(
                                                            b.location,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MutedText
                                                        )
                                                    }
                                                }
                                            },
                                            onClick = {
                                                preferredBranchId = b.id
                                                branchPickerExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            // Time picker (read-only field; tap opens dialog)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showTimePicker = true }
                            ) {
                                OutlinedTextField(
                                    value = preferredPickupTime,
                                    onValueChange = {},
                                    readOnly = true,
                                    enabled = false,
                                    label = { Text("Hora habitual de retiro *") },
                                    placeholder = { Text("Toca para elegir") },
                                    trailingIcon = { Icon(Icons.Default.AccessTime, null) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                        disabledTextColor = PrimaryBlue,
                                        disabledBorderColor = MutedText.copy(alpha = 0.4f),
                                        disabledLabelColor = MutedText,
                                        disabledTrailingIconColor = PrimaryBlue
                                    )
                                )
                            }
                            Text(
                                "Esta hora se usa solo como referencia para tu encargada. " +
                                    "Puedes ajustarla pedido por pedido.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MutedText
                            )
                        } else {
                            // DELIVERY
                            OutlinedTextField(
                                value = address, onValueChange = { address = it },
                                label = { Text("Dirección de entrega *") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = addressRef, onValueChange = { addressRef = it },
                                label = { Text("Punto de referencia") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                "Próximamente: capturar tu ubicación en el mapa para que el repartidor pueda " +
                                    "trazar la ruta hasta tu negocio.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MutedText
                            )
                        }

                        OutlinedTextField(
                            value = notes, onValueChange = { notes = it },
                            label = { Text("Notas para la panadería (opcional)") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.size(4.dp))

                        // ── Submit ───────────────────────────────────────
                        val canSubmit = !authState.isLoading &&
                            phone.isNotBlank() && (
                                deliveryMode == ResellerDeliveryMode.PICKUP &&
                                    preferredBranchId.isNotEmpty() &&
                                    preferredPickupTime.isNotEmpty()
                                ) || (
                                deliveryMode == ResellerDeliveryMode.DELIVERY &&
                                    address.isNotBlank()
                                )
                        Button(
                            onClick = {
                                val branch = setupState.branches.firstOrNull { it.id == preferredBranchId }
                                val draft = ExternalCustomer(
                                    name = displayName.trim(),
                                    businessName = businessName.trim(),
                                    phone = phone.trim().let { if (it.isEmpty()) "" else "+502$it" },
                                    phoneAlt = phoneAlt.trim().let { if (it.isEmpty()) "" else "+502$it" },
                                    address = if (deliveryMode == ResellerDeliveryMode.DELIVERY) address.trim()
                                              else branch?.location ?: "",
                                    taxId = taxId.trim(),
                                    deliveryMode = deliveryMode,
                                    preferredBranchId = if (deliveryMode == ResellerDeliveryMode.PICKUP) preferredBranchId
                                                        else "",
                                    preferredBranchName = if (deliveryMode == ResellerDeliveryMode.PICKUP) (branch?.name ?: "")
                                                          else "",
                                    preferredPickupTime = if (deliveryMode == ResellerDeliveryMode.PICKUP) preferredPickupTime
                                                          else "",
                                    deliveryAddressReference = if (deliveryMode == ResellerDeliveryMode.DELIVERY) addressRef.trim()
                                                               else "",
                                    notes = notes.trim()
                                )
                                authViewModel.completeResellerProfile(draft)
                            },
                            enabled = canSubmit && phone.isNotBlank() && (
                                (deliveryMode == ResellerDeliveryMode.PICKUP &&
                                    preferredBranchId.isNotEmpty() &&
                                    preferredPickupTime.isNotEmpty()) ||
                                (deliveryMode == ResellerDeliveryMode.DELIVERY &&
                                    address.isNotBlank())
                                ),
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            if (authState.isLoading) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Enviar para aprobación")
                            }
                        }
                        Text(
                            "Tu encargada de planta revisará tu solicitud y te asignará turno de producción. " +
                                "Te avisaremos cuando esté lista para empezar a pedir.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MutedText
                        )
                    }
                }
            }
        }
    }

    if (showTimePicker) {
        val initial = parseHHmm(preferredPickupTime) ?: (7 to 0)
        TimePickerDialog(
            initialHour = initial.first,
            initialMinute = initial.second,
            title = "Hora habitual de retiro",
            onDismiss = { showTimePicker = false },
            onConfirm = { h, m ->
                preferredPickupTime = formatHHmm(h, m)
                showTimePicker = false
            }
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = PrimaryBlue,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 4.dp)
    )
}
