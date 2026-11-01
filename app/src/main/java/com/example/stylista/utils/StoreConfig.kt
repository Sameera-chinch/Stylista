package com.example.stylista.utils

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import com.example.stylista.R
import com.example.stylista.activities.*
import com.example.stylista.adapters.*
import com.example.stylista.chatbot.*
import com.example.stylista.database.StylistaDatabase
import com.example.stylista.database.dao.*
import com.example.stylista.database.entities.*
import com.example.stylista.models.*
import com.example.stylista.repositories.*

object StoreConfig {
    const val DEFAULT_FREE_DELIVERY_ABOVE = 1499
    const val DEFAULT_STANDARD_DELIVERY_FEE = 99

    var freeDeliveryAbove: Int = DEFAULT_FREE_DELIVERY_ABOVE
        private set
    var standardDeliveryFee: Int = DEFAULT_STANDARD_DELIVERY_FEE
        private set

    suspend fun syncWithFirestore() {
        try {
            val snapshot = FirebaseFirestore.getInstance()
                .collection("config")
                .document("store")
                .get()
                .await()

            if (snapshot.exists()) {
                val freeThreshold = snapshot.getLong("freeDeliveryAbove")?.toInt()
                val fee = snapshot.getLong("standardDeliveryFee")?.toInt()
                if (freeThreshold != null && freeThreshold > 0) {
                    freeDeliveryAbove = freeThreshold
                }
                if (fee != null && fee >= 0) {
                    standardDeliveryFee = fee
                }
            }
        } catch (e: Exception) {
            // Keep default values on network failure or if document doesn't exist
        }
    }

    fun calculateDeliveryFee(subtotal: Int): Int {
        if (subtotal <= 0) return 0
        return if (subtotal >= freeDeliveryAbove) 0 else standardDeliveryFee
    }

    fun getDeliveryBannerMessage(subtotal: Int): String {
        return if (subtotal >= freeDeliveryAbove) {
            "🎉 You've unlocked FREE Delivery!"
        } else {
            val diff = freeDeliveryAbove - subtotal
            "Add ₹$diff more for FREE Delivery!"
        }
    }
}
