package com.bakery.erp.presentation.delivery

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.ui.state.ToggleableState
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
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.hasPermission
import com.bakery.erp.model.Branch
import com.bakery.erp.model.BranchCapability
import com.bakery.erp.model.DeliveryRun
import com.bakery.erp.model.DispatchNote
import com.bakery.erp.model.FixedAsset
import com.bakery.erp.model.canAccessBranch
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.presentation.branches.BranchViewModel
import com.bakery.erp.ui.AdaptiveContainer
import com.bakery.erp.util.fmt
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant
import com.bakery.erp.ui.theme.BrandColors.Primary as DeepSeaBlue
import com.bakery.erp.ui.theme.BrandColors.Ivory as WarmIvory
import com.bakery.erp.ui.theme.BrandColors.Accent as BurntOrange
import com.bakery.erp.ui.theme.BrandColors


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeliveryRunFormScreen(
    runId: String?,
    onNavigateBack: () -> Unit,
    authViewModel: AuthViewModel,
    viewModel: DeliveryViewModel,
    branchViewModel: BranchViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val branchState by branchViewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val user = authState.user
    val companyId = user?.orgId ?: ""
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(companyId) {
        if (companyId.isNotEmpty()) branchViewModel.loadBranches(companyId)
    }
    LaunchedEffect(runId, companyId) {
        if (companyId.isNotEmpty() && !runId.isNullOrEmpty()) viewModel.loadRunDetail(companyId, runId)
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }
    LaunchedEffect(state.isSaved) {
        if (state.isSaved) { viewModel.clearSaved(); onNavigateBack() }
    }

    val existing = state.selectedRun.takeIf { it?.id == runId }

    // ── Estado del form ──────────────────────────────────────────────────────
    var originBranchId by rememberSaveable(existing?.id) {
        mutableStateOf(existing?.originBranchId ?: "")
    }
    var driverUserId by rememberSaveable(existing?.id) {
        mutableStateOf(existing?.driverUserId ?: "")
    }
    var driverName by rememberSaveable(existing?.id) {
        mutableStateOf(existing?.driverName ?: "")
    }
    var vehicleAssetId by rememberSaveable(existing?.id) {
        mutableStateOf(existing?.vehicleAssetId ?: "")
    }
    var vehicleInfo by rememberSaveable(existing?.id) {
        mutableStateOf(existing?.vehicleInfo ?: "")
    }
    var notes by rememberSaveable(existing?.id) {
        mutableStateOf(existing?.notes ?: "")
    }
    var plannedDate by rememberSaveable(existing?.id) {
        mutableStateOf(existing?.plannedDate?.takeIf { it > 0 } ?: startOfToday())
    }
    var selectedNoteIds by remember(existing?.id) {
        mutableStateOf<Set<String>>(existing?.dispatchNoteIds?.toSet() ?: emptySet())
    }

    // ── Datos derivados ──────────────────────────────────────────────────────
    val plantBranches = remember(branchState.branchList, user) {
        branchState.branchList
            .filter { BranchCapability.CAN_PRODUCE in it.capabilities }
            .filter { user?.canAccessBranch(it.id) == true }
            .sortedBy { it.name }
    }
    val selectedBranch: Branch? = remember(plantBranches, originBranchId) {
        plantBranches.firstOrNull { it.id == originBranchId }
    }

    // Auto-seleccionar la única planta accesible (evita un dropdown innecesario).
    LaunchedEffect(plantBranches, existing?.id) {
        if (originBranchId.isEmpty() && plantBranches.size == 1) {
            originBranchId = plantBranches.first().id
        }
    }

    // Cargar candidatos cuando hay planta + fecha
    LaunchedEffect(originBranchId, plannedDate, runId) {
        if (companyId.isNotEmpty() && originBranchId.isNotEmpty()) {
            viewModel.loadFormData(companyId, originBranchId, plannedDate, runId.orEmpty())
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (runId == null) "Nueva hoja de reparto" else "Editar hoja", fontWeight = FontWeight.SemiBold) },
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
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // ── Planta origen ────────────────────────────────────────
                    if (plantBranches.size == 1 && selectedBranch != null) {
                        // Solo una planta accesible → mostrar como label fijo.
                        PlantLabelCard(branchName = selectedBranch.name)
                    } else {
                        var plantExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = plantExpanded,
                            onExpandedChange = { plantExpanded = !plantExpanded }
                        ) {
                            OutlinedTextField(
                                readOnly = true,
                                value = selectedBranch?.name ?: "Selecciona planta origen",
                                onValueChange = {},
                                label = { Text("Planta productora *") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = plantExpanded) },
                                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = plantExpanded,
                                onDismissRequest = { plantExpanded = false }
                            ) {
                                if (plantBranches.isEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("(Sin plantas accesibles)") },
                                        onClick = { plantExpanded = false },
                                        enabled = false
                                    )
                                }
                                plantBranches.forEach { b ->
                                    DropdownMenuItem(
                                        text = { Text(b.name) },
                                        onClick = { originBranchId = b.id; plantExpanded = false }
                                    )
                                }
                            }
                        }
                    }

                    // ── Fecha ────────────────────────────────────────────────
                    DayPickerRow(
                        plannedDate = plannedDate,
                        onPrev = { plannedDate = addDays(plannedDate, -1) },
                        onNext = { plannedDate = addDays(plannedDate, 1) },
                        onResetToday = { plannedDate = startOfToday() }
                    )

                    // ── Repartidor ───────────────────────────────────────────
                    var driverExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = driverExpanded,
                        onExpandedChange = { driverExpanded = !driverExpanded }
                    ) {
                        OutlinedTextField(
                            readOnly = true,
                            value = driverName.ifEmpty { "Selecciona repartidor" },
                            onValueChange = {},
                            label = { Text("Repartidor *") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = driverExpanded) },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = driverExpanded,
                            onDismissRequest = { driverExpanded = false }
                        ) {
                            if (state.driverCandidates.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("(Nadie con permiso de Repartidor)") },
                                    onClick = { driverExpanded = false },
                                    enabled = false
                                )
                            }
                            state.driverCandidates.forEach { u ->
                                DropdownMenuItem(
                                    text = { Text(u.displayName.ifEmpty { u.email }) },
                                    onClick = {
                                        driverUserId = u.id
                                        driverName = u.displayName.ifEmpty { u.email }
                                        driverExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Vehículo: dropdown de activos fijos categoría Vehículos.
                    var vehicleExpanded by remember { mutableStateOf(false) }
                    val selectedVehicle = remember(state.vehicleCandidates, vehicleAssetId) {
                        state.vehicleCandidates.firstOrNull { it.id == vehicleAssetId }
                    }
                    // Auto-preseleccionar si solo hay un vehículo activo.
                    LaunchedEffect(state.vehicleCandidates, existing?.id) {
                        if (vehicleAssetId.isEmpty() && state.vehicleCandidates.size == 1) {
                            val v = state.vehicleCandidates.first()
                            vehicleAssetId = v.id
                            vehicleInfo = vehicleDisplayName(v)
                        }
                    }
                    ExposedDropdownMenuBox(
                        expanded = vehicleExpanded,
                        onExpandedChange = { vehicleExpanded = !vehicleExpanded }
                    ) {
                        OutlinedTextField(
                            readOnly = true,
                            value = selectedVehicle?.let { vehicleDisplayName(it) }
                                ?: if (state.vehicleCandidates.isEmpty()) "Sin vehículos registrados"
                                   else "Selecciona vehículo",
                            onValueChange = {},
                            label = { Text("Vehículo") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = vehicleExpanded) },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = vehicleExpanded,
                            onDismissRequest = { vehicleExpanded = false }
                        ) {
                            if (state.vehicleCandidates.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("Registra un Activo Fijo categoría \"Vehículos\"") },
                                    onClick = { vehicleExpanded = false },
                                    enabled = false
                                )
                            }
                            state.vehicleCandidates.forEach { v ->
                                DropdownMenuItem(
                                    text = { Text(vehicleDisplayName(v)) },
                                    onClick = {
                                        vehicleAssetId = v.id
                                        vehicleInfo = vehicleDisplayName(v)
                                        vehicleExpanded = false
                                    }
                                )
                            }
                            if (state.vehicleCandidates.isNotEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("(Sin vehículo asignado)") },
                                    onClick = {
                                        vehicleAssetId = ""
                                        vehicleInfo = ""
                                        vehicleExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Notas (opcional)") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 3
                    )

                    // ── Selección de notas ───────────────────────────────────
                    Text("Notas para cargar", style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold, color = DeepSeaBlue,
                        modifier = Modifier.padding(top = 8.dp))

                    if (originBranchId.isEmpty()) {
                        Text("Primero selecciona la planta para ver las notas READY.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else if (state.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    } else if (state.candidateNotes.isEmpty()) {
                        Text("No hay notas listas para esta planta en este día.",
                            style = MaterialTheme.typography.bodySmall,
                            color = BrandColors.AccentDeep)
                    } else {
                        // Agrupar por turno para que el supervisor vea explícitamente
                        // de qué turno es cada nota y pueda seleccionar todas las del turno.
                        val groupedByShift = remember(state.candidateNotes) {
                            state.candidateNotes
                                .groupBy { it.shiftId to it.shiftName.ifEmpty { "Sin turno" } }
                                .toList()
                                .sortedBy { it.first.second }
                        }
                        groupedByShift.forEach { (key, notesInShift) ->
                            val shiftName = key.second
                            val idsInShift = notesInShift.map { it.id }.toSet()
                            val allSelected = idsInShift.all { it in selectedNoteIds }
                            val someSelected = !allSelected && idsInShift.any { it in selectedNoteIds }
                            ShiftHeaderRow(
                                shiftName = shiftName,
                                noteCount = notesInShift.size,
                                checked = allSelected,
                                partiallyChecked = someSelected,
                                onCheckedChange = { checkAll ->
                                    selectedNoteIds = if (checkAll) selectedNoteIds + idsInShift
                                                       else selectedNoteIds - idsInShift
                                }
                            )
                            notesInShift.forEach { note ->
                                NoteCheckRow(
                                    note = note,
                                    checked = note.id in selectedNoteIds,
                                    canSeeValue = user?.hasPermission(com.bakery.erp.model.Permission.VIEW_DISPATCH_VALUE) == true,
                                    tripWarning = state.candidateNoteTripWarnings[note.id],
                                    onCheckedChange = { checked ->
                                        selectedNoteIds = if (checked) selectedNoteIds + note.id
                                                           else selectedNoteIds - note.id
                                    }
                                )
                            }
                        }
                    }

                    val canSave = originBranchId.isNotEmpty() && driverUserId.isNotEmpty() &&
                        selectedNoteIds.isNotEmpty()
                    Button(
                        onClick = {
                            user ?: return@Button
                            val branchName = selectedBranch?.name.orEmpty()
                            val toSave = DeliveryRun(
                                id = runId.orEmpty(),
                                originBranchId = originBranchId,
                                originBranchName = branchName,
                                driverUserId = driverUserId,
                                driverName = driverName,
                                plannedDate = plannedDate,
                                vehicleAssetId = vehicleAssetId,
                                vehicleInfo = vehicleInfo.ifBlank { null },
                                notes = notes.ifBlank { null },
                                dispatchNoteIds = selectedNoteIds.toList(),
                                stopCount = selectedNoteIds.size,
                                // estado / timestamps los maneja el repo
                                status = existing?.status ?: com.bakery.erp.model.DeliveryRunStatus.PENDING,
                                createdAt = existing?.createdAt ?: 0L,
                                createdByUserId = existing?.createdByUserId ?: user.id,
                                createdByUserName = existing?.createdByUserName ?: user.displayName
                            )
                            viewModel.saveRun(companyId, toSave, selectedNoteIds.toList(), user)
                        },
                        enabled = canSave && !state.isLoading,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                    ) {
                        if (state.isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp),
                                color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Save, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Guardar hoja", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayPickerRow(
    plannedDate: Long,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onResetToday: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onPrev) {
                Icon(Icons.Default.ChevronLeft, "Día anterior", tint = DeepSeaBlue)
            }
            Column(
                modifier = Modifier.weight(1f).clickable { onResetToday() },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Día planeado", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatDay(plannedDate),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = DeepSeaBlue)
            }
            IconButton(onClick = onNext) {
                Icon(Icons.Default.ChevronRight, "Día siguiente", tint = DeepSeaBlue)
            }
        }
    }
}

