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
            "- ID:${p.id} | Name:\"${p.name}\" | Cat:${p.category} | Sub:${p.subcategory} | Rs.${p.price} | Desc:\"${p.description}\" | InStockSizes:${p.inStockSizes}"
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
5. Garment vs Accessory Accuracy (CRITICAL):
   - A "dress" or clothing item MUST come from apparel subcategories (e.g., Sub:Dresses [IDs 22, 24, 29] or Sub:Kurta [ID 26]).
   - NEVER suggest accessories (such as ID 17 "Gold Buckle Dress Belt" or ID 15 "Minimalist Steel Watch") as dresses or main clothing pieces.
6. Color & Missing Variation Handling (CRITICAL):
   - If the user asks for a specific color or item that Stylista does not carry as a single solid piece (e.g., "blue dress", "green gown"):
     * Explicitly and honestly let the user know we do not have a dedicated solid piece in that exact color in our current collection.
     * Recommend the closest available dresses (e.g., Floral Midi Dress ID 22 or Summer Sundress ID 24).
     * Pair them thoughtfully with available matching color items from the catalog (e.g., Classic Casual Denim Jacket ID 23 in washed blue, or Shoulder Bag ID 3 in pastel baby blue, or Velvet Padded Headband ID 21 in navy velvet).
     * Order the product IDs with the primary dress first, followed by the matching styling pieces.
     * NEVER suggest completely unrelated items like watches or belts for a dress query.
7. Recommendation Order:
   - ALWAYS put the PRIMARY product that directly answers the user's specific request FIRST in "productIds" (e.g. if the user asks for a dress, put the dress ID first).
   - Put secondary styling suggestions (pairing accessories, jewelry, matching bags, jackets) after the primary item.
   - You MUST return your response as a valid JSON object:
{
  "reply": "Your friendly conversational styling response here...",
  "productIds": [primaryProductId, pairingProductId1, pairingProductId2]
}
If no specific products from the catalog are relevant, provide an empty array for "productIds".
8. Do NOT include markdown code blocks (such as ```json) around your response. Return raw JSON only.
9. NEVER request or reveal sensitive personal information like passwords, addresses, or payment cards.
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
