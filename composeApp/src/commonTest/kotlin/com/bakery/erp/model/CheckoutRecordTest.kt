package com.bakery.erp.model

import kotlin.test.Test
import kotlin.test.assertEquals

class CheckoutRecordTest {

    @Test
    fun ventasDelMismoCobroSonUnSoloCobro() {
        val pan = Sale(id = "s1", source = SaleSource.POS, total = 10.0, checkoutId = "co1",
            checkoutTotal = 85.0, tenderedAmount = 100.0, changeAmount = 15.0,
            billValues = listOf(100.0), billCounts = listOf(1.0), createdAt = 2_000)
        val restaurante = Sale(id = "s2", source = SaleSource.COMANDAYA, total = 75.0,
            checkoutId = "co1", checkoutTotal = 85.0, tenderedAmount = 100.0, changeAmount = 15.0,
            collectedAt = 1_900, createdAt = 500)
        val anterior = Sale(id = "s0", source = SaleSource.POS, total = 5.0,
            tenderedAmount = 5.0, createdAt = 1_000)

        val result = checkoutsFromSales(listOf(anterior, pan, restaurante))

        assertEquals(2, result.size)
        assertEquals(85.0, result[0].total)
        assertEquals(15.0, result[0].change)
        assertEquals(listOf(100.0 to 1), result[0].bills)
        assertEquals(5.0, result[1].total)
    }

    @Test
    fun cuentaRecibidaDelMeseroNoEsUnCobroDeLaCaja() {
        val recibida = Sale(id = "r", source = SaleSource.COMANDAYA, total = 50.0, collectedAt = 10)
        assertEquals(0, checkoutsFromSales(listOf(recibida)).size)
    }
}
