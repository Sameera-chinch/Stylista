package com.example.stylista.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.stylista.database.entities.*

@Dao
interface CampusLocationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocations(locations: List<CampusLocationEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocation(location: CampusLocationEntity): Long

    @Query("SELECT * FROM campus_locations ORDER BY name ASC")
    suspend fun getAllLocations(): List<CampusLocationEntity>

    @Query("SELECT * FROM campus_locations WHERE category = :category ORDER BY name ASC")
    suspend fun getLocationsByCategory(category: String): List<CampusLocationEntity>

    @Query("SELECT * FROM campus_locations WHERE LOWER(name) LIKE '%' || LOWER(:query) || '%' OR LOWER(category) LIKE '%' || LOWER(:query) || '%' ORDER BY name ASC")
    suspend fun searchLocations(query: String): List<CampusLocationEntity>

    @Query("SELECT * FROM campus_locations WHERE id = :id LIMIT 1")
    suspend fun getLocationById(id: Int): CampusLocationEntity?

    @Query("SELECT COUNT(*) FROM campus_locations")
    suspend fun getLocationsCount(): Int

    @Query("DELETE FROM campus_locations")
    suspend fun clearAllLocations(): Int
}
