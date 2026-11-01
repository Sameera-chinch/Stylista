package com.example.stylista

import com.example.stylista.chatbot.LocalFashionStylist
import org.junit.Assert.*
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

class LocalFashionStylistTest {

    @Test
    fun testGreetingGeneratesWarmResponse() {
        val response = LocalFashionStylist.generateLocalResponse("hello there!")
        assertTrue(response.contains("Stylista fashion styling assistant", ignoreCase = true))
    }

    @Test
    fun testCategoryQuestionsGiveCatalogGuidance() {
        val dressResponse = LocalFashionStylist.generateLocalResponse("Show me western dresses")
        assertTrue(dressResponse.contains("Western", ignoreCase = true) || dressResponse.contains("dress", ignoreCase = true))

        val ethnicResponse = LocalFashionStylist.generateLocalResponse("I need something for ethnic wedding")
        assertTrue(ethnicResponse.contains("Ethnic", ignoreCase = true) || ethnicResponse.contains("anarkali", ignoreCase = true))

        val accessoryResponse = LocalFashionStylist.generateLocalResponse("What accessories do you have?")
        assertTrue(accessoryResponse.contains("bags", ignoreCase = true) || accessoryResponse.contains("jewelry", ignoreCase = true))
    }

    @Test
    fun testBudgetQuery() {
        val budgetResponse = LocalFashionStylist.generateLocalResponse("Looking for stylish bags under 1500")
        assertTrue(budgetResponse.contains("₹", ignoreCase = true) || budgetResponse.contains("budget", ignoreCase = true))
    }

    @Test
    fun testStoreLocationsQuery() {
        val mapResponse = LocalFashionStylist.generateLocalResponse("Where is your boutique or store located?")
        assertTrue(mapResponse.contains("Stylista Boutique", ignoreCase = true) || mapResponse.contains("Campus Store", ignoreCase = true))
    }

    @Test
    fun testSizeGuideQuery() {
        val sizeResponse = LocalFashionStylist.generateLocalResponse("How do sizes work? Is size M true to size?")
        assertTrue(sizeResponse.contains("Size Guide", ignoreCase = true) || sizeResponse.contains("One Size", ignoreCase = true))
    }

    @Test
    fun testDeliveryReturnsQuery() {
        val returnResponse = LocalFashionStylist.generateLocalResponse("What is your return policy?")
        assertTrue(returnResponse.contains("return", ignoreCase = true) || returnResponse.contains("delivery", ignoreCase = true))
    }

    @Test
    fun testBlueDressQuery() {
        val blueDressResponse = LocalFashionStylist.generateLocalResponse("Suggest me a blue dress")
        assertTrue(blueDressResponse.contains("blue", ignoreCase = true))
        assertTrue(blueDressResponse.contains("dress", ignoreCase = true))
    }
}
