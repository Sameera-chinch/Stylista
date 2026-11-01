package com.example.stylista.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.stylista.database.entities.AddressEntity
import com.example.stylista.database.entities.*

@Dao
interface AddressDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAddress(address: AddressEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAddresses(addresses: List<AddressEntity>): List<Long>

    @Update
    suspend fun updateAddress(address: AddressEntity): Int

    @Query("SELECT * FROM addresses WHERE userId = :userId ORDER BY isDefault DESC, createdAt DESC")
    suspend fun getAddressesForUser(userId: String): List<AddressEntity>

    @Query("SELECT * FROM addresses WHERE userId = :userId AND isDefault = 1 LIMIT 1")
    suspend fun getDefaultAddress(userId: String): AddressEntity?

    @Query("SELECT * FROM addresses WHERE id = :id AND userId = :userId LIMIT 1")
    suspend fun getAddressById(id: String, userId: String): AddressEntity?

    @Query("UPDATE addresses SET isDefault = 0 WHERE userId = :userId")
    suspend fun clearDefaultFlag(userId: String): Int

    @Query("UPDATE addresses SET isDefault = 1 WHERE id = :addressId AND userId = :userId")
    suspend fun setDefaultAddress(addressId: String, userId: String): Int

    @Query("DELETE FROM addresses WHERE id = :addressId AND userId = :userId")
    suspend fun deleteAddress(addressId: String, userId: String): Int

    @Query("DELETE FROM addresses WHERE userId = :userId")
    suspend fun clearAddressesForUser(userId: String): Int
}
