package com.bakery.erp.model

/**
 * Preparación reutilizable que sirve para una de dos cosas (definido por [usage]):
 *  - [PreparationUsage.DOUGH]: esponja/masa madre/prefermento — se incorpora a una masa
 *    en producción. Sus ingredientes se descuentan de los ingredientes que pediría la receta
 *    al 100% del producto.
 *  - [PreparationUsage.DECORATION]: adorno/relleno (concha, etc.) — se aplica sobre el
 *    producto terminado en gramos por unidad. Vive vinculada al Producto, no a la receta.
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
    val ingredients: List<PreparationIngredient> = emptyList()
) {
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

    fun label(usage: String) = when (usage) {
        DOUGH      -> "Para masa"
        DECORATION -> "Para adorno"
        else       -> "Desconocido"
    }
}
