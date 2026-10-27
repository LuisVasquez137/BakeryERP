package com.bakery.erp.model

/**
 * Preparación reutilizable que sirve para una de dos cosas (definido por [usage]):
 *  - [PreparationUsage.DOUGH]: esponja/masa madre/prefermento — se incorpora a una masa
 *    en producción. Sus ingredientes se descuentan de los ingredientes que pediría la receta
 *    al 100% del producto.
 *  - [PreparationUsage.DECORATION]: adorno/relleno (concha, etc.) — se aplica sobre el
 *    producto terminado en gramos por unidad. Vive vinculada al Producto, no a la receta.
 *  - [PreparationUsage.BATTER]: masa de bizcocho (vainilla, chocolate). Tiene insumos pero no
 *    saldo propio: se bate y se echa en moldes el mismo día.
 *  - [PreparationUsage.BASE]: el molde que se cuenta (bizcocho de frutas 22 oz, plancha). No
 *    tiene insumos: dice de qué masa es ([batterPreparationId]) y cuánta lleva
 *    ([batterGramsPerUnit]). Su saldo va en unidades. Ver "Bizcochos" en `arquitectura/catalogo.md`.
 *
 * El peso total resultante del lote NO lo ingresa el usuario: se calcula sumando los
 * pesos de los ingredientes ([batchWeightGrams]).
 */
data class Preparation(
    val id: String = "",
    val name: String = "",
    val description: String? = null,
    val usage: String = PreparationUsage.DOUGH,
    val ingredientCount: Int = 0,          // denormalizado para la lista
    /** % del peso del lote que es harina (insumos categoría "Harinas"). Denormalizado. */
    val flourFractionPct: Double = 0.0,
    val notes: String? = null,
    val active: Boolean = true,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val ingredients: List<PreparationIngredient> = emptyList(),
    /** Solo [PreparationUsage.BASE]: la masa con que se llena el molde. */
    val batterPreparationId: String = "",
    val batterPreparationName: String = "",
    /** Solo [PreparationUsage.BASE]: masa cruda que lleva UN molde (frutas = 22 oz). */
    val batterGramsPerUnit: Double = 0.0,
    /** Solo [PreparationUsage.BASE]: cuántos tener siempre fríos para decorar. 0 = sin mínimo. */
    val stockMinimumUnits: Double = 0.0,
    /**
     * Plantas que la hacen. Vacío = todas: así lo que ya existía no desaparece de ninguna
     * planta mientras la van asignando.
     */
    val branchIds: List<String> = emptyList()
) {
    /** La hace la planta [branchId]. */
    fun isMadeIn(branchId: String): Boolean = branchIds.isEmpty() || branchId in branchIds

    /** Se cuenta por unidades (bizcochos), no se pesa. */
    val isCounted: Boolean get() = usage == PreparationUsage.BASE

    /** Peso total de 1 lote en gramos = Σ de los ingredientes (derivado, no se persiste). */
    val batchWeightGrams: Double
        get() = ingredients.sumOf {
            toGramsOrNull(it.quantityPerBatch, it.unit, it.unitWeightGrams) ?: 0.0
        }
}

data class PreparationIngredient(
    val id: String = "",
    val ingredientId: String = "",
    val ingredientName: String = "",
    val quantityPerBatch: Double = 0.0,    // cantidad fija para 1 lote estándar
    val unit: String = "",                 // unidad de la cantidad (lb, gr, ...)
    /**
     * Peso de UNA unidad del insumo, copiado al guardar. Solo aplica cuando [unit]
     * es de conteo: sin él, 6 huevos entraban al peso del lote como 6 gramos y
     * distorsionaban la fracción con que la esponja descuenta de la receta.
     */
    val unitWeightGrams: Double = 0.0
)

object PreparationUsage {
    const val DOUGH      = "DOUGH"       // esponja / masa madre / prefermento
    const val DECORATION = "DECORATION"  // concha / adorno / relleno
    const val BATTER     = "BATTER"      // masa de bizcocho: se bate y se echa en moldes
    const val BASE       = "BASE"        // bizcocho / plancha: se cuenta

    fun label(usage: String) = when (usage) {
        DOUGH      -> "Para masa"
        DECORATION -> "Para adorno"
        BATTER     -> "Masa de bizcocho"
        BASE       -> "Bizcocho"
        else       -> "Desconocido"
    }
}
