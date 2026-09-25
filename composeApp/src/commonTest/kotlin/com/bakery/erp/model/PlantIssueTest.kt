package com.bakery.erp.model

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Material usado en planta (ver [PlantIssue]).
 *
 * Lo que se verifica es la aritmética que nadie puede revisar a ojo desde la pantalla:
 * las conversiones a la unidad del stock (donde un error descuenta 100 veces de más) y
 * el intervalo entre empaques, que es el número por el que existe el módulo.
 */
class PlantIssueTest {

    private val flour = Ingredient(
        id = "flour",
        name = "Harina suave",
        allowedUnits = listOf("lb", "kg"),
        purchaseFormats = listOf(
            PurchaseFormat(id = "sack", description = "Saco de 100 lb", unit = "lb", unitsPerPackage = 100.0, isDefault = true)
        )
    )

    private fun assertClose(expected: Double, actual: Double?, tolerance: Double = 0.01) {
        assertNotNull(actual)
        assertTrue(abs(expected - actual) <= tolerance, "esperaba ~$expected, obtuve $actual")
    }

    // ── Empaques ─────────────────────────────────────────────────────────────

    @Test
    fun `un saco descuenta sus libras en la unidad base`() {
        assertClose(100.0, packagesToBaseUnitOrNull(1.0, flour.purchaseFormats[0], flour))
        assertClose(200.0, packagesToBaseUnitOrNull(2.0, flour.purchaseFormats[0], flour))
    }

    @Test
    fun `el formato en otra unidad de la misma familia se convierte, no se pasa tal cual`() {
        // El saco viene declarado en kg pero el stock vive en lb: sin conversión se
        // descontarían 45 lb de un saco que trae 99.
        val ing = flour.copy(
            purchaseFormats = listOf(
                PurchaseFormat(id = "s", description = "Saco 45 kg", unit = "kg", unitsPerPackage = 45.0)
            )
        )
        assertClose(99.21, packagesToBaseUnitOrNull(1.0, ing.purchaseFormats[0], ing))
    }

    @Test
    fun `un empaque de conteo cruza a peso por el peso unitario`() {
        val eggs = countedEggs(unitWeightGrams = 50.0)
        // 30 huevos × 50 g = 1500 g ≈ 3.31 lb
        assertClose(3.31, packagesToBaseUnitOrNull(1.0, eggs.purchaseFormats[0], eggs))
    }

    @Test
    fun `sin equivalencia posible devuelve null en vez de inventar un numero`() {
        // Un empaque contado sobre un insumo que se guarda en libras, sin peso unitario
        // configurado: no hay puente. Antes `toBaseUnit` devolvía el valor tal cual y
        // 30 huevos entraban como 30 libras.
        val eggs = countedEggs(unitWeightGrams = 0.0)
        assertNull(packagesToBaseUnitOrNull(1.0, eggs.purchaseFormats[0], eggs))
    }

    @Test
    fun `cantidad cero o empaque sin equivalencia no descuentan nada`() {
        assertNull(packagesToBaseUnitOrNull(0.0, flour.purchaseFormats[0], flour))
        val broken = PurchaseFormat(id = "x", description = "Suelto", unit = "lb", unitsPerPackage = 0.0)
        assertNull(packagesToBaseUnitOrNull(1.0, broken, flour))
    }

    // ── Cantidad suelta ──────────────────────────────────────────────────────

    @Test
    fun `las onzas sueltas se convierten a la unidad del stock`() {
        // El caso que motivó la opción: alguien agarra 8 oz de un bote ya abierto.
        // Sin conversión se le descontarían 8 lb — 16 veces de más.
        assertClose(0.5, looseToBaseUnitOrNull(8.0, "oz", flour))
        assertClose(5.0, looseToBaseUnitOrNull(5.0, "lb", flour))
        assertClose(2.2, looseToBaseUnitOrNull(1.0, "kg", flour))
    }

    @Test
    fun `una unidad suelta sin puente al stock devuelve null`() {
        // "6 unidad" de harina no significa nada: se rechaza el vale en vez de
        // descontar 6 libras.
        assertNull(looseToBaseUnitOrNull(6.0, "unidad", flour))
        assertNull(looseToBaseUnitOrNull(0.0, "lb", flour))
    }

    @Test
    fun `las unidades ofrecidas son las que de verdad convierten a la del stock`() {
        // La harina en lb acepta toda la familia de peso; nunca ml ni unidad, que
        // ofrecidas rechazarían el vale después de teclearlo.
        val options = flour.looseUnitOptions()
        assertTrue("lb" in options && "oz" in options && "kg" in options && "gr" in options, "$options")
        assertTrue("unidad" !in options && "ml" !in options, "$options")
    }

