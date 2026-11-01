package com.example.stylista.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.stylista.database.entities.*

@Dao
interface OrderDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrder(order: OrderEntity): Long

    @Query("SELECT * FROM orders WHERE userId = :userId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastOrder(userId: String): OrderEntity?

    @Query("SELECT * FROM orders ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastOrder(): OrderEntity?

    @Query("SELECT * FROM orders WHERE userId = :userId ORDER BY timestamp DESC")
    suspend fun getAllOrders(userId: String): List<OrderEntity>

    @Query("SELECT * FROM orders ORDER BY timestamp DESC")
    suspend fun getAllOrders(): List<OrderEntity>

    @Query("SELECT * FROM orders WHERE orderNumber = :orderNumber LIMIT 1")
    suspend fun getOrderByOrderNumber(orderNumber: String): OrderEntity?

    @Query("SELECT * FROM orders WHERE id = :id LIMIT 1")
    suspend fun getOrderById(id: Int): OrderEntity?

    @Update
    suspend fun updateOrder(order: OrderEntity): Int

    @Query("UPDATE orders SET status = :status WHERE orderNumber = :orderNumber")
    suspend fun updateOrderStatus(orderNumber: String, status: String): Int
}
