package com.bakery.erp.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Bakery Boutique Palette
private val DeepSeaBlue    = Color(0xFF123456) // Tu color elegido
private val WheatLight     = Color(0xFFF7F2E9) // Fondo Trigo muy claro y limpio
private val BurntOrange = Color(0xFFCC5500) // El nuevo protagonista
private val WheatDeep      = Color(0xFFE9DCC9) // Trigo para contenedores (Adiós al morado)
private val DeepSeaLow     = Color(0xFF345678) // Versión más clara para textos secundarios
private val HoneyAccent    = Color(0xFFC27A3F) // Acento para botones especiales
private val WarmIvory   = Color(0xFFFDFBF7)

private val BakeryLightColorScheme = lightColorScheme(
    primary            = DeepSeaBlue,
    onPrimary          = Color.White,
    primaryContainer   = WheatLight,
    onPrimaryContainer = DeepSeaBlue,

    secondary            = BurntOrange,
    onSecondary          = Color.White,
    secondaryContainer   = Color(0xFFFDEFD9),
    onSecondaryContainer = Color(0xFF8D5A2E),

    background       = WarmIvory,
    onBackground     = DeepSeaBlue,

    surface          = Color.White, // Las cards blancas resaltan sobre el trigo
    onSurface        = DeepSeaBlue,
    surfaceVariant   = Color(0xFFEEE4D3),
    onSurfaceVariant = DeepSeaLow,

    outline = Color(0xFFB4A089)
)
private val BakeryShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small      = RoundedCornerShape(10.dp),
    medium     = RoundedCornerShape(14.dp),
    large      = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val BakeryTypography = Typography(
    displayLarge   = TextStyle(fontWeight = FontWeight.Light,    fontSize = 57.sp, letterSpacing = (-0.25).sp),
    displayMedium  = TextStyle(fontWeight = FontWeight.Light,    fontSize = 45.sp),
    displaySmall   = TextStyle(fontWeight = FontWeight.Normal,   fontSize = 36.sp),
    headlineLarge  = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 32.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 28.sp),
    headlineSmall  = TextStyle(fontWeight = FontWeight.Medium,   fontSize = 24.sp),
    titleLarge     = TextStyle(fontWeight = FontWeight.Medium,   fontSize = 22.sp),
    titleMedium    = TextStyle(fontWeight = FontWeight.Medium,   fontSize = 16.sp, letterSpacing = 0.15.sp),
    titleSmall     = TextStyle(fontWeight = FontWeight.Medium,   fontSize = 14.sp, letterSpacing = 0.1.sp),
    bodyLarge      = TextStyle(fontWeight = FontWeight.Normal,   fontSize = 16.sp, letterSpacing = 0.5.sp),
    bodyMedium     = TextStyle(fontWeight = FontWeight.Normal,   fontSize = 14.sp, letterSpacing = 0.25.sp),
    bodySmall      = TextStyle(fontWeight = FontWeight.Normal,   fontSize = 12.sp, letterSpacing = 0.4.sp),
    labelLarge     = TextStyle(fontWeight = FontWeight.Medium,   fontSize = 14.sp, letterSpacing = 0.1.sp),
    labelMedium    = TextStyle(fontWeight = FontWeight.Medium,   fontSize = 12.sp, letterSpacing = 0.5.sp),
    labelSmall     = TextStyle(fontWeight = FontWeight.Medium,   fontSize = 11.sp, letterSpacing = 0.5.sp),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BakeryLightColorScheme,
        shapes      = BakeryShapes,
        typography  = BakeryTypography,
        content     = content
    )
}
