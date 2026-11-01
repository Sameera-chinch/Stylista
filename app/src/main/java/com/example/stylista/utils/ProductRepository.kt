package com.example.stylista.utils

import android.content.Context
import android.util.Log
import com.example.stylista.R
import com.example.stylista.database.ProductEntity
import com.example.stylista.database.StylistaDatabase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import org.json.JSONArray

object ProductRepository {

    private const val TAG = "ProductRepository"

    fun loadSeedProductsFromAsset(context: Context): List<ProductEntity> {
        val productList = mutableListOf<ProductEntity>()
        try {
            val jsonString = context.assets.open("products_seed.json").bufferedReader().use { it.readText() }
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = obj.getInt("id")
                val name = obj.getString("name")
                val price = obj.getInt("price")
                val imageResName = obj.optString("imageResName", "")
                val imageUrl = obj.optString("imageUrl", "")
                val category = obj.getString("category")
                val subcategory = obj.optString("subcategory", "")
                val availableSizes = obj.optString("availableSizes", "Free Size")
                val inStockSizes = obj.optString("inStockSizes", "Free Size")
                val description = obj.optString("description", "")
                val rating = obj.optDouble("rating", 4.7)
                val reviewCount = obj.optInt("reviewCount", 100)
                val inStock = obj.optBoolean("inStock", true)
                val isFeatured = obj.optBoolean("isFeatured", false)
                val isNewArrival = obj.optBoolean("isNewArrival", false)

                val resId = if (imageResName.isNotEmpty()) {
                    context.resources.getIdentifier(imageResName, "drawable", context.packageName)
                } else 0
                val finalRes = if (resId != 0) resId else R.drawable.ic_placeholder_product

                productList.add(
                    ProductEntity(
                        id = id,
                        name = name,
                        price = price,
                        imageRes = finalRes,
                        category = category,
                        subcategory = subcategory,
                        availableSizes = availableSizes,
                        inStockSizes = inStockSizes,
                        description = description,
                        rating = rating,
                        reviewCount = reviewCount,
                        imageUrl = imageUrl,
                        inStock = inStock,
                        createdAt = System.currentTimeMillis(),
                        isFeatured = isFeatured,
                        isNewArrival = isNewArrival
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading seed products from asset", e)
        }
        return productList
    }

    suspend fun ensureDatabaseInitialized(context: Context): List<ProductEntity> {
        val db = StylistaDatabase.getInstance(context)
        val productDao = db.productDao()
        val existingCount = productDao.getProductsCount()

        if (existingCount == 0) {
            val seedList = loadSeedProductsFromAsset(context)
            if (seedList.isNotEmpty()) {
                productDao.insertProducts(seedList)
                Log.d(TAG, "Populated Room database with ${seedList.size} seed products from JSON asset")
            }
        } else {
            // Ensure any existing products have imageUrl populated
            val existing = productDao.getAllProducts()
            val needUpdate = existing.filter { it.imageUrl.isBlank() }
            if (needUpdate.isNotEmpty()) {
                val updated = needUpdate.map { p ->
                    p.copy(imageUrl = "file:///android_asset/product_images/${p.id}.webp")
                }
                productDao.insertProducts(updated)
                Log.d(TAG, "Updated ${updated.size} existing products with asset imageUrls")
            }
        }
        return productDao.getAllProducts()
    }

    suspend fun syncWithFirestore(context: Context) {
        val firestore = FirebaseFirestore.getInstance()
        val db = StylistaDatabase.getInstance(context)
        val productDao = db.productDao()

        try {
            val snapshot = firestore.collection("products").get().await()
            if (!snapshot.isEmpty) {
                // Firestore has products: sync to Room single source of truth
                val products = mutableListOf<ProductEntity>()
                for (doc in snapshot.documents) {
                    val id = doc.getLong("id")?.toInt() ?: doc.id.toIntOrNull() ?: continue
                    val name = doc.getString("name") ?: ""
                    val price = doc.getLong("price")?.toInt() ?: 0
                    val imageResName = doc.getString("imageResName") ?: ""
                    val rawImageUrl = doc.getString("imageUrl") ?: ""
                    val imageUrl = if (rawImageUrl.isNotBlank()) rawImageUrl else "file:///android_asset/product_images/$id.webp"
                    val category = doc.getString("category") ?: ""
                    val subcategory = doc.getString("subcategory") ?: ""
                    val availableSizes = doc.getString("availableSizes") ?: "Free Size"
                    val inStockSizes = doc.getString("inStockSizes") ?: "Free Size"
                    val description = doc.getString("description") ?: ""
                    val rating = doc.getDouble("rating") ?: 4.7
                    val reviewCount = doc.getLong("reviewCount")?.toInt() ?: 100
                    val inStock = doc.getBoolean("inStock") ?: true
                    val isFeatured = doc.getBoolean("isFeatured") ?: false
                    val isNewArrival = doc.getBoolean("isNewArrival") ?: false

                    val resId = if (imageResName.isNotEmpty()) {
                        context.resources.getIdentifier(imageResName, "drawable", context.packageName)
                    } else 0
                    val finalRes = if (resId != 0) resId else R.drawable.ic_placeholder_product

                    products.add(
                        ProductEntity(
                            id = id,
                            name = name,
                            price = price,
                            imageRes = finalRes,
                            category = category,
                            subcategory = subcategory,
                            availableSizes = availableSizes,
                            inStockSizes = inStockSizes,
                            description = description,
                            rating = rating,
                            reviewCount = reviewCount,
                            imageUrl = imageUrl,
                            inStock = inStock,
                            createdAt = System.currentTimeMillis(),
                            isFeatured = isFeatured,
                            isNewArrival = isNewArrival
                        )
                    )
                }

                if (products.isNotEmpty()) {
                    productDao.insertProducts(products)
                    Log.d(TAG, "Successfully synced ${products.size} products from Firestore to Room")
                }
            } else {
                // Firestore collection is empty.
                // Seed Firestore ONLY if signed in as a real non-guest user
                val currentUser = FirebaseAuth.getInstance().currentUser
                if (currentUser != null && !currentUser.isAnonymous) {
                    Log.d(TAG, "Firestore collection 'products' is empty. User ${currentUser.uid} signed in. Seeding Firestore...")
                    val localProducts = productDao.getAllProducts()
                    val batch = firestore.batch()
                    for (product in localProducts) {
                        val docRef = firestore.collection("products").document(product.id.toString())
                        val data = hashMapOf(
                            "id" to product.id,
                            "name" to product.name,
                            "price" to product.price,
                            "imageUrl" to product.imageUrl,
                            "category" to product.category,
                            "subcategory" to product.subcategory,
                            "availableSizes" to product.availableSizes,
                            "inStockSizes" to product.inStockSizes,
                            "description" to product.description,
                            "rating" to product.rating,
                            "reviewCount" to product.reviewCount,
                            "inStock" to product.inStock,
                            "isFeatured" to product.isFeatured,
                            "isNewArrival" to product.isNewArrival
                        )
                        batch.set(docRef, data)
                    }
                    batch.commit().await()
                    Log.d(TAG, "Successfully seeded ${localProducts.size} products to Firestore!")
                } else {
                    Log.d(TAG, "Firestore is empty, but user is guest/anonymous or not signed in. Skipping seed write.")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during Firestore product sync", e)
        }
    }
}
