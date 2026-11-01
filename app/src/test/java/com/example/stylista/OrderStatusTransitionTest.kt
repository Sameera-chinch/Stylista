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

class OrderStatusTransitionTest {

    @Test
    fun testValidForwardTransitions() {
        assertTrue(OrderPolicyHelper.isValidStatusTransition("PLACED", "CONFIRMED"))
        assertTrue(OrderPolicyHelper.isValidStatusTransition("CONFIRMED", "PACKED"))
        assertTrue(OrderPolicyHelper.isValidStatusTransition("PACKED", "SHIPPED"))
        assertTrue(OrderPolicyHelper.isValidStatusTransition("SHIPPED", "OUT_FOR_DELIVERY"))
        assertTrue(OrderPolicyHelper.isValidStatusTransition("OUT_FOR_DELIVERY", "DELIVERED"))
    }

    @Test
    fun testCancellationGuardLogic() {
        // Cancellation allowed during early preparation phases
        assertTrue(OrderPolicyHelper.canCancel("PLACED"))
        assertTrue(OrderPolicyHelper.canCancel("CONFIRMED"))
        assertTrue(OrderPolicyHelper.canCancel("PACKED"))
        assertTrue(OrderPolicyHelper.isValidStatusTransition("PLACED", "CANCELLED"))
        assertTrue(OrderPolicyHelper.isValidStatusTransition("CONFIRMED", "CANCELLED"))
        assertTrue(OrderPolicyHelper.isValidStatusTransition("PACKED", "CANCELLED"))

        // Cancellation NOT allowed once shipped or delivered
        assertFalse(OrderPolicyHelper.canCancel("SHIPPED"))
        assertFalse(OrderPolicyHelper.canCancel("OUT_FOR_DELIVERY"))
        assertFalse(OrderPolicyHelper.canCancel("DELIVERED"))
        assertFalse(OrderPolicyHelper.canCancel("CANCELLED"))

        assertFalse(OrderPolicyHelper.isValidStatusTransition("SHIPPED", "CANCELLED"))
        assertFalse(OrderPolicyHelper.isValidStatusTransition("OUT_FOR_DELIVERY", "CANCELLED"))
        assertFalse(OrderPolicyHelper.isValidStatusTransition("DELIVERED", "CANCELLED"))
    }

    @Test
    fun testTerminalStatesCannotTransition() {
        assertFalse(OrderPolicyHelper.isValidStatusTransition("CANCELLED", "PLACED"))
        assertFalse(OrderPolicyHelper.isValidStatusTransition("CANCELLED", "CONFIRMED"))
        assertFalse(OrderPolicyHelper.isValidStatusTransition("DELIVERED", "SHIPPED"))
    }
}
