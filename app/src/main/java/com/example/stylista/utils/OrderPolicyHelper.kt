package com.example.stylista.utils
import com.example.stylista.R
import com.example.stylista.activities.*
import com.example.stylista.adapters.*
import com.example.stylista.chatbot.*
import com.example.stylista.database.StylistaDatabase
import com.example.stylista.database.dao.*
import com.example.stylista.database.entities.*
import com.example.stylista.models.*
import com.example.stylista.repositories.*

object OrderPolicyHelper {

    const val EXCHANGE_WINDOW_MS = 7 * 24 * 60 * 60 * 1000L // 7 days in milliseconds

    private val CANCELLABLE_STATUSES = setOf("PLACED", "CONFIRMED", "PACKED")

    fun canCancel(status: String): Boolean {
        return CANCELLABLE_STATUSES.contains(status.trim().uppercase())
    }

    fun canExchange(status: String, deliveredOrCreatedAtTimestamp: Long, currentTimestamp: Long = System.currentTimeMillis()): Boolean {
        if (!status.trim().equals("DELIVERED", ignoreCase = true)) {
            return false
        }
        val elapsed = currentTimestamp - deliveredOrCreatedAtTimestamp
        return elapsed in 0..EXCHANGE_WINDOW_MS
    }

    fun isValidStatusTransition(fromStatus: String, toStatus: String): Boolean {
        val from = fromStatus.trim().uppercase()
        val to = toStatus.trim().uppercase()

        if (from == to) return true
        if (from == "CANCELLED") return false // Terminal state

        return when (from) {
            "PLACED" -> to in listOf("CONFIRMED", "CANCELLED")
            "CONFIRMED" -> to in listOf("PACKED", "CANCELLED")
            "PACKED" -> to in listOf("SHIPPED", "CANCELLED")
            "SHIPPED" -> to == "OUT_FOR_DELIVERY"
            "OUT_FOR_DELIVERY" -> to == "DELIVERED"
            "DELIVERED" -> false // Terminal delivery state
            else -> false
        }
    }
}
