package com.example.stylista

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.example.stylista.database.CampusLocationEntity
import com.example.stylista.utils.CampusDirectionsHelper
import com.example.stylista.utils.CampusLocationsData
import com.example.stylista.utils.CampusRepository
import com.example.stylista.utils.RouteResult
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.chip.ChipGroup
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

class CampusMapActivity : AppCompatActivity(), OnMapReadyCallback {

    private var map: GoogleMap? = null
    private lateinit var repository: CampusRepository
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var osmWebView: WebView
    private var isUsingOsm = true

    private var allLocations: List<CampusLocationEntity> = emptyList()
    private val locationMarkerMap = HashMap<Marker, CampusLocationEntity>()
    private var selectedLocation: CampusLocationEntity? = null
    private var userLastLocation: Location? = null

    // Navigation state
    private var activePolyline: Polyline? = null
    private var isNavigating = false
    private var activeRouteResult: RouteResult? = null

    // UI Elements
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>
    private lateinit var bottomSheetView: View
    private lateinit var searchAutoComplete: AutoCompleteTextView
    private lateinit var btnClearSearch: TextView
    private lateinit var categoryChipGroup: ChipGroup
    private lateinit var layoutLocating: LinearLayout
    private lateinit var tvLocatingStatus: TextView
    private lateinit var navHudContainer: LinearLayout
    private lateinit var tvNavTargetTitle: TextView
    private lateinit var tvNavRemainingInfo: TextView
    private lateinit var btnStopNav: TextView
    private lateinit var fabMyLocation: FloatingActionButton
    private lateinit var fabMapLayers: FloatingActionButton

    // Map style state (0: General Street Map (OSM), 1: Satellite & Streets, 2: Terrain Street Map, 3: Stylista Styled)
    private var currentMapTypeIndex = 0

    // Bottom sheet details views
    private lateinit var tvPlaceName: TextView
    private lateinit var tvPlaceCategory: TextView
    private lateinit var tvPlaceFloor: TextView
    private lateinit var tvPlaceHours: TextView
    private lateinit var tvPlaceDescription: TextView
    private lateinit var layoutRouteEstimate: LinearLayout
    private lateinit var tvRouteSummary: TextView
    private lateinit var btnNavigate: TextView
    private lateinit var btnOpenExternalMap: TextView
    private lateinit var btnSharePlace: TextView
    private lateinit var btnCloseSheet: TextView

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        if (fineGranted || coarseGranted) {
            enableUserLocation()
            fetchCurrentLocation(animateCamera = true)
        } else {
            showSnackbar(getString(R.string.location_permission_denied))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_campus_map)

        repository = CampusRepository(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        initViews()
        setupBottomSheet()
        setupSearch()
        setupCategoryChips()
        setupButtons()
        setupOsmWebView()
        setupLocationTrackingCallback()
        loadCampusLocations()
        checkLocationPermissionsOnLaunch()

        val apiKey = getMapsApiKey()
        if (apiKey.isNotBlank() && apiKey != "YOUR_API_KEY_HERE") {
            val mapFragment = supportFragmentManager.findFragmentById(R.id.mapFragment) as? SupportMapFragment
            mapFragment?.getMapAsync(this)
        } else {
            // Use OpenStreetMap General Street Map directly (guaranteed visible and interactive)
            switchToOsmMap("street")
        }
    }

    private fun initViews() {
        bottomSheetView = findViewById(R.id.placeDetailsBottomSheet)
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheetView)

        searchAutoComplete = findViewById(R.id.searchAutoComplete)
        btnClearSearch = findViewById(R.id.btnClearSearch)
        categoryChipGroup = findViewById(R.id.categoryChipGroup)
        layoutLocating = findViewById(R.id.layoutLocating)
        tvLocatingStatus = findViewById(R.id.tvLocatingStatus)

        navHudContainer = findViewById(R.id.navHudContainer)
        tvNavTargetTitle = findViewById(R.id.tvNavTargetTitle)
        tvNavRemainingInfo = findViewById(R.id.tvNavRemainingInfo)
        btnStopNav = findViewById(R.id.btnStopNav)
        fabMyLocation = findViewById(R.id.fabMyLocation)
        fabMapLayers = findViewById(R.id.fabMapLayers)
        osmWebView = findViewById(R.id.osmWebView)

