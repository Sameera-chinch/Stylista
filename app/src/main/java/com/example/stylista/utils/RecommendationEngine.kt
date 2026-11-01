package com.example.stylista.utils

import android.content.Context
import com.example.stylista.database.ProductEntity
import com.example.stylista.database.StylistaDatabase
import kotlin.math.abs

object RecommendationEngine {

    suspend fun getHomeRecommendations(context: Context, limit: Int = 10): List<ProductEntity> {
        return try {
            val uid = UserPreferencesHelper.getActiveUserId(context)
            val selectedStyles = UserPreferencesHelper.getSelectedStyles(context, uid)
            val selectedSizes = UserPreferencesHelper.getSelectedSizes(context, uid)
            val (minBudget, maxBudget) = UserPreferencesHelper.getBudgetRange(context, uid)
            val isOnboarded = UserPreferencesHelper.isOnboardingCompleted(context, uid)

            // Only show when preferences are set
            if (selectedStyles.isEmpty() && selectedSizes.isEmpty() && !isOnboarded) {
                return emptyList()
            }

            val db = StylistaDatabase.getInstance(context)
            val allProducts = db.productDao().getAllProducts()
            if (allProducts.isEmpty()) return emptyList()

            val wishlistItems = db.wishlistDao().getWishlist(uid)
            val cartItems = db.cartDao().getCartItems(uid)
            val viewedIds = UserPreferencesHelper.getViewedProductIds(context, uid)

            val wishlistProductIds = wishlistItems.map { it.productId }.toSet()
            val cartProductIds = cartItems.map { it.productId }.toSet()

            // Filter products by selected styles, in-stock sizes, and budget range
            val matchedProducts = allProducts.filter { product ->
                if (!product.inStock) return@filter false

                val matchesStyle = if (selectedStyles.isNotEmpty()) {
                    selectedStyles.any { style ->
                        product.category.equals(style, ignoreCase = true) ||
                        product.subcategory.equals(style, ignoreCase = true) ||
                        product.category.contains(style, ignoreCase = true)
                    }
                } else true

                val matchesSize = if (selectedSizes.isNotEmpty()) {
                    product.isUniversalSize() || selectedSizes.any { size -> product.isSizeInStock(size) }
                } else true

                val matchesBudget = product.price in (minBudget * 85 / 100)..(maxBudget * 115 / 100)

                matchesStyle && matchesSize && matchesBudget
            }

            // Fallback if very narrow filters yield zero items, still prioritize in-stock and style
            val candidates = if (matchedProducts.isNotEmpty()) {
                matchedProducts
            } else {
                allProducts.filter { product ->
                    product.inStock && (selectedStyles.isEmpty() || selectedStyles.any { style ->
                        product.category.contains(style, ignoreCase = true)
                    })
                }
            }

            if (candidates.isEmpty()) return emptyList()

            val scoredProducts = candidates.map { product ->
                var score = 40.0

                // Match with user's selected styles
                if (selectedStyles.any { style -> product.category.contains(style, ignoreCase = true) }) {
                    score += 40.0
                }

                // In-stock preferred size
                if (selectedSizes.any { size -> product.isSizeInStock(size) }) {
                    score += 20.0
                }

                // Budget proximity
                if (product.price in minBudget..maxBudget) {
                    score += 20.0
                }

                // Novelty boost for products the user hasn't seen yet
                if (product.id.toString() !in viewedIds) {
                    score += 15.0
                }

                // Affinity from wishlist
                if (product.id in wishlistProductIds) {
                    score += 10.0
                }

                // Slight penalty if already in cart
                if (product.id in cartProductIds) {
                    score -= 15.0
                }

                product to score
            }

            scoredProducts
                .sortedByDescending { it.second }
                .map { it.first }
                .take(limit)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun getSimilarProducts(
        context: Context,
        currentProduct: ProductEntity,
        limit: Int = 6
    ): List<ProductEntity> {
        return try {
            val db = StylistaDatabase.getInstance(context)
            val allProducts = db.productDao().getAllProducts()

            allProducts
                .filter { it.id != currentProduct.id }
                .map { product ->
                    var score = 0.0

                    // Same category match
                    if (product.category.equals(currentProduct.category, ignoreCase = true)) {
                        score += 50.0
                    }

                    // Same subcategory match
                    if (currentProduct.subcategory.isNotBlank() &&
                        product.subcategory.equals(currentProduct.subcategory, ignoreCase = true)
                    ) {
                        score += 35.0
                    }

                    // Similar price range
                    val priceDiff = abs(product.price - currentProduct.price)
                    val priceScore = (15.0 - (priceDiff / 100.0)).coerceIn(0.0, 15.0)
                    score += priceScore

                    product to score
                }
                .filter { it.second > 0 }
                .sortedByDescending { it.second }
                .map { it.first }
                .take(limit)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}
