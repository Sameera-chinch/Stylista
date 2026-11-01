package com.example.stylista.chatbot

import android.content.Context
import com.example.stylista.database.entities.ProductEntity
import com.example.stylista.database.StylistaDatabase
import java.util.Locale
import com.example.stylista.BuildConfig
import com.example.stylista.models.*
import com.example.stylista.repositories.*
import com.example.stylista.utils.*

data class LocalStylistResult(
    val reply: String,
    val productIds: List<Int>,
    val matchedCategory: String? = null
)

object LocalFashionStylist {

    fun generateLocalResponse(query: String): String {
        val lower = query.lowercase(Locale.ROOT).trim()
        if (lower in listOf("hi", "hello", "hey", "help", "good morning", "good evening", "namaste") || lower.startsWith("hello") || lower.startsWith("hey")) {
            return "Hello! ✨ I'm your Stylista fashion styling assistant. How can I help style your look today?"
        }
        if (lower.contains("under") || lower.contains("budget") || lower.contains("below") || lower.contains("cheap") || lower.contains("less than")) {
            return "Stylista offers fabulous styles for every budget, with accessories starting from ₹299 and stylish dresses from ₹999."
        }
        if (lower.contains("size") || lower.contains("sizing") || lower.contains("fit")) {
            return "Here's Stylista's Size Guide: Apparel is available in XS, S, M, L, XL with live stock indicators. Accessories and bags are crafted in versatile One Size."
        }
        if (lower.contains("store") || lower.contains("location") || lower.contains("where") || lower.contains("boutique") || lower.contains("campus")) {
            return "Stylista has boutiques and campus store locations! Tap the Map icon on the top header to see nearby locations and live walking directions."
        }
        if (lower.contains("return") || lower.contains("delivery") || lower.contains("refund") || lower.contains("policy")) {
            return "Stylista offers easy 15-day returns and contactless returns at our Boutique Hubs. Orders arrive in 2-4 business days."
        }
        if (lower.contains("bag") || lower.contains("tote") || lower.contains("jewelry") || lower.contains("necklace") || lower.contains("accessory") || lower.contains("accessories")) {
            return "Our accessories collection features leather bags, fine jewelry, sunglasses, and timepieces to complete any look."
        }
        if (lower.contains("blue") && lower.contains("dress")) {
            return "While we don't have a solid blue dress, our Floral Midi Dress and Summer Sundress look fantastic layered with our washed denim jacket and pastel baby-blue shoulder bag!"
        }
        if (lower.contains("blue")) {
            return "Looking for blue pieces? Check out our pastel baby-blue shoulder bag, washed denim jacket, and navy velvet headband!"
        }
        if (lower.contains("dress") || lower.contains("western") || lower.contains("sundress")) {
            return "Explore our Western wear collection featuring summer sundresses, denim jackets, and tailored separates."
        }
        if (lower.contains("ethnic") || lower.contains("kurta") || lower.contains("anarkali") || lower.contains("wedding")) {
            return "Our Ethnic festive collection features embroidered silk Anarkali suits and pure linen co-ord sets."
        }
        return "I'm Stylista's dedicated fashion assistant! Ask me about outfit styling, dresses, bags, jewelry, size guide, or store locations."
    }

