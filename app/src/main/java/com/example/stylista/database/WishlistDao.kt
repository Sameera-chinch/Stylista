package com.example.stylista.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface WishlistDao {
    @Query("SELECT * FROM wishlist WHERE userId = :userId ORDER BY addedTimestamp DESC")
    suspend fun getWishlist(userId: String): List<WishlistEntity>

    @Query("SELECT * FROM wishlist ORDER BY addedTimestamp DESC")
    suspend fun getWishlist(): List<WishlistEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWishlistItem(item: WishlistEntity): Long

    @Delete
    suspend fun deleteWishlistItem(item: WishlistEntity): Int

    @Query("DELETE FROM wishlist WHERE userId = :userId AND productId = :productId")
    suspend fun deleteWishlistItemByProductId(userId: String, productId: Int): Int

    @Query("DELETE FROM wishlist WHERE productId = :productId")
    suspend fun deleteWishlistItemByProductId(productId: Int): Int

    @Query("DELETE FROM wishlist WHERE userId = :userId")
    suspend fun clearWishlist(userId: String): Int

    @Query("DELETE FROM wishlist")
    suspend fun clearWishlist(): Int

    @Query("SELECT EXISTS(SELECT 1 FROM wishlist WHERE userId = :userId AND productId = :productId)")
    suspend fun isProductWishlisted(userId: String, productId: Int): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM wishlist WHERE productId = :productId)")
    suspend fun isProductWishlisted(productId: Int): Boolean
}
