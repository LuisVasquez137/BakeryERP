package com.bakery.erp.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * El número que no puede salir mal es la conversión caja → unidades: si "1 caja" entrara como
 * 1 lata, el POS avisaría sobreventa desde la segunda y el costo por unidad saldría 24 veces
 * más caro. Lo segundo es que ningún producto viejo desaparezca del catálogo de su proveedor.
 */
class ResalePurchaseFormatTest {

    private val cajaDistribuidora = PurchaseFormat(
        id = "f1", description = "Caja 24 latas", unit = "unidad",
        supplierId = "coca", supplierName = "Distribuidora", unitsPerPackage = 24.0,
        packageCost = 120.0, isDefault = true
    )
    private val sixPackMayorista = PurchaseFormat(
        id = "f2", description = "Six pack", unit = "unidad",
        supplierId = "mayorista", supplierName = "Mayorista", unitsPerPackage = 6.0,
        packageCost = 33.0
    )
    private val lata = Product(
        id = "p1", name = "Coca-Cola lata", unit = "unidad", isInHouseProduced = false,
        purchaseFormats = listOf(cajaDistribuidora, sixPackMayorista)
    )

    @Test
    fun unaCajaEntraComoSusUnidadesConCostoUnitario() {
        val line = resalePurchaseLine(lata, cajaDistribuidora, quantity = 2.0, price = 120.0)
        assertEquals(48.0, line.quantityReceived)
        assertEquals(2.0, line.packagesReceived)
        assertEquals(5.0, line.unitCost)
        assertEquals(240.0, line.lineTotal)
        assertEquals("f1", line.formatId)
        assertEquals(PurchaseLineKind.RESALE_PRODUCT, line.lineKind)
    }

    // ── Producto que vende del stock de un insumo (la leche, el huevo) ──────

    private val leche = Ingredient(
        id = "ing-leche", name = "Leche", allowedUnits = listOf("lt"),
        usage = IngredientUsage.PRODUCTION
    )
    private val cartonDoceLitros = PurchaseFormat(
        id = "f3", description = "Caja de 12", unit = "unidad",
        supplierId = "lacteos", supplierName = "Lacteos", unitsPerPackage = 12.0,
        packageCost = 144.0, isDefault = true
    )
    private val lecheProducto = Product(
        id = "p-leche", name = "Leche 1 lt", unit = "unidad", isInHouseProduced = false,
        linkedIngredientId = "ing-leche", linkedIngredientName = "Leche",
        ingredientUnitsPerSale = 1.0,
        purchaseFormats = listOf(cartonDoceLitros)
    )

    @Test
    fun productoRespaldadoPorInsumoEntraAlInventarioDelInsumo() {
        val line = resalePurchaseLine(lecheProducto, cartonDoceLitros, quantity = 2.0, price = 144.0, linkedIngredient = leche)
        // El destino cambia...
        assertEquals(PurchaseLineKind.INGREDIENT, line.lineKind)
        assertEquals("ing-leche", line.ingredientId)
        assertEquals("lt", line.ingredientUnit)
        assertEquals(24.0, line.quantityReceived)
        assertEquals(12.0, line.unitCost)
        // ...pero el papel no: el importe y la presentación se conservan.
        assertEquals(288.0, line.lineTotal)
        assertEquals("f3", line.formatId)
        assertEquals("p-leche", line.productId)
        assertTrue(line.isRedirectedToIngredient)
    }

    @Test
    fun laVentaPorDocenaConvierteAUnidadesDelInsumo() {
        val huevoIng = Ingredient(id = "ing-huevo", name = "Huevo", allowedUnits = listOf("unidad"))
        val docena = Product(
            id = "p-huevo", name = "Huevo docena", unit = "docena", isInHouseProduced = false,
            linkedIngredientId = "ing-huevo", linkedIngredientName = "Huevo",
            ingredientUnitsPerSale = 12.0
        )
        val line = resalePurchaseLine(docena, null, quantity = 5.0, price = 24.0, linkedIngredient = huevoIng)
        assertEquals(60.0, line.quantityReceived)   // 5 docenas = 60 huevos
        assertEquals(2.0, line.unitCost)            // Q120 / 60
        assertEquals(120.0, line.lineTotal)
    }

