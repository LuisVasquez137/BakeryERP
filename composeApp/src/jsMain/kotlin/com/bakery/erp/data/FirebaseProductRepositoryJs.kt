package com.bakery.erp.data

import com.bakery.erp.domain.ProductRepository
import com.bakery.erp.model.BakingTechnicalSheet
import com.bakery.erp.model.DoughFilling
import com.bakery.erp.model.Product
import com.bakery.erp.model.ProductCharacteristics
import com.bakery.erp.model.ProductDecoration
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.toMilliseconds
import dev.gitlive.firebase.storage.Data
import dev.gitlive.firebase.storage.FirebaseStorage
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.time.Clock
import org.khronos.webgl.Int8Array
import org.khronos.webgl.Uint8Array

private fun ByteArray.toUint8Array(): Uint8Array {
    val int8 = unsafeCast<Int8Array>()
    return Uint8Array(int8.buffer, int8.byteOffset, int8.length)
}

class FirebaseProductRepositoryJs(
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage
) : ProductRepository {

    private fun productsCol(companyId: String) =
        firestore.collection("companies/$companyId/products")

    override suspend fun getProducts(companyId: String): List<Product> =
        productsCol(companyId).get().documents.map { it.toProduct() }

    override suspend fun addProduct(companyId: String, product: Product) {
        val docRef = if (product.id.isEmpty()) productsCol(companyId).document
        else productsCol(companyId).document(product.id)
        docRef.set(product.copy(id = docRef.id).toMap())
    }

    override suspend fun updateProduct(companyId: String, product: Product) {
        productsCol(companyId).document(product.id).set(product.toMap())
    }

    override suspend fun deleteProduct(companyId: String, productId: String) {
        productsCol(companyId).document(productId).delete()
    }

    override suspend fun updateRawWeights(companyId: String, gramsByProductId: Map<String, Double>, unit: String) {
        if (gramsByProductId.isEmpty()) return
        coroutineScope {
            gramsByProductId.map { (productId, grams) ->
                async {
                    runCatching {
                        productsCol(companyId).document(productId).update(
                            firestoreSafeOf("rawWeightGrams" to grams, "rawWeightUnit" to unit)
                        )
                    }
                }
            }.awaitAll()
        }
    }

    override suspend fun updatePurchaseFormats(companyId: String, product: Product) {
        productsCol(companyId).document(product.id).update(
            firestoreSafeOf(
                *productPurchaseFormatFields(product.purchaseFormats).toList().toTypedArray(),
                "supplierId" to product.supplierId,
                "supplierName" to product.supplierName,
                "purchaseCost" to product.purchaseCost
            )
        )
    }

    override suspend fun uploadImage(imageBytes: ByteArray, productId: String): String {
        val ref = storage.reference.child("products/$productId.jpg")
        ref.putData(Data(imageBytes.toUint8Array()))
        return ref.getDownloadUrl()
    }

    private fun Product.toMap(): Map<String, Any?> {
        // Kotlin/JS Long es un objeto custom que el SDK de Firestore Web no
        // serializa ("Unsupported field value: a custom Long object"). Los
        // timestamps se mandan como Double (número JS nativo); epoch millis
        // cabe de sobra en Double sin pérdida de precisión.
        val now = Clock.System.now().toEpochMilliseconds().toDouble()
        return (mapOf(
            "id" to id,
            "name" to name,
            "description" to description,
            "category" to category,
            "imageUrl" to imageUrl,
            "active" to active,
            "salePrice" to salePrice,
            "employeeDiscountPercent" to employeeDiscountPercent,
            "unit" to unit,
            "weightPerUnit" to weightPerUnit,
            "rawWeightGrams" to rawWeightGrams,
            "rawWeightUnit" to rawWeightUnit,
            "shelfLife" to shelfLife,
            "storageType" to storageType,
            "unitsPerTray" to unitsPerTray,
            "crustColor" to characteristics.crustColor,
            "crumbColor" to characteristics.crumbColor,
            "texture" to characteristics.texture,
            "shape" to characteristics.shape,
            "aroma" to characteristics.aroma,
            "flavorProfile" to characteristics.flavorProfile,
            "bakingTemperatureCelsius" to technicalSheet.bakingTemperatureCelsius,
            "bakingTimeMinutes" to technicalSheet.bakingTimeMinutes,
            "fermentationTimeHours" to technicalSheet.fermentationTimeHours,
            "fermentationTemperatureCelsius" to technicalSheet.fermentationTemperatureCelsius,
            "hydrationPercentage" to technicalSheet.hydrationPercentage,
            "shapingNotes" to technicalSheet.shapingNotes,
            "bakingNotes" to technicalSheet.bakingNotes,
            // Arreglos paralelos en vez de List<Map<String, Any?>>: GitLive no tiene
            // serializer para `Any` (ver feedback_gitlive_any_serializer) — un
            // adorno guardado nunca volvía a leerse de vuelta.
            "decorationPreparationIds" to decorations.map { it.preparationId },
            "decorationPreparationNames" to decorations.map { it.preparationName },
            "decorationIngredientIds" to decorations.map { it.ingredientId },
            "decorationIngredientNames" to decorations.map { it.ingredientName },
            "decorationGramsPerUnit" to decorations.map { it.gramsPerUnit },
            "doughFillingRecipeIds" to doughFillings.map { it.recipeId },
            "doughFillingRecipeNames" to doughFillings.map { it.recipeName },
            "doughFillingRawWeightGrams" to doughFillings.map { it.rawWeightGrams },
            "isInHouseProduced" to isInHouseProduced,
            "supplierId" to supplierId,
            "supplierName" to supplierName,
            "purchaseCost" to purchaseCost,
            "barcode" to barcode,
            "isPerishable" to isPerishable,
            // Producto respaldado por un insumo (huevo): vende del BranchStock del insumo
            "linkedIngredientId" to linkedIngredientId,
            "linkedIngredientName" to linkedIngredientName,
            "ingredientUnitsPerSale" to ingredientUnitsPerSale,
            "portionOfProductId" to portionOfProductId,
            "portionOfProductName" to portionOfProductName,
            "portionsPerWhole" to portionsPerWhole,
            "notes" to notes,
            "createdAt" to if (createdAt == 0L) now else createdAt.toDouble(),
            "updatedAt" to now
        ) + productPurchaseFormatFields(purchaseFormats)).filterValues { it != null }
    }

    private fun DocumentSnapshot.toProduct() = Product(
        id = id,
        name = get<String?>("name") ?: "",
        description = get<String?>("description") ?: "",
        category = get<String?>("category") ?: "",
        imageUrl = get<String?>("imageUrl"),
        active = get<Boolean?>("active") ?: true,
        salePrice = get<Double?>("salePrice") ?: 0.0,
        employeeDiscountPercent = runCatching { get<Double?>("employeeDiscountPercent") }.getOrNull() ?: 0.0,
        unit = get<String?>("unit") ?: "",
        weightPerUnit = get<Double?>("weightPerUnit"),
        rawWeightGrams = get<Double?>("rawWeightGrams"),
        rawWeightUnit = get<String?>("rawWeightUnit"),
        shelfLife = get<Long?>("shelfLife")?.toInt(),
        storageType = get<String?>("storageType"),
        unitsPerTray = runCatching { get<Long?>("unitsPerTray")?.toInt() }.getOrNull() ?: 0,
        characteristics = ProductCharacteristics(
            crustColor = get<String?>("crustColor"),
            crumbColor = get<String?>("crumbColor"),
            texture = get<String?>("texture"),
            shape = get<String?>("shape"),
            aroma = get<String?>("aroma"),
            flavorProfile = get<String?>("flavorProfile")
        ),
        technicalSheet = BakingTechnicalSheet(
            bakingTemperatureCelsius = get<Long?>("bakingTemperatureCelsius")?.toInt(),
            bakingTimeMinutes = get<Long?>("bakingTimeMinutes")?.toInt(),
            fermentationTimeHours = get<Double?>("fermentationTimeHours"),
            fermentationTemperatureCelsius = get<Long?>("fermentationTemperatureCelsius")?.toInt(),
            hydrationPercentage = get<Double?>("hydrationPercentage"),
            shapingNotes = get<String?>("shapingNotes"),
            bakingNotes = get<String?>("bakingNotes")
        ),
        decorations = runCatching {
            val preparationIds = get<List<String>?>("decorationPreparationIds") ?: emptyList()
            val preparationNames = get<List<String>?>("decorationPreparationNames") ?: emptyList()
            val ingredientIds = get<List<String>?>("decorationIngredientIds") ?: emptyList()
            val ingredientNames = get<List<String>?>("decorationIngredientNames") ?: emptyList()
            val gramsPerUnit = get<List<Double>?>("decorationGramsPerUnit") ?: emptyList()
            preparationIds.indices.map { i ->
                ProductDecoration(
                    preparationId = preparationIds[i],
                    preparationName = preparationNames.getOrElse(i) { "" },
                    ingredientId = ingredientIds.getOrElse(i) { "" },
                    ingredientName = ingredientNames.getOrElse(i) { "" },
                    gramsPerUnit = gramsPerUnit.getOrElse(i) { 0.0 }
                )
            }
        }.getOrElse { emptyList() },
        doughFillings = runCatching {
            val recipeIds = get<List<String>?>("doughFillingRecipeIds") ?: emptyList()
            val recipeNames = get<List<String>?>("doughFillingRecipeNames") ?: emptyList()
            val rawWeightGrams = get<List<Double>?>("doughFillingRawWeightGrams") ?: emptyList()
            recipeIds.indices.map { i ->
                DoughFilling(
                    recipeId = recipeIds[i],
                    recipeName = recipeNames.getOrElse(i) { "" },
                    rawWeightGrams = rawWeightGrams.getOrElse(i) { 0.0 }
                )
            }
        }.getOrElse { emptyList() },
        isInHouseProduced = runCatching { get<Boolean?>("isInHouseProduced") }.getOrNull() ?: true,
        purchaseFormats = readProductPurchaseFormats(),
        supplierId = runCatching { get<String?>("supplierId") }.getOrNull(),
        supplierName = runCatching { get<String?>("supplierName") }.getOrNull(),
        purchaseCost = runCatching { get<Double?>("purchaseCost") }.getOrNull(),
        barcode = runCatching { get<String?>("barcode") }.getOrNull() ?: "",
        isPerishable = runCatching { get<Boolean?>("isPerishable") }.getOrNull() ?: false,
        linkedIngredientId = runCatching { get<String?>("linkedIngredientId") }.getOrNull() ?: "",
        linkedIngredientName = runCatching { get<String?>("linkedIngredientName") }.getOrNull() ?: "",
        // 0 en docs viejos; `ingredientUnitsFor` lo trata como uno a uno.
        ingredientUnitsPerSale = runCatching { get<Double?>("ingredientUnitsPerSale") }.getOrNull() ?: 1.0,
        portionOfProductId = runCatching { get<String?>("portionOfProductId") }.getOrNull() ?: "",
        portionOfProductName = runCatching { get<String?>("portionOfProductName") }.getOrNull() ?: "",
        portionsPerWhole = runCatching { get<Long?>("portionsPerWhole")?.toInt() }.getOrNull() ?: 0,
        notes = get<String?>("notes"),
        createdAt = readTimestampAsMillis("createdAt"),
        updatedAt = readTimestampAsMillis("updatedAt")
    )

    private fun DocumentSnapshot.readTimestampAsMillis(field: String): Long =
        runCatching { get<Timestamp?>(field)?.toMilliseconds()?.toLong() }.getOrNull()
            ?: runCatching { get<Long?>(field) }.getOrNull()
            ?: 0L
}
