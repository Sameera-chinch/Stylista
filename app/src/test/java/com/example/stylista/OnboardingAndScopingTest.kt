package com.example.stylista

import com.example.stylista.database.entities.ProductEntity
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

class OnboardingAndScopingTest {

    // Simulates the routing decision logic after authentication
    enum class Destination {
        STYLE_ONBOARDING,
        HOME
    }

    private fun determineDestination(isNewSignup: Boolean, isGuest: Boolean): Destination {
        return if (isNewSignup && !isGuest) {
            Destination.STYLE_ONBOARDING
        } else {
            Destination.HOME
        }
    }

    @Test
    fun testAuthenticationRoutingDecision() {
        // Brand new registered user MUST go to style onboarding
        val signupDest = determineDestination(isNewSignup = true, isGuest = false)
        assertEquals(Destination.STYLE_ONBOARDING, signupDest)

        // Existing user logging in MUST go straight to Home
        val loginDest = determineDestination(isNewSignup = false, isGuest = false)
        assertEquals(Destination.HOME, loginDest)

        // Guest user MUST go straight to Home
        val guestDest = determineDestination(isNewSignup = false, isGuest = true)
        assertEquals(Destination.HOME, guestDest)
    }

    @Test
    fun testUserPreferenceScopingMap() {
        // Simulates the SharedPreferences key prefixing used in UserPreferencesHelper
        val storage = mutableMapOf<String, Any>()

        fun saveUserStyles(uid: String, styles: Set<String>) {
            storage["pref_styles_$uid"] = styles
        }

        fun getUserStyles(uid: String): Set<String> {
            @Suppress("UNCHECKED_CAST")
            return (storage["pref_styles_$uid"] as? Set<String>) ?: emptySet()
        }

        val uidA = "firebase_user_aaa"
        val uidB = "firebase_user_bbb"

        saveUserStyles(uidA, setOf("Western", "Formal"))
        saveUserStyles(uidB, setOf("Ethnic"))

        val stylesA = getUserStyles(uidA)
        val stylesB = getUserStyles(uidB)

        assertEquals(setOf("Western", "Formal"), stylesA)
        assertEquals(setOf("Ethnic"), stylesB)
        assertFalse(stylesA.contains("Ethnic"))
        assertFalse(stylesB.contains("Western"))
    }

    @Test
    fun testRecommendationFilterByPreferences() {
        val catalog = listOf(
            ProductEntity(
                id = 1,
                name = "Floral Summer Dress",
                price = 1499,
                category = "Western",
                availableSizes = "XS, S, M, L",
                inStockSizes = "S, M",
                inStock = true
            ),
            ProductEntity(
                id = 2,
                name = "Silk Anarkali Kurta",
                price = 2899,
                category = "Ethnic",
                availableSizes = "S, M, L, XL",
                inStockSizes = "L, XL",
                inStock = true
            ),
            ProductEntity(
                id = 3,
                name = "Leather Crossbody Bag",
                price = 999,
                category = "Accessories",
                subcategory = "Bags",
                availableSizes = "Free Size",
                inStockSizes = "Free Size",
                inStock = true
            ),
            ProductEntity(
                id = 4,
                name = "Out of Stock Dress",
                price = 1299,
                category = "Western",
                availableSizes = "S, M",
                inStockSizes = "",
                inStock = false
            )
        )

        // User A: Western styles, size M, budget 1000 - 2000
        val userAStyles = setOf("Western")
        val userASizes = setOf("M")
        val userABudget = 1000..2000

        val recsForA = catalog.filter { product ->
            product.inStock &&
            userAStyles.any { it.equals(product.category, ignoreCase = true) } &&
            (product.isUniversalSize() || userASizes.any { product.isSizeInStock(it) }) &&
            product.price in userABudget
        }

        assertEquals(1, recsForA.size)
        assertEquals(1, recsForA.first().id) // Floral Summer Dress

        // User B: Ethnic styles, size XL, budget 2500 - 3500
        val userBStyles = setOf("Ethnic")
        val userBSizes = setOf("XL")
        val userBBudget = 2500..3500

        val recsForB = catalog.filter { product ->
            product.inStock &&
            userBStyles.any { it.equals(product.category, ignoreCase = true) } &&
            (product.isUniversalSize() || userBSizes.any { product.isSizeInStock(it) }) &&
            product.price in userBBudget
        }

        assertEquals(1, recsForB.size)
        assertEquals(2, recsForB.first().id) // Silk Anarkali Kurta
    }
}
