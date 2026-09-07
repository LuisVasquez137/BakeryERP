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
    // ── Azul (estructura e identidad) ────────────────────────────────────────
    val Primary          = Color(0xFF005B8D) // Helvetia Blue — primario, headers
    val PrimaryDark      = Color(0xFF00486F) // hover / borde azul
    val PrimaryContainer = Color(0xFFDCEEFB) // contenedor azul claro

    // ── Acento (estado: "esto necesita tus ojos / está en curso") ────────────
    // REGLA: el acento SIEMPRE es relleno, nunca color de texto sobre blanco.
    // La tinta encima es azul (`OnAccent`) o blanca (sobre `AccentDeep`) — nunca café.
    // Cada par de abajo está verificado ≥4.5:1; ver BrandChipContrastTest.
    // Muestreado del croissant del logo (#FE7F03). Antes era #FF9800, medio tono más ámbar:
    // convivían dos naranjas de marca y el chip de avisos no era el del icono de la app.
    val Accent     = Color(0xFFFE7F03) // naranja de marca; SOLO relleno, lleva OnAccent encima
    val OnAccent   = Color(0xFF00344F) // azul marino: la tinta sobre Accent (5.1:1)
    val AccentDeep = Color(0xFFA85200) // naranja oscurecido para aguantar texto BLANCO (5.4:1)
    val AccentSoft = Color(0xFFFFF3E0) // relleno pálido; lleva Primary encima (6.7:1)
    // Naranja para OBJETOS GRÁFICOS (iconos, trazos): WCAG les pide 3:1, no el 4.5:1 del
    // texto, y ese margen es lo que permite subirle el tono. Es el MISMO hue del logo (29.6°)
    // bajado en luminosidad hasta el límite: `Accent` puro da 2.3:1 sobre AccentSoft, y contra
    // un fondo claro no hay forma de que ese naranja llegue a 3:1 (ni sobre blanco pasa de
    // 2.55:1). Así que el icono es lo más cerca del logo que se puede estar sin dejar de verse
    // en una tablet enharinada. NO usar como texto: para eso está AccentDeep.
    val AccentIcon = Color(0xFFDE6E01) // 3.0:1 sobre AccentSoft

    // ── Neutros ──────────────────────────────────────────────────────────────
    val Ivory     = Color(0xFFFDFBF7) // fondo cálido
    val Sand      = Color(0xFFF5EFE6) // cards beige
    val TextSlate = Color(0xFF1A2B38) // texto principal
    val MutedSlate = Color(0xFF5E6E7A) // texto secundario
    val Outline   = Color(0xFFC8A882) // borde sutil café latte
    // Semánticos que NO cambian: success verde / error rojo se mantienen inline donde aplica.
}

private val BakeryLightColorScheme = lightColorScheme(
    primary            = BrandColors.Primary,
    onPrimary          = Color.White,
    primaryContainer   = BrandColors.PrimaryContainer,
    onPrimaryContainer = BrandColors.Primary,

    // secondary lleva la version oscurecida porque Material pinta onSecondary encima:
    // con el naranja de marca el blanco daria 2.2:1 en cualquier componente por defecto.
    secondary            = BrandColors.AccentDeep,
    onSecondary          = Color.White,
    secondaryContainer   = BrandColors.AccentSoft,
    onSecondaryContainer = BrandColors.Primary,

    // El naranja de marca vive en tertiary, siempre con su tinta azul.
    tertiary             = BrandColors.Accent,
    onTertiary           = BrandColors.OnAccent,
    tertiaryContainer    = BrandColors.Sand,
    onTertiaryContainer  = BrandColors.Primary,

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
