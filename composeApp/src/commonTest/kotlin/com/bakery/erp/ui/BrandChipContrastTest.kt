package com.bakery.erp.ui

import androidx.compose.ui.graphics.Color
import com.bakery.erp.ui.theme.BrandColors
import com.bakery.erp.ui.theme.ChipEmphasis
import com.bakery.erp.ui.theme.ChipTone
import com.bakery.erp.ui.theme.chipColors
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * El acento dorado se usaba como color de TEXTO sobre blanco y daba 2.2:1 — muy por
 * debajo del 4.5:1 legible. En una tablet enharinada bajo la luz de una panadería ese
 * texto simplemente no está. Este test es el guardarraíl para que no vuelva: cualquier
 * par (fondo, tinta) que alguien agregue a la paleta tiene que pasar por aquí.
 *
 * Se exige 4.5:1 (texto normal) y no 3:1 (texto grande) a propósito: los chips van en
 * `labelSmall` (11sp), que no califica como texto grande por más negrita que lleve.
 */
class BrandChipContrastTest {

    /** Luminancia relativa WCAG 2.x. */
    private fun luminance(c: Color): Double {
        fun channel(v: Float): Double {
            val s = v.toDouble()
            return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)
    }

    private fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    @Test
    fun everyChipPairIsLegible() {
        val failures = mutableListOf<String>()
        for (tone in ChipTone.entries) {
            for (emphasis in ChipEmphasis.entries) {
                val (bg, fg) = chipColors(tone, emphasis)
                val ratio = contrast(bg, fg)
                if (ratio < 4.5) failures += "$tone/$emphasis = ${(ratio * 100).toInt() / 100.0}:1"
            }
        }
        assertTrue(failures.isEmpty(), "Pares de chip por debajo de 4.5:1 -> $failures")
    }

    /**
     * `AccentIcon` es el naranja de los iconos: los objetos gráficos piden 3:1, no el 4.5:1
     * del texto, y ese margen es lo que lo mantiene naranja en vez de marrón. El test fija
     * las dos mitades de la regla — alcanza para un icono y NO para texto — que es
     * exactamente por qué existe separado de `AccentDeep`.
     */
    @Test
    fun accentIconClearsTheGraphicThresholdButNotTheTextOne() {
        val onSoft = contrast(BrandColors.AccentIcon, BrandColors.AccentSoft)
        assertTrue(onSoft >= 3.0, "AccentIcon sobre AccentSoft debe pasar 3:1, da $onSoft")
        assertTrue(
            onSoft < 4.5,
            "Si AccentIcon pasara 4.5:1, AccentDeep deja de tener razon de ser"
        )
    }

    /**
     * El naranja de marca sólo es válido como relleno. Si alguien lo usa como texto sobre
     * blanco o marfil vuelve el bug original, así que se deja constancia del número.
     */
    @Test
    fun accentIsUnusableAsTextAndThatIsWhyItIsFillOnly() {
        assertTrue(
            contrast(BrandColors.Accent, Color.White) < 4.5,
            "Si Accent llegara a pasar como texto sobre blanco, revisar esta regla"
        )
        assertTrue(
            contrast(BrandColors.Accent, BrandColors.OnAccent) >= 4.5,
            "Accent debe ser legible con su tinta OnAccent"
        )
        assertTrue(
            contrast(BrandColors.AccentDeep, Color.White) >= 4.5,
            "AccentDeep existe exactamente para aguantar texto blanco"
        )
        assertTrue(
            contrast(BrandColors.AccentSoft, BrandColors.Primary) >= 4.5,
            "El chip palido lleva tinta azul de marca"
        )
    }
}
