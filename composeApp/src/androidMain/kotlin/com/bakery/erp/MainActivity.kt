package com.bakery.erp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.bakery.erp.navigation.PendingDeepLink

class MainActivity : ComponentActivity() {

    // El resultado no se usa: si el usuario dice que no, los avisos siguen
    // llegando a la campana de la app. El push es un extra, no un requisito.
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Debe ir ANTES de super.onCreate(): el splash nativo solo muestra el fondo ivory
        // un instante; el logo completo lo enseña el SplashScreen de Compose.
        installSplashScreen()
        // Íconos de la barra de estado blancos por defecto: casi toda la app tiene el
        // encabezado azul detrás. Con el estilo automático salían negros (tema claro).
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        super.onCreate(savedInstanceState)

        askForNotificationPermissionIfNeeded()
        captureNotificationTarget(intent)

        setContent {
            App()
        }
    }

    /** App ya abierta en background: el tap llega por acá, no por onCreate. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        captureNotificationTarget(intent)
    }

    /**
     * Rescata el destino que viajó en el `data` del push. Al tocar la
     * notificación con la app cerrada, Android lanza esta activity con esos
     * campos como extras del intent; el grafo de navegación todavía no existe,
     * así que se deja en [PendingDeepLink] para que lo consuma cuando arranque.
     */
    private fun captureNotificationTarget(intent: Intent?) {
        val extras = intent?.extras ?: return
        val targetScreen = extras.getString("targetScreen").orEmpty()
        if (targetScreen.isEmpty() || targetScreen == "NONE") return
        PendingDeepLink.set(targetScreen, extras.getString("targetId").orEmpty())
    }

    /** Android 13+ exige permiso en runtime para mostrar notificaciones. */
    private fun askForNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            runCatching {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}