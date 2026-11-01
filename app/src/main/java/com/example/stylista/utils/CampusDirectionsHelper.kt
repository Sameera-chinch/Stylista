package com.example.stylista.utils

import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.*

data class RouteResult(
    val points: List<LatLng>,
    val distanceMeters: Int,
    val durationSeconds: Int,
    val distanceText: String,
    val durationText: String,
    val isFallback: Boolean = false,
    val fallbackReason: String? = null
)

object CampusDirectionsHelper {

    /**
     * Fetches walking route from Google Directions API or falls back to straight-line Haversine route.
     */
    suspend fun getWalkingRoute(
        origin: LatLng,
        destination: LatLng,
        apiKey: String
    ): RouteResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || apiKey == "YOUR_API_KEY_HERE") {
            return@withContext createFallbackRoute(origin, destination, "Using direct campus path (API key not configured)")
        }

        try {
            val urlString = "https://maps.googleapis.com/maps/api/directions/json" +
                    "?origin=${origin.latitude},${origin.longitude}" +
                    "&destination=${destination.latitude},${destination.longitude}" +
                    "&mode=walking" +
                    "&key=$apiKey"

            val url = URL(urlString)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6000
                readTimeout = 6000
                doInput = true
            }

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = reader.use { it.readText() }
                connection.disconnect()

                val json = JSONObject(response)
                val status = json.optString("status")

                if (status == "OK") {
                    val routes = json.getJSONArray("routes")
                    if (routes.length() > 0) {
                        val route = routes.getJSONObject(0)
                        val legs = route.getJSONArray("legs")
                        val leg = legs.getJSONObject(0)

                        val distanceObj = leg.getJSONObject("distance")
                        val durationObj = leg.getJSONObject("duration")

                        val distanceMeters = distanceObj.getInt("value")
                        val distanceText = distanceObj.getString("text")

                        val durationSecs = durationObj.getInt("value")
                        val durationText = durationObj.getString("text")

                        val overviewPolyline = route.getJSONObject("overview_polyline")
                        val encodedPoints = overviewPolyline.getString("points")
                        val decodedPath = decodePolyline(encodedPoints)

                        return@withContext RouteResult(
                            points = decodedPath,
                            distanceMeters = distanceMeters,
                            durationSeconds = durationSecs,
                            distanceText = distanceText,
                            durationText = durationText,
                            isFallback = false
                        )
                    }
                } else {
                    val errorMsg = json.optString("error_message", "Directions status: $status")
                    return@withContext createFallbackRoute(origin, destination, errorMsg)
                }
            }

            createFallbackRoute(origin, destination, "Network error: HTTP $responseCode")
        } catch (e: Exception) {
            createFallbackRoute(origin, destination, "Offline mode / direct path (${e.localizedMessage ?: "Unknown error"})")
        }
    }

    /**
     * Fallback straight-line estimation with Haversine formula and average walking speed (~1.35 m/s).
     */
    fun createFallbackRoute(origin: LatLng, destination: LatLng, reason: String): RouteResult {
        val distanceMeters = calculateHaversineDistance(origin, destination).roundToInt()
        val durationSeconds = ((distanceMeters / 1.35)).roundToInt().coerceAtLeast(30)

        val distanceText = if (distanceMeters < 1000) {
            "$distanceMeters m"
        } else {
            String.format("%.1f km", distanceMeters / 1000.0)
        }

        val minutes = (durationSeconds + 59) / 60
        val durationText = if (minutes <= 1) "1 min" else "$minutes mins"

        return RouteResult(
            points = listOf(origin, destination),
            distanceMeters = distanceMeters,
            durationSeconds = durationSeconds,
            distanceText = distanceText,
            durationText = durationText,
            isFallback = true,
            fallbackReason = reason
        )
    }

    /**
     * Calculates great-circle distance between two points in meters.
     */
    fun calculateHaversineDistance(start: LatLng, end: LatLng): Double {
        val earthRadius = 6371000.0 // meters
        val dLat = Math.toRadians(end.latitude - start.latitude)
        val dLng = Math.toRadians(end.longitude - start.longitude)
        val lat1 = Math.toRadians(start.latitude)
        val lat2 = Math.toRadians(end.latitude)

        val a = sin(dLat / 2).pow(2.0) +
                sin(dLng / 2).pow(2.0) * cos(lat1) * cos(lat2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }

    /**
     * Decodes encoded Google Maps polyline string into list of LatLng coordinates.
     */
    fun decodePolyline(encoded: String): List<LatLng> {
        val poly = ArrayList<LatLng>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0

        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lat += dlat

            shift = 0
            result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lng += dlng

            val p = LatLng(lat.toDouble() / 1E5, lng.toDouble() / 1E5)
            poly.add(p)
        }
        return poly
    }
}