@Composable
private fun PlantLabelCard(branchName: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                "Planta productora",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                branchName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = DeepSeaBlue
            )
        }
    }
}

@Composable
private fun ShiftHeaderRow(
    shiftName: String,
    noteCount: Int,
    checked: Boolean,
    partiallyChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val state = when {
        checked -> ToggleableState.On
        partiallyChecked -> ToggleableState.Indeterminate
        else -> ToggleableState.Off
    }
    Row(
        modifier = Modifier.fillMaxWidth()
            .padding(top = 8.dp)
            .background(DeepSeaBlue.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TriStateCheckbox(
            state = state,
            onClick = { onCheckedChange(state != ToggleableState.On) }
        )
        Spacer(Modifier.width(4.dp))
        Text(
            shiftName,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = DeepSeaBlue,
            modifier = Modifier.weight(1f)
        )
        Text(
            "$noteCount hoja${if (noteCount == 1) "" else "s"}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun NoteCheckRow(
    note: DispatchNote,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    // Con costo estándar el valor de la nota es lo que cuesta hacer el pan: no es para todos.
    canSeeValue: Boolean = false,
    tripWarning: String? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (checked) Color(0xFFE8F5E9) else Color.White
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = checked, onCheckedChange = onCheckedChange)
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(note.destinationBranchName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = DeepSeaBlue)
                Text(
                    "${note.lineCount} producto(s)" +
                        if (canSeeValue && note.totalValueWholesale > 0) " · Q${note.totalValueWholesale.fmt(2)}" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // El turno de esta nota no es el viaje en que su sucursal recibe. No
                // bloquea nada —el pan salió del único turno que lo hornea— pero es la
                // línea que evita que la nota se quede esperando un viaje que no le toca.
                // Naranja porque es estado que necesita tus ojos, y AccentDeep porque es
                // el único naranja legible como texto sobre blanco (ver paleta).
                if (tripWarning != null) {
                    Text(
                        tripWarning,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = BrandColors.AccentDeep
                    )
                }
            }
        }
    }
}

