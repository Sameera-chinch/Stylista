package com.example.stylista.utils

import android.content.Context
import android.util.Log
import com.example.stylista.database.ProductEntity
import com.example.stylista.database.StylistaDatabase
import com.example.stylista.database.WishlistEntity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

object WishlistRepository {

    private const val TAG = "WishlistRepository"

    suspend fun toggleWishlist(
        context: Context,
        product: ProductEntity
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        val userId = UserPreferencesHelper.getActiveUserId(context)
        val db = StylistaDatabase.getInstance(context)
        val isCurrentlyInWishlist = db.wishlistDao().isProductWishlisted(userId, product.id)
        val authUser = FirebaseAuth.getInstance().currentUser

        if (isCurrentlyInWishlist) {
            // Remove
            db.wishlistDao().deleteWishlistItemByProductId(userId, product.id)

            if (authUser != null && !authUser.isAnonymous) {
                try {
                    FirebaseFirestore.getInstance()
                        .collection("users")
                        .document(authUser.uid)
                        .collection("wishlist")
                        .document(product.id.toString())
                        .delete()
                        .await()
                    Log.d(TAG, "Deleted product ${product.id} from Firestore wishlist")
                } catch (e: Exception) {
                    Log.e(TAG, "Firestore error deleting wishlist item", e)
                    return@withContext Result.failure(e)
                }
            }
            Result.success(false) // Not wishlisted
        } else {
            // Add
            db.wishlistDao().insertWishlistItem(
                WishlistEntity(
                    productId = product.id,
                    addedTimestamp = System.currentTimeMillis(),
                    userId = userId
                )
            )

            if (authUser != null && !authUser.isAnonymous) {
                try {
                    val wishlistDoc = hashMapOf(
                        "productId" to product.id,
                        "name" to product.name,
                        "price" to product.price,
                        "imageUrl" to (product.imageUrl ?: ""),
                        "category" to product.category,
                        "addedAt" to System.currentTimeMillis()
                    )

                    FirebaseFirestore.getInstance()
                        .collection("users")
                        .document(authUser.uid)
                        .collection("wishlist")
                        .document(product.id.toString())
                        .set(wishlistDoc, SetOptions.merge())
                        .await()
                    Log.d(TAG, "Added product ${product.id} to Firestore wishlist")
                } catch (e: Exception) {
                    Log.e(TAG, "Firestore error adding wishlist item", e)
                    return@withContext Result.failure(e)
                }
            }
            Result.success(true) // Wishlisted
        }
    }

    suspend fun syncWishlistOnLogin(
        context: Context,
        userUid: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val db = StylistaDatabase.getInstance(context)
            val firestore = FirebaseFirestore.getInstance()

            val snapshot = firestore.collection("users")
                .document(userUid)
                .collection("wishlist")
                .get()
                .await()

            for (doc in snapshot.documents) {
                val pId = doc.getLong("productId")?.toInt() ?: continue
                if (!db.wishlistDao().isProductWishlisted(userUid, pId)) {
                    db.wishlistDao().insertWishlistItem(
                        WishlistEntity(
                            productId = pId,
                            addedTimestamp = doc.getLong("addedAt") ?: System.currentTimeMillis(),
                            userId = userUid
                        )
                    )
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync wishlist on login", e)
            Result.failure(e)
        }
    }
}