    suspend fun getStylingAdvice(context: Context, query: String): LocalStylistResult {
        val db = StylistaDatabase.getInstance(context)
        val allProducts = try {
            db.productDao().getAllProducts()
        } catch (e: Exception) {
            emptyList()
        }

        val lower = query.lowercase(Locale.ROOT).trim()

        // 1. Budget extraction (e.g. "under 1500", "below 2000", "less than 1000")
        val budgetRegex = Regex("""(?:under|below|less than|within)\s*(?:rs\.?|inr|₹)?\s*(\d+)""")
        val budgetMatch = budgetRegex.find(lower)
        val budget = budgetMatch?.groupValues?.get(1)?.toIntOrNull()

        if (budget != null) {
            val affordable = allProducts.filter { it.price <= budget }.sortedByDescending { it.rating }.take(4)
            if (affordable.isNotEmpty()) {
                val names = affordable.joinToString(", ") { "${it.name} (₹${it.price})" }
                return LocalStylistResult(
                    reply = "Here are our top styles under ₹$budget from our collection:\n$names. Tap any item below to view sizes and details!",
                    productIds = affordable.map { it.id }
                )
            } else {
                return LocalStylistResult(
                    reply = "We don't have items strictly under ₹$budget right now, but our collection starts at ₹499 for silk hair accessories and ₹699 for crystal rings!",
                    productIds = allProducts.sortedBy { it.price }.take(3).map { it.id }
                )
            }
        }

        // 2. Greeting
        if (lower in listOf("hi", "hello", "hey", "help", "good morning", "good evening", "namaste")) {
            val uid = UserPreferencesHelper.getActiveUserId(context)
            val userStyles = UserPreferencesHelper.getSelectedStyles(context, uid)
            val userSizes = UserPreferencesHelper.getSelectedSizes(context, uid)
            val styleNote = when {
                userStyles.isNotEmpty() && userSizes.isNotEmpty() ->
                    "I see you love ${userStyles.joinToString(" & ")} in size ${userSizes.joinToString(", ")}!"
                userStyles.isNotEmpty() ->
                    "I see you love ${userStyles.joinToString(" & ")} looks!"
                else ->
                    "I'm here to help you pick the perfect outfit!"
            }
            val featured = allProducts.take(3)
            return LocalStylistResult(
                reply = "Hello! ✨ I'm your Stylista personal fashion stylist. $styleNote\n\nAsk me about:\n• Outfit ideas for work, parties, or workouts\n• Bags, jewelry, watches, or sunglasses\n• Outfits by budget (e.g. \"styles under 1500\")\n• Size guides and store locations",
                productIds = featured.map { it.id }
            )
        }

        // 3. Sizing questions
        if (lower.contains("size") || lower.contains("sizing") || lower.contains("fit") || lower.contains("measurement")) {
            return LocalStylistResult(
                reply = "Here's Stylista's sizing guide:\n\n• **Apparel (Dresses, Blazers, Tops, Kurta):** Available in XS, S, M, L, and XL. Each item page indicates live in-stock sizes.\n• **Accessories (Bags, Jewelry, Sunglasses):** Crafted in versatile \"One Size\" / \"Free Size\".\n• **Belts & Watches:** Offered in S/M/L or adjustable fit bands.\n\nSelect any product below to view available sizes!",
                productIds = allProducts.filter { it.category == "Western" || it.category == "Formal" }.take(3).map { it.id }
            )
        }

        // 4. Store / Location / Boutique questions
        if (lower.contains("store") || lower.contains("location") || lower.contains("where") || lower.contains("boutique") || lower.contains("campus") || lower.contains("outlet")) {
            return LocalStylistResult(
                reply = "Stylista has campus boutiques and boutique hubs across the area! Tap the 📍 Map icon on the top header to see nearby locations, opening hours, and live walking directions.",
                productIds = emptyList()
            )
        }

        // 5. Specific Categories & Subcategories
        // Bags
        if (lower.contains("bag") || lower.contains("tote") || lower.contains("handbag") || lower.contains("purse")) {
            val bags = allProducts.filter { it.subcategory.equals("Bags", ignoreCase = true) }
            return LocalStylistResult(
                reply = "A statement bag elevates any ensemble. Here are our top handbags and totes in genuine leather, pastel blue, and classic canvas:",
                productIds = bags.map { it.id }
            )
        }

        // Jewelry
        if (lower.contains("jewelry") || lower.contains("necklace") || lower.contains("earring") || lower.contains("bracelet") || lower.contains("ring")) {
            val jewelry = allProducts.filter { it.subcategory.equals("Jewelry", ignoreCase = true) }
            return LocalStylistResult(
                reply = "Subtle golden and pearl accents add effortless chic. Here are our popular necklaces, earrings, and charm bracelets:",
                productIds = jewelry.map { it.id }
            )
        }

        // Sunglasses
        if (lower.contains("sunglass") || lower.contains("shades") || lower.contains("aviator") || lower.contains("eyewear")) {
            val shades = allProducts.filter { it.subcategory.equals("Sunglasses", ignoreCase = true) }
            return LocalStylistResult(
                reply = "Protect your eyes in style with our UV400 and polarized sunglasses, from retro aviators to bold cat-eye silhouettes:",
                productIds = shades.map { it.id }
            )
        }

        // Watches
        if (lower.contains("watch") || lower.contains("timepiece")) {
            val watches = allProducts.filter { it.subcategory.equals("Watches", ignoreCase = true) }
            return LocalStylistResult(
                reply = "A refined watch completes your wrist stack. Here are our mesh rose gold, leather strap, and steel options:",
                productIds = watches.map { it.id }
            )
        }

        // Blue dress query
        if (lower.contains("blue") && (lower.contains("dress") || lower.contains("sundress") || lower.contains("gown") || lower.contains("midi") || lower.contains("frock"))) {
            val floralDress = allProducts.find { it.id == 22 } ?: allProducts.firstOrNull { it.subcategory.equals("Dresses", ignoreCase = true) }
            val denimJacket = allProducts.find { it.id == 23 }
            val blueBag = allProducts.find { it.id == 3 }
            val items = listOfNotNull(floralDress, denimJacket, blueBag)
            return LocalStylistResult(
                reply = "We don't currently have a solid blue dress in our collection, but our Floral Midi Dress pairs gorgeously with our washed Denim Jacket and pastel baby-blue Shoulder Bag for an effortless blue-accented look!",
                productIds = items.map { it.id }
            )
        }

        // General Blue collection
        if (lower.contains("blue") && !lower.contains("bag") && !lower.contains("watch")) {
            val blueItems = allProducts.filter {
                it.id in listOf(23, 3, 21, 27, 15) || it.description.contains("blue", ignoreCase = true)
            }
            return LocalStylistResult(
                reply = "Here are our trending blue essentials, from our washed denim jacket and pastel baby-blue shoulder bag to navy velvet headbands:",
                productIds = blueItems.take(4).map { it.id }
            )
        }

        // Dresses
        if (lower.contains("dress") || lower.contains("sundress") || lower.contains("midi") || lower.contains("frock")) {
            val dresses = allProducts.filter {
                it.subcategory.equals("Dresses", ignoreCase = true) || it.name.contains("Dress", ignoreCase = true)
            }
            return LocalStylistResult(
                reply = "From breezy summer sundresses to floral midi styles and comfortable maternity wear, here are our trending dresses:",
                productIds = dresses.map { it.id }
            )
        }

        // Western
        if (lower.contains("western") || lower.contains("skirt") || lower.contains("jacket") || lower.contains("denim")) {
            val western = allProducts.filter { it.category.equals("Western", ignoreCase = true) }
            return LocalStylistResult(
                reply = "From breezy summer sundresses to floral midi styles and washed denim jackets, here are our trending western essentials:",
                productIds = western.map { it.id }
            )
        }

        // Ethnic & Festive
        if (lower.contains("ethnic") || lower.contains("kurta") || lower.contains("anarkali") || lower.contains("festive") || lower.contains("wedding") || lower.contains("traditional")) {
            val ethnic = allProducts.filter { it.category.equals("Ethnic", ignoreCase = true) }
            return LocalStylistResult(
                reply = "For weddings, festivals, or cultural gatherings, pure linen co-ords and embroidered silk Anarkali kurtas offer timeless grace:",
                productIds = ethnic.map { it.id }
            )
        }

        // Formal & Office
        if (lower.contains("formal") || lower.contains("blazer") || lower.contains("office") || lower.contains("interview") || lower.contains("suit") || lower.contains("work")) {
            val formal = allProducts.filter { it.category.equals("Formal", ignoreCase = true) }
            return LocalStylistResult(
                reply = "Command the room with sharp tailoring. Here are our oversized blazers and power pantsuits:",
                productIds = formal.map { it.id }
            )
        }

        // Sports & Gym
        if (lower.contains("sport") || lower.contains("gym") || lower.contains("workout") || lower.contains("legging") || lower.contains("active") || lower.contains("run")) {
            val sports = allProducts.filter { it.category.equals("Sports", ignoreCase = true) }
            return LocalStylistResult(
                reply = "Stay energized and supported with breathable activewear sets and high-waist performance leggings:",
                productIds = sports.map { it.id }
            )
        }

        // Maternity
        if (lower.contains("maternity") || lower.contains("nursing") || lower.contains("pregnancy") || lower.contains("baby")) {
            val mat = allProducts.filter { it.category.equals("Maternity", ignoreCase = true) }
            return LocalStylistResult(
                reply = "Designed for softness and comfort through every stage, here are our breathable cotton maternity dresses and nursing tops:",
                productIds = mat.map { it.id }
            )
        }

        // Belts & Hair Accessories
        if (lower.contains("belt") || lower.contains("hair") || lower.contains("scrunchie") || lower.contains("clip") || lower.contains("headband")) {
            val acc = allProducts.filter { it.subcategory == "Belts" || it.subcategory == "Hair Accessories" }
            return LocalStylistResult(
                reply = "The right finishing touch makes the outfit. Check out our genuine leather belts, silk scrunchie sets, and pearl clips:",
                productIds = acc.map { it.id }
            )
        }

        // Color coordination
        if (lower.contains("color") || lower.contains("pair") || lower.contains("match") || lower.contains("yellow") || lower.contains("blue")) {
            val matching = allProducts.filter { it.name.contains("Dress") || it.name.contains("Handbag") || it.name.contains("Necklace") }.take(3)
            return LocalStylistResult(
                reply = "Our seasonal palette revolves around soft butter yellow and pastel baby blue! Pair a butter-yellow sundress with a baby blue shoulder bag and delicate gold jewelry for a fresh, balanced look.",
                productIds = matching.map { it.id }
            )
        }

        // Unrelated topics (Math, Coding, Politics)
        if (lower.contains("python") || lower.contains("java") || lower.contains("code") || lower.contains("politics") || lower.contains("math") || lower.contains("calculate")) {
            return LocalStylistResult(
                reply = "I'm Stylista's dedicated fashion assistant, so I specialize exclusively in clothing, accessories, and outfit styling! Let me know what look you're searching for today.",
                productIds = emptyList()
            )
        }

        // Fallback keyword search over catalog product names & categories
        val matched = allProducts.filter {
            it.name.contains(lower, ignoreCase = true) ||
            it.category.contains(lower, ignoreCase = true) ||
            it.subcategory.contains(lower, ignoreCase = true)
        }.take(4)

        if (matched.isNotEmpty()) {
            return LocalStylistResult(
                reply = "Here are matching styles from our collection for \"$query\":",
                productIds = matched.map { it.id }
            )
        }

        // General fallback
        val recommendations = RecommendationEngine.getHomeRecommendations(context, limit = 3)
        return LocalStylistResult(
            reply = "I couldn't find a direct match for \"$query\", but here are top trending picks from our collection. You can also ask me about dresses, bags, jewelry, blazers, or styles within your budget!",
            productIds = recommendations.map { it.id }
        )
    }
}
