package com.bakery.erp.data

import com.bakery.erp.domain.IngredientUsage
import com.bakery.erp.domain.RecipeRepository
import com.bakery.erp.model.Recipe
import com.bakery.erp.model.RecipeComponent
import com.bakery.erp.model.RecipeStage
import com.bakery.erp.model.RecipeYield
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.toMilliseconds

/**
 * Estructura Firestore:
 *   companies/{orgId}/recipes/{recipeId}                    ← datos de la receta (flat)
 *   companies/{orgId}/recipes/{recipeId}/stages/{stageId}   ← etapas
 *   companies/{orgId}/recipes/{recipeId}/components/{id}    ← ingredientes (con stageId)
 *   companies/{orgId}/recipes/{recipeId}/yields/{yieldId}   ← productos que produce
 */
class FirebaseRecipeRepository(
    private val firestore: FirebaseFirestore
) : RecipeRepository {

    private fun col(companyId: String) =
        firestore.collection("companies/$companyId/recipes")

    private fun stagesCol(companyId: String, recipeId: String) =
        firestore.collection("companies/$companyId/recipes/$recipeId/stages")

    private fun componentsCol(companyId: String, recipeId: String) =
        firestore.collection("companies/$companyId/recipes/$recipeId/components")

    private fun yieldsCol(companyId: String, recipeId: String) =
        firestore.collection("companies/$companyId/recipes/$recipeId/yields")

    // ── Lectura ──────────────────────────────────────────────────────────────

    override suspend fun getRecipes(companyId: String): List<Recipe> {
        return col(companyId).get().documents.map { it.toRecipe() }
    }

    override suspend fun getRecipesWithYields(companyId: String): List<Recipe> {
        val docs = col(companyId).get().documents
        return docs.map { doc ->
            val yields = runCatching {
                yieldsCol(companyId, doc.id).get().documents.map { it.toRecipeYield() }
            }.getOrElse { emptyList() }
            doc.toRecipe().copy(yields = yields)
        }
    }

    override suspend fun getRecipe(companyId: String, recipeId: String): Recipe {
        val doc = col(companyId).document(recipeId).get()
        val stages = runCatching {
            stagesCol(companyId, recipeId).get().documents.map { it.toRecipeStage() }
        }.getOrElse { emptyList() }

        val allComponents = runCatching {
            componentsCol(companyId, recipeId).get().documents.map { it.toRecipeComponent() }
        }.getOrElse { emptyList() }

        val yields = runCatching {
            yieldsCol(companyId, recipeId).get().documents.map { it.toRecipeYield() }
        }.getOrElse { emptyList() }

        val componentsByStage = allComponents.groupBy { it.stageId }
        val hydratedStages = stages
            .sortedBy { it.order }
            .map { stage ->
                stage.copy(
                    components = (componentsByStage[stage.id] ?: emptyList())
                        .sortedBy { it.order }
                )
            }

        return doc.toRecipe().copy(stages = hydratedStages, yields = yields)
    }

    // ── Escritura ────────────────────────────────────────────────────────────

    override suspend fun addRecipe(companyId: String, recipe: Recipe) {
        val docRef = if (recipe.id.isEmpty()) col(companyId).document
        else col(companyId).document(recipe.id)
        val recipeId = docRef.id
        val finalRecipe = recipe.copy(id = recipeId)
        docRef.set(finalRecipe.toMap())
        writeSubcollections(companyId, recipeId, finalRecipe)
    }

    override suspend fun updateRecipe(companyId: String, recipe: Recipe) {
        col(companyId).document(recipe.id).set(recipe.toMap())
        deleteSubcollections(companyId, recipe.id)
        writeSubcollections(companyId, recipe.id, recipe)
    }

    override suspend fun deleteRecipe(companyId: String, recipeId: String) {
        deleteSubcollections(companyId, recipeId)
        col(companyId).document(recipeId).delete()
    }

    override suspend fun getRecipesUsingIngredient(
        companyId: String,
        ingredientId: String
    ): List<IngredientUsage> {
        val recipeDocs = runCatching { col(companyId).get().documents }.getOrElse { emptyList() }
        val result = mutableListOf<IngredientUsage>()
        for (doc in recipeDocs) {
            val matchingComponents = runCatching {
                componentsCol(companyId, doc.id).get().documents
                    .filter { it.get<String?>("ingredientId") == ingredientId }
            }.getOrElse { emptyList() }
            for (comp in matchingComponents) {
                result.add(
                    IngredientUsage(
                        recipeId       = doc.id,
                        recipeName     = doc.get<String?>("name") ?: "",
                        recipeCategory = doc.get<String?>("category") ?: "",
                        percentage     = comp.get<Double?>("percentage") ?: 0.0,
                        isBase         = comp.get<Boolean?>("isBase") ?: false,
                        ingredientUnit = comp.get<String?>("ingredientUnit") ?: ""
                    )
                )
            }
        }
        return result
    }

    // ── Helpers de escritura ─────────────────────────────────────────────────

    private suspend fun writeSubcollections(companyId: String, recipeId: String, recipe: Recipe) {
        recipe.stages.forEach { stage ->
            stagesCol(companyId, recipeId).document(stage.id).set(stage.toMap())
            stage.components.forEach { component ->
                componentsCol(companyId, recipeId).document(component.id).set(component.toMap())
            }
        }
        recipe.yields.forEach { yld ->
            yieldsCol(companyId, recipeId).document(yld.id).set(yld.toMap())
        }
    }

    private suspend fun deleteSubcollections(companyId: String, recipeId: String) {
        runCatching {
            componentsCol(companyId, recipeId).get().documents.forEach { doc ->
                componentsCol(companyId, recipeId).document(doc.id).delete()
            }
        }
        runCatching {
            stagesCol(companyId, recipeId).get().documents.forEach { doc ->
                stagesCol(companyId, recipeId).document(doc.id).delete()
            }
        }
        runCatching {
            yieldsCol(companyId, recipeId).get().documents.forEach { doc ->
                yieldsCol(companyId, recipeId).document(doc.id).delete()
            }
        }
    }

    // ── Serialización → Firestore ────────────────────────────────────────────

    private fun Recipe.toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "name" to name,
        "description" to description,
        "category" to category,
        "inputMode" to inputMode,
        "stageCount" to stages.size,
        "yieldCount" to yields.size,
        "notes" to notes,
        "active" to active,
        "createdAt" to if (createdAt == 0L) System.currentTimeMillis() else createdAt,
        "updatedAt" to System.currentTimeMillis()
        // stages y yields NO se guardan en el documento raíz
    )

    private fun RecipeStage.toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "name" to name,
        "order" to order,
        "type" to type,
        "instructions" to instructions,
        "restTimeMinutes" to restTimeMinutes,
        "notes" to notes
        // components NO se guardan en el documento de la etapa
    )

    private fun RecipeComponent.toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "stageId" to stageId,
        "ingredientId" to ingredientId,
        "ingredientName" to ingredientName,
        "ingredientUnit" to ingredientUnit,
        "percentage" to percentage,
        "isBase" to isBase,
        "order" to order
    )

    private fun RecipeYield.toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "productId" to productId,
        "productName" to productName
    )

    // ── Deserialización ← Firestore ──────────────────────────────────────────

    private fun DocumentSnapshot.toRecipe() = Recipe(
        id = id,
        name = get<String?>("name") ?: "",
        description = get<String?>("description"),
        category = get<String?>("category") ?: "",
        inputMode = get<String?>("inputMode") ?: "PERCENTAGE",
        stageCount = get<Long?>("stageCount")?.toInt() ?: 0,
        yieldCount = get<Long?>("yieldCount")?.toInt() ?: 0,
        notes = get<String?>("notes"),
        active = get<Boolean?>("active") ?: true,
        createdAt = readTimestampAsMillis("createdAt"),
        updatedAt = readTimestampAsMillis("updatedAt")
    )

    private fun DocumentSnapshot.toRecipeStage() = RecipeStage(
        id = id,
        name = get<String?>("name") ?: "",
        order = get<Long?>("order")?.toInt() ?: 0,
        type = get<String?>("type") ?: "MASA_PRINCIPAL",
        instructions = get<String?>("instructions"),
        restTimeMinutes = get<Long?>("restTimeMinutes")?.toInt(),
        notes = get<String?>("notes")
    )

    private fun DocumentSnapshot.toRecipeComponent() = RecipeComponent(
        id = id,
        stageId = get<String?>("stageId") ?: "",
        ingredientId = get<String?>("ingredientId") ?: "",
        ingredientName = get<String?>("ingredientName") ?: "",
        ingredientUnit = get<String?>("ingredientUnit") ?: "",
        percentage = get<Double?>("percentage") ?: 0.0,
        isBase = get<Boolean?>("isBase") ?: false,
        order = get<Long?>("order")?.toInt() ?: 0
    )

    private fun DocumentSnapshot.toRecipeYield() = RecipeYield(
        id = id,
        productId = get<String?>("productId") ?: "",
        productName = get<String?>("productName") ?: ""
    )

    private fun DocumentSnapshot.readTimestampAsMillis(field: String): Long =
        runCatching { get<Timestamp?>(field)?.toMilliseconds()?.toLong() }.getOrNull()
            ?: runCatching { get<Long?>(field) }.getOrNull()
            ?: 0L
}
