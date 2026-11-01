package com.example.stylista.repositories

import android.content.Context
import android.util.Log
import com.example.stylista.database.entities.CartEntity
import com.example.stylista.database.entities.ProductEntity
import com.example.stylista.database.StylistaDatabase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import com.example.stylista.database.dao.*
import com.example.stylista.database.entities.*
import com.example.stylista.models.*
import com.example.stylista.utils.*

object CartRepository {

    private const val TAG = "CartRepository"
    private const val PREF_GUEST_TIP_SHOWN = "pref_guest_tip_shown"

    fun getCartItemId(productId: Int, size: String): String {
        val cleanSize = size.trim().uppercase()
        return "${productId}_$cleanSize"
    }

    suspend fun addToCart(
        context: Context,
        product: ProductEntity,
        size: String,
        quantity: Int = 1
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val userId = UserPreferencesHelper.getActiveUserId(context)
        val db = StylistaDatabase.getInstance(context)
        val cartItemId = getCartItemId(product.id, size)

        val existingItem = db.cartDao().getCartItemByProductAndSize(userId, product.id, size)
        val finalQty = if (existingItem != null) existingItem.quantity + quantity else quantity

        if (existingItem != null) {
            db.cartDao().updateCartItem(existingItem.copy(quantity = finalQty))
        } else {
            db.cartDao().insertCartItem(
                CartEntity(
                    productId = product.id,
                    size = size,
                    quantity = finalQty,
                    userId = userId
                )
            )
        }

        // Sync to Firestore for authenticated users
        val authUser = FirebaseAuth.getInstance().currentUser
        if (authUser != null && !authUser.isAnonymous) {
            try {
                val cartDoc = hashMapOf(
                    "productId" to product.id,
                    "name" to product.name,
                    "price" to product.price,
                    "size" to size,
                    "quantity" to finalQty,
                    "imageUrl" to (product.imageUrl ?: ""),
                    "addedAt" to (existingItem?.let { System.currentTimeMillis() } ?: System.currentTimeMillis()),
                    "updatedAt" to System.currentTimeMillis()
                )

                FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(authUser.uid)
                    .collection("cart")
                    .document(cartItemId)
                    .set(cartDoc, SetOptions.merge())
                    .await()

                Log.d(TAG, "Successfully synced cart item $cartItemId to Firestore for user ${authUser.uid}")
            } catch (e: Exception) {
                Log.e(TAG, "Firestore write error for cart item $cartItemId", e)
                return@withContext Result.failure(e)
            }
        }

        Result.success(Unit)
    }

    suspend fun updateQuantity(
        context: Context,
        cartEntity: CartEntity,
        newQty: Int,
        product: ProductEntity? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val db = StylistaDatabase.getInstance(context)
        val cartItemId = getCartItemId(cartEntity.productId, cartEntity.size)

        if (newQty <= 0) {
            return@withContext removeFromCart(context, cartEntity)
        }

        db.cartDao().updateCartItem(cartEntity.copy(quantity = newQty))

        val authUser = FirebaseAuth.getInstance().currentUser
        if (authUser != null && !authUser.isAnonymous) {
            try {
                val updates = hashMapOf<String, Any>(
                    "quantity" to newQty,
                    "updatedAt" to System.currentTimeMillis()
                )
                if (product != null) {
                    updates["price"] = product.price
                    updates["name"] = product.name
                }

                FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(authUser.uid)
                    .collection("cart")
                    .document(cartItemId)
                    .set(updates, SetOptions.merge())
                    .await()

                Log.d(TAG, "Updated cart item $cartItemId quantity to $newQty in Firestore")
            } catch (e: Exception) {
                Log.e(TAG, "Firestore update quantity error for $cartItemId", e)
                return@withContext Result.failure(e)
            }
        }

        Result.success(Unit)
    }

    suspend fun removeFromCart(
        context: Context,
        cartEntity: CartEntity
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val db = StylistaDatabase.getInstance(context)
        val cartItemId = getCartItemId(cartEntity.productId, cartEntity.size)

        db.cartDao().deleteCartItem(cartEntity)

        val authUser = FirebaseAuth.getInstance().currentUser
        if (authUser != null && !authUser.isAnonymous) {
            try {
                FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(authUser.uid)
                    .collection("cart")
                    .document(cartItemId)
                    .delete()
                    .await()

                Log.d(TAG, "Deleted cart item $cartItemId from Firestore")
            } catch (e: Exception) {
                Log.e(TAG, "Firestore delete error for cart item $cartItemId", e)
                return@withContext Result.failure(e)
            }
        }

        Result.success(Unit)
    }

