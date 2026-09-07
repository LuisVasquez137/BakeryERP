package com.bakery.erp.presentation.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.bakery.erp.ui.LoadingOverlay

@Composable
fun JoinOrgScreen(
    onNavigateBack: () -> Unit,
    viewModel: AuthViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val isGoogleUser = state.user != null

    // Remember declarations incondicionales (regla de Compose)
    var inviteCode by remember { mutableStateOf("") }
    var displayName by remember(state.user) { mutableStateOf(state.user?.displayName ?: "") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordMismatch by remember { mutableStateOf(false) }
    /** "EMPLOYEE" o "RESELLER" — el revendedor solo se permite via cuenta nueva
     *  (no para Google sign-in que ya tiene perfil OWNER por defecto). */
    var joinAs by remember { mutableStateOf("EMPLOYEE") }

    Box(
        modifier = Modifier.fillMaxSize().imePadding(),
        contentAlignment = Alignment.Center
    ) {
    Column(
        modifier = Modifier
            .widthIn(max = 440.dp)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Unirme a una panadería",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Ingresa el código que te compartió el administrador",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Selector de tipo de cuenta (solo para registro nuevo con email).
        if (!isGoogleUser) {
            Spacer(Modifier.height(20.dp))
            Text(
                text = "¿Cómo te vas a unir?",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = joinAs == "EMPLOYEE",
                    onClick = { joinAs = "EMPLOYEE"; viewModel.clearError() },
                    label = { Text("Soy colaborador") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = joinAs == "RESELLER",
                    onClick = { joinAs = "RESELLER"; viewModel.clearError() },
                    label = { Text("Soy revendedor") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = when (joinAs) {
                    "RESELLER" -> "Tienda externa que compra al por mayor para revender. " +
                        "Después de crear tu cuenta te pediremos sucursal de retiro y hora habitual."
                    else -> "Empleado o encargado de la panadería."
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(20.dp))

        OutlinedTextField(
            value = inviteCode,
            onValueChange = { inviteCode = it.uppercase().take(6); viewModel.clearError() },
            label = { Text("Código de invitación") },
            placeholder = { Text("Ej: PANX7K") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            isError = state.error != null,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                capitalization = KeyboardCapitalization.Characters
            ),
            supportingText = { Text("${inviteCode.length}/6 caracteres") }
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = displayName,
            onValueChange = { displayName = it; viewModel.clearError() },
            label = { Text("Tu nombre completo") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
        )

        if (!isGoogleUser) {
            // Solo usuarios por email necesitan ingresar credenciales
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = email,
                onValueChange = { email = it; viewModel.clearError() },
                label = { Text("Correo electrónico") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                isError = state.error != null
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it; passwordMismatch = false; viewModel.clearError() },
                label = { Text("Contraseña") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                isError = passwordMismatch || state.error != null
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it; passwordMismatch = false },
                label = { Text("Confirmar contraseña") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                isError = passwordMismatch
            )
        }

        val errorMessage = when {
            passwordMismatch -> "Las contraseñas no coinciden"
            state.error != null -> state.error
            else -> null
        }
        if (errorMessage != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                if (isGoogleUser) {
                    viewModel.joinOrg(inviteCode.trim(), displayName.trim())
                } else {
                    if (password != confirmPassword) { passwordMismatch = true; return@Button }
                    when (joinAs) {
                        "RESELLER" -> viewModel.signupAsReseller(
                            inviteCode.trim(), displayName.trim(), email.trim(), password
                        )
                        else -> viewModel.joinWithInviteCode(
                            inviteCode.trim(), displayName.trim(), email.trim(), password
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isLoading &&
                    inviteCode.length == 6 &&
                    displayName.isNotBlank() &&
                    (isGoogleUser || (email.isNotBlank() && password.isNotBlank()))
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.height(18.dp)
                )
            } else {
                Text(
                    when (joinAs) {
                        "RESELLER" -> "Continuar como revendedor"
                        else -> "Unirme a la panadería"
                    }
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onNavigateBack) { Text("Volver") }
    }

    // Overlay de carga a pantalla completa mientras se procesa la solicitud de unirse.
    LoadingOverlay(visible = state.isLoading)
    }
}
