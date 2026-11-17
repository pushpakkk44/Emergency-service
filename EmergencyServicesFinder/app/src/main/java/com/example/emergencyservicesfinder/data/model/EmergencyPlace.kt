package com.example.emergencyservicesfinder.data.model

data class EmergencyPlace(
    val id: String,
    val name: String,
    val category: String,
    val latitude: Double,
    val longitude: Double,
    val distanceMeters: Float = 0f,
    val phone: String? = null,
    val address: String? = null,
    val isFavorite: Boolean = false
)
