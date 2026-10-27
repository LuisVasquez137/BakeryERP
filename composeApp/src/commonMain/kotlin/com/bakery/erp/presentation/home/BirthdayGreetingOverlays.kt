package com.bakery.erp.presentation.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bakery.erp.ui.BrandHeaderBrush
import com.bakery.erp.ui.FallingConfetti
import com.bakery.erp.ui.theme.BrandColors
import kotlinx.coroutines.delay

/**
 * Lo que sale al entrar, en este orden: si soy el cumpleañero, quiénes me felicitaron; después,
 * uno por uno, los cumpleañeros de hoy a quienes todavía no felicité. Se pintan encima de todo
 * (como la bienvenida), no como diálogos: así no se adelantan al saludo del PIN.
 */
@Composable
fun BirthdayGreetingOverlays(viewModel: BirthdayGreetingViewModel) {
    val state by viewModel.uiState.collectAsState()
    val sent = state.justSentTo
    when {
        state.received.isNotEmpty() && state.me != null ->
            BirthdayCelebration(state.me!!, state.received.map { it.senderName to it.senderPhotoUrl },
                total = state.receivedTotal, onDone = viewModel::dismissReceived)
        sent != null -> SentConfirmation(sent, onDone = viewModel::clearJustSent)
        state.prompts.isNotEmpty() -> {
            val person = state.prompts.first()
            GreetPrompt(person, onGreet = { viewModel.send(person) }, onSkip = { viewModel.skip(person) })
        }
    }
}

/** Fondo oscurecido que se traga los toques. */
@Composable
private fun Scrim(content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center
    ) { content() }
}

/** Estilo Duolingo: el botón grande es felicitar; omitir casi no se ve. */
@Composable
private fun GreetPrompt(person: BirthdayPerson, onGreet: () -> Unit, onSkip: () -> Unit) {
    val pop = remember(person.employeeId) { Animatable(0.85f) }
    LaunchedEffect(person.employeeId) { pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
    val first = firstName(person.name)
    Scrim {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            modifier = Modifier.padding(24.dp).widthIn(max = 420.dp).fillMaxWidth().scale(pop.value)
        ) {
            Box {
                FallingConfetti(Modifier.matchParentSize(), count = 22, alpha = 0.55f)
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(contentAlignment = Alignment.BottomEnd) {
                        PersonPhoto(person.name, person.photoUrl, 96, ring = BrandColors.Accent)
                        Box(
                            Modifier.size(34.dp).clip(CircleShape).background(BrandColors.Accent),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Default.Cake, null, tint = BrandColors.OnAccent, modifier = Modifier.size(20.dp)) }
                    }
                    Text("¡Hoy es el cumpleaños de $first!", style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold, color = BrandColors.Primary, textAlign = TextAlign.Center)
                    Text("Envíale tus felicitaciones: le va a aparecer cuando entre a la app.",
                        style = MaterialTheme.typography.bodyMedium, color = BrandColors.MutedSlate, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(6.dp))
                    Button(
                        onClick = onGreet,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandColors.Accent, contentColor = BrandColors.OnAccent),
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Text("Felicitar a $first 🎉", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }
                    TextButton(onClick = onSkip) {
                        Text("Omitir", color = BrandColors.MutedSlate.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

/** "¡Enviado!": un check que salta y se va solo. */
@Composable
private fun SentConfirmation(person: BirthdayPerson, onDone: () -> Unit) {
    val scale = remember { Animatable(0.3f) }
    LaunchedEffect(person.employeeId) {
        scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
        delay(1100)
        onDone()
    }
    Scrim {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.scale(scale.value)) {
            Box(
                Modifier.size(96.dp).clip(CircleShape).background(BrandColors.Success),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(56.dp)) }
            Spacer(Modifier.height(14.dp))
            Text("¡Felicitación enviada a ${firstName(person.name)}!", color = Color.White,
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
    }
}

/** La celebración del cumpleañero: pantalla completa, confeti y quiénes lo felicitaron. */
@Composable
private fun BirthdayCelebration(
    me: GreetingPerson,
    senders: List<Pair<String, String>>,
    total: Int,
    onDone: () -> Unit
) {
    val pop = remember { Animatable(0.6f) }
    val fade = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        fade.animateTo(1f, tween(400))
        pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }
    Box(
        Modifier.fillMaxSize().background(BrandHeaderBrush)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center
    ) {
        FallingConfetti(Modifier.matchParentSize(), count = 70, alpha = 0.95f, periodMillis = 5000)
        Column(
            Modifier.padding(24.dp).widthIn(max = 520.dp).scale(pop.value),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier.size(84.dp).clip(CircleShape).background(BrandColors.Accent),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.Cake, null, tint = BrandColors.OnAccent, modifier = Modifier.size(48.dp)) }
            Text("¡Feliz cumpleaños, ${firstName(me.name)}!", color = Color.White,
                style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
            // Fotos encimadas de quienes felicitaron (las nuevas).
            val shown = senders.take(6)
            Box(Modifier.width((56 + (shown.size - 1) * 38).dp).height(56.dp)) {
                shown.forEachIndexed { i, (name, photo) ->
                    Box(Modifier.offset(x = (i * 38).dp)) { PersonPhoto(name, photo, 56, ring = Color.White) }
                }
            }
            Text(
                joinNames(senders.map { firstName(it.first) }) +
                    if (senders.size == 1) " te envió felicitaciones" else " te enviaron felicitaciones",
                color = Color.White, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center
            )
            if (total > senders.size) {
                Text("Ya van $total felicitaciones hoy", color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onDone,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandColors.Accent, contentColor = BrandColors.OnAccent),
                modifier = Modifier.widthIn(min = 220.dp).height(52.dp)
            ) { Text("¡Gracias!", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium) }
        }
    }
}
