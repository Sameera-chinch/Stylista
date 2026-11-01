package com.example.stylista

import com.example.stylista.database.CartEntity
import com.example.stylista.database.ProductEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.text.NumberFormat
import java.util.Locale

class CartCalculationTest {

    private fun calculateTotal(items: List<CartItemUI>): Double {
        return items.sumOf { item ->
            (item.product.price * item.cart.quantity).toDouble()
        }
    }

    @Test
    fun testCalculateCartTotal() {
        val cartItems = listOf(
            CartItemUI(
                cart = CartEntity(
                    id = 1,
                    productId = 1,
                    size = "One Size",
                    quantity = 2
                ),
                product = ProductEntity(
                    id = 1,
                    name = "Classic Leather Tote Bag",
                    price = 2499,
                    imageRes = 0,
                    category = "Accessories"
                )
            ),
            CartItemUI(
                cart = CartEntity(
                    id = 2,
                    productId = 2,
                    size = "M",
                    quantity = 1
                ),
                product = ProductEntity(
                    id = 2,
                    name = "Floral Summer Sundress",
                    price = 1499,
                    imageRes = 0,
                    category = "Western"
                )
            ),
            CartItemUI(
                cart = CartEntity(
                    id = 3,
                    productId = 3,
                    size = "One Size",
                    quantity = 3
                ),
                product = ProductEntity(
                    id = 3,
                    name = "Silk Hair Scrunchie",
                    price = 299,
                    imageRes = 0,
                    category = "Accessories"
                )
            )
        )

        // 2,499 * 2 = 4,998
        // 1,499 * 1 = 1,499
        // 299 * 3 = 897
        // Total = 4,998 + 1,499 + 897 = 7,394
        val total = calculateTotal(cartItems)
        assertEquals(7394.0, total, 0.01)

        val formatted = NumberFormat.getNumberInstance(Locale.US).format(total)
        assertEquals("7,394", formatted)
    }

    @Test
    fun testEmptyCartTotal() {
        val total = calculateTotal(emptyList())
        assertEquals(0.0, total, 0.01)
    }
}
