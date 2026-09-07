package com.bakery.erp.presentation.splash

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import bakeryerp.composeapp.generated.resources.Res
import bakeryerp.composeapp.generated.resources.bakeos_logo
import com.bakery.erp.ui.StatusBarIcons
import com.bakery.erp.ui.theme.BrandColors
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource

/**
 * Splash de marca en Compose: muestra el logo horizontal completo sobre fondo ivory.
 * Funciona igual en Android, web e iOS (el logo horizontal NO se recorta, a diferencia del
 * splash nativo de Android 12+ que enmascara el icono a un círculo).
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    // Fondo claro → íconos del sistema oscuros mientras se ve el splash.
    StatusBarIcons(darkIcons = true)

    LaunchedEffect(Unit) {
        delay(1500)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BrandColors.Ivory),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(Res.drawable.bakeos_logo),
            contentDescription = "BakeOS",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .widthIn(max = 320.dp)
                .padding(24.dp)
        )
    }
}
