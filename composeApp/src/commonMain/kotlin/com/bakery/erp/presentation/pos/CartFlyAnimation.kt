package com.bakery.erp.presentation.pos

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bakery.erp.ui.ProductImage
import com.bakery.erp.ui.theme.BrandColors
import kotlin.math.roundToInt
import kotlin.time.Clock

/**
 * "Vuelo al carrito": al agregar un producto, una miniatura sale de su ficha y viaja en arco
 * hasta el carrito —el panel de la derecha en horizontal, el ícono de arriba en vertical—, y al
 * llegar el renglón se ilumina un instante.
 *
 * No es adorno: en vertical el carrito no se ve, y sin una señal la cajera no sabe si el toque
 * entró, así que vuelve a tocar. Se mantiene sobrio a propósito (medio segundo, una curva
 * suave, sin rebotes) porque es una herramienta de trabajo que se usa cientos de veces al día.
 */
class CartFlightState {
    /** Dónde está cada ficha en pantalla, por clave (productId, "grp_<id>", "grpq_<n>"). */
    val sources = mutableStateMapOf<String, Rect>()
    var target by mutableStateOf<Rect?>(null)
    internal val flights = mutableStateListOf<CartFlight>()
    private var nextId = 0

    /** Cuántas llegaron — el ícono del carrito late con cada una. */
    var arrivals by mutableIntStateOf(0)
        private set
    /** Qué renglón iluminar y cuándo llegó. */
    var lastArrivedItem by mutableStateOf<String?>(null)
        private set
    var lastArrivedAt by mutableLongStateOf(0L)
        private set

    /**
     * Lanza el vuelo desde la ficha [sourceKey]. [itemId] es lo que identifica el renglón del
     * carrito que se ilumina (productId o "grp_<groupId>"). Si la ficha ya no está en pantalla
     * o no hay carrito visible, el renglón se ilumina igual, sin vuelo.
     */
    fun launch(sourceKey: String, itemId: String, imageUrl: String?, label: String) {
        val from = sources[sourceKey]
        val to = target
        if (from == null || to == null) { arrive(itemId); return }
        flights += CartFlight(nextId++, from.center, Offset(to.center.x, to.top + minOf(to.height, 120f) / 2f), itemId, imageUrl, label)
    }

    internal fun arrive(itemId: String) {
        arrivals++
        lastArrivedItem = itemId
        lastArrivedAt = Clock.System.now().toEpochMilliseconds()
    }
}

internal data class CartFlight(
    val id: Int,
    val from: Offset,
    val to: Offset,
    val itemId: String,
    val imageUrl: String?,
    val label: String
)

val LocalCartFlight = staticCompositionLocalOf<CartFlightState?> { null }

/** Registra la ficha como punto de salida; se olvida al salir de pantalla (grilla perezosa). */
@Composable
fun Modifier.cartFlightSource(key: String): Modifier {
    val state = LocalCartFlight.current ?: return this
    DisposableEffect(key) { onDispose { state.sources.remove(key) } }
    return this.onGloballyPositioned { state.sources[key] = it.boundsInWindow() }
}

/** Marca el carrito (panel o ícono) como destino del vuelo. */
@Composable
fun Modifier.cartFlightTarget(): Modifier {
    val state = LocalCartFlight.current ?: return this
    DisposableEffect(Unit) { onDispose { state.target = null } }
    return this.onGloballyPositioned { state.target = it.boundsInWindow() }
}

/** Capa encima de toda la pantalla donde se dibujan los vuelos. */
@Composable
fun CartFlightOverlay(state: CartFlightState) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInWindow() }) {
        state.flights.toList().forEach { f ->
            key(f.id) {
                FlyingItem(f, origin) {
                    state.flights.remove(f)
                    state.arrive(f.itemId)
                }
            }
        }
    }
}

// Sale rápido y se asienta despacio: se lee como "se fue para allá" y no como un rebote.
private val FlightEasing = CubicBezierEasing(0.3f, 0f, 0.2f, 1f)

@Composable
private fun FlyingItem(flight: CartFlight, origin: Offset, onDone: () -> Unit) {
    val progress = remember { Animatable(0f) }
    val density = LocalDensity.current
    val sizePx = with(density) { 56.dp.toPx() }
    val lift = with(density) { 90.dp.toPx() }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(durationMillis = 560, easing = FlightEasing))
        onDone()
    }
    val t = progress.value
    // Arco: el punto de control va por encima del más alto de los dos extremos.
    val control = Offset((flight.from.x + flight.to.x) / 2f, minOf(flight.from.y, flight.to.y) - lift)
    val u = 1 - t
    val p = flight.from * (u * u) + control * (2 * u * t) + flight.to * (t * t)
    val scale = 1f - 0.55f * t
    val alpha = if (t < 0.75f) 1f else (1f - (t - 0.75f) / 0.25f).coerceIn(0f, 1f)

    Box(
        Modifier
            .offset { IntOffset((p.x - origin.x - sizePx / 2).roundToInt(), (p.y - origin.y - sizePx / 2).roundToInt()) }
            .size(56.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }
            .shadow(8.dp, CircleShape)
            .clip(CircleShape)
            .background(BrandColors.Primary)
            .border(2.dp, Color.White, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (!flight.imageUrl.isNullOrBlank()) {
            ProductImage(flight.imageUrl, null, Modifier.fillMaxSize())
        } else {
            Text(flight.label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
        }
    }
}
