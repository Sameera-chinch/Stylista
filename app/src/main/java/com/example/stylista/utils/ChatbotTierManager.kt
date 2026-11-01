package com.example.stylista.utils

import android.content.Context
import android.util.Log
import com.example.stylista.BuildConfig
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.GenerationConfig
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.TextPart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class ChatbotResponse(
    val replyText: String,
    val recommendedProductIds: List<Int>,
    val tierLabel: String,
    val isFallback: Boolean
)

interface IChatbotTierStrategy {
    suspend fun executeTier1(query: String, context: Context): ChatbotResponse?
    suspend fun executeTier2(query: String, context: Context): ChatbotResponse?
    suspend fun executeTier3(query: String, context: Context): ChatbotResponse
}

class DefaultChatbotTierStrategy : IChatbotTierStrategy {

    companion object {
        private const val TAG = "ChatbotTierManager"
        private const val TIMEOUT_MS = 15_000L
    }

    override suspend fun executeTier1(query: String, context: Context): ChatbotResponse? = withContext(Dispatchers.IO) {
        val directKey = BuildConfig.GEMINI_API_KEY
        if (directKey.isNotBlank() && directKey.startsWith("AIzaSy")) {
            val response = executeGeminiRest(query, directKey, context)
            if (response != null) return@withContext response
        }

        withTimeoutOrNull(TIMEOUT_MS) {
            try {
                val systemPrompt = ChatbotGroundingHelper.buildSystemInstruction(context)
                val config = GenerationConfig.builder()
                    .setResponseMimeType("application/json")
                    .setTemperature(0.7f)
                    .build()
                val sysContent = Content(role = "system", parts = listOf(TextPart(systemPrompt)))

                val candidateModels = listOf("gemini-2.5-flash", "gemini-3.8-flash", "gemini-3.0-flash")
                var rawText: String? = null

                for (modelName in candidateModels) {
                    try {
                        val model = Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
                            modelName,
                            config,
                            emptyList(),
                            emptyList(),
                            null,
                            sysContent
                        )
                        val userContent = Content(role = "user", parts = listOf(TextPart(query)))
                        val response = model.generateContent(listOf(userContent))
                        val text = response.text?.trim()
                        if (!text.isNullOrBlank()) {
                            rawText = text
                            break
                        }
                    } catch (e: Exception) {
                        val msg = e.message ?: ""
                        Log.w(TAG, "Tier 1 ($modelName) failed: $msg")
                        if (msg.contains("firebasevertexai") || msg.contains("Vertex AI in Firebase API")) {
                            break
                        }
                    }
                }

                if (rawText.isNullOrBlank()) return@withTimeoutOrNull null

                val parsed = ChatbotGroundingHelper.parseResponse(rawText)
                ChatbotResponse(
                    replyText = parsed.reply,
                    recommendedProductIds = parsed.productIds,
                    tierLabel = "AI",
                    isFallback = false
                )
            } catch (e: Exception) {
                Log.w(TAG, "Tier 1 (Gemini) failed: ${e.message}")
                null
            }
        }
    }

    private suspend fun executeGeminiRest(query: String, apiKey: String, context: Context): ChatbotResponse? {
        var connection: HttpURLConnection? = null
        try {
            val systemPrompt = ChatbotGroundingHelper.buildSystemInstruction(context)
            val candidateModels = listOf("gemini-2.5-flash", "gemini-1.5-flash", "gemini-2.0-flash", "gemini-3.8-flash")

            for (modelName in candidateModels) {
                try {
                    val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey")
                    connection = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        connectTimeout = 10_000
                        readTimeout = 10_000
                        doInput = true
                        doOutput = true
                        setRequestProperty("Content-Type", "application/json")
                    }

                    val payload = JSONObject().apply {
                        put("system_instruction", JSONObject().apply {
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply { put("text", systemPrompt) })
                            })
                        })
                        put("contents", JSONArray().apply {
                            put(JSONObject().apply {
                                put("role", "user")
                                put("parts", JSONArray().apply {
                                    put(JSONObject().apply { put("text", query) })
                                })
                            })
                        })
                        put("generationConfig", JSONObject().apply {
                            put("responseMimeType", "application/json")
                            put("temperature", 0.7)
                        })
                    }

                    OutputStreamWriter(connection.outputStream).use { writer ->
                        writer.write(payload.toString())
                        writer.flush()
                    }

                    val code = connection.responseCode
                    if (code in 200..299) {
                        val responseStr = BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
                        val json = JSONObject(responseStr)
                        val text = json.optJSONArray("candidates")
                            ?.optJSONObject(0)
                            ?.optJSONObject("content")
                            ?.optJSONArray("parts")
                            ?.optJSONObject(0)
                            ?.optString("text") ?: ""

                        if (text.isNotBlank()) {
                            val parsed = ChatbotGroundingHelper.parseResponse(text)
                            return ChatbotResponse(
                                replyText = parsed.reply,
                                recommendedProductIds = parsed.productIds,
                                tierLabel = "AI",
                                isFallback = false
                            )
                        }
                    } else {
                        Log.w(TAG, "Direct Gemini REST ($modelName) failed with code $code")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Direct Gemini REST ($modelName) error: ${e.message}")
                } finally {
                    connection?.disconnect()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "executeGeminiRest failed: ${e.message}")
        }
        return null
    }

    override suspend fun executeTier2(query: String, context: Context): ChatbotResponse? = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.XAI_API_KEY
        if (apiKey.isBlank()) {
            Log.d(TAG, "Tier 2 skipped: XAI_API_KEY not configured in local.properties")
            return@withContext null
        }

        withTimeoutOrNull(TIMEOUT_MS) {
            var connection: HttpURLConnection? = null
            try {
                val catalogSummary = ChatbotGroundingHelper.buildSystemInstruction(context)
                val isGroq = apiKey.startsWith("gsk_")
                val endpointUrl = if (isGroq) {
                    "https://api.groq.com/openai/v1/chat/completions"
                } else {
                    "https://api.x.ai/v1/chat/completions"
                }
                val modelName = if (isGroq) "openai/gpt-oss-120b" else "grok-beta"

                val url = URL(endpointUrl)
                connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 8_000
                    readTimeout = 8_000
                    doInput = true
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Authorization", "Bearer $apiKey")
                }

                val payload = JSONObject().apply {
                    put("model", modelName)
                    val messages = JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "system")
                            put("content", catalogSummary)
                        })
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", query)
                        })
                    }
                    put("messages", messages)
                    put("temperature", 0.7)
                }

                OutputStreamWriter(connection.outputStream).use { writer ->
                    writer.write(payload.toString())
                    writer.flush()
                }

                val responseCode = connection.responseCode
                if (responseCode in 200..299) {
                    val responseStr = BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
                    val json = JSONObject(responseStr)
                    val choices = json.optJSONArray("choices")
                    val content = choices?.optJSONObject(0)?.optJSONObject("message")?.optString("content") ?: ""

                    if (content.isNotBlank()) {
                        val parsed = ChatbotGroundingHelper.parseResponse(content)
                        ChatbotResponse(
                            replyText = parsed.reply,
                            recommendedProductIds = parsed.productIds,
                            tierLabel = "AI",
                            isFallback = false
                        )
                    } else {
                        null
                    }
                } else {
                    Log.w(TAG, "Tier 2 HTTP error: $responseCode")
                    null
                }
            } catch (e: Exception) {
                Log.w(TAG, "Tier 2 execution failed: ${e.message}")
                null
            } finally {
                connection?.disconnect()
            }
        }
    }

    override suspend fun executeTier3(query: String, context: Context): ChatbotResponse = withContext(Dispatchers.IO) {
        val result = LocalFashionStylist.getStylingAdvice(context, query)
        ChatbotResponse(
            replyText = result.reply,
            recommendedProductIds = result.productIds,
            tierLabel = "Offline stylist",
            isFallback = true
        )
    }
}

object ChatbotTierManager {

    suspend fun processQuery(
        query: String,
        context: Context,
        strategy: IChatbotTierStrategy = DefaultChatbotTierStrategy()
    ): ChatbotResponse {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            return ChatbotResponse(
                replyText = "Please ask a question about styling, outfit suggestions, or our collections.",
                recommendedProductIds = emptyList(),
                tierLabel = "Offline stylist",
                isFallback = true
            )
        }

        // Tier 1: Primary AI
        val tier1Result = strategy.executeTier1(trimmed, context)
        if (tier1Result != null && tier1Result.replyText.isNotBlank()) {
            return tier1Result
        }

        // Tier 2: Backup AI (e.g. xAI)
        val tier2Result = strategy.executeTier2(trimmed, context)
        if (tier2Result != null && tier2Result.replyText.isNotBlank()) {
            return tier2Result
        }

        // Tier 3: Local Offline Stylist
        return strategy.executeTier3(trimmed, context)
    }
}
