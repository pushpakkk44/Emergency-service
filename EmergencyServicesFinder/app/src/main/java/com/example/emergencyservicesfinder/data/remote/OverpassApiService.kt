package com.example.emergencyservicesfinder.data.remote

import android.location.Location
import com.example.emergencyservicesfinder.data.model.EmergencyPlace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class OverpassApiService {
    suspend fun fetchEmergencyPlaces(
        category: String,
        radiusMeters: Int,
        userLat: Double,
        userLon: Double
    ): List<EmergencyPlace> = withContext(Dispatchers.IO) {
        val rawQuery = "[out:json];node[amenity=$category](around:$radiusMeters,$userLat,$userLon);out;"
        val encodedQuery = URLEncoder.encode(rawQuery, "UTF-8")
        val urlString = "https://overpass-api.de/api/interpreter?data=$encodedQuery"

        val places = mutableListOf<EmergencyPlace>()
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 15000
        connection.readTimeout = 15000

        try {
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonObject = JSONObject(responseText)
                val elements = jsonObject.optJSONArray("elements")

                if (elements != null) {
                    for (i in 0 until elements.length()) {
                        val elem = elements.getJSONObject(i)
                        val id = elem.optString("id", i.toString())
                        val lat = elem.optDouble("lat", 0.0)
                        val lon = elem.optDouble("lon", 0.0)
                        val tags = elem.optJSONObject("tags")

                        var name = tags?.optString("name")
                        if (name.isNullOrEmpty()) {
                            name = tags?.optString("name:en")
                        }
                        if (name.isNullOrEmpty()) {
                            val capitalizedCat = category.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                            name = "Unnamed $capitalizedCat"
                        }

                        var phone = tags?.optString("phone")
                        if (phone.isNullOrEmpty()) {
                            phone = tags?.optString("contact:phone")
                        }
                        if (phone.isNullOrEmpty()) {
                            phone = tags?.optString("phone:emergency")
                        }
                        if (phone.isNullOrEmpty()) {
                            phone = tags?.optString("mobile")
                        }
                        if (phone.isNullOrEmpty()) {
                            phone = null
                        }

                        val street = tags?.optString("addr:street")
                        val houseNum = tags?.optString("addr:housenumber")
                        val city = tags?.optString("addr:city")
                        val addressParts = listOfNotNull(
                            if (!houseNum.isNullOrEmpty()) houseNum else null,
                            if (!street.isNullOrEmpty()) street else null,
                            if (!city.isNullOrEmpty()) city else null
                        )
                        val address = if (addressParts.isNotEmpty()) addressParts.joinToString(", ") else null

                        // Compute distance using Location.distanceBetween()
                        val results = FloatArray(1)
                        Location.distanceBetween(userLat, userLon, lat, lon, results)
                        val distanceMeters = results[0]

                        places.add(
                            EmergencyPlace(
                                id = id,
                                name = name,
                                category = category,
                                latitude = lat,
                                longitude = lon,
                                distanceMeters = distanceMeters,
                                phone = phone,
                                address = address
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            connection.disconnect()
        }

        // Sort nearest first
        places.sortBy { it.distanceMeters }
        return@withContext places
    }
}
