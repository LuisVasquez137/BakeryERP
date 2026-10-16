package com.bakery.erp.presentation.operator

import com.bakery.erp.model.dayGreeting

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bakery.erp.model.OperatorSession
import com.bakery.erp.model.welcomeHeadline
import com.bakery.erp.ui.ProductImage
import com.bakery.erp.ui.theme.BrandColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * Saludo de la primera vez del día que alguien entra con su PIN. Tapa el Home unos segundos
 * y se va solo; tocarlo lo cierra antes (a quien tiene prisa no se le hace esperar).
 */
@Composable
fun WelcomeOverlay(session: OperatorSession, onFinished: () -> Unit) {
    val backdrop = remember { Animatable(0f) }
    val avatarScale = remember { Animatable(0.4f) }
    val avatarAlpha = remember { Animatable(0f) }
    val headline = remember { Animatable(0f) }
    val name = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var leaving by remember { mutableStateOf(false) }

    fun leave() {
        if (leaving) return
        leaving = true
        scope.launch {
            backdrop.animateTo(0f, tween(350))
            onFinished()
        }
    }

    LaunchedEffect(session.employeeId) {
        launch { backdrop.animateTo(1f, tween(300)) }
        launch { avatarAlpha.animateTo(1f, tween(300, delayMillis = 120)) }
        launch {
            avatarScale.animateTo(
                1f,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
            )
        }
        launch { headline.animateTo(1f, tween(450, delayMillis = 380, easing = FastOutSlowInEasing)) }
        launch { name.animateTo(1f, tween(450, delayMillis = 620, easing = FastOutSlowInEasing)) }
        delay(3200)
        leave()
    }

    val firstName = session.name.trim().split(" ").firstOrNull().orEmpty().ifBlank { session.name }
    val dayGreeting = remember {
        dayGreeting(Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).hour)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(backdrop.value)
            .background(Brush.verticalGradient(listOf(BrandColors.Primary, Color(0xFF003E61))))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = ::leave
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(132.dp)
                    .scale(avatarScale.value)
                    .alpha(avatarAlpha.value)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.15f))
                    .border(4.dp, BrandColors.Accent, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (session.photoUrl.isNotBlank()) {
                    ProductImage(
                        imageUrl = session.photoUrl,
                        contentDescription = "Foto de ${session.name}",
                        modifier = Modifier.size(132.dp)
                    )
                } else {
                    Text(
                        firstName.firstOrNull()?.uppercase() ?: "",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.displayMedium
                    )
                }
            }
            Spacer(Modifier.height(28.dp))
            Text(
                welcomeHeadline(session.gender),
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer {
                    alpha = headline.value
                    translationY = (1f - headline.value) * 40f
                }
            )
            Spacer(Modifier.height(6.dp))
            Text(
                session.name,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer {
                    alpha = name.value
                    translationY = (1f - name.value) * 40f
                }
            )
            Spacer(Modifier.height(18.dp))
            Text(
                "$dayGreeting, que tengas un buen turno",
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(name.value)
            )
        }
    }
}