private fun startOfToday(): Long {
    val tz = TimeZone.currentSystemDefault()
    val today = Clock.System.now().toLocalDateTime(tz).date
    return LocalDateTime(today, LocalTime(0, 0)).toInstant(tz).toEpochMilliseconds()
}

private fun addDays(anchorMillis: Long, days: Int): Long {
    val tz = TimeZone.currentSystemDefault()
    val date = Instant.fromEpochMilliseconds(anchorMillis).toLocalDateTime(tz).date
    var y = date.year; var m = date.monthNumber; var d = date.dayOfMonth + days
    if (days > 0) {
        while (d > daysInMonth(y, m)) {
            d -= daysInMonth(y, m); m += 1
            if (m > 12) { m = 1; y += 1 }
        }
    } else {
        while (d < 1) {
            m -= 1
            if (m < 1) { m = 12; y -= 1 }
            d += daysInMonth(y, m)
        }
    }
    return LocalDateTime(LocalDate(y, m, d), LocalTime(0, 0)).toInstant(tz).toEpochMilliseconds()
}

private fun daysInMonth(year: Int, month: Int) = when (month) {
    1, 3, 5, 7, 8, 10, 12 -> 31
    4, 6, 9, 11 -> 30
    2 -> if ((year % 4 == 0 && year % 100 != 0) || year % 400 == 0) 29 else 28
    else -> 30
}

internal fun vehicleDisplayName(asset: FixedAsset): String {
    val plate = listOfNotNull(asset.assetCode.ifBlank { null }, asset.serialNumber).firstOrNull()
    return if (plate != null) "${asset.name} · $plate" else asset.name
}
