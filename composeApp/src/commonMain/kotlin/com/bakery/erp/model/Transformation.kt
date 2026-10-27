package com.bakery.erp.model

import com.bakery.erp.util.fmtTrim
import kotlin.math.floor

/**
 * Transformar: hacer un producto a partir de otro que ya está en el inventario. Pan molde →
 * pan tostado (de un molde salen ~1.5 empaques), pirujo de mantequilla → pirujo con ajo. Es la
 * generalización de Porcionar (`model/Portioning.kt`): sale el original, entra el nuevo con el
 * costo del original más los insumos que se le agregan. Se hace en Calvario. Tests en
 * `TransformationTest`.
 *
 * Se anota lo que de verdad salió, no el factor: si de 4 moldes salieron 5 empaques, son 5.
 */

/** Un insumo que se le agrega a cada unidad que sale (la bolsa del tostado, el ajo del pirujo). */
data class TransformExtra(
    val ingredientId: String = "",
    val ingredientName: String = "",
    /** Por cada unidad que SALE, en [unit]. */
    val quantityPerUnit: Double = 0.0,
    val unit: String = ""
)

data class TransformationRequest(
    val branchId: String,
    val branchName: String,
    /** El producto que sale; su [Product.transformFromProductId] dice de qué se hace. */
    val product: Product,
    /** Cuántos del original se usaron. */
    val sourceUnits: Double,
    /** Cuántos salieron de verdad. */
    val producedUnits: Double,
    val userId: String,
    val userName: String,
    val note: String? = null
)

/** Lo que se sugiere que salga de [sourceUnits]: lo normal, en piezas enteras (no media bolsa). */
fun Product.suggestedTransformOutput(sourceUnits: Double): Double =
    if (!isTransformed || sourceUnits <= 0.0) 0.0 else floor(sourceUnits * transformYield + 1e-9)

/** Insumos a descontar por [producedUnits]: cada extra × lo que salió. */
fun Product.transformExtrasFor(producedUnits: Double): List<Pair<TransformExtra, Double>> =
    transformExtras.filter { it.ingredientId.isNotEmpty() && it.quantityPerUnit > 0.0 }
        .map { it to it.quantityPerUnit * producedUnits }

/**
 * Por qué no se puede transformar, o `null` si se puede. [sourceStock] = existencias actuales
 * del original en la sucursal. Igual que al porcionar: no deja usar más de lo que el sistema
 * dice que hay, porque el costo del nuevo sale del original.
 */
fun transformationError(request: TransformationRequest, sourceStock: FinishedProductStock?): String? {
    val p = request.product
    val have = sourceStock?.currentStock ?: 0.0
    return when {
        !p.isTransformed -> "${p.name} no está configurado como hecho de otro producto"
        request.sourceUnits <= 0.0 -> "Indica cuántos ${p.transformFromProductName} usaste"
        request.producedUnits <= 0.0 -> "Indica cuántos ${p.name} salieron"
        have + 1e-9 < request.sourceUnits ->
            "Según el inventario hay ${have.fmtTrim()} de ${p.transformFromProductName} en esta " +
                "sucursal. Si hay más, primero ajusta el inventario."
        else -> null
    }
}

/**
 * Los dos movimientos, en orden: primero sale el original y después entra el nuevo (si el
 * segundo falla queda un original de menos con su referencia, no producto sin costo).
 *
 * Costo de cada uno que sale = (originales × su costo promedio + [extrasCost]) ÷ los que salieron.
 */
fun transformationMovements(
    request: TransformationRequest,
    sourceStock: FinishedProductStock,
    extrasCost: Double,
    referenceId: String
): List<FinishedStockMovement> {
    val p = request.product
    val sourceCost = sourceStock.avgUnitCost
    val reason = buildString {
        append("${request.sourceUnits.fmtTrim()} ${p.transformFromProductName} → ${request.producedUnits.fmtTrim()} ${p.name}")
        request.note?.trim()?.takeIf { it.isNotEmpty() }?.let { append(" — $it") }
    }
    val base = FinishedStockMovement(
        branchId = request.branchId,
        branchName = request.branchName,
        type = FinishedMovementType.TRANSFORMACION,
        referenceType = FinishedMovementReference.TRANSFORMATION,
        referenceId = referenceId,
        reason = reason,
        createdByUserId = request.userId,
        createdByUserName = request.userName
    )
    return listOf(
        base.copy(
            productId = p.transformFromProductId,
            productName = p.transformFromProductName,
            quantity = -request.sourceUnits,
            unitPriceAtMovement = sourceCost
        ),
        base.copy(
            productId = p.id,
            productName = p.name,
            quantity = request.producedUnits,
            unitPriceAtMovement = (request.sourceUnits * sourceCost + extrasCost) / request.producedUnits
        )
    )
}
