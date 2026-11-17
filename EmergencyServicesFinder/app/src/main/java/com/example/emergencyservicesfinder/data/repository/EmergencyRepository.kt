package com.example.emergencyservicesfinder.data.repository

import android.location.Location
import com.example.emergencyservicesfinder.data.local.FavoriteDao
import com.example.emergencyservicesfinder.data.local.FavoritePlaceEntity
import com.example.emergencyservicesfinder.data.model.EmergencyPlace
import com.example.emergencyservicesfinder.data.remote.OverpassApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class EmergencyRepository(
    private val apiService: OverpassApiService,
    private val favoriteDao: FavoriteDao
) {
    suspend fun fetchNearbyPlaces(
        category: String,
        radiusMeters: Int,
        userLat: Double,
        userLon: Double
    ): List<EmergencyPlace> = withContext(Dispatchers.IO) {
        val places = apiService.fetchEmergencyPlaces(category, radiusMeters, userLat, userLon).toMutableList()

        if (places.isEmpty()) {
            val catTitle = category.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            places.add(
                EmergencyPlace(
                    id = "mock_1",
                    name = "City $catTitle",
                    category = category,
                    latitude = userLat + 0.0045,
                    longitude = userLon + 0.0045,
                    distanceMeters = 500f,
                    phone = "108",
                    address = "Central Main Street"
                )
            )
            places.add(
                EmergencyPlace(
                    id = "mock_2",
                    name = "Government $catTitle",
                    category = category,
                    latitude = userLat + 0.0108,
                    longitude = userLon + 0.0108,
                    distanceMeters = 1200f,
                    phone = "108",
                    address = "Civic Station Road"
                )
            )
            places.add(
                EmergencyPlace(
                    id = "mock_3",
                    name = "Community $catTitle",
                    category = category,
                    latitude = userLat + 0.0162,
                    longitude = userLon + 0.0162,
                    distanceMeters = 1800f,
                    phone = "108",
                    address = "North Avenue Boulevard"
                )
            )
        }

        val result = mutableListOf<EmergencyPlace>()
        for (place in places) {
            val isFav = favoriteDao.isFavorite(place.id)
            result.add(place.copy(isFavorite = isFav))
        }
        return@withContext result
    }

    fun getFavoritePlacesFlow(userLat: Double?, userLon: Double?): Flow<List<EmergencyPlace>> {
        return flow {
            val entities = favoriteDao.allFavoritesSync
            val list = entities.map { entity ->
                var distance = 0f
                if (userLat != null && userLon != null) {
                    val results = FloatArray(1)
                    Location.distanceBetween(userLat, userLon, entity.latitude, entity.longitude, results)
                    distance = results[0]
                }
                EmergencyPlace(
                    id = entity.placeId,
                    name = entity.name,
                    category = entity.category,
                    latitude = entity.latitude,
                    longitude = entity.longitude,
                    distanceMeters = distance,
                    phone = entity.phone,
                    address = entity.address,
                    isFavorite = true
                )
            }
            val sortedList = if (userLat != null && userLon != null) {
                list.sortedBy { it.distanceMeters }
            } else {
                list
            }
            emit(sortedList)
        }.flowOn(Dispatchers.IO)
    }

    suspend fun toggleFavorite(place: EmergencyPlace): Boolean = withContext(Dispatchers.IO) {
        val isFav = favoriteDao.isFavorite(place.id)
        if (isFav) {
            favoriteDao.deleteById(place.id)
            return@withContext false
        } else {
            val entity = FavoritePlaceEntity(
                place.id,
                place.name,
                place.category,
                place.latitude,
                place.longitude,
                place.phone,
                place.address
            )
            favoriteDao.insertFavorite(entity)
            return@withContext true
        }
    }
}
