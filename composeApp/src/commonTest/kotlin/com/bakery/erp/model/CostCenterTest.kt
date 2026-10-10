package com.bakery.erp.model

import kotlin.test.Test
import kotlin.test.assertEquals

class CostCenterTest {

    @Test
    fun valorDesconocidoOVacioEsLaSucursal() {
        assertEquals(CostCenter.BRANCH, CostCenter.normalize(null))
        assertEquals(CostCenter.BRANCH, CostCenter.normalize(""))
        assertEquals(CostCenter.BRANCH, CostCenter.normalize("OTRA_COSA"))
        assertEquals(CostCenter.GENERAL, CostCenter.normalize("GENERAL"))
        assertEquals(CostCenter.DELIVERY, CostCenter.normalize("DELIVERY"))
    }

    @Test
    fun laFacturaSoloLeMarcaElDuenoALasLineasQueSeGastan() {
        val lines = listOf(
            PurchaseLine(id = "harina", lineKind = PurchaseLineKind.INGREDIENT),
            PurchaseLine(id = "gaseosa", lineKind = PurchaseLineKind.RESALE_PRODUCT),
            PurchaseLine(id = "cloro", lineKind = PurchaseLineKind.SUPPLY),
            PurchaseLine(id = "bolsas", lineKind = PurchaseLineKind.SUPPLY_NO_STOCK),
            PurchaseLine(id = "contador", lineKind = PurchaseLineKind.SERVICE),
        )
        val stamped = lines.withExpenseCostCenter(CostCenter.GENERAL).associate { it.id to it.costCenter }
        assertEquals(CostCenter.BRANCH, stamped["harina"])
        assertEquals(CostCenter.BRANCH, stamped["gaseosa"])
        assertEquals(CostCenter.GENERAL, stamped["cloro"])
        assertEquals(CostCenter.GENERAL, stamped["bolsas"])
        assertEquals(CostCenter.GENERAL, stamped["contador"])
    }

    @Test
    fun volverALaSucursalLimpiaLoQueSeHabiaMarcado() {
        val lines = listOf(PurchaseLine(lineKind = PurchaseLineKind.SERVICE, costCenter = CostCenter.DELIVERY))
        assertEquals(CostCenter.BRANCH, lines.withExpenseCostCenter(CostCenter.BRANCH).single().costCenter)
    }

    @Test
    fun losCompartidosTienenSuPropioRenglonEnElReporte() {
        assertEquals("planta", costCenterReportKey(CostCenter.BRANCH, "planta"))
        assertEquals("Planta", costCenterReportName(CostCenter.BRANCH, "Planta"))
        // Anotado en la planta, pero es de Generales: no puede caer en el renglón de la planta.
        assertEquals("cc:GENERAL", costCenterReportKey(CostCenter.GENERAL, "planta"))
        assertEquals("Generales", costCenterReportName(CostCenter.GENERAL, "Planta"))
        assertEquals("cc:DELIVERY", costCenterReportKey(CostCenter.DELIVERY, "planta"))
        assertEquals("Reparto", costCenterReportName(CostCenter.DELIVERY, "Planta"))
    }
}
