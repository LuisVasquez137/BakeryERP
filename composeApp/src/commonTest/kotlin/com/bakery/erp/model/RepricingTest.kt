package com.bakery.erp.model

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Lo que se prueba acá no son los números sino las dos reglas que, al romperse, convierten la
 * sugerencia en algo que el usuario empieza a cerrar sin leer: que nunca redondee hacia abajo
 * (se comería el margen) y que no grite por un aumento de cinco centavos.
 */
class RepricingTest {

    private fun assertMoney(expected: Double, actual: Double) =
        assertTrue(abs(expected - actual) < 0.001, "esperaba $expected y vino $actual")

    // ── Redondeo ─────────────────────────────────────────────────────────────

    @Test
    fun redondea_siempre_hacia_arriba_al_medio_quetzal() {
        assertMoney(12.00, roundUpToPriceStep(11.71))
        assertMoney(12.00, roundUpToPriceStep(11.51))
        assertMoney(11.50, roundUpToPriceStep(11.01))
        assertMoney(0.50, roundUpToPriceStep(0.01))
    }

    @Test
    fun un_precio_ya_exacto_no_salta_al_escalon_siguiente() {
        // Sin el épsilon, el error de coma flotante de haber dividido y multiplicado antes
        // empujaba 10.50 a 11.00 — subiendo medio quetzal sin ninguna razón.
        assertMoney(10.50, roundUpToPriceStep(10.50))
        assertMoney(12.00, roundUpToPriceStep(12.00))
        assertMoney(10.00, roundUpToPriceStep(10.0 * (8.0 / 8.0)))
    }

    // ── El caso normal ───────────────────────────────────────────────────────

    @Test
    fun conserva_el_margen_y_redondea() {
        // Lata a Q10.00 con costo Q7.00 = 30% de margen. El costo sube a Q8.20:
        // precio ideal 10 × (8.20/7.00) = 11.71 → Q12.00.
        val price = suggestedSalePrice(currentPrice = 10.0, oldCost = 7.0, newCost = 8.2)
        assertNotNull(price)
        assertMoney(12.00, price)
    }

    @Test
    fun el_margen_resultante_nunca_queda_por_debajo_del_original() {
        // Es la consecuencia de redondear hacia arriba, y es la razón de hacerlo así.
        val original = (10.0 - 7.0) / 10.0
        val nuevo = suggestedSalePrice(10.0, 7.0, 8.2)!!
        val margenNuevo = (nuevo - 8.2) / nuevo
        assertTrue(margenNuevo >= original, "el margen bajó de $original a $margenNuevo")
    }

    // ── El amortiguador ──────────────────────────────────────────────────────

    @Test
    fun un_aumento_de_centavos_no_dispara_nada() {
        // Costo 7.00 → 7.05: el precio ideal es 10.07. Sin amortiguador, redondear hacia
        // arriba propondría Q10.50: subir el precio 5% porque el costo subió 0.7%.
        assertNull(suggestedSalePrice(currentPrice = 10.0, oldCost = 7.0, newCost = 7.05))
    }

    @Test
    fun justo_en_el_limite_del_amortiguador_no_avisa() {
        // ideal - actual == 0.25 exacto: el amortiguador es estricto, no avisa.
        val newCost = 7.0 * (10.25 / 10.0)
        assertNull(suggestedSalePrice(currentPrice = 10.0, oldCost = 7.0, newCost = newCost))
    }

    @Test
    fun apenas_pasa_el_amortiguador_si_avisa() {
        val newCost = 7.0 * (10.30 / 10.0)
        val price = suggestedSalePrice(10.0, 7.0, newCost)
        assertNotNull(price)
        assertMoney(10.50, price)
    }

    // ── Cuándo no hay nada que proponer ──────────────────────────────────────

    @Test
    fun si_el_costo_baja_no_se_sugiere_nada() {
        // Bajar el precio de venta es una decisión comercial que no se automatiza.
        assertNull(suggestedSalePrice(10.0, 8.0, 7.0))
    }

    @Test
    fun si_el_costo_no_cambia_no_se_sugiere_nada() {
        assertNull(suggestedSalePrice(10.0, 7.0, 7.0))
    }

    @Test
    fun sin_precio_o_sin_costo_previo_no_hay_regla_de_tres() {
        // Un producto sin precio de venta todavía (recién creado desde la factura) no tiene
        // margen que conservar: no se puede inventar uno.
        assertNull(suggestedSalePrice(currentPrice = 0.0, oldCost = 7.0, newCost = 9.0))
        assertNull(suggestedSalePrice(currentPrice = 10.0, oldCost = 0.0, newCost = 9.0))
    }

    // ── Qué productos entran ─────────────────────────────────────────────────

    @Test
    fun el_pan_propio_queda_afuera() {
        // Su precio sale de la receta y del costeo, no de lo que costó el saco de harina.
        val pan = Product(id = "p", name = "Pirujo", salePrice = 2.0, isInHouseProduced = true)
        assertNull(repriceSuggestionFor(pan, oldCost = 1.0, newCost = 1.6))
    }

    @Test
    fun el_producto_respaldado_por_insumo_si_entra() {
        // La leche: su línea de compra es de tipo INSUMO porque `resalePurchaseLine` la
        // redirige, pero es un producto de reventa con precio en el mostrador. Filtrar por
        // tipo de línea la dejaría afuera justo a ella.
        val leche = Product(
            id = "leche", name = "Leche Foremost", salePrice = 8.5,
            isInHouseProduced = false, linkedIngredientId = "ing-leche"
        )
        val s = repriceSuggestionFor(leche, oldCost = 6.0, newCost = 6.8)
        assertNotNull(s)
        assertMoney(10.00, s.suggestedPrice)
        assertTrue(leche.isIngredientBacked)
    }

    // ── Lo que el diálogo muestra ────────────────────────────────────────────

    @Test
    fun la_sugerencia_explica_de_donde_sale_el_numero() {
        val s = RepriceSuggestion(
            productId = "x", productName = "Lata",
            oldCost = 7.0, newCost = 8.2, currentPrice = 10.0, suggestedPrice = 12.0
        )
        assertTrue(abs(s.costIncreasePct - 17.142) < 0.01, "costo +${s.costIncreasePct}%")
        assertTrue(abs(s.currentMarginPct - 18.0) < 0.01, "margen hoy ${s.currentMarginPct}%")
        assertTrue(abs(s.suggestedMarginPct - 31.666) < 0.01, "margen nuevo ${s.suggestedMarginPct}%")
        // Aplicar la sugerencia recupera margen; no aplicarla lo deja muy por debajo del 30%
        // que el producto tenía antes de que el proveedor subiera.
        assertTrue(s.suggestedMarginPct > s.currentMarginPct)
    }

    @Test
    fun redondear_hacia_arriba_deja_el_precio_en_un_numero_que_la_caja_maneja() {
        // Un precio torcido se endereza de paso: 11.30 con costo +20% da 13.56 → 14.00.
        val price = suggestedSalePrice(11.30, 7.0, 8.4)
        assertNotNull(price)
        assertEquals(0.0, price % PRICE_STEP, 0.001)
    }
}
