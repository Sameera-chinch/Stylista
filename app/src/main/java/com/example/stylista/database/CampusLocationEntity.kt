package com.example.stylista.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "campus_locations")
data class CampusLocationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val category: String,
    val description: String,
    val latitude: Double,
    val longitude: Double,
    val floor: String = "",
    val openingHours: String = ""
)
