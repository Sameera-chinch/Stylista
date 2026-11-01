package com.example.stylista

import android.content.Context
import com.example.stylista.chatbot.ChatbotResponse
import com.example.stylista.chatbot.ChatbotTierManager
import com.example.stylista.chatbot.IChatbotTierStrategy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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

class ChatbotTierManagerTest {

    private class FakeContext : android.content.ContextWrapper(null)
    private val fakeContext: Context = FakeContext()

    @Test
    fun testTier1SuccessReturnsAI() = runBlocking {
        val fakeStrategy = object : IChatbotTierStrategy {
            override suspend fun executeTier1(query: String, context: Context): ChatbotResponse? {
                return ChatbotResponse("Tier 1 Gemini Advice", listOf(1, 2), "AI", false)
            }

            override suspend fun executeTier2(query: String, context: Context): ChatbotResponse? {
                return ChatbotResponse("Tier 2 Backup Advice", listOf(3), "Backup AI", true)
            }

            override suspend fun executeTier3(query: String, context: Context): ChatbotResponse {
                return ChatbotResponse("Tier 3 Offline Advice", listOf(4), "Offline stylist", true)
            }
        }

        val result = ChatbotTierManager.processQuery("Recommend outfit", fakeContext, fakeStrategy)
        assertEquals("AI", result.tierLabel)
        assertEquals("Tier 1 Gemini Advice", result.replyText)
        assertFalse(result.isFallback)
    }

    @Test
    fun testTier1FailsTier2SuccessReturnsBackupAI() = runBlocking {
        val fakeStrategy = object : IChatbotTierStrategy {
            override suspend fun executeTier1(query: String, context: Context): ChatbotResponse? {
                return null // Failed / timeout / HTTP 429
            }

            override suspend fun executeTier2(query: String, context: Context): ChatbotResponse? {
                return ChatbotResponse("Tier 2 xAI Advice", listOf(22), "Backup AI", true)
            }

            override suspend fun executeTier3(query: String, context: Context): ChatbotResponse {
                return ChatbotResponse("Tier 3 Offline Advice", listOf(4), "Offline stylist", true)
            }
        }

        val result = ChatbotTierManager.processQuery("Recommend dress", fakeContext, fakeStrategy)
        assertEquals("Backup AI", result.tierLabel)
        assertEquals("Tier 2 xAI Advice", result.replyText)
        assertTrue(result.isFallback)
    }

    @Test
    fun testTier1And2FailReturnsOfflineStylist() = runBlocking {
        val fakeStrategy = object : IChatbotTierStrategy {
            override suspend fun executeTier1(query: String, context: Context): ChatbotResponse? {
                return null // Timeout
            }

            override suspend fun executeTier2(query: String, context: Context): ChatbotResponse? {
                return null // Network offline / missing key
            }

            override suspend fun executeTier3(query: String, context: Context): ChatbotResponse {
                return ChatbotResponse("Tier 3 Local Catalog", listOf(24), "Offline stylist", true)
            }
        }

        val result = ChatbotTierManager.processQuery("Summer outfit", fakeContext, fakeStrategy)
        assertEquals("Offline stylist", result.tierLabel)
        assertEquals("Tier 3 Local Catalog", result.replyText)
        assertTrue(result.isFallback)
    }

    @Test
    fun testEmptyMessageGuard() = runBlocking {
        val fakeStrategy = object : IChatbotTierStrategy {
            override suspend fun executeTier1(query: String, context: Context): ChatbotResponse? = null
            override suspend fun executeTier2(query: String, context: Context): ChatbotResponse? = null
            override suspend fun executeTier3(query: String, context: Context): ChatbotResponse {
                return ChatbotResponse("Offline fallback", emptyList(), "Offline stylist", true)
            }
        }

        val result = ChatbotTierManager.processQuery("   ", fakeContext, fakeStrategy)
        assertEquals("Offline stylist", result.tierLabel)
        assertTrue(result.replyText.contains("Please ask a question"))
    }
}
