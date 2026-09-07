package com.bakery.erp.ui

import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

@Composable
actual fun BiometricClockButton(
    isClockedIn: Boolean,
    isLoading: Boolean,
    onClockIn: () -> Unit,
    onClockOut: () -> Unit,
    modifier: Modifier
) {
    val context = LocalContext.current
    var errorMsg by remember { mutableStateOf<String?>(null) }

    fun authenticate(onSuccess: () -> Unit) {
        val activity = context as? FragmentActivity ?: run { onSuccess(); return }
        val executor = ContextCompat.getMainExecutor(context)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                errorMsg = null
                onSuccess()
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                    errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                    errorMsg = errString.toString()
                }
            }
            override fun onAuthenticationFailed() {
                errorMsg = "Huella no reconocida, intenta de nuevo"
            }
        }
        val prompt = BiometricPrompt(activity, executor, callback)
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(if (!isClockedIn) "Marcar Entrada" else "Marcar Salida")
            .setSubtitle("Verifica tu identidad con huella o rostro")
            .setNegativeButtonText("Cancelar")
            .build()
        prompt.authenticate(info)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = {
                errorMsg = null
                authenticate { if (!isClockedIn) onClockIn() else onClockOut() }
            },
            enabled = !isLoading,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isClockedIn) Color(0xFFB00020) else Color(0xFF1B5E20)
            ),
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = if (isClockedIn) Icons.Default.Logout else Icons.Default.Login,
                    contentDescription = null
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = if (isClockedIn) "Marcar Salida" else "Marcar Entrada",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.size(8.dp))
                Icon(
                    imageVector = Icons.Default.Fingerprint,
                    contentDescription = null
                )
            }
        }
        if (errorMsg != null) {
            Text(
                text = errorMsg!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
