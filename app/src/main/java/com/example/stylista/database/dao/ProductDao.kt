package com.example.stylista.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.stylista.database.entities.*

@Dao
interface ProductDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProducts(products: List<ProductEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: ProductEntity): Long

    @Query("SELECT COUNT(*) FROM products")
    suspend fun getProductsCount(): Int

    @Query("SELECT * FROM products")
    suspend fun getAllProducts(): List<ProductEntity>

    @Query("SELECT * FROM products WHERE category = :category")
    suspend fun getProductsByCategory(category: String): List<ProductEntity>

    @Query("SELECT * FROM products WHERE LOWER(name) LIKE '%' || LOWER(:query) || '%' OR LOWER(category) LIKE '%' || LOWER(:query) || '%' OR LOWER(subcategory) LIKE '%' || LOWER(:query) || '%'")
    suspend fun searchProducts(query: String): List<ProductEntity>

    @Query("SELECT * FROM products WHERE name = :name LIMIT 1")
    suspend fun getProductByName(name: String): ProductEntity?

    @Query("SELECT * FROM products WHERE id = :id LIMIT 1")
    suspend fun getProductById(id: Int): ProductEntity?

    @Query("SELECT * FROM products WHERE category = :category AND subcategory = :subcategory")
    suspend fun getProductsByCategoryAndSubcategory(category: String, subcategory: String): List<ProductEntity>

    @Query("SELECT DISTINCT subcategory FROM products WHERE category = :category AND subcategory != ''")
    suspend fun getSubcategoriesByCategory(category: String): List<String>

    @Query("SELECT DISTINCT category FROM products WHERE category != ''")
    suspend fun getAllCategories(): List<String>

    @Query("SELECT * FROM products WHERE id IN (:ids)")
    suspend fun getProductsByIds(ids: List<Int>): List<ProductEntity>

    @Query("SELECT * FROM products WHERE isFeatured = 1 ORDER BY rating DESC LIMIT :limit")
    suspend fun getFeaturedProducts(limit: Int): List<ProductEntity>

    @Query("SELECT * FROM products WHERE isNewArrival = 1 OR id > 20 ORDER BY id DESC LIMIT :limit")
    suspend fun getNewArrivals(limit: Int): List<ProductEntity>

    @Query("DELETE FROM products")
    suspend fun clearProducts(): Int
}
