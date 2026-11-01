package com.example.stylista.data.repository

import android.content.Context
import android.util.Log
import com.example.stylista.database.AddressEntity
import com.example.stylista.database.StylistaDatabase
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

object AddressRepository {

    private const val TAG = "AddressRepository"
    private val firestore: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    suspend fun saveAddress(context: Context, address: AddressEntity) = withContext(Dispatchers.IO) {
        val db = StylistaDatabase.getInstance(context)
        val addressDao = db.addressDao()

        // If this address is set as default, clear default flag on other addresses first
        if (address.isDefault) {
            addressDao.clearDefaultFlag(address.userId)
        }

        // Save to Room DB
        addressDao.insertAddress(address)

        // Dual-write to Cloud Firestore users/{uid}/addresses/{addressId}
        if (address.userId.isNotBlank() && !address.userId.startsWith("guest_")) {
            try {
                val addressMap = hashMapOf(
                    "id" to address.id,
                    "userId" to address.userId,
                    "name" to address.name,
                    "phone" to address.phone,
                    "pincode" to address.pincode,
                    "houseFlat" to address.houseFlat,
                    "streetArea" to address.streetArea,
                    "landmark" to address.landmark,
                    "city" to address.city,
                    "state" to address.state,
                    "type" to address.type,
                    "isDefault" to address.isDefault,
                    "createdAt" to address.createdAt
                )

                firestore.collection("users")
                    .document(address.userId)
                    .collection("addresses")
                    .document(address.id)
                    .set(addressMap)
                    .await()

                // If default, update other addresses in Firestore as non-default
                if (address.isDefault) {
                    val allAddresses = firestore.collection("users")
                        .document(address.userId)
                        .collection("addresses")
                        .get()
                        .await()

                    for (doc in allAddresses.documents) {
                        if (doc.id != address.id && doc.getBoolean("isDefault") == true) {
                            doc.reference.update("isDefault", false)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to sync address to Firestore: ${e.message}")
            }
        }
    }

    suspend fun setDefaultAddress(context: Context, addressId: String, userId: String) = withContext(Dispatchers.IO) {
        val db = StylistaDatabase.getInstance(context)
        val addressDao = db.addressDao()

        addressDao.clearDefaultFlag(userId)
        addressDao.setDefaultAddress(addressId, userId)

        if (userId.isNotBlank() && !userId.startsWith("guest_")) {
            try {
                val collection = firestore.collection("users")
                    .document(userId)
                    .collection("addresses")

                val docs = collection.get().await()
                for (doc in docs.documents) {
                    val isTarget = doc.id == addressId
                    if (doc.getBoolean("isDefault") != isTarget) {
                        doc.reference.update("isDefault", isTarget)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to update default address in Firestore: ${e.message}")
            }
        }
    }

    suspend fun deleteAddress(context: Context, addressId: String, userId: String) = withContext(Dispatchers.IO) {
        val db = StylistaDatabase.getInstance(context)
        db.addressDao().deleteAddress(addressId, userId)

        if (userId.isNotBlank() && !userId.startsWith("guest_")) {
            try {
                firestore.collection("users")
                    .document(userId)
                    .collection("addresses")
                    .document(addressId)
                    .delete()
                    .await()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to delete address from Firestore: ${e.message}")
            }
        }
    }

    suspend fun getAddresses(context: Context, userId: String): List<AddressEntity> = withContext(Dispatchers.IO) {
        val db = StylistaDatabase.getInstance(context)
        val localList = db.addressDao().getAddressesForUser(userId)
        if (localList.isNotEmpty() || userId.startsWith("guest_")) {
            return@withContext localList
        }

        // Try syncing from Firestore
        syncAddressesFromFirestore(context, userId)
        return@withContext db.addressDao().getAddressesForUser(userId)
    }

    suspend fun getAddressById(context: Context, addressId: String, userId: String): AddressEntity? = withContext(Dispatchers.IO) {
        val db = StylistaDatabase.getInstance(context)
        db.addressDao().getAddressById(addressId, userId)
    }

    suspend fun getDefaultAddress(context: Context, userId: String): AddressEntity? = withContext(Dispatchers.IO) {
        val db = StylistaDatabase.getInstance(context)
        val defaultAddr = db.addressDao().getDefaultAddress(userId)
        if (defaultAddr != null) return@withContext defaultAddr

        // If no default explicitly marked, pick first available
        val all = db.addressDao().getAddressesForUser(userId)
        if (all.isNotEmpty()) return@withContext all.first()

        if (!userId.startsWith("guest_")) {
            syncAddressesFromFirestore(context, userId)
            return@withContext db.addressDao().getDefaultAddress(userId)
                ?: db.addressDao().getAddressesForUser(userId).firstOrNull()
        }
        null
    }

    suspend fun syncAddressesFromFirestore(context: Context, userId: String) = withContext(Dispatchers.IO) {
        if (userId.isBlank() || userId.startsWith("guest_")) return@withContext
        try {
            val snapshot = firestore.collection("users")
                .document(userId)
                .collection("addresses")
                .get()
                .await()

            val remoteAddresses = snapshot.documents.mapNotNull { doc ->
                val id = doc.getString("id") ?: doc.id
                val name = doc.getString("name") ?: return@mapNotNull null
                val phone = doc.getString("phone") ?: ""
                val pincode = doc.getString("pincode") ?: ""
                val houseFlat = doc.getString("houseFlat") ?: ""
                val streetArea = doc.getString("streetArea") ?: ""
                val landmark = doc.getString("landmark") ?: ""
                val city = doc.getString("city") ?: ""
                val state = doc.getString("state") ?: ""
                val type = doc.getString("type") ?: "Home"
                val isDefault = doc.getBoolean("isDefault") ?: false
                val createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()

                AddressEntity(
                    id = id,
                    userId = userId,
                    name = name,
                    phone = phone,
                    pincode = pincode,
                    houseFlat = houseFlat,
                    streetArea = streetArea,
                    landmark = landmark,
                    city = city,
                    state = state,
                    type = type,
                    isDefault = isDefault,
                    createdAt = createdAt
                )
            }

            if (remoteAddresses.isNotEmpty()) {
                val db = StylistaDatabase.getInstance(context)
                db.addressDao().insertAddresses(remoteAddresses)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to sync addresses from Firestore: ${e.message}")
        }
    }
}
