package com.bakery.erp.model

import com.bakery.erp.domain.PriceSource
import com.bakery.erp.domain.groupListPrice
import com.bakery.erp.domain.resolveListPrices
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class ListPriceResolutionTest {

    private val frances = Product(id = "frances", name = "Francés", salePrice = 0.50)
    private val sheca = Product(id = "sheca", name = "Sheca", salePrice = 0.50)
    private val pastel = Product(id = "pastel", name = "Pastel", salePrice = 120.0)
    private val products = listOf(frances, sheca, pastel)
    private val byId = products.associateBy { it.id }

    private val group = POSCategory(
        id = "g50", name = "0.50", productIds = listOf("frances", "sheca"),
        sellAsGroup = true, groupUnitPrice = 0.50
    )

    @Test
    fun elPrecioFijoGanaAlPorcentaje() {
        val list = PriceList(id = "rev", name = "Revendedores", priceFactor = 0.80)
        val prices = resolveListPrices(
            list, listOf(ProductPrice(productId = "pastel", priceListId = "rev", unitPrice = 100.0)), products
        )
        assertEquals(100.0, prices.getValue("pastel").unitPrice)
        assertEquals(PriceSource.PRODUCT_OVERRIDE, prices.getValue("pastel").source)
        assertEquals(0.40, prices.getValue("frances").unitPrice)
        assertEquals(PriceSource.LIST_FACTOR, prices.getValue("frances").source)
    }

    @Test
    fun sinPorcentajeNiPrecioFijoNoHayPrecio() {
        val list = PriceList(id = "rev", name = "Revendedores", priceFactor = null)
        val prices = resolveListPrices(list, emptyList(), products)
        assertFalse(prices.getValue("frances").found)
        assertEquals(PriceSource.NONE, prices.getValue("frances").source)
    }

    @Test
    fun elPrecioSeRedondeaACentavos() {
        val list = PriceList(id = "rev", name = "Revendedores", priceFactor = 0.83)
        // 0.83 × 0.50 = 0.415
        val price = resolveListPrices(list, emptyList(), products).getValue("frances").unitPrice
        assertEquals(price, kotlin.math.round(price * 100) / 100)
    }

    @Test
    fun elGrupoTieneElPrecioDeSusPanesSiTodosCoinciden() {
        val list = PriceList(id = "rev", name = "Revendedores", priceFactor = 0.80)
        val prices = resolveListPrices(list, emptyList(), products)
        assertEquals(0.40, groupListPrice(group, byId, prices, list))
    }

    @Test
    fun siUnPanDelGrupoTienePrecioDistintoElGrupoNoSePuedeCobrarJunto() {
        val list = PriceList(id = "rev", name = "Revendedores", priceFactor = 0.80)
        val prices = resolveListPrices(
            list, listOf(ProductPrice(productId = "sheca", priceListId = "rev", unitPrice = 0.45)), products
        )
        assertNull(groupListPrice(group, byId, prices, list))
    }

    @Test
    fun siUnPanDelGrupoNoTienePrecioElGrupoTampoco() {
        val list = PriceList(id = "rev", name = "Revendedores", priceFactor = null)
        val prices = resolveListPrices(
            list, listOf(ProductPrice(productId = "frances", priceListId = "rev", unitPrice = 0.40)), products
        )
        assertNull(groupListPrice(group, byId, prices, list))
    }

    private val frances150 = Product(id = "frances150", name = "Francés grande", salePrice = 1.50)

    @Test
    fun elEscalonLeGanaAlPorcentaje() {
        val list = PriceList(
            id = "rev", name = "Revendedores", type = PriceListType.CUSTOM, priceFactor = 0.80,
            priceSteps = listOf(PriceStep(0.50, 0.40), PriceStep(1.50, 1.25))
        )
        val prices = resolveListPrices(list, emptyList(), products + frances150)
        assertEquals(0.40, prices.getValue("frances").unitPrice)
        assertEquals(1.25, prices.getValue("frances150").unitPrice)
        // El pastel no tiene escalón: cae al porcentaje.
        assertEquals(96.0, prices.getValue("pastel").unitPrice)
    }

    @Test
    fun elPrecioFijoDelProductoLeGanaAlEscalon() {
        val list = PriceList(id = "rev", name = "Revendedores", type = PriceListType.CUSTOM,
            priceSteps = listOf(PriceStep(1.50, 1.25)))
        val prices = resolveListPrices(
            list, listOf(ProductPrice(productId = "frances150", priceListId = "rev", unitPrice = 1.20)),
            products + frances150
        )
        assertEquals(1.20, prices.getValue("frances150").unitPrice)
        // Sin escalón ni porcentaje, el pastel no tiene precio.
        assertFalse(prices.getValue("pastel").found)
    }

    @Test
    fun elGrupoUsaElEscalon() {
        val list = PriceList(id = "rev", name = "Revendedores", type = PriceListType.CUSTOM,
            priceSteps = listOf(PriceStep(0.50, 0.40)))
        val prices = resolveListPrices(list, emptyList(), products)
        assertEquals(0.40, groupListPrice(group, byId, prices, list))
        // Grupo sin panes cargados: se resuelve con la regla de la lista sobre su precio.
        assertEquals(0.40, groupListPrice(group.copy(productIds = emptyList()), byId, prices, list))
    }
}
