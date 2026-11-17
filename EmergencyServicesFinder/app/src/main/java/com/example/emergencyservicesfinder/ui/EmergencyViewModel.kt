package com.example.emergencyservicesfinder.ui

import android.location.Location
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.emergencyservicesfinder.data.model.EmergencyPlace
import com.example.emergencyservicesfinder.data.repository.EmergencyRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class EmergencyViewModel(
    private val repository: EmergencyRepository
) : ViewModel() {

    private val _places = MutableLiveData<List<EmergencyPlace>>()
    val places: LiveData<List<EmergencyPlace>> = _places

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private val _userLocation = MutableLiveData<Location?>()
    val userLocation: LiveData<Location?> = _userLocation

    var selectedCategory: String = "hospital"
        private set

    var selectedRadiusMeters: Int = 1000
        private set

    var isFavoritesMode: Boolean = false
        private set

    private var favoritesJob: Job? = null

    fun setCategory(category: String) {
        if (selectedCategory != category) {
            selectedCategory = category
            if (!isFavoritesMode) {
                fetchNearbyPlaces()
            }
        }
    }

    fun setRadius(radiusMeters: Int) {
        if (selectedRadiusMeters != radiusMeters) {
            selectedRadiusMeters = radiusMeters
            if (!isFavoritesMode) {
                fetchNearbyPlaces()
            }
        }
    }

    fun setFavoritesMode(enabled: Boolean) {
        isFavoritesMode = enabled
        if (enabled) {
            loadFavorites()
        } else {
            favoritesJob?.cancel()
            fetchNearbyPlaces()
        }
    }

    fun setLocation(location: Location) {
        _userLocation.value = location
        if (isFavoritesMode) {
            loadFavorites()
        } else {
            fetchNearbyPlaces()
        }
    }

    fun fetchNearbyPlaces() {
        val loc = _userLocation.value
        if (loc == null) {
            _errorMessage.value = "Waiting for location..."
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val results = repository.fetchNearbyPlaces(
                    category = selectedCategory,
                    radiusMeters = selectedRadiusMeters,
                    userLat = loc.latitude,
                    userLon = loc.longitude
                )
                if (!isFavoritesMode) {
                    _places.value = results
                }
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "Error fetching emergency places"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadFavorites() {
        favoritesJob?.cancel()
        favoritesJob = viewModelScope.launch {
            _isLoading.value = false
            _errorMessage.value = null
            val loc = _userLocation.value
            repository.getFavoritePlacesFlow(loc?.latitude, loc?.longitude).collectLatest { favList ->
                if (isFavoritesMode) {
                    _places.value = favList
                }
            }
        }
    }

    fun toggleFavorite(place: EmergencyPlace) {
        viewModelScope.launch {
            repository.toggleFavorite(place)
            if (!isFavoritesMode) {
                fetchNearbyPlaces()
            }
        }
    }
}

class EmergencyViewModelFactory(
    private val repository: EmergencyRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EmergencyViewModel::class.java)) {
            return EmergencyViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
