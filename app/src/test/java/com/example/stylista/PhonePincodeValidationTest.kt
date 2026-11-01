package com.example.stylista

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

class PhonePincodeValidationTest {

    private val phoneRegex = Regex("^[6-9]\\d{9}$")
    private val pincodeRegex = Regex("^[1-9][0-9]{5}$")

    @Test
    fun testValidIndianPhoneNumbers() {
        val validPhones = listOf(
            "9876543210",
            "8123456789",
            "7001234567",
            "6398765432",
            "9999999999"
        )
        for (phone in validPhones) {
            assertTrue("Phone $phone should be valid", phoneRegex.matches(phone))
        }
    }

    @Test
    fun testInvalidIndianPhoneNumbers() {
        val invalidPhones = listOf(
            "5123456789", // Starts with 5
            "1234567890", // Starts with 1
            "0987654321", // Starts with 0
            "987654321",  // 9 digits
            "98765432100", // 11 digits
            "abcdefghij", // Alpha
            "987654321a", // Alpha suffix
            "",           // Empty
            " 9876543210", // Leading space
            "98765 43210"  // Internal space
        )
        for (phone in invalidPhones) {
            assertFalse("Phone $phone should be invalid", phoneRegex.matches(phone))
        }
    }

    @Test
    fun testValidIndianPincodes() {
        val validPincodes = listOf(
            "560001",
            "110001",
            "400001",
            "600028",
            "700001",
            "500081"
        )
        for (pin in validPincodes) {
            assertTrue("PIN $pin should be valid", pincodeRegex.matches(pin))
        }
    }

    @Test
    fun testInvalidIndianPincodes() {
        val invalidPincodes = listOf(
            "012345", // Starts with 0
            "56001",  // 5 digits
            "5600001", // 7 digits
            "560 01", // Internal space
            "56000a", // Alpha
            "abcdef", // Alpha
            ""        // Empty
        )
        for (pin in invalidPincodes) {
            assertFalse("PIN $pin should be invalid", pincodeRegex.matches(pin))
        }
    }
}
