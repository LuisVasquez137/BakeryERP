package com.bakery.erp.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.bakery.erp.ui.theme.BrandColors
import kotlin.math.PI
import kotlin.math.sin

private class ConfettiPiece(
    val x: Float, val phase: Float, val speed: Float, val sway: Float,
    val sizeDp: Float, val spin: Float, val colorIndex: Int, val isRect: Boolean
)

/**
 * Confeti que cae despacio y en bucle, con los colores de la marca. Para celebrar (el día del
 * cumpleaños); el resto del tiempo la tarjeta usa puntitos quietos. Las piezas salen de una
 * semilla fija: se ve igual cada vez y no cuesta nada recalcularlas.
 *
 * @param count piezas en pantalla. @param alpha opacidad: bajita detrás de texto.
 * @param periodMillis lo que tarda una pieza en cruzar de arriba abajo (más = más tranquilo).
 */
@Composable
fun FallingConfetti(modifier: Modifier, count: Int = 24, alpha: Float = 0.8f, periodMillis: Int = 6000) {
    val colors = listOf(BrandColors.Accent, BrandColors.ChartSky, BrandColors.Teal, BrandColors.AccentIcon, BrandColors.Primary)
    val pieces = remember(count) {
        var seed = 11
        fun next(): Float { seed = (seed * 1103515245 + 12345) and 0x7fffffff; return (seed % 1000) / 1000f }
        List(count) { i ->
            ConfettiPiece(
                x = next(), phase = next(), speed = 0.7f + next() * 0.6f, sway = 0.01f + next() * 0.03f,
                sizeDp = 3f + next() * 4f, spin = (next() - 0.5f) * 720f, colorIndex = i % colors.size, isRect = i % 3 != 0
            )
        }
    }
    val t by rememberInfiniteTransition(label = "confetti").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(periodMillis, easing = LinearEasing), RepeatMode.Restart),
        label = "confettiT"
    )
    Canvas(modifier) {
        pieces.forEach { p ->
            val progress = (p.phase + t * p.speed) % 1f
            // Entra desde arriba de la tarjeta y sale por abajo.
            val y = (progress * 1.2f - 0.1f) * size.height
            val x = (p.x + sin((progress * 2f * PI + p.phase * 6f).toFloat()) * p.sway) * size.width
            val s = p.sizeDp.dp.toPx()
            val c = colors[p.colorIndex].copy(alpha = alpha)
            rotate(p.spin * progress, pivot = Offset(x, y)) {
                if (p.isRect) drawRect(c, topLeft = Offset(x - s / 2, y - s / 4), size = Size(s, s / 2))
                else drawCircle(c, s / 2.5f, Offset(x, y))
            }
        }
    }
}
