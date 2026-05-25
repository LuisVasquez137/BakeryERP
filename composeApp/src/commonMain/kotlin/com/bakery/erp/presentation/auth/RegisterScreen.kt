package com.bakery.erp.presentation.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.remember

@Composable
fun RegisterScreen(
    onNavigateToLogin: () -> Unit,
    viewModel: AuthViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val isGoogleUser = state.user != null

    // Todos los remember deben declararse incondicionalmente (regla de Compose)
    var displayName by remember(state.user) { mutableStateOf(state.user?.displayName ?: "") }
    var orgName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordMismatch by remember { mutableStateOf(false) }
    var step by remember { mutableStateOf(0) } // solo usado en Path B (email)

    Box(
        modifier = Modifier.fillMaxSize(),
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
        if (isGoogleUser) {
            // ── PATH A: usuario ya autenticado con Google ────────────────────
            Text(
                text = "Crear nueva panadería",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Serás el propietario de la organización",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(32.dp))

            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it; viewModel.clearError() },
                label = { Text("Tu nombre completo") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = orgName,
                onValueChange = { orgName = it; viewModel.clearError() },
                label = { Text("Nombre de la panadería") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                isError = state.error != null
            )

            if (state.error != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = state.error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { viewModel.createOrg(orgName.trim(), displayName.trim()) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isLoading && orgName.isNotBlank() && displayName.isNotBlank()
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.height(18.dp)
                    )
                } else {
                    Text("Crear panadería")
                }
            }

        } else {
            // ── PATH B: nuevo usuario por email, flujo 2 pasos ──────────────
            Text(
                text = if (step == 0) "Crear cuenta" else "Crear nueva panadería",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (step == 0) "Paso 1 de 2 — Datos personales" else "Paso 2 de 2 — Tu panadería",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(32.dp))

            if (step == 0) {
                // Paso 0: datos del usuario
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it; viewModel.clearError() },
                    label = { Text("Tu nombre completo") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
                )
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
                        if (password != confirmPassword) { passwordMismatch = true; return@Button }
                        step = 1
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isLoading &&
                            displayName.isNotBlank() &&
                            email.isNotBlank() &&
                            password.isNotBlank() &&
                            confirmPassword.isNotBlank()
                ) {
                    Text("Siguiente")
                }
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onNavigateToLogin) {
                    Text("¿Ya tienes cuenta? Inicia sesión")
                }

            } else {
                // Paso 1: datos de la panadería
                OutlinedTextField(
                    value = orgName,
                    onValueChange = { orgName = it; viewModel.clearError() },
                    label = { Text("Nombre de la panadería") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    isError = state.error != null
                )

                if (state.error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = state.error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = {
                        viewModel.register(email.trim(), password, orgName.trim(), displayName.trim())
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isLoading && orgName.isNotBlank()
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.height(18.dp)
                        )
                    } else {
                        Text("Crear panadería")
                    }
                }
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = { step = 0; viewModel.clearError() }) {
                    Text("Atrás")
                }
            }
        }
    }
    }
}
