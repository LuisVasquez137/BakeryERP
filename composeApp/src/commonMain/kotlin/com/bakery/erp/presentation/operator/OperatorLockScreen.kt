package com.bakery.erp.presentation.operator

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bakery.erp.model.Employee
import com.bakery.erp.model.OperatorSession
import com.bakery.erp.model.User
import com.bakery.erp.ui.LoadingOverlay
import com.bakery.erp.ui.ProductImage
import com.bakery.erp.ui.theme.BrandColors

/**
 * Pantalla que tapa todo en un equipo de sucursal mientras nadie se ha identificado.
 * Se pinta ENCIMA del NavHost (AppNavigation), así al volver alguien encuentra la app
 * donde la dejó la persona anterior… pero con sus propios permisos.
 */
@Composable
fun OperatorLockScreen(
    account: User,
    viewModel: OperatorLockViewModel,
    onSession: (OperatorSession) -> Unit,
    onSignOutDevice: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var confirmSignOut by remember { mutableStateOf(false) }

    LaunchedEffect(account.orgId) { viewModel.load(account.orgId) }
    LaunchedEffect(state.session) {
        state.session?.let { onSession(it); viewModel.reset() }
    }

    // Tapa todo y se traga los toques: nada de lo de abajo se puede tocar.
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        color = BrandColors.Ivory
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BrandColors.Primary)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Lock, null, tint = BrandColors.Ivory)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Equipo bloqueado", color = BrandColors.Ivory, fontWeight = FontWeight.Bold)
                    Text(
                        account.displayName.ifBlank { account.email },
                        color = BrandColors.Ivory.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                TextButton(onClick = { confirmSignOut = true }) {
                    Icon(Icons.AutoMirrored.Filled.Logout, null, tint = BrandColors.Ivory, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Cerrar sesión", color = BrandColors.Ivory)
                }
            }

            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                val selected = state.selected
                if (selected == null) {
                    PickPerson(account = account, state = state, onPick = viewModel::select)
                } else {
                    PinEntry(
                        employee = selected,
                        step = state.step,
                        error = state.error,
                        isWorking = state.isWorking,
                        onBack = viewModel::back,
                        onSubmit = { pin -> viewModel.submit(account.orgId, account.id, pin) },
                        onTyping = viewModel::clearError
                    )
                }
            }
        }
        LoadingOverlay(visible = state.isWorking, message = "Verificando…")
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("¿Cerrar la sesión del equipo?") },
            text = {
                Text(
                    "Para volver a usar este equipo habrá que entrar con el correo y la contraseña " +
                        "de la cuenta de la sucursal."
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmSignOut = false; onSignOutDevice() }) { Text("Cerrar sesión") }
            },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun PickPerson(account: User, state: OperatorLockUiState, onPick: (Employee) -> Unit) {
    var query by remember { mutableStateOf("") }
    val filtered = state.employees.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
    // Primero la gente de las sucursales de este equipo; los demás, por si alguien cubre turno.
    val (here, others) = filtered.partition { account.branchIds.isEmpty() || it.branchId in account.branchIds }

    Column(Modifier.widthIn(max = 900.dp).fillMaxSize().padding(horizontal = 16.dp)) {
        Text(
            "¿Quién va a usar el equipo?",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = BrandColors.Primary,
            modifier = Modifier.padding(top = 20.dp, bottom = 4.dp)
        )
        Text("Toca tu nombre y escribe tu PIN.", color = BrandColors.MutedSlate)
        if (state.employees.size > 8) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Buscar nombre") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
        when {
            state.isLoading -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.employees.isEmpty() -> Text(
                state.error ?: ("Todavía no hay colaboradores con PIN. El dueño los asigna en " +
                    "Colaboradores → ficha → Acceso."),
                color = BrandColors.MutedSlate,
                modifier = Modifier.padding(vertical = 24.dp)
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 140.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(here, key = { it.id }) { PersonCard(it, onPick) }
                if (others.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            "Otras sucursales",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = BrandColors.MutedSlate,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    items(others, key = { it.id }) { PersonCard(it, onPick) }
                }
                item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun PersonCard(employee: Employee, onPick: (Employee) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onPick(employee) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 16.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Avatar(employee, 64)
            Spacer(Modifier.height(8.dp))
            Text(
                employee.name,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (employee.positionTitle.isNotBlank()) {
                Text(
                    employee.positionTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = BrandColors.MutedSlate,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun Avatar(employee: Employee, sizeDp: Int) {
    Box(
        modifier = Modifier.size(sizeDp.dp).clip(CircleShape).background(BrandColors.PrimaryContainer),
        contentAlignment = Alignment.Center
    ) {
        if (employee.photoUrl.isNotBlank()) {
            ProductImage(imageUrl = employee.photoUrl, contentDescription = employee.name, modifier = Modifier.size(sizeDp.dp))
        } else {
            Text(
                employee.name.trim().firstOrNull()?.uppercase() ?: "?",
                fontSize = (sizeDp / 2.4).sp,
                fontWeight = FontWeight.Bold,
                color = BrandColors.Primary
            )
        }
    }
}

@Composable
private fun PinEntry(
    employee: Employee,
    step: LockStep,
    error: String?,
    isWorking: Boolean,
    onBack: () -> Unit,
    onSubmit: (String) -> Unit,
    onTyping: () -> Unit
) {
    // Los dígitos viven solo aquí y se borran al cambiar de paso o al fallar.
    var digits by remember(step) { mutableStateOf("") }
    LaunchedEffect(error) { if (error != null) digits = "" }

    val prompt = when (step) {
        LockStep.NEW_PIN -> "Tu PIN era temporal. Escoge uno nuevo que solo tú sepas."
        LockStep.CONFIRM_NEW_PIN -> "Repite tu PIN nuevo."
        else -> "Escribe tu PIN"
    }

    Column(
        Modifier.widthIn(max = 420.dp).fillMaxWidth().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth()) {
            TextButton(onClick = onBack, enabled = !isWorking) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("No soy yo")
            }
        }
        Avatar(employee, 80)
        Spacer(Modifier.height(8.dp))
        Text(employee.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(prompt, color = BrandColors.MutedSlate, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))

        // Puntos: 6 casillas; las que no se usan quedan como contorno tenue.
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(6) { i ->
                val filled = i < digits.length
                Box(
                    Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(if (filled) BrandColors.Primary else BrandColors.Ivory)
                        .border(1.5.dp, if (i < 4 || filled) BrandColors.Primary else BrandColors.Outline, CircleShape)
                )
            }
        }
        Box(Modifier.height(44.dp).padding(top = 8.dp), contentAlignment = Alignment.Center) {
            if (error != null) {
                Text(error, color = BrandColors.Danger, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            }
        }

        val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("<", "0", "OK"))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    row.forEach { key ->
                        when (key) {
                            "<" -> PadKey(icon = Icons.AutoMirrored.Filled.Backspace, enabled = digits.isNotEmpty() && !isWorking) {
                                digits = digits.dropLast(1)
                            }
                            "OK" -> PadKey(icon = Icons.Default.Check, enabled = digits.length >= 4 && !isWorking, primary = true) {
                                val pin = digits
                                digits = ""
                                onSubmit(pin)
                            }
                            else -> PadKey(label = key, enabled = digits.length < 6 && !isWorking) {
                                onTyping()
                                digits += key
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PadKey(
    label: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean,
    primary: Boolean = false,
    onClick: () -> Unit
) {
    val bg = when {
        primary && enabled -> BrandColors.Primary
        primary -> BrandColors.PrimaryContainer
        else -> MaterialTheme.colorScheme.surface
    }
    val fg = if (primary && enabled) BrandColors.Ivory else BrandColors.Primary
    Box(
        modifier = Modifier
            .size(76.dp)
            .clip(CircleShape)
            .background(bg)
            .border(1.dp, BrandColors.Primary.copy(alpha = 0.2f), CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (label != null) {
            Text(label, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = fg.copy(alpha = if (enabled) 1f else 0.4f))
        } else if (icon != null) {
            Icon(icon, null, tint = fg.copy(alpha = if (enabled || primary) 1f else 0.4f))
        }
    }
}
