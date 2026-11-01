package com.example.stylista

import com.example.stylista.utils.OrderPolicyHelper
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.example.stylista.activities.*
import com.example.stylista.adapters.*
import com.example.stylista.chatbot.*
import com.example.stylista.database.StylistaDatabase
import com.example.stylista.database.dao.*
import com.example.stylista.database.entities.*
import com.example.stylista.models.*
import com.example.stylista.repositories.*
import com.example.stylista.utils.*

class ExchangeWindowTest {

    private val oneDayMs = 24 * 60 * 60 * 1000L

    @Test
    fun testEligibleWithin7Days() {
        val now = 1700000000000L

        // Delivered 1 day ago
        val delivered1DayAgo = now - oneDayMs
        assertTrue(OrderPolicyHelper.canExchange("DELIVERED", delivered1DayAgo, currentTimestamp = now))

        // Delivered 3 days ago
        val delivered3DaysAgo = now - 3 * oneDayMs
        assertTrue(OrderPolicyHelper.canExchange("DELIVERED", delivered3DaysAgo, currentTimestamp = now))

        // Delivered 6 days and 23 hours ago
        val deliveredNearly7DaysAgo = now - (7 * oneDayMs - 3600000L)
        assertTrue(OrderPolicyHelper.canExchange("DELIVERED", deliveredNearly7DaysAgo, currentTimestamp = now))
    }

    @Test
    fun testIneligibleAfter7Days() {
        val now = 1700000000000L

        // Delivered 7 days and 1 minute ago
        val deliveredOver7DaysAgo = now - (7 * oneDayMs + 60000L)
        assertFalse(OrderPolicyHelper.canExchange("DELIVERED", deliveredOver7DaysAgo, currentTimestamp = now))

        // Delivered 10 days ago
        val delivered10DaysAgo = now - 10 * oneDayMs
        assertFalse(OrderPolicyHelper.canExchange("DELIVERED", delivered10DaysAgo, currentTimestamp = now))
    }

    @Test
    fun testIneligibleIfNotDelivered() {
        val now = 1700000000000L
        val recentlyPlaced = now - oneDayMs

        // Status is not DELIVERED
        assertFalse(OrderPolicyHelper.canExchange("PLACED", recentlyPlaced, currentTimestamp = now))
        assertFalse(OrderPolicyHelper.canExchange("CONFIRMED", recentlyPlaced, currentTimestamp = now))
        assertFalse(OrderPolicyHelper.canExchange("PACKED", recentlyPlaced, currentTimestamp = now))
        assertFalse(OrderPolicyHelper.canExchange("SHIPPED", recentlyPlaced, currentTimestamp = now))
        assertFalse(OrderPolicyHelper.canExchange("OUT_FOR_DELIVERY", recentlyPlaced, currentTimestamp = now))
        assertFalse(OrderPolicyHelper.canExchange("CANCELLED", recentlyPlaced, currentTimestamp = now))
    }
}