        tvPlaceName = findViewById(R.id.tvPlaceName)
        tvPlaceCategory = findViewById(R.id.tvPlaceCategory)
        tvPlaceFloor = findViewById(R.id.tvPlaceFloor)
        tvPlaceHours = findViewById(R.id.tvPlaceHours)
        tvPlaceDescription = findViewById(R.id.tvPlaceDescription)
        layoutRouteEstimate = findViewById(R.id.layoutRouteEstimate)
        tvRouteSummary = findViewById(R.id.tvRouteSummary)
        btnNavigate = findViewById(R.id.btnNavigate)
        btnOpenExternalMap = findViewById(R.id.btnOpenExternalMap)
        btnSharePlace = findViewById(R.id.btnSharePlace)
        btnCloseSheet = findViewById(R.id.btnCloseSheet)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupOsmWebView() {
        osmWebView.settings.javaScriptEnabled = true
        osmWebView.settings.domStorageEnabled = true
        osmWebView.settings.loadWithOverviewMode = true
        osmWebView.settings.useWideViewPort = true
        osmWebView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (allLocations.isNotEmpty()) {
                    updateOsmMarkers(allLocations)
                }
            }
        }
        osmWebView.addJavascriptInterface(object {
            @JavascriptInterface
            fun onMarkerClicked(name: String) {
                runOnUiThread {
                    val loc = allLocations.find { it.name.equals(name, ignoreCase = true) }
                    if (loc != null) {
                        showPlaceDetails(loc)
                    }
                }
            }

            @JavascriptInterface
            fun onMapClicked() {
                runOnUiThread {
                    if (bottomSheetBehavior.state != BottomSheetBehavior.STATE_HIDDEN && !isNavigating) {
                        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                        clearActiveRoute()
                    }
                }
            }
        }, "AndroidBridge")

        loadOsmHtml()
    }

    private fun loadOsmHtml() {
        val centerLat = CampusLocationsData.CAMPUS_CENTER.latitude
        val centerLng = CampusLocationsData.CAMPUS_CENTER.longitude
        val currentNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val isNight = currentNightMode == Configuration.UI_MODE_NIGHT_YES
        val mapBg = if (isNight) "#121212" else "#FFF9EA"
        val tileFilter = if (isNight) ".leaflet-tile-pane { filter: invert(100%) hue-rotate(180deg) brightness(95%) contrast(90%); }" else ""
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
            <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
            <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
            <style>
              body, html, #map { margin: 0; padding: 0; width: 100%; height: 100%; background: $mapBg; }
              $tileFilter
              .custom-pin {
                display: flex;
                align-items: center;
                justify-content: center;
                width: 32px;
                height: 32px;
                border-radius: 50%;
                color: white;
                font-weight: bold;
                font-size: 15px;
                box-shadow: 0 3px 8px rgba(0,0,0,0.35);
                border: 2px solid #FFFFFF;
                cursor: pointer;
              }
              .user-pulse {
                width: 18px;
                height: 18px;
                background: #659ACB;
                border-radius: 50%;
                border: 3px solid #FFFFFF;
                box-shadow: 0 0 12px rgba(101,154,203,0.9);
              }
            </style>
            </head>
            <body>
            <div id="map"></div>
            <script>
              var campusCenter = [$centerLat, $centerLng];
              var map = L.map('map', { zoomControl: false, attributionControl: false }).setView(campusCenter, 18);
              
              var streetLayer = L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', { maxZoom: 20 });
              var satelliteLayer = L.tileLayer('https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}', { maxZoom: 19 });
              var topoLayer = L.tileLayer('https://{s}.tile.opentopomap.org/{z}/{x}/{y}.png', { maxZoom: 17 });

              var defaultLayer = streetLayer;
              defaultLayer.addTo(map);

              var currentTileLayer = defaultLayer;
              var markers = {};
              var userMarker = null;
              var routeLine = null;

              map.on('click', function(e) {
                if (window.AndroidBridge && window.AndroidBridge.onMapClicked) {
                  window.AndroidBridge.onMapClicked();
                }
              });

              function setLayer(layerType) {
                map.removeLayer(currentTileLayer);
                if (layerType === 'satellite') {
                  currentTileLayer = satelliteLayer;
                } else if (layerType === 'topo') {
                  currentTileLayer = topoLayer;
                } else if (layerType === 'dark') {
                  currentTileLayer = darkLayer;
                } else {
                  currentTileLayer = streetLayer;
                }
                currentTileLayer.addTo(map);
              }

              function addMarkers(locationsJson) {
                Object.keys(markers).forEach(function(k) { map.removeLayer(markers[k]); });
                markers = {};
                var list = JSON.parse(locationsJson);
                list.forEach(function(loc) {
                  var color = getCategoryColor(loc.category);
                  var icon = L.divIcon({
                    className: 'custom-div-icon',
                    html: '<div class="custom-pin" style="background:' + color + ';">🛍️</div>',
                    iconSize: [32, 32],
                    iconAnchor: [16, 32]
                  });
                  var m = L.marker([loc.latitude, loc.longitude], { icon: icon }).addTo(map);
                  m.bindPopup('<b>' + loc.name + '</b><br>' + loc.category);
                  m.on('click', function() {
                    if (window.AndroidBridge && window.AndroidBridge.onMarkerClicked) {
                      window.AndroidBridge.onMarkerClicked(loc.name);
                    }
                  });
                  markers[loc.name] = m;
                });
              }

              function getCategoryColor(cat) {
                var c = (cat || '').toLowerCase();
                if (c.indexOf('boutique') >= 0) return '#0288D1';
                if (c.indexOf('campus') >= 0) return '#F57C00';
                if (c.indexOf('atelier') >= 0) return '#7B1FA2';
                if (c.indexOf('pickup') >= 0) return '#388E3C';
                if (c.indexOf('accessories') >= 0) return '#E65100';
                if (c.indexOf('athleisure') >= 0) return '#E91E63';
                return '#659ACB';
              }

              function focusLocation(name, lat, lng) {
                map.flyTo([lat, lng], 19, { animate: true, duration: 0.8 });
                if (markers[name]) {
                  markers[name].openPopup();
                }
              }

              function recenterCampus() {
                map.flyTo(campusCenter, 18, { animate: true, duration: 0.6 });
              }

              function updateUserLocation(lat, lng) {
                if (!userMarker) {
                  var icon = L.divIcon({
                    className: 'user-icon',
                    html: '<div class="user-pulse"></div>',
                    iconSize: [18, 18],
                    iconAnchor: [9, 9]
                  });
                  userMarker = L.marker([lat, lng], { icon: icon }).addTo(map);
                } else {
                  userMarker.setLatLng([lat, lng]);
                }
              }

              function drawRoute(pointsJson) {
                if (routeLine) map.removeLayer(routeLine);
                var points = JSON.parse(pointsJson);
                var latLngs = points.map(function(p) { return [p.lat, p.lng]; });
                routeLine = L.polyline(latLngs, { color: '#659ACB', weight: 6, opacity: 0.85 }).addTo(map);
                map.fitBounds(routeLine.getBounds(), { padding: [50, 50] });
              }

              function clearRoute() {
                if (routeLine) {
                  map.removeLayer(routeLine);
                  routeLine = null;
                }
              }
            </script>
            </body>
            </html>
        """.trimIndent()

        osmWebView.loadDataWithBaseURL("https://unpkg.com", html, "text/html", "UTF-8", null)
    }

    private fun setupBottomSheet() {
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        bottomSheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                if (newState == BottomSheetBehavior.STATE_HIDDEN && !isNavigating) {
                    clearActiveRoute()
                }
            }
            override fun onSlide(bottomSheet: View, slideOffset: Float) {}
        })

        btnCloseSheet.setOnClickListener {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            if (!isNavigating) {
                clearActiveRoute()
            }
        }
    }

    private fun setupButtons() {
        findViewById<TextView>(R.id.btnBack).setOnClickListener {
            finish()
        }

        findViewById<TextView>(R.id.btnRecenterCampus).setOnClickListener {
            recenterCampusView()
        }

        fabMyLocation.setOnClickListener {
            checkLocationPermissionsAndFocus()
        }

        fabMapLayers.setOnClickListener {
            showMapTypeDialog()
        }

        btnNavigate.setOnClickListener {
            selectedLocation?.let { location ->
                if (!isNavigating) {
                    if (activeRouteResult == null) {
                        requestAndDrawRoute(location, startNavImmediately = false)
                    } else {
                        startActiveNavigation(location)
                    }
                } else {
                    stopActiveNavigation()
                }
            }
        }

        btnStopNav.setOnClickListener {
            stopActiveNavigation()
        }

        btnOpenExternalMap.setOnClickListener {
            selectedLocation?.let { loc ->
                openInGoogleMapsApp(loc)
            }
        }

        btnSharePlace.setOnClickListener {
            selectedLocation?.let { loc ->
                shareLocationDetails(loc)
            }
        }
    }

    private fun setupSearch() {
        searchAutoComplete.doAfterTextChanged { text ->
            btnClearSearch.visibility = if (text.isNullOrEmpty()) View.GONE else View.VISIBLE
        }

        btnClearSearch.setOnClickListener {
            searchAutoComplete.setText("")
            hideKeyboard()
        }

        searchAutoComplete.setOnItemClickListener { parent, _, position, _ ->
            val selectedName = parent.getItemAtPosition(position) as? String ?: return@setOnItemClickListener
            val foundLocation = allLocations.find { it.name.equals(selectedName, ignoreCase = true) }
            if (foundLocation != null) {
                hideKeyboard()
                searchAutoComplete.clearFocus()
                focusLocationOnMap(foundLocation)
            }
        }

        searchAutoComplete.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = searchAutoComplete.text.toString().trim()
                if (query.isNotBlank()) {
                    hideKeyboard()
                    val match = allLocations.find {
                        it.name.contains(query, ignoreCase = true) ||
                                it.category.contains(query, ignoreCase = true) ||
                                it.description.contains(query, ignoreCase = true)
                    }
                    if (match != null) {
                        focusLocationOnMap(match)
                    } else {
                        showSnackbar("No Stylista outlet found matching \"$query\"")
                    }
                }
                true
            } else {
                false
            }
        }
    }

    private fun setupCategoryChips() {
        categoryChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            val selectedChipId = checkedIds.first()
            val category = when (selectedChipId) {
                R.id.chipBoutique -> "Boutique"
                R.id.chipCampusStore -> "Campus Store"
                R.id.chipAtelier -> "Atelier"
                R.id.chipPickup -> "Pickup Hub"
                R.id.chipAccessories -> "Accessories Hub"
                R.id.chipAthleisure -> "Athleisure"
                else -> "All"
            }
            filterMarkersByCategory(category)
        }
    }

    private fun setupLocationTrackingCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                userLastLocation = location

                // Update OSM user marker
                if (::osmWebView.isInitialized) {
                    osmWebView.evaluateJavascript("updateUserLocation(${location.latitude}, ${location.longitude});", null)
                }

                if (isNavigating && selectedLocation != null) {
                    val destLatLng = LatLng(selectedLocation!!.latitude, selectedLocation!!.longitude)
                    val userLatLng = LatLng(location.latitude, location.longitude)
                    val distanceMeters = CampusDirectionsHelper.calculateHaversineDistance(userLatLng, destLatLng)

                    val distanceText = if (distanceMeters < 1000) {
                        "${distanceMeters.toInt()} m"
                    } else {
                        String.format(Locale.getDefault(), "%.1f km", distanceMeters / 1000.0)
                    }
                    val walkingSecs = ((distanceMeters / 1.35)).toInt()
                    val mins = (walkingSecs + 59) / 60
                    val durationText = if (mins <= 1) "1 min" else "$mins mins"

                    tvNavRemainingInfo.text = getString(R.string.remaining_info, distanceText, durationText)

                    // Keep camera centered on user
                    map?.animateCamera(CameraUpdateFactory.newLatLng(userLatLng))

                    // Arrival threshold: 20 meters
                    if (distanceMeters <= 20.0) {
                        showArrivalDialog(selectedLocation!!.name)
                        stopActiveNavigation()
                    }
                }
            }
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        configureMapSettings()
        loadCampusLocations()
        checkLocationPermissionsOnLaunch()
    }

    private fun configureMapSettings() {
        val gMap = map ?: return

        val currentNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        if (currentNightMode == Configuration.UI_MODE_NIGHT_YES) {
            applyMapTheme()
        }

        gMap.uiSettings.isCompassEnabled = true
        gMap.uiSettings.isZoomControlsEnabled = true
        gMap.uiSettings.isMapToolbarEnabled = true
        gMap.uiSettings.isRotateGesturesEnabled = true
        gMap.uiSettings.isScrollGesturesEnabled = true
        gMap.uiSettings.isTiltGesturesEnabled = true
        gMap.uiSettings.isZoomGesturesEnabled = true
        gMap.isBuildingsEnabled = true

        recenterCampusView()

        gMap.setOnMarkerClickListener { marker ->
            val location = locationMarkerMap[marker]
            if (location != null) {
                showPlaceDetails(location)
            }
            false
        }

        gMap.setOnMapClickListener {
            if (bottomSheetBehavior.state != BottomSheetBehavior.STATE_HIDDEN && !isNavigating) {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                clearActiveRoute()
            }
        }
    }

    private fun showMapTypeDialog() {
        val options = arrayOf(
            getString(R.string.map_type_street),
            getString(R.string.map_type_satellite),
            getString(R.string.map_type_terrain),
            getString(R.string.map_type_campus)
        )
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.map_type_dialog_title))
            .setSingleChoiceItems(options, currentMapTypeIndex) { dialog, which ->
                currentMapTypeIndex = which
                applySelectedMapType(which)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun applySelectedMapType(typeIndex: Int) {
        when (typeIndex) {
            0 -> {
                // General Street Map (Standard OpenStreetMap & Google Street Map)
                switchToOsmMap("street")
                map?.mapType = GoogleMap.MAP_TYPE_NORMAL
                map?.setMapStyle(null)
                map?.isTrafficEnabled = true
                showSnackbar("🗺️ General Street Map enabled")
            }
            1 -> {
                // Satellite with Street overlay
                switchToOsmMap("satellite")
                map?.mapType = GoogleMap.MAP_TYPE_HYBRID
                showSnackbar("🛰️ Satellite & Streets view enabled")
            }
            2 -> {
                // Terrain Street Map
                switchToOsmMap("topo")
                map?.mapType = GoogleMap.MAP_TYPE_TERRAIN
                showSnackbar("⛰️ Terrain Street Map view enabled")
            }
            3 -> {
                // Themed Stylista Boutique Map
                switchToOsmMap("dark")
                map?.mapType = GoogleMap.MAP_TYPE_NORMAL
                applyMapTheme()
                showSnackbar("👗 Stylista Boutique Themed Map enabled")
            }
        }
    }

    private fun switchToOsmMap(layer: String) {
        isUsingOsm = true
        osmWebView.visibility = View.VISIBLE
        findViewById<View>(R.id.mapFragment)?.visibility = View.GONE
        osmWebView.evaluateJavascript("setLayer('$layer');", null)
    }

    private fun openInGoogleMapsApp(location: CampusLocationEntity) {
        val label = Uri.encode("${location.name}, ${CampusLocationsData.CAMPUS_NAME}")
        val gmmIntentUri = Uri.parse("geo:${location.latitude},${location.longitude}?q=${location.latitude},${location.longitude}($label)")
        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
            setPackage("com.google.android.apps.maps")
        }
        try {
            startActivity(mapIntent)
        } catch (e: Exception) {
            val webUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=${location.latitude},${location.longitude}")
            startActivity(Intent(Intent.ACTION_VIEW, webUri))
        }
    }

    private fun applyMapTheme() {
        val gMap = map ?: return
        val currentNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val styleRes = if (currentNightMode == Configuration.UI_MODE_NIGHT_YES) {
            R.raw.map_style_dark
        } else {
            R.raw.map_style_light
        }
        try {
            val success = gMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, styleRes))
            if (!success) {
                gMap.setMapStyle(null)
            }
        } catch (e: Exception) {
            gMap.setMapStyle(null)
        }
    }

    private fun recenterCampusView() {
        map?.animateCamera(
            CameraUpdateFactory.newLatLngZoom(
                CampusLocationsData.CAMPUS_CENTER,
                CampusLocationsData.DEFAULT_ZOOM
            )
        )
        if (::osmWebView.isInitialized) {
            osmWebView.evaluateJavascript("recenterCampus();", null)
        }
    }

    private fun loadCampusLocations() {
        lifecycleScope.launch(Dispatchers.IO) {
            val locations = repository.getLocations()
            withContext(Dispatchers.Main) {
                allLocations = locations
                populateMarkers(locations)
                updateSearchSuggestions(locations)
                updateOsmMarkers(locations)
            }
        }
    }

    private fun populateMarkers(locations: List<CampusLocationEntity>) {
        val gMap = map ?: return
        gMap.clear()
        locationMarkerMap.clear()
        activePolyline = null

        for (loc in locations) {
            val latLng = LatLng(loc.latitude, loc.longitude)
            val markerColor = getMarkerHueForCategory(loc.category)
            val markerOptions = MarkerOptions()
                .position(latLng)
                .title(loc.name)
                .snippet(loc.category)
                .icon(BitmapDescriptorFactory.defaultMarker(markerColor))

            val marker = gMap.addMarker(markerOptions)
            if (marker != null) {
                locationMarkerMap[marker] = loc
            }
        }
    }

    private fun updateOsmMarkers(locations: List<CampusLocationEntity>) {
        if (!::osmWebView.isInitialized) return
        val jsonArray = JSONArray()
        for (loc in locations) {
            val obj = JSONObject().apply {
                put("name", loc.name)
                put("category", loc.category)
                put("latitude", loc.latitude)
                put("longitude", loc.longitude)
                put("floor", loc.floor)
                put("openingHours", loc.openingHours)
            }
            jsonArray.put(obj)
        }
        val escaped = jsonArray.toString().replace("'", "\\'")
        osmWebView.evaluateJavascript("addMarkers('$escaped');", null)
    }

    private fun getMarkerHueForCategory(category: String): Float {
        return when (category.lowercase(Locale.getDefault())) {
            "boutique" -> BitmapDescriptorFactory.HUE_AZURE
            "campus store" -> BitmapDescriptorFactory.HUE_ORANGE
            "atelier" -> BitmapDescriptorFactory.HUE_VIOLET
            "pickup hub" -> BitmapDescriptorFactory.HUE_GREEN
            "accessories hub", "accessories" -> BitmapDescriptorFactory.HUE_YELLOW
            "athleisure" -> BitmapDescriptorFactory.HUE_ROSE
            else -> BitmapDescriptorFactory.HUE_BLUE
        }
    }

    private fun filterMarkersByCategory(category: String) {
        val filtered = if (category == "All") {
            allLocations
        } else {
            allLocations.filter { it.category.equals(category, ignoreCase = true) }
        }
        populateMarkers(filtered)
        updateOsmMarkers(filtered)
    }

    private fun updateSearchSuggestions(locations: List<CampusLocationEntity>) {
        val names = locations.map { it.name }
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, names)
        searchAutoComplete.setAdapter(adapter)
    }

    private fun focusLocationOnMap(location: CampusLocationEntity) {
        selectedLocation = location
        val target = LatLng(location.latitude, location.longitude)
        map?.animateCamera(CameraUpdateFactory.newLatLngZoom(target, 19.0f), 600, object : GoogleMap.CancelableCallback {
            override fun onFinish() {
                showPlaceDetails(location)
            }
            override fun onCancel() {}
        })

        if (::osmWebView.isInitialized) {
            val escapedName = location.name.replace("'", "\\'")
            osmWebView.evaluateJavascript("focusLocation('$escapedName', ${location.latitude}, ${location.longitude});", null)
            showPlaceDetails(location)
        }
    }

    private fun showPlaceDetails(location: CampusLocationEntity) {
        selectedLocation = location
        tvPlaceName.text = location.name
        tvPlaceCategory.text = location.category

        if (location.floor.isNotBlank()) {
            tvPlaceFloor.visibility = View.VISIBLE
            tvPlaceFloor.text = "Floor: ${location.floor}"
        } else {
            tvPlaceFloor.visibility = View.GONE
        }

        if (location.openingHours.isNotBlank()) {
            tvPlaceHours.visibility = View.VISIBLE
            tvPlaceHours.text = "Hours: ${location.openingHours}"
        } else {
            tvPlaceHours.visibility = View.GONE
        }

        tvPlaceDescription.text = location.description
        btnNavigate.text = getString(R.string.btn_navigate)
        layoutRouteEstimate.visibility = View.GONE
        activeRouteResult = null

        bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED

        // Calculate and show instant distance estimate if user location is available
        userLastLocation?.let { userLoc ->
            val userLatLng = LatLng(userLoc.latitude, userLoc.longitude)
            val destLatLng = LatLng(location.latitude, location.longitude)
            val distanceMeters = CampusDirectionsHelper.calculateHaversineDistance(userLatLng, destLatLng).toInt()
            val walkingSecs = (distanceMeters / 1.35).toInt()
            val mins = (walkingSecs + 59) / 60
            val distText = if (distanceMeters < 1000) "$distanceMeters m" else String.format(Locale.getDefault(), "%.1f km", distanceMeters / 1000.0)
            val durText = if (mins <= 1) "1 min" else "$mins mins"

            tvRouteSummary.text = "$distText away • ~$durText walk"
            layoutRouteEstimate.visibility = View.VISIBLE
        }
    }

    private fun requestAndDrawRoute(destination: CampusLocationEntity, startNavImmediately: Boolean) {
        if (!hasLocationPermission()) {
            showRationaleAndRequestPermissions()
            return
        }

        if (!isGpsEnabled()) {
            promptEnableGps()
            return
        }

        layoutLocating.visibility = View.VISIBLE
        tvLocatingStatus.text = "Acquiring live GPS position for routing..."

        lifecycleScope.launch(Dispatchers.IO) {
            val userLoc = fetchLocationSync()
            withContext(Dispatchers.Main) {
                layoutLocating.visibility = View.GONE
                if (userLoc == null) {
                    showSnackbar("Unable to determine current GPS location. Please ensure GPS is active.")
                    return@withContext
                }

                userLastLocation = userLoc
                val userLatLng = LatLng(userLoc.latitude, userLoc.longitude)
                val destLatLng = LatLng(destination.latitude, destination.longitude)

                if (!CampusLocationsData.CAMPUS_BOUNDS.contains(userLatLng)) {
                    showSnackbar(getString(R.string.nav_outside_campus))
                }

                val apiKey = getMapsApiKey()

                lifecycleScope.launch(Dispatchers.IO) {
                    val routeResult = CampusDirectionsHelper.getWalkingRoute(userLatLng, destLatLng, apiKey)
                    withContext(Dispatchers.Main) {
                        drawRouteOnMap(routeResult, userLatLng, destLatLng)

                        if (routeResult.isFallback) {
                            showSnackbar(getString(R.string.error_offline_route))
                        }

                        tvRouteSummary.text = "${routeResult.distanceText} • ${routeResult.durationText} walk"
                        layoutRouteEstimate.visibility = View.VISIBLE
                        activeRouteResult = routeResult
                        btnNavigate.text = getString(R.string.btn_start_nav)

                        if (startNavImmediately) {
                            startActiveNavigation(destination)
                        }
                    }
                }
            }
        }
    }

    private fun drawRouteOnMap(route: RouteResult, origin: LatLng, destination: LatLng) {
        val gMap = map
        if (gMap != null) {
            activePolyline?.remove()

            val polylineOptions = PolylineOptions()
                .addAll(route.points)
                .width(14f)
                .geodesic(true)
                .startCap(RoundCap())
                .endCap(RoundCap())

            if (route.isFallback) {
                polylineOptions.color(ContextCompat.getColor(this, R.color.route_fallback))
                polylineOptions.pattern(listOf(Dash(30f), Gap(20f)))
            } else {
                polylineOptions.color(ContextCompat.getColor(this, R.color.route_walking))
            }

            activePolyline = gMap.addPolyline(polylineOptions)

            val builder = LatLngBounds.Builder()
            builder.include(origin)
            builder.include(destination)
            for (pt in route.points) {
                builder.include(pt)
            }
            val bounds = builder.build()
            gMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 180))
        }

        if (::osmWebView.isInitialized) {
            val pointsArr = JSONArray()
            for (p in route.points) {
                val ptObj = JSONObject()
                ptObj.put("lat", p.latitude)
                ptObj.put("lng", p.longitude)
                pointsArr.put(ptObj)
            }
            osmWebView.evaluateJavascript("drawRoute('${pointsArr.toString()}');", null)
        }
    }

    private fun startActiveNavigation(destination: CampusLocationEntity) {
        isNavigating = true
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN

        tvNavTargetTitle.text = getString(R.string.navigating_to, destination.name)
        activeRouteResult?.let {
            tvNavRemainingInfo.text = getString(R.string.remaining_info, it.distanceText, it.durationText)
        }
        navHudContainer.visibility = View.VISIBLE

        startLiveLocationUpdates()
    }

    private fun stopActiveNavigation() {
        isNavigating = false
        navHudContainer.visibility = View.GONE
        stopLiveLocationUpdates()
        clearActiveRoute()
        btnNavigate.text = getString(R.string.btn_navigate)
    }

    private fun clearActiveRoute() {
        activePolyline?.remove()
        activePolyline = null
        activeRouteResult = null
        if (::osmWebView.isInitialized) {
            osmWebView.evaluateJavascript("clearRoute();", null)
        }
    }

    private fun showArrivalDialog(destinationName: String) {
        AlertDialog.Builder(this)
            .setTitle("Arrived! 📍")
            .setMessage(getString(R.string.arrived_destination_msg, destinationName))
            .setPositiveButton("OK", null)
            .show()
    }

    private fun shareLocationDetails(location: CampusLocationEntity) {
        val mapLink = "https://www.google.com/maps/search/?api=1&query=${location.latitude},${location.longitude}"
        val shareText = buildString {
            append("📍 ${location.name} (${CampusLocationsData.CAMPUS_NAME})\n")
            append("Category: ${location.category}\n")
            if (location.floor.isNotBlank()) append("Floor: ${location.floor}\n")
            if (location.openingHours.isNotBlank()) append("Hours: ${location.openingHours}\n")
            append("Description: ${location.description}\n\n")
            append("Location Link: $mapLink")
        }

        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, shareText)
            type = "text/plain"
        }
        startActivity(Intent.createChooser(sendIntent, "Share Stylista Store Location"))
    }

    // =========================================================================
    // PERMISSIONS & DYNAMIC LOCATION LOGIC
    // =========================================================================

    private fun checkLocationPermissionsOnLaunch() {
        if (hasLocationPermission()) {
            enableUserLocation()
            fetchCurrentLocation(animateCamera = false)
        }
        // If not granted, we allow user to explore map without popups until they request GPS action
    }

    private fun checkLocationPermissionsAndFocus() {
        if (!hasLocationPermission()) {
            showRationaleAndRequestPermissions()
            return
        }

        if (!isGpsEnabled()) {
            promptEnableGps()
            return
        }

        enableUserLocation()
        fetchCurrentLocation(animateCamera = true)
    }

    private fun showRationaleAndRequestPermissions() {
        AlertDialog.Builder(this)
            .setTitle("Boutique Location Access")
            .setMessage(getString(R.string.location_permission_rationale))
            .setPositiveButton("Continue") { _, _ ->
                requestLocationPermissions()
            }
            .setNegativeButton("Explore Stores") { dialog, _ ->
                dialog.dismiss()
                showSnackbar(getString(R.string.location_permission_denied))
            }
            .show()
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermissions() {
        locationPermissionRequest.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    @SuppressLint("MissingPermission")
    private fun enableUserLocation() {
        if (hasLocationPermission()) {
            map?.isMyLocationEnabled = true
            map?.uiSettings?.isMyLocationButtonEnabled = false
        }
    }

    @SuppressLint("MissingPermission")
    private fun fetchCurrentLocation(animateCamera: Boolean) {
        if (!hasLocationPermission()) return

        layoutLocating.visibility = View.VISIBLE
        tvLocatingStatus.text = "Acquiring live GPS position..."

        val cts = CancellationTokenSource()
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
            .addOnSuccessListener { location ->
                layoutLocating.visibility = View.GONE
                if (location != null) {
                    userLastLocation = location
                    if (::osmWebView.isInitialized) {
                        osmWebView.evaluateJavascript("updateUserLocation(${location.latitude}, ${location.longitude});", null)
                    }
                    if (animateCamera) {
                        val userLatLng = LatLng(location.latitude, location.longitude)
                        map?.animateCamera(CameraUpdateFactory.newLatLngZoom(userLatLng, 18.5f))
                        if (::osmWebView.isInitialized) {
                            osmWebView.evaluateJavascript("map.flyTo([${location.latitude}, ${location.longitude}], 18.5);", null)
                        }
                    }
                    val latStr = String.format(Locale.getDefault(), "%.4f", location.latitude)
                    val lngStr = String.format(Locale.getDefault(), "%.4f", location.longitude)
                    showSnackbar("📍 Your position: $latStr, $lngStr")
                } else {
                    fallbackToLastLocation(animateCamera)
                }
            }
            .addOnFailureListener {
                layoutLocating.visibility = View.GONE
                fallbackToLastLocation(animateCamera)
            }
    }

    @SuppressLint("MissingPermission")
    private fun fallbackToLastLocation(animateCamera: Boolean) {
        if (!hasLocationPermission()) return
        fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
            if (loc != null) {
                userLastLocation = loc
                if (::osmWebView.isInitialized) {
                    osmWebView.evaluateJavascript("updateUserLocation(${loc.latitude}, ${loc.longitude});", null)
                }
                if (animateCamera) {
                    map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(loc.latitude, loc.longitude), 18.5f))
                    if (::osmWebView.isInitialized) {
                        osmWebView.evaluateJavascript("map.flyTo([${loc.latitude}, ${loc.longitude}], 18.5);", null)
                    }
                }
                val latStr = String.format(Locale.getDefault(), "%.4f", loc.latitude)
                val lngStr = String.format(Locale.getDefault(), "%.4f", loc.longitude)
                showSnackbar("📍 Last known position: $latStr, $lngStr")
            } else {
                showSnackbar("GPS fix unavailable. Please check location settings.")
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun fetchLocationSync(): Location? = withContext(Dispatchers.IO) {
        if (!hasLocationPermission()) return@withContext null
        return@withContext try {
            val task = fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            com.google.android.gms.tasks.Tasks.await(task) ?: userLastLocation
        } catch (e: Exception) {
            userLastLocation
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLiveLocationUpdates() {
        if (!hasLocationPermission()) return
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2500)
            .setMinUpdateIntervalMillis(1500)
            .setMinUpdateDistanceMeters(2f)
            .build()

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
    }

    private fun stopLiveLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun isGpsEnabled(): Boolean {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        return locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true ||
                locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
    }

    private fun promptEnableGps() {
        AlertDialog.Builder(this)
            .setTitle("Location Services Disabled")
            .setMessage(getString(R.string.enable_gps_prompt))
            .setPositiveButton("Settings") { _, _ ->
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun getMapsApiKey(): String {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            appInfo.metaData?.getString("com.google.android.geo.API_KEY") ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(findViewById(R.id.campusCoordinatorLayout), message, Snackbar.LENGTH_LONG).show()
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(searchAutoComplete.windowToken, 0)
    }

    override fun onResume() {
        super.onResume()
        if (isNavigating) {
            startLiveLocationUpdates()
        }
    }

    override fun onPause() {
        super.onPause()
        if (isNavigating) {
            stopLiveLocationUpdates()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLiveLocationUpdates()
    }
}
