package com.example.stylista.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.stylista.database.entities.CartEntity
import com.example.stylista.database.entities.ProductEntity
import com.example.stylista.database.entities.*

@Dao
interface CartDao {
    @Query("SELECT * FROM cart WHERE userId = :userId")
    suspend fun getCartItems(userId: String): List<CartEntity>

    @Query("SELECT * FROM cart")
    suspend fun getAllCartItems(): List<CartEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCartItem(item: CartEntity): Long

    @Update
    suspend fun updateCartItem(item: CartEntity): Int

    @Delete
    suspend fun deleteCartItem(item: CartEntity): Int

    @Query("DELETE FROM cart WHERE userId = :userId")
    suspend fun clearCart(userId: String): Int

    @Query("DELETE FROM cart")
    suspend fun clearCart(): Int

    @Query("SELECT * FROM cart WHERE userId = :userId AND productId = :productId LIMIT 1")
    suspend fun getCartItemByProductId(userId: String, productId: Int): CartEntity?

    @Query("SELECT * FROM cart WHERE productId = :productId LIMIT 1")
    suspend fun getCartItemByProductId(productId: Int): CartEntity?

    @Query("SELECT * FROM cart WHERE userId = :userId AND productId = :productId AND size = :size LIMIT 1")
    suspend fun getCartItemByProductAndSize(userId: String, productId: Int, size: String): CartEntity?

    @Query("SELECT * FROM cart WHERE productId = :productId AND size = :size LIMIT 1")
    suspend fun getCartItemByProductAndSize(productId: Int, size: String): CartEntity?

    @Query("SELECT SUM(products.price * cart.quantity) FROM cart INNER JOIN products ON cart.productId = products.id WHERE cart.userId = :userId")
    suspend fun getCartTotal(userId: String): Int?

    @Query("SELECT SUM(products.price * cart.quantity) FROM cart INNER JOIN products ON cart.productId = products.id")
    suspend fun getCartTotal(): Int?

    @Query("SELECT COALESCE(SUM(quantity), 0) FROM cart WHERE userId = :userId")
    suspend fun getCartCount(userId: String): Int
}
