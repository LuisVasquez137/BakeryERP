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

// Bagueto — Helvetia Blue Palette
private val HelvetiaBlue  = Color(0xFF004F7C) // Primary brand color (slightly deeper)
private val WarmCream     = Color(0xFFFDFBF7) // Main background
private val CoffeeBrown   = Color(0xFF2E4A6E) // Dark marine accent
private val SandLight     = Color(0xFFF5EFE6) // Light beige containers
private val SlateText     = Color(0xFF1A2B38) // Primary dark text
private val MutedSlate    = Color(0xFF5E6E7A) // Secondary text
private val CafeLatte     = Color(0xFFC8A882) // Subtle warm border/outline

private val BakeryLightColorScheme = lightColorScheme(
    primary            = HelvetiaBlue,
    onPrimary          = Color.White,
    primaryContainer   = Color(0xFFE3F2FB),
    onPrimaryContainer = HelvetiaBlue,

    secondary            = CoffeeBrown,
    onSecondary          = Color.White,
    secondaryContainer   = SandLight,
    onSecondaryContainer = CoffeeBrown,

    background       = WarmCream,
    onBackground     = SlateText,

    surface          = Color.White,
    onSurface        = SlateText,
    surfaceVariant   = Color(0xFFF0EAE2),
    onSurfaceVariant = MutedSlate,

    outline = CafeLatte
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
