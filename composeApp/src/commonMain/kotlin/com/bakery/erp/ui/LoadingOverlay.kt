package com.bakery.erp.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bakery.erp.ui.theme.BrandColors

/**
 * Overlay de carga a pantalla completa con la identidad de BakeOS.
 *
 * Cubre toda la pantalla con un velo ivory casi opaco, un loader animado (dos arcos
 * girando en sentidos opuestos — azul Helvetia y dorado Honey — con un halo que late)
 * y un texto "Cargando datos…" con puntos animados. Consume los toques para bloquear
 * la UI de abajo mientras dura la operación.
 *
 * Todas las animaciones usan APIs de `animation.core` compatibles con KMP/JS.
 */
@Composable
fun LoadingOverlay(
    visible: Boolean,
    message: String = "Procesando...",
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(220)),
        exit = fadeOut(animationSpec = tween(220)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BrandColors.Ivory.copy(alpha = 0.94f))
                // Consumir gestos para que la pantalla quede "bloqueada" durante la carga.
                .pointerInput(Unit) { detectTapGestures { } },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                BakeryLoader()
                Spacer(Modifier.height(24.dp))
                LoadingText(message)
            }
        }
    }
}

@Composable
private fun BakeryLoader(diameter: Dp = 76.dp) {
    val transition = rememberInfiniteTransition(label = "loader")
    val angleOuter by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
        label = "outer"
    )
    val angleInner by transition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing)),
        label = "inner"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.82f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(diameter)) {
        // Halo dorado que late detrás de los arcos.
        Box(
            Modifier
                .size(diameter)
                .scale(pulse)
                .background(BrandColors.Accent.copy(alpha = 0.14f), shape = CircleShape)
        )
        Canvas(Modifier.size(diameter)) {
            val d = size.minDimension
            val strokeW = d * 0.09f
            val stroke = Stroke(width = strokeW, cap = StrokeCap.Round)

            // Arco exterior — azul Helvetia.
            val inset = strokeW / 2f
            val outerSize = Size(d - strokeW, d - strokeW)
            rotate(angleOuter) {
                drawArc(
                    color = BrandColors.Primary,
                    startAngle = 0f,
                    sweepAngle = 100f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = outerSize,
                    style = stroke
                )
            }

            // Arco interior — dorado Honey, radio menor y sentido opuesto.
            val innerInset = strokeW * 2.4f
            val innerSize = Size(d - innerInset * 2f, d - innerInset * 2f)
            rotate(angleInner) {
                drawArc(
                    color = BrandColors.Accent,
                    startAngle = 40f,
                    sweepAngle = 120f,
                    useCenter = false,
                    topLeft = Offset(innerInset, innerInset),
                    size = innerSize,
                    style = stroke
                )
            }
        }
    }
}

@Composable
private fun LoadingText(message: String) {
    val transition = rememberInfiniteTransition(label = "dots")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 3.99f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)),
        label = "dots"
    )
    val dots = ".".repeat(progress.toInt().coerceIn(0, 3))
    Text(
        text = message + dots,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = BrandColors.Primary
    )
}
