package com.example.stylista.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "orders")
data class OrderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val orderNumber: String = "",
    val totalAmount: Int,
    val paymentMethod: String,
    val timestamp: Long,
    val userId: String = "guest_user",
    val itemsJson: String = "",
    val addressJson: String = "",
    val status: String = "PLACED",
    val statusHistoryJson: String = "",
    val subtotal: Int = 0,
    val deliveryFee: Int = 0,
    val discountAmount: Int = 0,
    val estimatedDeliveryDate: Long = 0L,
    val paymentStatus: String = "PENDING",
    val razorpayPaymentId: String = ""
)
