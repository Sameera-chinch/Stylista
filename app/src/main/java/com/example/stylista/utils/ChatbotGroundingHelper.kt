package com.example.stylista.utils

import android.content.Context
import com.example.stylista.database.ProductEntity
import com.example.stylista.database.StylistaDatabase
import org.json.JSONArray
import org.json.JSONObject

object ChatbotGroundingHelper {

    suspend fun buildSystemInstruction(context: Context): String {
        val db = StylistaDatabase.getInstance(context)
        val products = try {
            db.productDao().getAllProducts()
        } catch (e: Exception) {
            emptyList()
        }

        val uid = UserPreferencesHelper.getActiveUserId(context)
        val selectedStyles = UserPreferencesHelper.getSelectedStyles(context, uid)
        val selectedSizes = UserPreferencesHelper.getSelectedSizes(context, uid)
        val (minBudget, maxBudget) = UserPreferencesHelper.getBudgetRange(context, uid)

        val catalogSummary = products.take(50).joinToString("\n") { p ->
            "- ID:${p.id} | Name:\"${p.name}\" | Category:${p.category} | Sub:${p.subcategory} | Price:₹${p.price} | InStockSizes:${p.inStockSizes}"
        }

        return """
You are Stylista Assistant, an elite, warm, and helpful personal fashion stylist for the Stylista shopping application.

User Style Preferences:
- Favorite Styles: ${if (selectedStyles.isNotEmpty()) selectedStyles.joinToString(", ") else "None specified yet"}
- Preferred Sizes: ${if (selectedSizes.isNotEmpty()) selectedSizes.joinToString(", ") else "Any size"}
- Budget Range: ₹$minBudget to ₹$maxBudget

Stylista Product Catalog:
$catalogSummary

Rules:
1. Always be polite, fashionable, and helpful.
2. Ground your advice in fashion styling, color matching, outfit pairing, and occasion dressing.
3. If the user asks about something completely unrelated to fashion, clothing, or shopping (e.g. math, code, politics), politely decline and steer them back to fashion and Stylista products.
4. When suggesting items, ONLY recommend product IDs that are strictly present in the Stylista Product Catalog above. Never invent IDs or product names.
5. Recommendation Order (CRITICAL):
   - ALWAYS put the PRIMARY product that directly answers the user's specific request FIRST in "productIds" (e.g. if the user asks for a floral dress, put the floral dress ID first).
   - Put secondary styling suggestions (pairing accessories, jewelry, matching bags, jackets) after the primary item.
   - You MUST return your response as a valid JSON object:
{
  "reply": "Your friendly conversational styling response here...",
  "productIds": [primaryProductId, pairingProductId1, pairingProductId2]
}
If no specific products from the catalog are relevant, provide an empty array for "productIds".
6. Do NOT include markdown code blocks (such as ```json) around your response. Return raw JSON only.
7. NEVER request or reveal sensitive personal information like passwords, addresses, or payment cards.
""".trimIndent()
    }

    data class ParsedResponse(
        val reply: String,
        val productIds: List<Int>
    )

    fun parseResponse(rawResponse: String): ParsedResponse {
        val trimmed = rawResponse.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        return try {
            val json = JSONObject(trimmed)
            val reply = json.optString("reply", trimmed)
            val idsArray = json.optJSONArray("productIds") ?: JSONArray()
            val ids = mutableListOf<Int>()
            for (i in 0 until idsArray.length()) {
                val id = idsArray.optInt(i, -1)
                if (id > 0) ids.add(id)
            }
            ParsedResponse(reply, ids)
        } catch (e: Exception) {
            // Fallback: Return raw text and try to match any numbers like [1, 2]
            ParsedResponse(trimmed, emptyList())
        }
    }
}