    @Test
    fun `un insumo contado con peso unitario si puede sacarse por peso y por unidad`() {
        // El huevo se cuenta, pero con `unitWeightGrams` puesto también se puede sacar
        // "media libra" — el puente existe en los dos sentidos.
        val eggs = countedEggs(unitWeightGrams = 50.0).copy(allowedUnits = listOf("unidad"))
        val options = eggs.looseUnitOptions()
        assertTrue("unidad" in options, "$options")
        assertClose(4.54, looseToBaseUnitOrNull(0.5, "lb", eggs))
    }

    // ── Ritmo: cada cuánto se abre otro empaque ──────────────────────────────

    @Test
    fun `el promedio mide el hueco entre empaques consecutivos`() {
        // Lunes, jueves, viernes → huecos de 3 y 1 día: promedio 2.
        val issues = listOf(
            issue("flour", day = 0),
            issue("flour", day = 3),
            issue("flour", day = 4)
        )
        val summary = summarizePlantIssues(issues).single()
        assertEquals(3, summary.issueCount)
        assertEquals(3, summary.packageIssueCount)
        assertClose(2.0, summary.avgDaysBetween)
        assertEquals(3.0, summary.packages)
    }

    @Test
    fun `las cantidades sueltas NO acortan el intervalo entre empaques`() {
        // Agarrar 8 oz del bote abierto no abre un empaque nuevo. Si contaran, el
        // intervalo de dos sacos separados por 4 días se leería como 2, y el reporte
        // mostraría una rotación que no ocurrió.
        val issues = listOf(
            issue("flour", day = 0),
            loose("flour", day = 2, quantity = 8.0, unit = "oz", baseQuantity = 0.5),
            issue("flour", day = 4)
        )
        val summary = summarizePlantIssues(issues).single()
        assertClose(4.0, summary.avgDaysBetween)
        assertEquals(3, summary.issueCount)
        assertEquals(2, summary.packageIssueCount)
    }

    @Test
    fun `las sueltas si suman al total de cantidad`() {
        // Ese material salió de la bodega: no cuenta como empaque abierto, pero el
        // inventario sí lo perdió.
        val issues = listOf(
            issue("flour", day = 0),
            loose("flour", day = 2, quantity = 8.0, unit = "oz", baseQuantity = 0.5)
        )
        val summary = summarizePlantIssues(issues).single()
        assertEquals(1.0, summary.packages)
        assertClose(100.5, summary.quantityInBaseUnit)
    }

    @Test
    fun `un solo empaque no tiene intervalo que medir`() {
        // Devolver 0 se leería como "abren uno diario", que es lo contrario de la verdad.
        val summary = summarizePlantIssues(listOf(issue("flour", day = 0))).single()
        assertNull(summary.avgDaysBetween)
        assertEquals("un solo empaque en el período", summary.rhythmLabel())
    }

    @Test
    fun `un insumo del que solo se sacaron cantidades sueltas lo dice`() {
        // Sin este caso la fila decía "una sola salida" sobre un insumo del que hubo
        // diez, porque ninguna era empaque.
        val issues = listOf(
            loose("lard", day = 0, quantity = 8.0, unit = "oz", baseQuantity = 0.5),
            loose("lard", day = 1, quantity = 4.0, unit = "oz", baseQuantity = 0.25)
        )
        val summary = summarizePlantIssues(issues).single()
        assertNull(summary.avgDaysBetween)
        assertEquals(0, summary.packageIssueCount)
        assertEquals("solo cantidades sueltas", summary.rhythmLabel())
    }

    @Test
    fun `cada insumo lleva su propio intervalo`() {
        val issues = listOf(
            issue("flour", day = 0), issue("flour", day = 2),
            issue("lard", day = 0), issue("lard", day = 10)
        )
        val byId = summarizePlantIssues(issues).associateBy { it.ingredientId }
        assertClose(2.0, byId.getValue("flour").avgDaysBetween)
        assertClose(10.0, byId.getValue("lard").avgDaysBetween)
    }

    // ── Aviso del diálogo ────────────────────────────────────────────────────

    @Test
    fun `el aviso cuenta desde la ultima salida de ese insumo`() {
        val issues = listOf(issue("flour", day = 0), issue("flour", day = 3), issue("lard", day = 5))
        val now = dayMillis(5)
        assertClose(2.0, daysSinceLastIssue(issues, "flour", now))
        assertClose(0.0, daysSinceLastIssue(issues, "lard", now))
        assertNull(daysSinceLastIssue(issues, "sugar", now))
    }

