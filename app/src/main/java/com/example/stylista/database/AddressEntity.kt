package com.example.stylista.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "addresses")
data class AddressEntity(
    @PrimaryKey
    val id: String,
    val userId: String,
    val name: String,
    val phone: String,
    val pincode: String,
    val houseFlat: String,
    val streetArea: String,
    val landmark: String = "",
    val city: String,
    val state: String,
    val type: String = "Home", // Home, Work, Other
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun getFullAddressString(): String {
        val parts = mutableListOf<String>()
        if (houseFlat.isNotBlank()) parts.add(houseFlat)
        if (streetArea.isNotBlank()) parts.add(streetArea)
        if (landmark.isNotBlank()) parts.add(landmark)
        parts.add("$city, $state - $pincode")
        return parts.joinToString(", ")
    }
}
