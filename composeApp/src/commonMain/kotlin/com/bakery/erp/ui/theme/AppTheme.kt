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

// BakeOS — Helvetia Blue palette.
// Fuente única de verdad de la marca. El código nuevo/editado debe importar `BrandColors`
// en vez de re-declarar literales `Color(0xFF...)` por pantalla (ver memoria
// feedback_ui_responsive_palette). La app SIEMPRE usa tema claro: no se define darkColorScheme.
object BrandColors {
    val Primary          = Color(0xFF005B8D) // Helvetia Blue — primario, headers
    val PrimaryDark      = Color(0xFF00486F) // hover / borde azul
    val PrimaryContainer = Color(0xFFDCEEFB) // contenedor azul claro
    val Accent           = Color(0xFFD9A441) // Honey Gold — acento (reemplaza el naranja quemado)
    val AccentDark       = Color(0xFF8A6420) // texto/borde acento oscuro legible
    val Ivory            = Color(0xFFFDFBF7) // fondo cálido
    val Sand             = Color(0xFFF5EFE6) // cards beige
    val TextSlate        = Color(0xFF1A2B38) // texto principal
    val MutedSlate       = Color(0xFF5E6E7A) // texto secundario
    val Outline          = Color(0xFFC8A882) // borde sutil café latte
    // Semánticos que NO cambian: success verde / error rojo se mantienen inline donde aplica.
}

private val BakeryLightColorScheme = lightColorScheme(
    primary            = BrandColors.Primary,
    onPrimary          = Color.White,
    primaryContainer   = BrandColors.PrimaryContainer,
    onPrimaryContainer = BrandColors.Primary,

    secondary            = BrandColors.Accent,
    onSecondary          = Color.White,
    secondaryContainer   = BrandColors.Sand,
    onSecondaryContainer = BrandColors.AccentDark,

    background       = BrandColors.Ivory,
    onBackground     = BrandColors.TextSlate,

    surface          = Color.White,
    onSurface        = BrandColors.TextSlate,
    surfaceVariant   = Color(0xFFF0EAE2),
    onSurfaceVariant = BrandColors.MutedSlate,

    outline = BrandColors.Outline
)

private val BakeryShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small      = RoundedCornerShape(10.dp),
    medium     = RoundedCornerShape(14.dp),
    large      = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp),
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
