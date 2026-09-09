package com.bakery.erp.model

import kotlin.test.Test
import kotlin.test.assertEquals

class BranchOrderingTest {

    private data class Row(val id: String, val name: String)

    private fun order(items: List<Row>, order: List<String>) =
        applyBranchOrder(items, order, { it.id }, { it.name }).map { it.name }

    private val rows = listOf(
        Row("b1", "Bombero"),
        Row("c1", "Calvario"),
        Row("d1", "Democracia"),
        Row("p1", "Pacajá"),
        Row("a1", "Salida De Almolonga")
    )

    @Test
    fun `sin orden configurado queda alfabetico`() {
        assertEquals(
            listOf("Bombero", "Calvario", "Democracia", "Pacajá", "Salida De Almolonga"),
            order(rows, emptyList())
        )
    }

    @Test
    fun `respeta el orden configurado por la planta`() {
        assertEquals(
            listOf("Pacajá", "Bombero", "Salida De Almolonga", "Calvario", "Democracia"),
            order(rows, listOf("p1", "b1", "a1", "c1", "d1"))
        )
    }

    @Test
    fun `las sucursales fuera de la lista van al final, alfabeticas`() {
        // Solo se configuraron dos: el resto conserva su orden alfabético detrás.
        assertEquals(
            listOf("Pacajá", "Democracia", "Bombero", "Calvario", "Salida De Almolonga"),
            order(rows, listOf("p1", "d1"))
        )
    }

    @Test
    fun `una sucursal nueva aparece al final y no rompe el orden guardado`() {
        // El caso real de guardar solo el orden explícito: dar de alta una sucursal
        // no obliga a reconfigurar, y la nueva no se pierde.
        val conNueva = rows + Row("n1", "Nueva Sucursal")
        assertEquals(
            listOf("Pacajá", "Bombero", "Calvario", "Democracia", "Nueva Sucursal", "Salida De Almolonga"),
            order(conNueva, listOf("p1", "b1"))
        )
    }

    @Test
    fun `ids que ya no existen en la lista se ignoran sin alterar el resto`() {
        // Una sucursal borrada sigue en dispatchBranchOrder; no debe afectar nada.
        assertEquals(
            listOf("Democracia", "Bombero", "Calvario", "Pacajá", "Salida De Almolonga"),
            order(rows, listOf("borrada", "d1"))
        )
    }
}
