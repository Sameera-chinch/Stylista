package com.example.stylista.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val price: Int,
    val imageRes: Int = 0,
    val category: String,
    val subcategory: String = "",
    val availableSizes: String = "Free Size",
    val inStockSizes: String = "Free Size",
    val description: String = "",
    val rating: Double = 4.7,
    val reviewCount: Int = 120,
    val imageUrl: String = "",
    val inStock: Boolean = true,
    val createdAt: Long = 0L,
    val isFeatured: Boolean = false,
    val isNewArrival: Boolean = false
) {
    fun getSizeList(): List<String> =
        availableSizes.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    fun getInStockList(): List<String> =
        inStockSizes.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    fun isSizeInStock(size: String): Boolean =
        getInStockList().any { it.equals(size, ignoreCase = true) }

    fun isUniversalSize(): Boolean {
        val list = getSizeList()
        return list.size == 1 && (list[0].equals("Free Size", ignoreCase = true) || list[0].equals("One Size", ignoreCase = true))
    }
}
