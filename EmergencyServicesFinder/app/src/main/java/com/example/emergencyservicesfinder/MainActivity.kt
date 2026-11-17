package com.example.emergencyservicesfinder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.emergencyservicesfinder.data.local.AppDatabase
import com.example.emergencyservicesfinder.data.remote.OverpassApiService
import com.example.emergencyservicesfinder.data.repository.EmergencyRepository
import com.example.emergencyservicesfinder.databinding.ActivityMainBinding
import com.example.emergencyservicesfinder.ui.AuthHelper
import com.example.emergencyservicesfinder.ui.EmergencyViewModel
import com.example.emergencyservicesfinder.ui.EmergencyViewModelFactory
import com.example.emergencyservicesfinder.ui.PlaceAdapter
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var adapter: PlaceAdapter
    private lateinit var authHelper: AuthHelper

    private val database by lazy { AppDatabase.getDatabase(this) }
    private val apiService by lazy { OverpassApiService() }
    private val repository by lazy { EmergencyRepository(apiService, database.favoriteDao()) }
    private val viewModel: EmergencyViewModel by viewModels { EmergencyViewModelFactory(repository) }

    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        authHelper.handleSignInResult(
            data = result.data,
            onSuccess = { _, name ->
                Toast.makeText(this, "Signed in as $name", Toast.LENGTH_SHORT).show()
                updateAuthUi()
            },
            onError = { message ->
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                updateAuthUi()
            }
        )
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (fineGranted || coarseGranted) {
            fetchDeviceLocation()
        } else {
            Toast.makeText(
                this,
                "Location permission is required to find nearby services. Using fallback location.",
                Toast.LENGTH_LONG
            ).show()
            useDefaultLocation()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        authHelper = AuthHelper(this)

        setupRecyclerView()
        setupChips()
        setupAuth()
        observeViewModel()

        fetchDeviceLocation()
    }

    private fun setupRecyclerView() {
        adapter = PlaceAdapter(
            onDirectionsClicked = { place ->
                val uri = Uri.parse("geo:${place.latitude},${place.longitude}?q=${place.latitude},${place.longitude}(${Uri.encode(place.name)})")
                val intent = Intent(Intent.ACTION_VIEW, uri)
                try {
                    startActivity(intent)
                } catch (_: Exception) {
                    val webUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=${place.latitude},${place.longitude}")
                    startActivity(Intent(Intent.ACTION_VIEW, webUri))
                }
            },
            onCallClicked = { place ->
                if (!place.phone.isNullOrEmpty()) {
                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${place.phone}"))
                    try {
                        startActivity(intent)
                    } catch (_: Exception) {
                        Toast.makeText(this, "Unable to launch phone dialer", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onFavoriteClicked = { place ->
                viewModel.toggleFavorite(place)
            }
        )
        binding.rvPlaces.layoutManager = LinearLayoutManager(this)
        binding.rvPlaces.adapter = adapter
    }

    private fun setupChips() {
        binding.chipGroupCategory.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                val category = when (checkedIds.first()) {
                    R.id.chipHospital -> "hospital"
                    R.id.chipPharmacy -> "pharmacy"
                    R.id.chipPolice -> "police"
                    else -> "hospital"
                }
                viewModel.setCategory(category)
            }
        }

        binding.chipGroupRadius.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                val radius = when (checkedIds.first()) {
                    R.id.chipRadius1km -> 1000
                    R.id.chipRadius2km -> 2000
                    R.id.chipRadius5km -> 5000
                    else -> 1000
                }
                viewModel.setRadius(radius)
            }
        }

        binding.btnSearchNearby.setOnClickListener {
            viewModel.fetchNearbyPlaces()
        }

        binding.toggleView.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btnTabNearby -> {
                        binding.filtersContainer.visibility = View.VISIBLE
                        viewModel.setFavoritesMode(false)
                    }
                    R.id.btnTabFavorites -> {
                        binding.filtersContainer.visibility = View.GONE
                        viewModel.setFavoritesMode(true)
                    }
                }
            }
        }

        binding.btnRetry.setOnClickListener {
            fetchDeviceLocation()
        }
    }

    private fun setupAuth() {
        updateAuthUi()

        binding.btnLoginWithGoogle.setOnClickListener {
            launchGoogleSignIn()
        }

        binding.btnAuth.setOnClickListener {
            if (authHelper.isUserSignedIn()) {
                authHelper.signOut {
                    Toast.makeText(this, "Signed out", Toast.LENGTH_SHORT).show()
                    updateAuthUi()
                }
            } else {
                launchGoogleSignIn()
            }
        }
    }

    private fun launchGoogleSignIn() {
        val intent = authHelper.getSignInIntent()
        if (intent != null) {
            signInLauncher.launch(intent)
        } else {
            authHelper.handleSignInResult(
                data = null,
                onSuccess = { _, name ->
                    Toast.makeText(this, "Signed in as $name", Toast.LENGTH_SHORT).show()
                    updateAuthUi()
                },
                onError = { msg ->
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    private fun updateAuthUi() {
        if (authHelper.isUserSignedIn()) {
            binding.loginContainer.visibility = View.GONE
            binding.mainFinderContainer.visibility = View.VISIBLE

            val email = authHelper.getUserEmail()
            val name = authHelper.getUserDisplayName()
            binding.tvUserEmail.text = email ?: name ?: "Signed In"
            binding.tvUserEmail.visibility = View.VISIBLE
            binding.btnAuth.text = "Sign Out"
        } else {
            binding.loginContainer.visibility = View.VISIBLE
            binding.mainFinderContainer.visibility = View.GONE

            binding.tvWelcomeUser.text = "Welcome User"
            binding.tvUserEmail.visibility = View.GONE
            binding.btnAuth.text = "Sign In"
        }
    }

    private fun observeViewModel() {
        viewModel.places.observe(this) { placesList ->
            adapter.submitList(placesList)
            if (placesList.isNullOrEmpty()) {
                binding.rvPlaces.visibility = View.GONE
                binding.tvResultsHeader.visibility = View.GONE
                binding.tvFigure3Caption.visibility = View.GONE
                binding.emptyStateView.visibility = View.VISIBLE
                if (viewModel.isFavoritesMode) {
                    binding.tvEmptyTitle.text = "No Favorites Saved"
                    binding.tvEmptySubtitle.text = "Tap the heart icon on any emergency place to save it to your favorites."
                } else {
                    val catName = when (viewModel.selectedCategory.lowercase()) {
                        "pharmacy" -> "Pharmacies"
                        "police" -> "Police Stations"
                        else -> "Hospitals"
                    }
                    val radiusKm = viewModel.selectedRadiusMeters / 1000
                    binding.tvEmptyTitle.text = "No $catName Found"
                    binding.tvEmptySubtitle.text = "No ${catName.lowercase()} were found within $radiusKm km. Try selecting a larger radius."
                }
            } else {
                binding.rvPlaces.visibility = View.VISIBLE
                binding.emptyStateView.visibility = View.GONE
                binding.tvFigure3Caption.visibility = View.VISIBLE
                binding.tvResultsHeader.visibility = View.VISIBLE

                if (viewModel.isFavoritesMode) {
                    binding.tvResultsHeader.text = "Favorite Places"
                    binding.tvFigure3Caption.text = "Figure 4: Favorites Screen"
                } else {
                    val catHeader = when (viewModel.selectedCategory.lowercase()) {
                        "pharmacy" -> "Nearby Pharmacies"
                        "police" -> "Nearby Police Stations"
                        else -> "Nearby Hospitals"
                    }
                    binding.tvResultsHeader.text = catHeader
                    binding.tvFigure3Caption.text = "Figure 3: Nearby Places Screen"
                }
            }
        }

        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        viewModel.errorMessage.observe(this) { error ->
            if (!error.isNullOrEmpty() && !viewModel.isFavoritesMode) {
                Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateLocationDisplay(location: Location) {
        val latLonStr = String.format(java.util.Locale.US, "Lat: %.4f, Lon: %.4f", location.latitude, location.longitude)
        binding.tvLatLon.text = latLonStr
    }

    private fun fetchDeviceLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
            return
        }

        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
            .addOnSuccessListener { location: Location? ->
                if (location != null) {
                    updateLocationDisplay(location)
                    viewModel.setLocation(location)
                } else {
                    fusedLocationClient.lastLocation
                        .addOnSuccessListener { lastLoc: Location? ->
                            if (lastLoc != null) {
                                updateLocationDisplay(lastLoc)
                                viewModel.setLocation(lastLoc)
                            } else {
                                useDefaultLocation()
                            }
                        }
                        .addOnFailureListener {
                            useDefaultLocation()
                        }
                }
            }
            .addOnFailureListener {
                useDefaultLocation()
            }
    }

    private fun useDefaultLocation() {
        val defaultLoc = Location("default").apply {
            latitude = 37.7749
            longitude = -122.4194
        }
        updateLocationDisplay(defaultLoc)
        viewModel.setLocation(defaultLoc)
    }
}
