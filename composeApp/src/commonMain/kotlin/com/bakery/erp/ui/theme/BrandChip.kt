package com.bakery.erp.ui.theme

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Significado del chip, no su color. Quien lo usa declara QUÉ está diciendo y la paleta
 * decide cómo se ve — antes cada pantalla elegía su propio hex y el mismo estado salía
 * de dos colores distintos en dos listas.
 *
 * `ATTENTION` es el naranja: "esto necesita tus ojos / está en curso". Por eso el naranja
 * no se usa decorativo — si todo fuera naranja, el naranja dejaría de avisar.
 */
enum class ChipTone { NEUTRAL, INFO, PROGRESS, ATTENTION, SUCCESS, DANGER, BRAND }

/** `SOLID` grita, `SOFT` acompaña. La mayoría de los estados de lista van `SOFT`. */
enum class ChipEmphasis { SOFT, SOLID }

/**
 * Par (fondo, tinta) de un chip. Lógica pura, sin Compose, para poder verificar el
 * contraste en un test (`BrandChipContrastTest`) en vez de a ojo.
 *
 * REGLA DE LA PALETA: el naranja es SIEMPRE relleno, nunca color de texto. La tinta
 * encima es azul (`OnAccent` sobre `Accent`) o blanca (sobre `AccentDeep`, que es el
 * naranja oscurecido justo para eso). Ningún par baja de 4.5:1 — los chips van en
 * `labelSmall`/`labelMedium` (11-12sp), que no califican como "texto grande" y por lo
 * tanto no pueden acogerse al umbral relajado de 3:1.
 */
fun chipColors(tone: ChipTone, emphasis: ChipEmphasis): Pair<Color, Color> =
    when (emphasis) {
        ChipEmphasis.SOFT -> when (tone) {
            ChipTone.NEUTRAL   -> Color(0xFFEDEFF1) to BrandColors.MutedSlate
            ChipTone.INFO      -> BrandColors.PrimaryContainer to BrandColors.Primary
            ChipTone.PROGRESS  -> Color(0xFFF3E5F5) to Color(0xFF6A1B9A)
            ChipTone.ATTENTION -> BrandColors.AccentSoft to BrandColors.Primary
            ChipTone.SUCCESS   -> Color(0xFFE8F5E9) to Color(0xFF1B5E20)
            ChipTone.DANGER    -> Color(0xFFFFEBEE) to Color(0xFFB3261E)
            ChipTone.BRAND     -> BrandColors.Sand to BrandColors.Primary
        }
        ChipEmphasis.SOLID -> when (tone) {
            ChipTone.NEUTRAL   -> BrandColors.MutedSlate to Color.White
            ChipTone.INFO      -> BrandColors.Primary to Color.White
            ChipTone.PROGRESS  -> Color(0xFF6A1B9A) to Color.White
            ChipTone.ATTENTION -> BrandColors.AccentDeep to Color.White
            ChipTone.SUCCESS   -> Color(0xFF2E7D32) to Color.White
            ChipTone.DANGER    -> Color(0xFFC62828) to Color.White
            ChipTone.BRAND     -> BrandColors.Accent to BrandColors.OnAccent
        }
    }

/**
 * El chip de estado de toda la app. Antes había ~20 `StatusChip`/`Badge`/`Pill` privados,
 * cada uno con su propio `Surface` y sus propios literales: era la razón estructural por
 * la que el color quedaba disparejo entre pantallas.
 *
 * El texto va SemiBold siempre — es parte del contrato de contraste, no decoración.
 */
@Composable
fun BrandChip(
    text: String,
    tone: ChipTone,
    emphasis: ChipEmphasis = ChipEmphasis.SOFT,
    modifier: Modifier = Modifier
) {
    val (bg, fg) = chipColors(tone, emphasis)
    Surface(modifier = modifier, shape = RoundedCornerShape(50), color = bg) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
