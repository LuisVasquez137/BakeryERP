package com.bakery.erp.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SupplierSearchTest {

    private fun supplier(company: String, salesperson: String = "") =
        Supplier(id = company + salesperson, company = company, salesperson = salesperson)

    // ── Orden ────────────────────────────────────────────────────────────────

    @Test
    fun ordena_alfabeticamente_sin_importar_mayusculas() {
        val ordered = listOf(supplier("molinos del sur"), supplier("Azucarera"), supplier("Bimbo"))
            .sortedForList()
            .map { it.company }
        assertEquals(listOf("Azucarera", "Bimbo", "molinos del sur"), ordered)
    }

    @Test
    fun la_tilde_no_manda_al_proveedor_al_final() {
        // Comparando por el código del carácter, 'Á' cae después de 'B' y Ávila saldría al final.
        val ordered = listOf(supplier("Barcenas"), supplier("Ávila"), supplier("Azul"))
            .sortedForList()
            .map { it.company }
        assertEquals(listOf("Ávila", "Azul", "Barcenas"), ordered)
    }

    @Test
    fun sin_empresa_ordena_por_vendedor() {
        val ordered = listOf(supplier("Zeta"), supplier(company = "", salesperson = "Ana"))
            .sortedForList()
            .map { it.company.ifBlank { it.salesperson } }
        assertEquals(listOf("Ana", "Zeta"), ordered)
    }

    // ── Búsqueda ─────────────────────────────────────────────────────────────

    @Test
    fun encuentra_por_empresa_con_y_sin_tilde() {
        val s = supplier("Azúcar del Trópico")
        assertTrue(matchesSupplierSearch(s, "azucar"))
        assertTrue(matchesSupplierSearch(s, "azúcar"))
        assertTrue(matchesSupplierSearch(s, "TROPICO"))
    }

    @Test
    fun encuentra_por_vendedor() {
        val s = supplier("Molinos Modernos", "José Pérez")
        assertTrue(matchesSupplierSearch(s, "jose"))
        assertTrue(matchesSupplierSearch(s, "perez"))
    }

    @Test
    fun palabras_de_los_dos_campos_en_cualquier_orden() {
        val s = supplier("Azucarera del Sur", "Mario López")
        assertTrue(matchesSupplierSearch(s, "mario azucarera"))
        assertTrue(matchesSupplierSearch(s, "sur lopez"))
    }

    @Test
    fun query_vacia_no_filtra() {
        assertTrue(matchesSupplierSearch(supplier("Cualquiera"), ""))
        assertTrue(matchesSupplierSearch(supplier("Cualquiera"), "   "))
    }

    @Test
    fun no_encuentra_lo_que_no_esta() {
        assertFalse(matchesSupplierSearch(supplier("Molinos Modernos", "José"), "bimbo"))
    }
}
