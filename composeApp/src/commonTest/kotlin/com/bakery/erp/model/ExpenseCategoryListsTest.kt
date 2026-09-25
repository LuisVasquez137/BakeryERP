package com.bakery.erp.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Las dos listas curadas de categorías de gasto.
 *
 * Lo que se prueba no son los nombres sino las invariantes que, al romperse, dejan un
 * formulario mostrando una categoría que no se puede volver a elegir —o peor, un gasto
 * guardado cuya categoría desapareció de todas las listas y ya nadie puede corregir.
 */
class ExpenseCategoryListsTest {

    @Test
    fun `toda categoria del enum se ofrece en alguna de las dos listas`() {
        val ofrecidas = (FIXED_EXPENSE_CATEGORIES + OPERATIONAL_EXPENSE_CATEGORIES).toSet()
        val huerfanas = ExpenseCategory.entries.filterNot { it in ofrecidas }
        // Una categoría que no se ofrece en ningún lado solo puede llegar a un documento por
        // un dato viejo, y entonces no hay formulario donde reclasificarla.
        assertTrue(huerfanas.isEmpty(), "Categorías sin formulario que las ofrezca: $huerfanas")
    }

    @Test
    fun `OTROS esta en las dos listas`() {
        // Es la salida honesta cuando nada encaja. Sin ella el usuario elige cualquier cosa
        // con tal de guardar, y el reporte queda peor que con todo en "Otros".
        assertTrue(ExpenseCategory.OTROS in FIXED_EXPENSE_CATEGORIES)
        assertTrue(ExpenseCategory.OTROS in OPERATIONAL_EXPENSE_CATEGORIES)
    }

    @Test
    fun `los compromisos del mes no se ofrecen al anotar el gasto del dia`() {
        // El motivo del cambio: quien anota una escoba tenía que descartar "Alquiler",
        // "Planilla" y "Depreciación de equipo" para llegar a algo que le sirviera.
        listOf(
            ExpenseCategory.ALQUILER,
            ExpenseCategory.PLANILLA,
            ExpenseCategory.SERVICIOS,
            ExpenseCategory.SEGURIDAD,
            ExpenseCategory.DEPRECIACION
        ).forEach {
            assertTrue(it !in OPERATIONAL_EXPENSE_CATEGORIES, "$it no va en el gasto del día")
        }
    }

    @Test
    fun `lo que se compra en el dia no se presupuesta como costo fijo`() {
        listOf(
            ExpenseCategory.LIMPIEZA,
            ExpenseCategory.EMPAQUE,
            ExpenseCategory.HERRAMIENTAS,
            ExpenseCategory.COCINA,
            ExpenseCategory.PAPELERIA,
            ExpenseCategory.UNIFORMES
        ).forEach {
            assertTrue(it !in FIXED_EXPENSE_CATEGORIES, "$it se compra, no se presupuesta")
        }
    }

    @Test
    fun `ninguna lista repite una categoria`() {
        assertEquals(FIXED_EXPENSE_CATEGORIES.size, FIXED_EXPENSE_CATEGORIES.distinct().size)
        assertEquals(
            OPERATIONAL_EXPENSE_CATEGORIES.size,
            OPERATIONAL_EXPENSE_CATEGORIES.distinct().size
        )
    }
}
