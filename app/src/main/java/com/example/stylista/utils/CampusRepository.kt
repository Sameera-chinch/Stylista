package com.example.stylista.utils

import android.content.Context
import android.util.Log
import com.example.stylista.database.CampusLocationEntity
import com.example.stylista.database.StylistaDatabase
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CampusRepository(private val context: Context) {

    private val db = StylistaDatabase.getInstance(context)
    private val campusDao = db.campusLocationDao()
    private val firestore = FirebaseFirestore.getInstance()

    companion object {
        private const val TAG = "CampusRepository"
        private const val COLLECTION_CAMPUS_LOCATIONS = "campus_locations"
    }

    suspend fun getLocations(): List<CampusLocationEntity> = withContext(Dispatchers.IO) {
        ensureSeeded()
        // Try syncing from Firestore in background (non-blocking)
        trySyncWithFirestore()
        campusDao.getAllLocations()
    }

    suspend fun getLocationsByCategory(category: String): List<CampusLocationEntity> = withContext(Dispatchers.IO) {
        ensureSeeded()
        if (category == "All" || category.isBlank()) {
            campusDao.getAllLocations()
        } else {
            campusDao.getLocationsByCategory(category)
        }
    }

    suspend fun searchLocations(query: String): List<CampusLocationEntity> = withContext(Dispatchers.IO) {
        ensureSeeded()
        if (query.isBlank()) {
            campusDao.getAllLocations()
        } else {
            campusDao.searchLocations(query.trim())
        }
    }

    suspend fun getLocationById(id: Int): CampusLocationEntity? = withContext(Dispatchers.IO) {
        ensureSeeded()
        campusDao.getLocationById(id)
    }

    private suspend fun ensureSeeded() {
        val count = campusDao.getLocationsCount()
        val all = if (count > 0) campusDao.getAllLocations() else emptyList()
        val hasStylistaBoutiques = all.any { it.name.contains("Stylista", ignoreCase = true) }

        if (count == 0 || !hasStylistaBoutiques) {
            Log.d(TAG, "Campus locations table needs Stylista boutique data, seeding...")
            campusDao.clearAllLocations()
            campusDao.insertLocations(CampusLocationsData.defaultLocations)
        }
    }

    private suspend fun trySyncWithFirestore() {
        try {
            val task = firestore.collection(COLLECTION_CAMPUS_LOCATIONS).get()
            val snapshot = com.google.android.gms.tasks.Tasks.await(task)
            if (!snapshot.isEmpty) {
                val remoteLocations = mutableListOf<CampusLocationEntity>()
                for (doc in snapshot.documents) {
                    val name = doc.getString("name") ?: continue
                    val category = doc.getString("category") ?: "Boutique"
                    val description = doc.getString("description") ?: ""
                    val lat = doc.getDouble("latitude") ?: doc.getDouble("lat") ?: continue
                    val lng = doc.getDouble("longitude") ?: doc.getDouble("lng") ?: continue
                    val floor = doc.getString("floor") ?: ""
                    val hours = doc.getString("openingHours") ?: doc.getString("opening_hours") ?: ""

                    remoteLocations.add(
                        CampusLocationEntity(
                            name = name,
                            category = category,
                            description = description,
                            latitude = lat,
                            longitude = lng,
                            floor = floor,
                            openingHours = hours
                        )
                    )
                }
                if (remoteLocations.isNotEmpty()) {
                    campusDao.insertLocations(remoteLocations)
                    Log.d(TAG, "Successfully synced ${remoteLocations.size} locations from Firestore")
                }
            }
        } catch (e: Exception) {
            // Firestore not configured or offline - fallback safely to Room
            Log.d(TAG, "Firestore sync skipped/offline: ${e.message}")
        }
    }
}
