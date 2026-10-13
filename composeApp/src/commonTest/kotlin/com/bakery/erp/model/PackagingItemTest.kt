package com.bakery.erp.model

import com.bakery.erp.domain.report.salesSummaryFromDaily
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PackagingItemTest {

    @Test
    fun aValidItemHasNoProblem() {
        assertNull(packagingItemProblem(PackagingItem(name = "Bolsa", salePrice = 0.25)))
    }

    @Test
    fun nameAndPriceAreRequired() {
        assertNotNull(packagingItemProblem(PackagingItem(name = "  ", salePrice = 0.25)))
        assertNotNull(packagingItemProblem(PackagingItem(name = "Bolsa", salePrice = 0.0)))
        assertNotNull(packagingItemProblem(PackagingItem(name = "x".repeat(PACKAGING_NAME_MAX_CHARS + 1), salePrice = 1.0)))
    }

    private val withBag = Sale(id = "a", packagingUnits = 2.0, packagingAmount = 0.50)
    private val withoutBag = Sale(id = "b")
    private val restaurant = Sale(id = "c", salesChannel = SalesChannel.RESTAURANT)

    @Test
    fun filterSplitsSalesByWhetherTheBagWasCharged() {
        val sales = listOf(withBag, withoutBag, restaurant)
        assertEquals(listOf("a", "b", "c"), sales.filterByPackaging(PackagingFilter.ALL).map { it.id })
        assertEquals(listOf("a"), sales.filterByPackaging(PackagingFilter.WITH).map { it.id })
        // El restaurante nunca cobra bolsa en caja: no cuenta como "sin empaque".
        assertEquals(listOf("b"), sales.filterByPackaging(PackagingFilter.WITHOUT).map { it.id })
    }

    @Test
    fun packagingStaysOutOfTopProductsAndUnitsByProduct() {
        val bag = packagingSaleKey("bolsa")
        val summary = salesSummaryFromDaily(listOf(
            SalesDaily(
                branchId = "s1", businessDate = "2026-10-12",
                revenue = 10.5, transactionCount = 2,
                productName = mapOf("p1" to "Champurrada", bag to "Bolsa"),
                productQty = mapOf("p1" to 5.0, bag to 2.0),
                productRevenue = mapOf("p1" to 10.0, bag to 0.5)
            )
        ))
        assertEquals(listOf("p1"), summary.topProducts.map { it.productId })
        assertEquals(setOf("p1"), summary.unitsByProduct.keys)
        assertEquals(1, summary.packagingSales.size)
        assertEquals(2.0, summary.packagingSales.single().quantity)
        assertTrue(isPackagingSaleKey(bag))
    }
}