    @Test
    fun `la advertencia de empaque abierto ignora las salidas sueltas`() {
        // "Puede que ese saco siga abierto" se mide contra el último EMPAQUE. Las onzas
        // que alguien agarró ayer no reinician esa cuenta.
        val issues = listOf(
            issue("flour", day = 0),
            loose("flour", day = 4, quantity = 8.0, unit = "oz", baseQuantity = 0.5)
        )
        val now = dayMillis(5)
        assertClose(1.0, daysSinceLastIssue(issues, "flour", now))
        assertClose(5.0, daysSinceLastIssue(issues, "flour", now, onlyPackages = true))
    }

    // ── Presentación ─────────────────────────────────────────────────────────

    @Test
    fun `el desglose por motivo suma los empaques de cada motivo`() {
        val issues = listOf(
            issue("flour", day = 0, reason = PlantIssueReason.DUSTING),
            issue("flour", day = 1, reason = PlantIssueReason.DUSTING),
            issue("flour", day = 2, reason = PlantIssueReason.SPILLAGE)
        )
        val summary = summarizePlantIssues(issues).single()
        assertEquals(2.0, summary.packagesByReason[PlantIssueReason.DUSTING])
        assertEquals(1.0, summary.packagesByReason[PlantIssueReason.SPILLAGE])
    }

    @Test
    fun `un motivo guardado que ya no esta en la lista se muestra tal cual`() {
        // El reporte viejo tiene que seguir diciendo qué pasó, no reetiquetarlo "Otro".
        assertEquals("MOTIVO_VIEJO", PlantIssueReason.label("MOTIVO_VIEJO"))
        assertEquals("Otro", PlantIssueReason.label(""))
    }

    @Test
    fun `el registro se lee en la unidad en que se tecleo`() {
        // "8 oz" no se relee como "0.5 lb": deja de parecerse a lo que la persona anotó.
        assertEquals("1 × Saco de 100 lb", issue("flour", day = 0).displayQuantity)
        assertEquals(
            "8 oz",
            loose("flour", day = 0, quantity = 8.0, unit = "oz", baseQuantity = 0.5).displayQuantity
        )
    }

    @Test
    fun `un registro suelto viejo sin la unidad tecleada cae a la unidad base`() {
        val old = PlantIssue(
            ingredientId = "flour", ingredientName = "Harina",
            quantityInBaseUnit = 12.5, baseUnit = "lb", issuedAt = dayMillis(0)
        )
        assertEquals("12.5 lb", old.displayQuantity)
    }

    @Test
    fun `la nota del kardex lleva motivo, detalle y cantidad tambien en las sueltas`() {
        val pkg = issue("flour", day = 0, reason = PlantIssueReason.DUSTING)
            .copy(notes = "para la sheca").kardexNote
        assertTrue("Enharinar" in pkg, pkg)
        assertTrue("para la sheca" in pkg, pkg)
        assertTrue("Saco de 100 lb" in pkg, pkg)

        // El Kardex muestra la unidad base en su columna, así que sin la cantidad acá
        // un vale de "8 oz" solo se veía como "0.5 lb".
        val looseNote = loose("flour", day = 0, quantity = 8.0, unit = "oz", baseQuantity = 0.5).kardexNote
        assertTrue("8 oz" in looseNote, looseNote)
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun countedEggs(unitWeightGrams: Double) = Ingredient(
        id = "egg",
        name = "Huevo",
        allowedUnits = listOf("lb"),
        unitWeightGrams = unitWeightGrams,
        purchaseFormats = listOf(
            PurchaseFormat(id = "c", description = "Cartón de 30", unit = "unidad", unitsPerPackage = 30.0)
        )
    )

    private fun dayMillis(day: Int): Long = 1_700_000_000_000L + (day * 86_400_000L)

    private fun issue(
        ingredientId: String,
        day: Int,
        packages: Double = 1.0,
        baseQuantity: Double = 100.0,
        reason: String = PlantIssueReason.DUSTING
    ) = PlantIssue(
        id = "$ingredientId-pkg-$day",
        ingredientId = ingredientId,
        ingredientName = ingredientId,
        formatDescription = "Saco de 100 lb",
        unitsPerPackage = 100.0,
        formatUnit = "lb",
        packageQuantity = packages,
        quantityInBaseUnit = baseQuantity,
        baseUnit = "lb",
        reason = reason,
        issuedAt = dayMillis(day)
    )

    private fun loose(
        ingredientId: String,
        day: Int,
        quantity: Double,
        unit: String,
        baseQuantity: Double,
        reason: String = PlantIssueReason.GREASING
    ) = PlantIssue(
        id = "$ingredientId-loose-$day",
        ingredientId = ingredientId,
        ingredientName = ingredientId,
        looseQuantity = quantity,
        looseUnit = unit,
        quantityInBaseUnit = baseQuantity,
        baseUnit = "lb",
        reason = reason,
        issuedAt = dayMillis(day)
    )
}
