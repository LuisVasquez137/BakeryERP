package com.bakery.erp.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Lo que este historial tiene que contestar es "¿cuántas bolsas le llevo compradas y a
 * cuánto?". Los dos modos de fallo son: contar dos veces una factura que trae el mismo ítem en
 * dos renglones, y partir en dos cosas lo que es una sola porque se escribió distinto.
 */
class SupplierPurchaseHistoryTest {

    private fun purchase(id: String, date: Long, total: Double = 0.0) =
        Purchase(id = id, supplierId = "prov", date = date, totalAmount = total)

    private fun conceptLine(
        id: String, conceptId: String, name: String, qty: Double, unitCost: Double
    ) = PurchaseLine(
        id = id, lineKind = PurchaseLineKind.SERVICE, conceptId = conceptId,
        conceptName = name, conceptUnit = "bolsas", quantityReceived = qty, unitCost = unitCost
    )

    @Test
    fun sumaCantidadesYCuentaComprasNoRenglones() {
        val p1 = purchase("c1", 1_000L)
        val p2 = purchase("c2", 2_000L)
        val history = buildSupplierItemHistory(
            listOf(p1, p2),
            mapOf(
                // La misma factura trae el concepto en dos renglones (dos tamaños de pedido).
                "c1" to listOf(
                    conceptLine("l1", "bolsa5", "Bolsas #5", 100.0, 2.0),
                    conceptLine("l2", "bolsa5", "Bolsas #5", 50.0, 2.0)
                ),
                "c2" to listOf(conceptLine("l3", "bolsa5", "Bolsas #5", 200.0, 2.5))
            )
        )
        assertEquals(1, history.size)
        val bolsas = history.first()
        assertEquals(350.0, bolsas.totalQuantity)
        assertEquals(800.0, bolsas.totalAmount)      // 200 + 100 + 500
        assertEquals(2, bolsas.timesBought)          // dos COMPRAS, no tres renglones
        assertEquals(2_000L, bolsas.lastDate)
        assertEquals(2.5, bolsas.lastUnitCost)       // el precio de la más reciente
        assertEquals("bolsas", bolsas.unit)
    }

    @Test
    fun elTextoLibreSeAgrupaPorNombreSinTildesNiMayusculas() {
        val history = buildSupplierItemHistory(
            listOf(purchase("c1", 1_000L), purchase("c2", 2_000L)),
            mapOf(
                "c1" to listOf(
                    PurchaseLine(
                        id = "l1", lineKind = PurchaseLineKind.SERVICE,
                        conceptName = "Reparación del horno", quantityReceived = 1.0, unitCost = 400.0
                    )
                ),
                "c2" to listOf(
                    PurchaseLine(
                        id = "l2", lineKind = PurchaseLineKind.SERVICE,
                        conceptName = "REPARACION DEL HORNO", quantityReceived = 1.0, unitCost = 250.0
                    )
                )
            )
        )
        assertEquals(1, history.size)
        assertEquals(650.0, history.first().totalAmount)
    }

    @Test
    fun conConceptoManda_elNombreTecleadoDejaDeImportar() {
        // El mismo concepto escrito distinto en dos facturas sigue siendo uno solo.
        val history = buildSupplierItemHistory(
            listOf(purchase("c1", 1_000L), purchase("c2", 2_000L)),
            mapOf(
                "c1" to listOf(conceptLine("l1", "bolsa5", "Bolsas #5", 100.0, 2.0)),
                "c2" to listOf(conceptLine("l2", "bolsa5", "bolsa numero 5", 100.0, 2.0))
            )
        )
        assertEquals(1, history.size)
        // Se muestra como se llamó la última vez.
        assertEquals("bolsa numero 5", history.first().name)
    }

    @Test
    fun laLineaRedirigidaAlInsumoSeAgrupaConSuProducto() {
        val redirigida = PurchaseLine(
            id = "l1", lineKind = PurchaseLineKind.INGREDIENT,
            productId = "p-leche", productName = "Leche 1 lt",
            ingredientId = "ing-leche", ingredientName = "Leche", ingredientUnit = "lt",
            quantityReceived = 12.0, unitCost = 12.0
        )
        val history = buildSupplierItemHistory(
            listOf(purchase("c1", 1_000L)),
            mapOf("c1" to listOf(redirigida))
        )
        assertEquals("P:p-leche", history.first().key)
        assertEquals(12.0, history.first().totalQuantity)
    }

    @Test
    fun ordenaLoMasRecientePrimero() {
        val history = buildSupplierItemHistory(
            listOf(purchase("c1", 1_000L), purchase("c2", 5_000L)),
            mapOf(
                "c1" to listOf(conceptLine("l1", "viejo", "Flete", 1.0, 50.0)),
                "c2" to listOf(conceptLine("l2", "nuevo", "Bolsas", 10.0, 2.0))
            )
        )
        assertEquals("Bolsas", history.first().name)
    }

    @Test
    fun totalesDelProveedorIncluyenSaldoPendiente() {
        val totals = listOf(
            Purchase(id = "c1", date = 1_000L, totalAmount = 500.0, paidAmount = 500.0),
            Purchase(id = "c2", date = 9_000L, totalAmount = 300.0, paidAmount = 100.0)
        ).supplierTotals()
        assertEquals(2, totals.purchaseCount)
        assertEquals(800.0, totals.totalAmount)
        assertEquals(200.0, totals.balanceDue)
        assertEquals(9_000L, totals.lastPurchaseDate)
    }

    // ── Catálogo de conceptos ───────────────────────────────────────────────

    @Test
    fun elCatalogoOfrecePrimeroLoQueEseProveedorVende() {
        val bolsas = PurchaseConcept(id = "b", name = "Bolsas #5", supplierIds = listOf("plasticos"))
        val flete = PurchaseConcept(id = "f", name = "Alquiler de flete")
        val ordered = listOf(flete, bolsas).forSupplier("plasticos")
        assertEquals("Bolsas #5", ordered.first().name)
        // Los de otros proveedores NO se esconden: la primera vez que la ferretería trae las
        // bolsas, el concepto tiene que estar a mano o se teclea de nuevo.
        assertEquals(2, ordered.size)
    }

    @Test
    fun noSeDaDeAltaDosVecesElMismoNombre() {
        val catalog = listOf(PurchaseConcept(id = "b", name = "Bolsas #5"))
        assertNotNull(catalog.findByName("  bolsas #5 "))
        assertNull(catalog.findByName("Bolsas #6"))
    }

    @Test
    fun elConceptoAprendeProveedorYPrecioSoloCuandoCambian() {
        val concept = PurchaseConcept(
            id = "b", name = "Bolsas", supplierIds = listOf("plasticos"),
            lastUnitCost = 2.0, lastPurchaseDate = 1_000L
        )
        assertNull(concept.afterPurchase("plasticos", 2.0, 1_000L))
        val updated = concept.afterPurchase("ferreteria", 2.5, 5_000L)
        assertNotNull(updated)
        assertTrue("ferreteria" in updated.supplierIds)
        assertEquals(2.5, updated.lastUnitCost)
        assertEquals(5_000L, updated.lastPurchaseDate)
    }
}