    @Test
    fun sinInsumoVinculadoNadaCambia() {
        val line = resalePurchaseLine(lata, cajaDistribuidora, quantity = 1.0, price = 120.0, linkedIngredient = leche)
        assertEquals(PurchaseLineKind.RESALE_PRODUCT, line.lineKind)
        assertFalse(line.isRedirectedToIngredient)
    }

    @Test
    fun elPrecioDeLaPresentacionSeActualizaAunqueLaLineaSeHayaRedirigido() {
        val line = resalePurchaseLine(lecheProducto, cartonDoceLitros, quantity = 1.0, price = 150.0, linkedIngredient = leche)
        val updated = lecheProducto.formatsWithPaidPrices(listOf(line))
        assertEquals(150.0, updated?.first()?.packageCost)
    }

    @Test
    fun porUnidadSueltaElPrecioEsDeUnaUnidad() {
        val line = resalePurchaseLine(lata, null, quantity = 6.0, price = 5.5)
        assertEquals(6.0, line.quantityReceived)
        assertEquals(5.5, line.unitCost)
        assertEquals(33.0, line.lineTotal)
        assertEquals("", line.formatId)
    }

    @Test
    fun unMismoProductoLoVendenDosProveedores() {
        assertTrue(lata.suppliedBy("coca"))
        assertTrue(lata.suppliedBy("mayorista"))
        assertFalse(lata.suppliedBy("molinos"))
        assertEquals("f2", lata.defaultFormatForSupplier("mayorista")?.id)
    }

    @Test
    fun productoViejoSigueEnElCatalogoDeSuProveedor() {
        val viejo = Product(id = "p2", supplierId = "coca", purchaseCost = 5.0, isInHouseProduced = false)
        assertTrue(viejo.suppliedBy("coca"))
        assertNull(viejo.defaultFormatForSupplier("coca"))
    }

    @Test
    fun productoViejoSeConvierteEnSuPrimeraPresentacion() {
        val viejo = Product(
            id = "p2", unit = "unidad", supplierId = "coca", supplierName = "Distribuidora",
            purchaseCost = 5.0, isInHouseProduced = false
        )
        val formats = viejo.purchaseFormatsForEditing { "nuevo" }
        assertEquals(1, formats.size)
        assertEquals(1.0, formats.first().unitsPerPackage)
        assertEquals(5.0, formats.first().packageCost)
        assertEquals("coca", formats.first().supplierId)
        assertTrue(formats.first().isDefault)
    }

    @Test
    fun elCostoDeReferenciaSaleDeLaPredeterminada() {
        val derived = lata.withDerivedPurchaseReference()
        assertEquals(5.0, derived.purchaseCost)
        assertEquals("coca", derived.supplierId)
    }

    @Test
    fun siempreQuedaUnaSolaPredeterminada() {
        val sinDefault = listOf(cajaDistribuidora.copy(isDefault = false), sixPackMayorista)
        assertEquals(listOf(true, false), sinDefault.normalizedDefault().map { it.isDefault })
        val dos = listOf(cajaDistribuidora, sixPackMayorista.copy(isDefault = true))
        assertEquals(1, dos.normalizedDefault().count { it.isDefault })
    }

    @Test
    fun elPrecioPagadoActualizaSoloSuPresentacion() {
        val line = resalePurchaseLine(lata, cajaDistribuidora, quantity = 1.0, price = 126.0)
        val updated = lata.formatsWithPaidPrices(listOf(line))!!
        assertEquals(126.0, updated.first { it.id == "f1" }.packageCost)
        assertEquals(33.0, updated.first { it.id == "f2" }.packageCost)
    }

    @Test
    fun sinCambioDePrecioNoHayNadaQueEscribir() {
        val igual = resalePurchaseLine(lata, cajaDistribuidora, quantity = 1.0, price = 120.0)
        val suelta = resalePurchaseLine(lata, null, quantity = 3.0, price = 9.0)
        assertNull(lata.formatsWithPaidPrices(listOf(igual, suelta)))
    }
}
