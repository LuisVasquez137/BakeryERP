package com.bakery.erp.presentation.auth

import com.bakery.erp.ui.StatusBarIcons

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bakery.erp.ui.LoadingOverlay

@Composable
fun AuthChoiceScreen(
    onNavigateToRegister: () -> Unit,
    onNavigateToJoinOrg: () -> Unit,
    onNavigateToLogin: () -> Unit,
    viewModel: AuthViewModel
) {
    // Fondo claro arriba: íconos del sistema oscuros (al salir se vuelven blancos).
    StatusBarIcons(darkIcons = true)
    val state by viewModel.uiState.collectAsState()
    // Usuario ya autenticado (p.ej. con Google) pero sin organización → onboarding.
    val isOnboarding = state.user != null
    val name = state.user?.displayName

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
    Column(
        modifier = Modifier
            .widthIn(max = 440.dp)
            .fillMaxWidth()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (isOnboarding && !name.isNullOrBlank()) "¡Hola, $name!" else "BakeOS",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "¿Deseas crear una nueva panadería o unirte a una ya existente?",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(48.dp))

        Button(
            onClick = onNavigateToRegister,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Crear una nueva panadería")
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Serás el propietario y podrás invitar a tu equipo.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(24.dp))
        OutlinedButton(
            onClick = onNavigateToJoinOrg,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Unirme a una panadería existente")
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Ingresa el código de invitación que te compartieron.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(40.dp))
        // Onboarding (Google): la sesión ya está iniciada → ofrecer cerrar sesión,
        // no "iniciar sesión". Registro por email: volver al login.
        if (isOnboarding) {
            TextButton(onClick = { viewModel.signOut() }) {
                Text("Cerrar sesión")
            }
        } else {
            TextButton(onClick = onNavigateToLogin) {
                Text("¿Ya tienes cuenta? Inicia sesión")
            }
        }
    }

    // Overlay mientras se cierra la sesión / verifica el estado.
    LoadingOverlay(visible = state.isLoading)
    }
}
