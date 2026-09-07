package com.bakery.erp.presentation.production

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bakery.erp.util.fmt
import com.bakery.erp.util.fmtTrim
import com.bakery.erp.util.formatBakerWeight
import kotlinx.coroutines.launch
import com.bakery.erp.ui.theme.BrandColors.Primary as PrimaryBlue
import com.bakery.erp.ui.theme.BrandColors.Accent as OrangeAccent
import com.bakery.erp.ui.theme.BrandColors.Ivory
import com.bakery.erp.ui.theme.BrandColors

private fun formatWeight(grams: Double): String = formatBakerWeight(grams)

/** "≈ 6 u" / "≈ 10.6 u" — lo que el panadero cuenta, al lado de lo que pesa. */
private fun formatCount(qty: Double, unit: String): String {
    val label = if (unit == "docena") "doc" else "u"
    return "≈ ${qty.fmtTrim(1)} $label"
}

/** "28 g" → ("28", "g"). Separa el número de la unidad para poder darles
 *  estilos distintos (número grande y en negrita, unidad chica y suave) sin
 *  tener que separarlos con espacio extra — el contraste es de tipografía, no
 *  de distancia. */
private fun splitWeightParts(formatted: String): Pair<String, String> {
    val idx = formatted.lastIndexOf(' ')
    return if (idx <= 0) formatted to "" else formatted.substring(0, idx) to formatted.substring(idx + 1)
}


@Composable
fun ScaledRecipePanel(
    view: ScaledRecipeView,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null,
    headerExtra: @Composable ColumnScope.() -> Unit = {}
) {
    val sponge = view.sponge
    val hasSponge = sponge != null
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    Box(modifier = modifier.fillMaxWidth().background(Ivory)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Card de masa: arriba de todo, nombre en blanco y grande, libras
            // debajo más chico — es lo primero que el panadero necesita ver, antes
            // de la esponja o la tabla. ──────────────────────────────────────────
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = PrimaryBlue,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp).padding(end = if (onClose != null) 32.dp else 0.dp)
                    ) {
                        Text(
                            view.productName,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            "${formatWeight(view.totalDoughGrams)} de masa total  ·  ${view.totalDoughGrams.fmt(0)} g",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.82f),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    if (onClose != null) {
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(32.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
                        }
                    }
                }
            }

            // ── Esponja: debajo de la card de masa — es decisión del panadero, no
            // de la encargada, así que vive junto al resto de controles del lote. ─
            headerExtra()
            if (hasSponge && sponge != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = BrandColors.AccentDeep.copy(alpha = 0.10f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "Con esponja activa: ${sponge.preparationName}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = BrandColors.AccentDeep
                        )
                        Text(
                            "Usa ${formatWeight(sponge.gramsUsed)} de esponja. El sistema descuenta los ingredientes que ya trae.",
                            style = MaterialTheme.typography.labelSmall,
                            color = BrandColors.AccentDeep
                        )
                    }
                }
            }

            // ── Tabla de ingredientes: solo cantidad y nombre — zebra striping
            // para no perder la fila al bajar. ────────────────────────────────
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color.White,
                shadowElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    IngredientHeader()
                    HorizontalDivider(color = Color(0xFFE0E0E0))
                    view.rows.forEachIndexed { idx, row ->
                        IngredientRow(row = row, zebra = idx % 2 == 1)
                        if (idx < view.rows.lastIndex) {
                            HorizontalDivider(color = Color(0xFFF0F0F0))
                        }
                    }
                }
            }

            // ── Adornos ──────────────────────────────────────────────────────────
            if (view.decorations.isNotEmpty()) {
                Text(
                    "Adornos",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryBlue,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color.White,
                    shadowElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        view.decorations.forEachIndexed { idx, dec ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(dec.preparationName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    Text(
                                        "${dec.gramsPerUnit.fmt(1)} g/unidad",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.Gray
                                    )
                                }
                                Text(
                                    formatWeight(dec.totalGrams),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryBlue
                                )
                            }
                            if (idx < view.decorations.lastIndex) HorizontalDivider(color = Color(0xFFF0F0F0))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }

        // ── Indicador de scroll: aparece solo si la receta no cabe entera en
        // lo que se ve y todavía queda algo abajo; desaparece al llegar al
        // final. Tocarlo baja de una vez hasta el fondo. ─────────────────────
        val canScrollMore = scrollState.maxValue > 0 && scrollState.value < scrollState.maxValue - 4
        if (canScrollMore) {
            val infiniteTransition = rememberInfiniteTransition(label = "scrollHint")
            val bounce by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 7f,
                animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
                label = "scrollHintBounce"
            )
            Surface(
                onClick = { coroutineScope.launch { scrollState.animateScrollTo(scrollState.maxValue) } },
                shape = CircleShape,
                color = PrimaryBlue,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .size(46.dp)
                    .offset(y = bounce.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = "Sigue más receta abajo",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun IngredientHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF6F4EE))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "A pesar",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = PrimaryBlue,
            modifier = Modifier.width(118.dp)
        )
        Text(
            "Ingrediente",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = PrimaryBlue,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun IngredientRow(row: ScaledRecipeRow, zebra: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (zebra) Color(0xFFF6F6F4) else Color.Transparent)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Cantidad primero y más grande: es el dato que el panadero necesita
        // leer de un vistazo. Antes iba al final de la fila y en letra chica.
        Column(modifier = Modifier.width(118.dp)) {
            val (weightNumber, weightUnit) = splitWeightParts(formatWeight(row.finalGrams))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    weightNumber,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = PrimaryBlue,
                    modifier = Modifier.alignByBaseline()
                )
                if (weightUnit.isNotEmpty()) {
                    Spacer(Modifier.width(3.dp))
                    Text(
                        weightUnit,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = BrandColors.MutedSlate,
                        modifier = Modifier.alignByBaseline()
                    )
                }
            }
            if (row.gramsFromSponge > 0.0) {
                Text(
                    "receta ${row.totalGrams.fmt(0)}g − esp. ${row.gramsFromSponge.fmt(0)}g",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }
            // El huevo se cuenta, no se pesa: el peso es el dato exacto, esto es
            // la lectura práctica en la mesa de trabajo.
            if (row.countEquivalent != null && row.countEquivalent > 0.0) {
                Text(
                    formatCount(row.countEquivalent, row.countUnit),
                    style = MaterialTheme.typography.labelSmall,
                    color = BrandColors.AccentDeep
                )
            }
        }
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            // Espacio de ancho fijo para la estrella, siempre presente aunque no
            // sea base: así el nombre arranca en la misma columna en toda la
            // tabla, en vez de correrse a la derecha solo en las filas base.
            Box(modifier = Modifier.width(16.dp), contentAlignment = Alignment.Center) {
                if (row.isBase) {
                    Text("★", color = BrandColors.AccentDeep, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }
            // Negrita y tamaño uniforme para TODOS los ingredientes, no solo el
            // base — la estrella ya distingue el base, no hace falta que además
            // se vea distinto en tamaño.
            Text(
                row.ingredientName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = PrimaryBlue
            )
        }
    }
}
