package com.example.stylista

import com.example.stylista.database.ProductEntity
import org.junit.Assert.*
import org.junit.Test

class ProductEntityTest {

    @Test
    fun testApparelSizeParsingAndStock() {
        val product = ProductEntity(
            id = 1,
            name = "Floral Summer Sundress",
            price = 1499,
            imageRes = 0,
            category = "Western",
            subcategory = "Dresses",
            description = "Lightweight dress",
            availableSizes = "XS, S, M, L, XL",
            inStockSizes = "S, M, L",
            rating = 4.8,
            reviewCount = 124
        )

        val sizeList = product.getSizeList()
        assertEquals(listOf("XS", "S", "M", "L", "XL"), sizeList)

        val inStockList = product.getInStockList()
        assertEquals(listOf("S", "M", "L"), inStockList)

        assertTrue(product.isSizeInStock("S"))
        assertTrue(product.isSizeInStock("M"))
        assertTrue(product.isSizeInStock("L"))
        assertFalse(product.isSizeInStock("XS"))
        assertFalse(product.isSizeInStock("XL"))
        assertFalse(product.isUniversalSize())
    }

    @Test
    fun testAccessoryUniversalSize() {
        val accessory = ProductEntity(
            id = 2,
            name = "Classic Leather Tote Bag",
            price = 2499,
            imageRes = 0,
            category = "Accessories",
            subcategory = "Bags",
            description = "Full grain leather bag",
            availableSizes = "One Size",
            inStockSizes = "One Size",
            rating = 4.9,
            reviewCount = 88
        )

        val sizeList = accessory.getSizeList()
        assertEquals(listOf("One Size"), sizeList)
        assertTrue(accessory.isUniversalSize())
        assertTrue(accessory.isSizeInStock("One Size"))
        assertFalse(accessory.isSizeInStock("M"))
    }

    @Test
    fun testFallbackWhenSizesBlank() {
        val product = ProductEntity(
            id = 3,
            name = "Hair Scrunchie",
            price = 299,
            imageRes = 0,
            category = "Accessories",
            subcategory = "Hair",
            description = "Silk scrunchie",
            availableSizes = "",
            inStockSizes = ""
        )

        val sizeList = product.getSizeList()
        assertTrue(product.isUniversalSize() || sizeList.isEmpty())
    }
}
