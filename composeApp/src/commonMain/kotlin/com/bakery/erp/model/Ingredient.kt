package com.bakery.erp.model

/**
 * Formato en que se compra un ingrediente.
 * Permite comprar en diferentes presentaciones (saco 50 lb, libra suelta, etc.)
 * con distintos proveedores y precios, midiendo siempre en la misma unidad base.
 */
data class PurchaseFormat(
    val id: String = "",
    val description: String = "",        // "Saco de 50 lbs", "Libra suelta"
    val unit: String = "",               // Unidad de medida de esta presentación (ej: lb, gr)
    val supplierId: String = "",
    val supplierName: String = "",       // Desnormalizado para display rápido
    val unitsPerPackage: Double = 1.0,   // Cuántas unidades trae el paquete (en `unit`)
    val packageCost: Double = 0.0,       // Costo del paquete en Q
    val isDefault: Boolean = false       // Formato preferido para calcular costos
) {
    /** Costo por unidad calculado automáticamente */
    val costPerUnit: Double
        get() = if (unitsPerPackage > 0) packageCost / unitsPerPackage else 0.0
}

/**
 * Para qué sirve el insumo. Decide en qué catálogos aparece, no cómo se almacena:
 * un suministro tiene exactamente el mismo stock por sucursal, Kardex, formatos de
 * compra y aviso de mínimo que una harina.
 *
 * Se modeló como campo del insumo y no como un catálogo aparte porque lo único que
 * de verdad difiere es dónde se ofrece — duplicar el modelo habría duplicado stock,
 * Kardex, alertas, la pantalla de ajuste y la captura de compra para obtener lo que
 * `Ingredient` ya hace. La categoría "Empaques" ya venía usando este patrón de hecho.
 */
enum class IngredientUsage {
    /** Materia prima: entra a recetas, a preparaciones y al consumo de producción. */
    PRODUCTION,
    /**
     * Suministro de operación (cloro, papel, desinfectante, escobas). Se cuenta y se
     * repone, pero nunca entra a una receta y su compra se gasta de inmediato: su
     * inventario existe para contestar "¿me queda?", no para valorar nada.
     */
    SUPPLY;

    val label: String get() = when (this) {
        PRODUCTION -> "Materia prima"
        SUPPLY -> "Suministro de operación"
    }

    companion object {
        /** Los insumos que existían antes de este campo son todos materia prima. */
        fun from(raw: String?): IngredientUsage =
            entries.firstOrNull { it.name == raw } ?: PRODUCTION
    }
}

data class Ingredient(
    val id: String = "",
    val name: String = "",
    val category: String = "",
    /** Ver [IngredientUsage]. Default PRODUCTION: los insumos ya cargados son materia prima. */
    val usage: IngredientUsage = IngredientUsage.PRODUCTION,
    /**
     * Código de barras del empaque, si lo trae. Opcional — la harina a granel no tiene.
     * Su valor está en la captura de compra: escanear el garrafón encuentra el insumo
     * sin teclear el nombre. Vacío = sin código.
     */
    val barcode: String = "",
    val allowedUnits: List<String> = emptyList(),  // Unidades en que se puede medir este ingrediente
    /**
     * Cuánto pesa UNA unidad, en gramos (huevo ≈ 50 g). Solo aplica a insumos que
     * se cuentan en vez de pesarse. Es lo que permite que el huevo siga viviendo
     * en unidades en el stock y el Kardex, pero entre a la receta por su peso —
     * el % del panadero es una razón entre pesos. 0 = sin configurar.
     */
    val unitWeightGrams: Double = 0.0,
    val purchaseFormats: List<PurchaseFormat> = emptyList(),
    val stockAlert: Double? = null,
    /**
     * Si caduca. Solo en estos insumos el formulario de compra pide fecha de vencimiento:
     * ponerle el campo a la harina a granel y a la sal sería ruido en la única pantalla
     * donde la velocidad de captura importa.
     */
    val isPerishable: Boolean = false,
    val notes: String? = null,
    val active: Boolean = true,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
) {
    /** Primera unidad — usada como referencia en stock y alertas */
    val effectiveBaseUnit: String
        get() = allowedUnits.firstOrNull() ?: ""

    /** true si alguna unidad permitida tiene equivalencia en peso configurable (unidad/docena). */
    val usesCountUnits: Boolean
        get() = allowedUnits.any { countUnitsPer(it) != null }

    /**
     * Unidades de medida que tienen sentido al escribir este insumo en una receta.
     * Un insumo de conteo con peso unitario acepta también gr/oz/lb: la encargada
     * puede anotar "6 unidad" o "10.6 oz" y sale el mismo porcentaje.
     */
    val recipeUnitOptions: List<String>
        get() = if (usesCountUnits && unitWeightGrams > 0.0)
            (allowedUnits + listOf("gr", "oz", "lb")).distinct()
        else allowedUnits

    /** Costo vigente por unidad, según el formato predeterminado */
    val defaultUnitCost: Double
        get() {
            val fmt = purchaseFormats.firstOrNull { it.isDefault } ?: purchaseFormats.firstOrNull()
            return fmt?.costPerUnit ?: 0.0
        }
}

/**
 * Categorías de suministros de operación. Se listan aparte de [INGREDIENT_CATEGORIES]
 * porque "Harinas" no significa nada para el cloro y "Limpieza" no significa nada para
 * la masa: mezclarlas dejaría los dos formularios con dos tercios de opciones inútiles.
 */
/**
 * Los insumos que tiene sentido escribir en una receta o en una preparación.
 *
 * [keepIds] conserva los que la receta YA tiene aunque hoy estén marcados como suministro:
 * si alguien reclasifica un insumo por error, la fila de la receta debe seguir mostrando su
 * nombre para poder corregirla, no quedarse en blanco.
 */
fun List<Ingredient>.usableInRecipes(keepIds: Set<String> = emptySet()): List<Ingredient> =
    filter { it.usage == IngredientUsage.PRODUCTION || it.id in keepIds }

val SUPPLY_CATEGORIES = listOf(
    "Limpieza y desinfección",
    "Empaque y desechables",
    "Papelería",
    "Repuestos y ferretería",
    "Uniformes y protección",
    "Otros"
)

/** Categorías predefinidas de materias primas para panadería */
val INGREDIENT_CATEGORIES = listOf(
    "Harinas",
    "Grasas",
    "Azúcares",
    "Lácteos",
    "Levaduras",
    "Huevos",
    "Frutas y Rellenos",
    "Coberturas",
    "Especias y Saborizantes",
    "Empaques",
    "Otros"
)

/** Unidades de medida predefinidas */
val INGREDIENT_UNITS = listOf(
    "lb",
    "kg",
    "oz",
    "gr",
    "lt",
    "ml",
    "unidad",
    "docena",
    "saco"
)
