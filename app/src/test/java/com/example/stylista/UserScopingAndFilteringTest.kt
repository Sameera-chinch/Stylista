package com.example.stylista

import com.example.stylista.database.entities.CartEntity
import com.example.stylista.database.entities.ProductEntity
import com.example.stylista.database.entities.WishlistEntity
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

class UserScopingAndFilteringTest {

    @Test
    fun testUserCartScoping() {
        val userACart = listOf(
            CartEntity(id = 1, productId = 101, size = "M", quantity = 2, userId = "user_A"),
            CartEntity(id = 2, productId = 102, size = "L", quantity = 1, userId = "user_A")
        )

        val userBCart = listOf(
            CartEntity(id = 3, productId = 103, size = "S", quantity = 1, userId = "user_B")
        )

        val allCartItems = userACart + userBCart

        val filteredForA = allCartItems.filter { it.userId == "user_A" }
        val filteredForB = allCartItems.filter { it.userId == "user_B" }

        assertEquals(2, filteredForA.size)
        assertEquals(1, filteredForB.size)
        assertEquals(103, filteredForB.first().productId)
    }

    @Test
    fun testUserWishlistScoping() {
        val wishlist = listOf(
            WishlistEntity(id = 1, productId = 5, addedTimestamp = 1000L, userId = "user_1"),
            WishlistEntity(id = 2, productId = 8, addedTimestamp = 2000L, userId = "user_2"),
            WishlistEntity(id = 3, productId = 12, addedTimestamp = 3000L, userId = "user_1")
        )

        val user1Items = wishlist.filter { it.userId == "user_1" }
        val user2Items = wishlist.filter { it.userId == "user_2" }

        assertEquals(2, user1Items.size)
        assertEquals(1, user2Items.size)
        assertTrue(user1Items.any { it.productId == 5 })
        assertTrue(user1Items.any { it.productId == 12 })
        assertFalse(user1Items.any { it.productId == 8 })
    }

    @Test
    fun testProductFilteringByPriceAndStock() {
        val products = listOf(
            ProductEntity(id = 1, name = "P1", price = 500, imageRes = 0, category = "Western", availableSizes = "S, M", inStockSizes = "M", inStock = true),
            ProductEntity(id = 2, name = "P2", price = 1500, imageRes = 0, category = "Western", availableSizes = "S, M, L", inStockSizes = "S, M, L", inStock = true),
            ProductEntity(id = 3, name = "P3", price = 2500, imageRes = 0, category = "Western", availableSizes = "L", inStockSizes = "", inStock = false)
        )

        // Filter price <= 1500 and inStock == true
        val filtered = products.filter { it.price in 500..1500 && it.inStock }
        assertEquals(2, filtered.size)

        // Filter size contains "L" and inStock
        val sizeLInStock = products.filter { it.isSizeInStock("L") }
        assertEquals(1, sizeLInStock.size)
        assertEquals(2, sizeLInStock.first().id)
    }

    @Test
    fun testProductSorting() {
        val products = listOf(
            ProductEntity(id = 1, name = "B", price = 1500, imageRes = 0, category = "Western", rating = 4.2),
            ProductEntity(id = 2, name = "A", price = 500, imageRes = 0, category = "Western", rating = 4.9),
            ProductEntity(id = 3, name = "C", price = 2500, imageRes = 0, category = "Western", rating = 4.5)
        )

        val priceAsc = products.sortedBy { it.price }
        assertEquals(listOf(2, 1, 3), priceAsc.map { it.id })

        val priceDesc = products.sortedByDescending { it.price }
        assertEquals(listOf(3, 1, 2), priceDesc.map { it.id })

        val ratingDesc = products.sortedByDescending { it.rating }
        assertEquals(listOf(2, 3, 1), ratingDesc.map { it.id })
    }
}
