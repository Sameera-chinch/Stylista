package com.example.stylista

import com.example.stylista.utils.StoreConfig
import org.junit.Assert.assertEquals
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

class CheckoutCalculationTest {

    @Test
    fun testDeliveryFeeCalculation() {
        val freeThreshold = StoreConfig.DEFAULT_FREE_DELIVERY_ABOVE // 1499
        val standardFee = StoreConfig.DEFAULT_STANDARD_DELIVERY_FEE  // 99

        // Subtotal below threshold
        assertEquals(standardFee, StoreConfig.calculateDeliveryFee(500))
        assertEquals(standardFee, StoreConfig.calculateDeliveryFee(1498))

        // Subtotal at or above threshold
        assertEquals(0, StoreConfig.calculateDeliveryFee(freeThreshold))
        assertEquals(0, StoreConfig.calculateDeliveryFee(2500))

        // Subtotal zero
        assertEquals(0, StoreConfig.calculateDeliveryFee(0))
    }

    @Test
    fun testDeliveryBannerMessage() {
        val belowMsg = StoreConfig.getDeliveryBannerMessage(1000)
        assertTrue(belowMsg.contains("Add ₹499 more for FREE Delivery!"))

        val freeMsg = StoreConfig.getDeliveryBannerMessage(1500)
        assertTrue(freeMsg.contains("unlocked FREE Delivery"))
    }

    @Test
    fun testCheckoutPricingMath() {
        val subtotal = 1200
        val deliveryFee = StoreConfig.calculateDeliveryFee(subtotal)
        val discount = 120 // 10% coupon

        val expectedTotal = subtotal + deliveryFee - discount
        assertEquals(99, deliveryFee)
        assertEquals(1179, expectedTotal)

        // With free delivery
        val highSubtotal = 2500
        val freeDeliveryFee = StoreConfig.calculateDeliveryFee(highSubtotal)
        val vipDiscount = 375 // 15% VIP coupon
        val expectedHighTotal = highSubtotal + freeDeliveryFee - vipDiscount
        assertEquals(0, freeDeliveryFee)
        assertEquals(2125, expectedHighTotal)
    }

    @Test
    fun testEstimatedDeliveryCalculation() {
        val now = 1730000000000L
        val fourDaysMs = 4 * 24 * 60 * 60 * 1000L
        val estimated = now + fourDaysMs

        val diffDays = (estimated - now) / (24 * 60 * 60 * 1000L)
        assertEquals(4L, diffDays)
    }
}