    suspend fun clearUserCart(
        context: Context,
        userId: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val db = StylistaDatabase.getInstance(context)
        db.cartDao().clearCart(userId)

        val authUser = FirebaseAuth.getInstance().currentUser
        if (authUser != null && !authUser.isAnonymous && authUser.uid == userId) {
            try {
                val firestore = FirebaseFirestore.getInstance()
                val snapshot = firestore.collection("users")
                    .document(authUser.uid)
                    .collection("cart")
                    .get()
                    .await()

                val batch = firestore.batch()
                for (doc in snapshot.documents) {
                    batch.delete(doc.reference)
                }
                batch.commit().await()
                Log.d(TAG, "Cleared all cart documents in Firestore for ${authUser.uid}")
            } catch (e: Exception) {
                Log.e(TAG, "Firestore clear cart error", e)
                return@withContext Result.failure(e)
            }
        }

        Result.success(Unit)
    }

    suspend fun syncCartOnLogin(
        context: Context,
        userUid: String,
        guestUserId: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val db = StylistaDatabase.getInstance(context)
            val firestore = FirebaseFirestore.getInstance()

            // 1. Fetch cloud cart items from Firestore
            val cloudSnapshot = firestore.collection("users")
                .document(userUid)
                .collection("cart")
                .get()
                .await()

            val cloudItemsMap = mutableMapOf<String, Int>() // cartItemId -> quantity
            for (doc in cloudSnapshot.documents) {
                val pId = doc.getLong("productId")?.toInt() ?: continue
                val sz = doc.getString("size") ?: "Free Size"
                val qty = doc.getLong("quantity")?.toInt() ?: 1
                val id = getCartItemId(pId, sz)
                cloudItemsMap[id] = qty
            }

            // 2. Fetch local guest items to merge if any
            val guestItems = if (!guestUserId.isNullOrBlank() && guestUserId != userUid) {
                db.cartDao().getCartItems(guestUserId)
            } else {
                emptyList()
            }

            // 3. Merge guest items into cloud
            val mergedMap = HashMap(cloudItemsMap)
            val batch = firestore.batch()

            for (guestItem in guestItems) {
                val id = getCartItemId(guestItem.productId, guestItem.size)
                val currentQty = mergedMap[id] ?: 0
                val combinedQty = currentQty + guestItem.quantity
                mergedMap[id] = combinedQty

                val product = db.productDao().getProductById(guestItem.productId)
                val docRef = firestore.collection("users")
                    .document(userUid)
                    .collection("cart")
                    .document(id)

                val docData = hashMapOf(
                    "productId" to guestItem.productId,
                    "name" to (product?.name ?: "Product #${guestItem.productId}"),
                    "price" to (product?.price ?: 0),
                    "size" to guestItem.size,
                    "quantity" to combinedQty,
                    "imageUrl" to (product?.imageUrl ?: ""),
                    "addedAt" to System.currentTimeMillis(),
                    "updatedAt" to System.currentTimeMillis()
                )
                batch.set(docRef, docData, SetOptions.merge())
            }

            if (guestItems.isNotEmpty() && !guestUserId.isNullOrBlank()) {
                batch.commit().await()
                // Clear local guest cart now that it is merged into account
                db.cartDao().clearCart(guestUserId)
                Log.d(TAG, "Merged ${guestItems.size} guest items into user $userUid")
            }

            // 4. Populate Room cache from the merged dataset
            // First clear current local user cache
            db.cartDao().clearCart(userUid)

            // Re-fetch all documents from Firestore to get full canonical state
            val finalSnapshot = firestore.collection("users")
                .document(userUid)
                .collection("cart")
                .get()
                .await()

            for (doc in finalSnapshot.documents) {
                val pId = doc.getLong("productId")?.toInt() ?: continue
                val sz = doc.getString("size") ?: "Free Size"
                val qty = doc.getLong("quantity")?.toInt() ?: 1

                db.cartDao().insertCartItem(
                    CartEntity(
                        productId = pId,
                        size = sz,
                        quantity = qty,
                        userId = userUid
                    )
                )
            }

            Log.d(TAG, "Synced ${finalSnapshot.size()} cart items from Firestore into Room for user $userUid")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync cart on login", e)
            Result.failure(e)
        }
    }

    fun shouldShowGuestTip(context: Context): Boolean {
        val prefs = context.getSharedPreferences("stylista_guest_tips", Context.MODE_PRIVATE)
        val shown = prefs.getBoolean(PREF_GUEST_TIP_SHOWN, false)
        if (!shown) {
            prefs.edit().putBoolean(PREF_GUEST_TIP_SHOWN, true).apply()
            return true
        }
        return false
    }
}
