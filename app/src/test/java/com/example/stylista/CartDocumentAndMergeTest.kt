package com.example.stylista

import com.example.stylista.repositories.CartRepository
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

class CartDocumentAndMergeTest {

    @Test
    fun testDeterministicCartDocumentId() {
        // Same product and size should always have the exact same ID
        val id1 = CartRepository.getCartItemId(1, "M")
        val id2 = CartRepository.getCartItemId(1, "m")
        val id3 = CartRepository.getCartItemId(1, " M ")

        assertEquals("1_M", id1)
        assertEquals("1_M", id2)
        assertEquals("1_M", id3)

        // Different size should be a distinct document
        val idLarge = CartRepository.getCartItemId(1, "L")
        assertEquals("1_L", idLarge)
        assertTrue(id1 != idLarge)

        // Free size handling
        val idFree = CartRepository.getCartItemId(5, "Free Size")
        assertEquals("5_FREE SIZE", idFree)
    }

    @Test
    fun testCartMergeLogicSimulated() {
        // Cloud cart: 1_M with quantity 2
        val cloudCart = mutableMapOf(
            "1_M" to 2,
            "2_L" to 1
        )

        // Guest cart: 1_M with quantity 1, and 3_S with quantity 3
        val guestCart = listOf(
            Pair("1_M", 1),
            Pair("3_S", 3)
        )

        val merged = HashMap(cloudCart)
        for ((itemId, guestQty) in guestCart) {
            val existingQty = merged[itemId] ?: 0
            merged[itemId] = existingQty + guestQty
        }

        // Expected:
        // 1_M: 2 (cloud) + 1 (guest) = 3
        // 2_L: 1 (cloud only) = 1
        // 3_S: 3 (guest only) = 3
        assertEquals(3, merged.size)
        assertEquals(3, merged["1_M"])
        assertEquals(1, merged["2_L"])
        assertEquals(3, merged["3_S"])
    }
}
