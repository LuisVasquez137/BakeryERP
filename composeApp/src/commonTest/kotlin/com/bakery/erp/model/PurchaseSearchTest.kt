package com.bakery.erp.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PurchaseSearchTest {

    private val formats = listOf(
        PurchaseFormat(id = "a", supplierId = "dist", description = "Azúcar blanca Don Justo cabal"),
        PurchaseFormat(id = "b", supplierId = "otro", description = "AZUCAR ESTANDAR 50LB"),
        PurchaseFormat(id = "c", supplierId = "dist", description = "Caja 24", supplierItemName = "CC lata 354ml")
    )

    @Test
    fun encuentraPorElNombreDelCatalogo() {
        assertTrue(matchesPurchaseSearch("Azúcar", formats, "dist", "azucar"))
    }

    @Test
    fun encuentraPorComoLoEscribeLaFacturaEnCualquierOrden() {
        assertTrue(matchesPurchaseSearch("Azúcar", formats, "dist", "don justo"))
        assertTrue(matchesPurchaseSearch("Azúcar", formats, "dist", "justo cabal blanca"))
    }

    @Test
    fun noUsaElNombreQueLeDaOtroProveedor() {
        assertFalse(matchesPurchaseSearch("Azúcar", formats, "dist", "estandar"))
        assertEquals(listOf("Azúcar blanca Don Justo cabal", "CC lata 354ml", "Caja 24"), formats.invoiceNamesFor("dist"))
    }

    @Test
    fun enReventaEncuentraPorLaDescripcionAparteDelEmpaque() {
        assertTrue(matchesPurchaseSearch("Coca Cola 354 Ml", formats, "dist", "cc lata"))
    }

    @Test
    fun busquedaVaciaMuestraTodo() {
        assertTrue(matchesPurchaseSearch("Harina", emptyList(), "dist", "  "))
    }

    // ── Lo que se DIBUJA en la fila del catálogo ─────────────────────────────

    @Test
    fun laFilaMuestraSoloElNombreDeFacturaDeEseProveedor() {
        // Sin el empaque ("Caja 24"): ése ya va en su propio renglón de la fila, y repetirlo
        // arriba en cursiva no agrega nada. Y sin el nombre que le da otro proveedor.
        assertEquals(listOf("CC lata 354ml"), formats.supplierItemNamesFor("dist"))
    }

    @Test
    fun unaPresentacionSinProveedorCuentaParaTodos() {
        // Presentaciones de antes de que tuvieran dueño: esconderlas dejaría al insumo sin
        // fila en la factura de nadie.
        val huerfana = listOf(
            PurchaseFormat(id = "x", supplierId = "", description = "Fardo", supplierItemName = "Bolsa #5")
        )
        assertEquals(listOf("Bolsa #5"), huerfana.supplierItemNamesFor("cualquiera"))
    }

    @Test
    fun sinNombreDeFacturaNoHayNadaQueDibujar() {
        val soloEmpaque = listOf(PurchaseFormat(id = "y", supplierId = "dist", description = "Saco 100 lb"))
        assertTrue(soloEmpaque.supplierItemNamesFor("dist").isEmpty())
    }
}
