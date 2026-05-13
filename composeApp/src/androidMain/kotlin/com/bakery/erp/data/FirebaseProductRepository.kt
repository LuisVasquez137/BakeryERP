package com.bakery.erp.data

import com.bakery.erp.domain.ProductRepository
import com.bakery.erp.model.Product
import com.bakery.erp.model.ProductCharacteristics
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.toMilliseconds
import dev.gitlive.firebase.storage.Data
import dev.gitlive.firebase.storage.FirebaseStorage

class FirebaseProductRepository(
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

    override suspend fun uploadImage(imageBytes: ByteArray, productId: String): String {
        val ref = storage.reference.child("products/$productId.jpg")
        ref.putData(Data(imageBytes))
        return ref.getDownloadUrl()
    }

    // ── Serialization ───────────────────────────────────────────────────────

    // Documento completamente plano — sin mapas anidados para evitar errores de serialización
    private fun Product.toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "name" to name,
        "description" to description,
        "category" to category,
        "imageUrl" to imageUrl,
        "active" to active,
        "salePrice" to salePrice,
        "unit" to unit,
        "weightPerUnit" to weightPerUnit,
        "rawWeightGrams" to rawWeightGrams,
        "shelfLife" to shelfLife,
        "storageType" to storageType,
        // Características aplanadas al nivel raíz
        "crustColor" to characteristics.crustColor,
        "crumbColor" to characteristics.crumbColor,
        "texture" to characteristics.texture,
        "shape" to characteristics.shape,
        "aroma" to characteristics.aroma,
        "flavorProfile" to characteristics.flavorProfile,
        "notes" to notes,
        "createdAt" to if (createdAt == 0L) System.currentTimeMillis() else createdAt,
        "updatedAt" to System.currentTimeMillis()
    )

    private fun DocumentSnapshot.toProduct() = Product(
        id = id,
        name = get<String?>("name") ?: "",
        description = get<String?>("description") ?: "",
        category = get<String?>("category") ?: "",
        imageUrl = get<String?>("imageUrl"),
        active = get<Boolean?>("active") ?: true,
        salePrice = get<Double?>("salePrice") ?: 0.0,
        unit = get<String?>("unit") ?: "",
        weightPerUnit = get<Double?>("weightPerUnit"),
        rawWeightGrams = get<Double?>("rawWeightGrams"),
        shelfLife = get<Long?>("shelfLife")?.toInt(),
        storageType = get<String?>("storageType"),
        characteristics = ProductCharacteristics(
            crustColor = get<String?>("crustColor"),
            crumbColor = get<String?>("crumbColor"),
            texture = get<String?>("texture"),
            shape = get<String?>("shape"),
            aroma = get<String?>("aroma"),
            flavorProfile = get<String?>("flavorProfile")
        ),
        notes = get<String?>("notes"),
        createdAt = readTimestampAsMillis("createdAt"),
        updatedAt = readTimestampAsMillis("updatedAt")
    )

    // Lee un campo que puede ser Timestamp (documentos viejos) o Long (documentos nuevos)
    private fun DocumentSnapshot.readTimestampAsMillis(field: String): Long =
        runCatching { get<Timestamp?>(field)?.toMilliseconds()?.toLong() }.getOrNull()
            ?: runCatching { get<Long?>(field) }.getOrNull()
            ?: 0L
}
