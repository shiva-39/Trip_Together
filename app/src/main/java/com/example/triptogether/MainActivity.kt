package com.example.triptogether

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.location.Location
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FindCurrentPlaceRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.maps.DirectionsApi
import com.google.maps.GeoApiContext
import com.google.maps.model.TravelMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.IOException
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private var googleMap: GoogleMap? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var placesClient: PlacesClient
    private lateinit var geoApiContext: GeoApiContext
    private val locationPermissionCode = 1
    private val defaultZoom = 15f
    private var isPopupVisible = false
    private var startLocation: LatLng? = null
    private var endLocation: LatLng? = null
    private var isRideCreator = false
    private var rideId: String? = null
    private var isNavigating = false
    private lateinit var locationCallback: LocationCallback
    private var currentStepIndex = 0
    private var navigationSteps: List<String> = emptyList()
    private var stepEndLocations: List<LatLng> = emptyList()
    private lateinit var rideManager: RideManager
    private val riderMarkers = mutableMapOf<String, Marker>()
    private var lastSOSAlertTime: Long = 0
    private var lastMessageTime: Long = 0
    private var convoyMediaPlayer: MediaPlayer? = null
    private var restStopMediaPlayer: MediaPlayer? = null
    private var fuelStopMediaPlayer: MediaPlayer? = null
    private var isConvoyModeOn = false
    private var leaderId: String? = null
    private var isCameraUpdatePaused = false
    private var isRideStarted = false
    private var routePolyline: com.google.android.gms.maps.model.Polyline? = null
    private var startMarker: Marker? = null
    private var endMarker: Marker? = null
    private val nearbyPlaceMarkers = mutableListOf<Marker>()

    private lateinit var popupContainer: FrameLayout
    private lateinit var backButton: ImageButton
    private lateinit var toggleButton: com.google.android.material.floatingactionbutton.FloatingActionButton
    private lateinit var btn1: Button
    private lateinit var btn2: Button
    private lateinit var btn3: Button
    private lateinit var btn4: Button
    private lateinit var btn5: Button
    private lateinit var routeButton: Button
    private lateinit var endRideButton: Button
    private lateinit var groupName: TextView
    private lateinit var distanceText: TextView
    private lateinit var estimatedTimeText: TextView
    private lateinit var navigationInstructionText: TextView
    private lateinit var chatMessagesContainer: LinearLayout

    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val memberUsernames = mutableMapOf<String, String>()

    private lateinit var sharedPreferences: SharedPreferences

    private var lastFuelStopTime: Long
        get() = sharedPreferences.getLong("lastFuelStopTime", 0L)
        set(value) = sharedPreferences.edit().putLong("lastFuelStopTime", value).apply()

    private var lastRestStopTime: Long
        get() = sharedPreferences.getLong("lastRestStopTime", 0L)
        set(value) = sharedPreferences.edit().putLong("lastRestStopTime", value).apply()

    private val handler = Handler(Looper.getMainLooper())
    private val cleanupRunnable = object : Runnable {
        override fun run() {
            rideManager.cleanupOldEvents()
            handler.postDelayed(this, 30000) // Run every 30 seconds
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val currentUid = auth.currentUser?.uid
        if (currentUid == null) {
            Toast.makeText(this, "Please log in", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        Log.d("MainActivity", "User UID: $currentUid")
        setContentView(R.layout.activity_main)

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)

        try {
            initializeViews()
            initializeFirebase()
            initializeLocationServices()
            initializePlacesApi()
            initializeDirectionsApi()

            val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as? SupportMapFragment
            if (mapFragment != null) {
                Log.d("MainActivity", "Map fragment found, calling getMapAsync")
                mapFragment.getMapAsync(this)
            } else {
                Log.e("MainActivity", "Map fragment not found")
                Toast.makeText(this, "Map fragment not found", Toast.LENGTH_SHORT).show()
            }

            checkLocationPermission()
            setupLocationCallback()
            setupLocationUpdates()

            // Start periodic cleanup
            handler.post(cleanupRunnable)

            popupContainer.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN && isPopupVisible) {
                    hidePopup()
                    true
                } else {
                    false
                }
            }

        } catch (e: Exception) {
            Log.e("MainActivity", "Initialization error: ${e.message}", e)
            Toast.makeText(this, "Initialization error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onMapReady(map: GoogleMap) {
        Log.d("MainActivity", "onMapReady called")
        googleMap = map
        googleMap?.uiSettings?.isZoomControlsEnabled = true
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            googleMap?.isMyLocationEnabled = true
            showMyLocation()
        }
        rideId?.let { rideManager.listenForRiderLocations { updateRiderLocations(it) } }
    }

    private fun initializeViews() {
        popupContainer = findViewById(R.id.popup_container) ?: throw IllegalStateException("popup_container not found")
        backButton = findViewById(R.id.backButton) ?: throw IllegalStateException("back_button not found")
        toggleButton = findViewById(R.id.toggle_button) ?: throw IllegalStateException("toggle_button not found")
        btn1 = findViewById(R.id.btn1) ?: throw IllegalStateException("btn1 not found")
        btn2 = findViewById(R.id.btn2) ?: throw IllegalStateException("btn2 not found")
        btn3 = findViewById(R.id.btn3) ?: throw IllegalStateException("btn3 not found")
        btn4 = findViewById(R.id.btn4) ?: throw IllegalStateException("btn4 not found")
        btn5 = findViewById(R.id.btn5) ?: throw IllegalStateException("btn5 not found")
        routeButton = findViewById(R.id.route_button) ?: throw IllegalStateException("route_button not found")
        endRideButton = findViewById(R.id.end_ride_button) ?: throw IllegalStateException("end_ride_button not found")
        groupName = findViewById(R.id.group_name) ?: throw IllegalStateException("group_name not found")
        distanceText = findViewById(R.id.distanceText) ?: throw IllegalStateException("distanceText not found")
        estimatedTimeText = findViewById(R.id.estimatedTimeText) ?: throw IllegalStateException("estimatedTimeText not found")
        navigationInstructionText = findViewById(R.id.navigationInstructionText) ?: throw IllegalStateException("navigationInstructionText not found")
        chatMessagesContainer = findViewById(R.id.chatMessagesContainer) ?: throw IllegalStateException("chatMessagesContainer not found")
        Log.d("MainActivity", "Views initialized")
    }

    private fun initializeFirebase() {
        rideId = intent.getStringExtra("rideId")
        val currentUser = auth.currentUser

        if (rideId != null) {
            rideManager = RideManager(rideId!!)
            Log.d("MainActivity", "Firebase initialized with rideId from Intent: $rideId")
            fetchRideDetails()
        } else if (currentUser != null) {
            Log.d("MainActivity", "No rideId in Intent, fetching from user_rides for ${currentUser.email}")
            db.collection("user_rides").document(currentUser.email!!).get()
                .addOnSuccessListener { doc ->
                    rideId = doc.getString("rideId")
                    if (rideId != null) {
                        rideManager = RideManager(rideId!!)
                        Log.d("MainActivity", "Firebase initialized with rideId from Firestore: $rideId")
                        fetchRideDetails()
                    } else {
                        Log.w("MainActivity", "No rideId found in user_rides for ${currentUser.email}")
                        Toast.makeText(this, "No ride associated. Redirecting...", Toast.LENGTH_SHORT).show()
                        navigateToHomeScreen()
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("MainActivity", "Failed to fetch user_rides: ${e.message}")
                    Toast.makeText(this, "Error fetching ride details", Toast.LENGTH_SHORT).show()
                    navigateToHomeScreen()
                }
        } else {
            Log.w("MainActivity", "No user logged in, cannot fetch rideId")
            Toast.makeText(this, "Please log in to continue", Toast.LENGTH_SHORT).show()
            navigateToHomeScreen()
        }
    }

    private fun setupRideListeners() {
        if (leaderId == null) {
            Log.w("MainActivity", "leaderId not initialized, deferring setupRideListeners")
            return
        }
        rideId?.let {
            lastSOSAlertTime = System.currentTimeMillis()
            lastMessageTime = System.currentTimeMillis()
            rideManager.listenForMessages { message ->
                runOnUiThread {
                    if (message.timestamp > lastMessageTime) {
                        displayChatMessage(message)
                        lastMessageTime = message.timestamp
                        Log.d("MainActivity", "New message received: ${message.text}")
                    } else {
                        Log.d("MainActivity", "Ignoring old message: ${message.timestamp} < $lastMessageTime")
                    }
                }
            }
            rideManager.listenForSOS { alert ->
                runOnUiThread {
                    if (alert.timestamp > lastSOSAlertTime) {
                        Log.d("MainActivity", "SOS alert received from ${alert.senderId}")
                        playSOSSound()
                        Toast.makeText(this, "SOS from ${memberUsernames[alert.senderId] ?: alert.senderId}", Toast.LENGTH_SHORT).show()
                        lastSOSAlertTime = alert.timestamp
                    } else {
                        Log.d("MainActivity", "Ignoring old SOS alert: ${alert.timestamp} < $lastSOSAlertTime")
                    }
                }
            }
            rideManager.listenForConvoyMode { enabled ->
                runOnUiThread {
                    isConvoyModeOn = enabled
                    Log.d("MainActivity", "Convoy mode changed: $enabled")
                    if (enabled) {
                        playConvoySound()
                        rideManager.checkConvoyDistance(leaderId!!) { memberId, distance, isLeader ->
                            val memberUsername = memberUsernames[memberId] ?: memberId
                            val currentUserId = auth.currentUser?.uid
                            if (currentUserId == leaderId) {
                                val message = "$memberUsername is ${String.format("%.1f", distance)}km away!"
                                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                            } else if (currentUserId == memberId) {
                                val message = "You are ${String.format("%.1f", distance)}km from the leader!"
                                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                    Toast.makeText(this, "Convoy mode: $enabled", Toast.LENGTH_SHORT).show()
                }
            }
            rideManager.listenForRestStops { stop, snapshot ->
                runOnUiThread {
                    Log.d("MainActivity", "Rest stop received: key=${snapshot.key}, sender=${stop.senderId}, timestamp=${stop.timestamp}, admin=${stop.adminInitiated}")
                    if (stop.timestamp > lastRestStopTime) {
                        if (stop.adminInitiated) { // Changed from stop.isAdminInitiated
                            Log.d("MainActivity", "Admin-initiated rest stop detected, playing sound")
                            playRestStopSound()
                            fetchNearbyPlaces()
                            Toast.makeText(this, "Rest stop declared by leader", Toast.LENGTH_SHORT).show()
                        } else {
                            Log.d("MainActivity", "Non-admin rest stop request detected")
                            Toast.makeText(this, "Rest stop requested by ${memberUsernames[stop.senderId] ?: stop.senderId}", Toast.LENGTH_SHORT).show()
                        }
                        lastRestStopTime = stop.timestamp
                    } else {
                        Log.d("MainActivity", "Ignoring old rest stop: ${stop.timestamp} < $lastRestStopTime")
                    }
                }
            }

            rideManager.listenForFuelStops { stop, snapshot ->
                runOnUiThread {
                    Log.d("MainActivity", "Fuel stop received: key=${snapshot.key}, sender=${stop.senderId}, timestamp=${stop.timestamp}, admin=${stop.adminInitiated}")
                    if (stop.timestamp > lastFuelStopTime) {
                        if (stop.adminInitiated) { // Changed from stop.isAdminInitiated
                            Log.d("MainActivity", "Admin-initiated fuel stop detected, playing sound")
                            playFuelStopSound()
                            fetchNearbyPlaces()
                            Toast.makeText(this, "Fuel stop declared by leader", Toast.LENGTH_SHORT).show()
                        } else {
                            Log.d("MainActivity", "Non-admin fuel stop request detected")
                            Toast.makeText(this, "Fuel stop requested by ${memberUsernames[stop.senderId] ?: stop.senderId}", Toast.LENGTH_SHORT).show()
                        }
                        lastFuelStopTime = stop.timestamp
                    } else {
                        Log.d("MainActivity", "Ignoring old fuel stop: ${stop.timestamp} < $lastFuelStopTime")
                    }
                }
            }
            if (auth.currentUser?.uid == leaderId) {
                rideManager.listenForLeaderRequests { request ->
                    runOnUiThread {
                        Log.d("MainActivity", "Leader request received: ${request.text}")
                        displayChatMessage(ChatMsg(request.text, request.senderId, request.timestamp))
                    }
                }
            }
        }
    }

    private fun checkInitialRideStatus() {
        rideId?.let {
            rideManager.checkRideStatus { status ->
                Log.d("MainActivity", "Initial ride status: $status")
                when (status) {
                    "started" -> {
                        if (!isRideStarted) {
                            isRideStarted = true
                            isNavigating = true
                            runOnUiThread {
                                navigationInstructionText.visibility = View.VISIBLE
                                routeButton.visibility = View.GONE
                                endRideButton.visibility = if (auth.currentUser?.uid == leaderId) View.VISIBLE else View.GONE
                                startNavigation()
                                if (leaderId != null) {
                                    setupRideListeners()
                                    enableRideFeatures()
                                } else {
                                    Log.w("MainActivity", "leaderId not initialized, skipping listener setup")
                                }
                                Toast.makeText(this, "Ride is ongoing", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    "ended" -> {
                        if (isRideStarted) {
                            isRideStarted = false
                            isNavigating = false
                            runOnUiThread {
                                stopNavigation()
                                navigationInstructionText.visibility = View.GONE
                                disableRideFeatures()
                                Toast.makeText(this, "Ride has ended", Toast.LENGTH_SHORT).show()
                                endRideInFirestore()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun setupRideStatusListener() {
        rideManager.listenForRideStatus { status ->
            Log.d("MainActivity", "Ride status changed to: $status")
            when (status) {
                "started" -> {
                    if (!isRideStarted) {
                        isRideStarted = true
                        isNavigating = true
                        runOnUiThread {
                            navigationInstructionText.visibility = View.VISIBLE
                            routeButton.visibility = View.GONE
                            endRideButton.visibility = if (auth.currentUser?.uid == leaderId) View.VISIBLE else View.GONE
                            startNavigation()
                            if (leaderId != null) {
                                setupRideListeners()
                                enableRideFeatures()
                            } else {
                                Log.w("MainActivity", "leaderId not initialized, skipping listener setup")
                            }
                            Toast.makeText(this, "Ride started", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                "ended" -> {
                    if (isRideStarted) {
                        isRideStarted = false
                        isNavigating = false
                        runOnUiThread {
                            stopNavigation()
                            navigationInstructionText.visibility = View.GONE
                            disableRideFeatures()
                            Toast.makeText(this, "Ride ended", Toast.LENGTH_SHORT).show()
                            endRideInFirestore()
                        }
                    }
                }
            }
        }
    }

    private fun initializeLocationServices() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        Log.d("MainActivity", "Location services initialized")
    }

    private fun initializePlacesApi() {
        Places.initialize(applicationContext, "AIzaSyA-V4L9_na1mUx_hb0cpvqMes4xTWF1fLk")
        placesClient = Places.createClient(this)
        Log.d("MainActivity", "Places API initialized")
    }

    private fun initializeDirectionsApi() {
        geoApiContext = GeoApiContext.Builder().apiKey("AIzaSyA-V4L9_na1mUx_hb0cpvqMes4xTWF1fLk").build()
        Log.d("MainActivity", "Directions API initialized")
    }

    private fun setupLocationCallback() {
        var lastUpdateTime = 0L
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastUpdateTime >= 10000) { // Update every 10 seconds
                        val currentLatLng = LatLng(location.latitude, location.longitude)
                        rideManager.updateRiderLocation(location.latitude, location.longitude)
                        lastUpdateTime = currentTime
                        Log.d("MainActivity", "Location updated: $currentLatLng")
                        if (isNavigating && !isCameraUpdatePaused) {
                            startLocation = currentLatLng
                            drawRoute()
                            googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, defaultZoom))
                            updateNavigationInstruction(currentLatLng)
                        }
                    }
                } ?: Log.w("MainActivity", "No location result for user")
            }
        }
    }

    private fun setupLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            val locationRequest = LocationRequest.create().apply {
                interval = 5000
                fastestInterval = 5000
                priority = LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY
            }
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
            Log.d("MainActivity", "Location updates requested")
        } else {
            checkLocationPermission()
        }
    }

    private fun enableRideFeatures() {
        if (leaderId == null) {
            Log.w("MainActivity", "leaderId not initialized, deferring enableRideFeatures")
            return
        }
        toggleButton.isEnabled = true
        btn1.isEnabled = true
        btn2.isEnabled = true
        btn3.isEnabled = true
        btn4.isEnabled = true
        btn5.isEnabled = true
        setupRideListeners() // Ensure listeners are active
    }

    private fun disableRideFeatures() {
        toggleButton.isEnabled = false
        btn1.isEnabled = false
        btn2.isEnabled = false
        btn3.isEnabled = false
        btn4.isEnabled = false
        btn5.isEnabled = false
        rideManager.cleanupListeners()
    }

    private fun setupButtonListeners() {
        backButton.setOnClickListener { finish() }
        toggleButton.setOnClickListener { togglePopup() }
        btn1.setOnClickListener {
            val currentUserId = auth.currentUser?.uid ?: return@setOnClickListener
            if (leaderId == null) {
                Toast.makeText(this, "Ride leader not identified", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (currentUserId == leaderId) {
                rideManager.sendSOS(currentUserId)
                Toast.makeText(this, "SOS sent by leader", Toast.LENGTH_SHORT).show()
                Log.d("MainActivity", "Leader sent SOS")
            } else {
                val requesterName = memberUsernames[currentUserId] ?: currentUserId
                val requestMessage = "$requesterName has requested to send an SOS"
                rideManager.sendRequestToLeader(requestMessage, currentUserId, leaderId!!)
                Toast.makeText(this, "SOS request sent to leader", Toast.LENGTH_SHORT).show()
            }
        }
        btn2.setOnClickListener {
            val currentUserId = auth.currentUser?.uid ?: return@setOnClickListener
            if (leaderId == null) {
                Toast.makeText(this, "Ride leader not identified", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (currentUserId == leaderId) {
                if (!isConvoyModeOn) {
                    isConvoyModeOn = true
                    rideManager.toggleConvoyMode(true)
                    Toast.makeText(this, "Convoy mode enabled", Toast.LENGTH_SHORT).show()
                    Log.d("MainActivity", "Leader enabled convoy mode")
                } else if (isNavigating && currentStepIndex >= navigationSteps.size) {
                    isConvoyModeOn = false
                    rideManager.toggleConvoyMode(false)
                    Toast.makeText(this, "Convoy mode disabled (section completed)", Toast.LENGTH_SHORT).show()
                } else {
                    isConvoyModeOn = false
                    rideManager.toggleConvoyMode(false)
                    Toast.makeText(this, "Convoy mode disabled", Toast.LENGTH_SHORT).show()
                }
            } else {
                val requesterName = memberUsernames[currentUserId] ?: currentUserId
                val requestMessage = "$requesterName has requested to toggle convoy mode"
                rideManager.sendRequestToLeader(requestMessage, currentUserId, leaderId!!)
                Toast.makeText(this, "Convoy mode request sent to the leader", Toast.LENGTH_SHORT).show()
            }
        }
        btn3.setOnClickListener {
            val currentUserId = auth.currentUser?.uid ?: return@setOnClickListener
            if (leaderId == null) {
                Toast.makeText(this, "Ride leader not identified", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (currentUserId == leaderId) {
                rideManager.requestFuelStop(currentUserId, isAdminInitiated = true)
                Toast.makeText(this, "Fuel stop declared", Toast.LENGTH_SHORT).show()
                Log.d("MainActivity", "Leader declared fuel stop")
            } else {
                val requesterName = memberUsernames[currentUserId] ?: currentUserId
                val requestMessage = "$requesterName has requested a fuel stop"
                rideManager.sendRequestToLeader(requestMessage, currentUserId, leaderId!!)
                Toast.makeText(this, "Fuel stop request sent to leader", Toast.LENGTH_SHORT).show()
            }
        }
        btn4.setOnClickListener {
            val currentUserId = auth.currentUser?.uid ?: return@setOnClickListener
            if (leaderId == null) {
                Toast.makeText(this, "Ride leader not identified", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (currentUserId == leaderId) {
                rideManager.requestRestStop(currentUserId, isAdminInitiated = true)
                Toast.makeText(this, "Rest stop declared", Toast.LENGTH_SHORT).show()
                Log.d("MainActivity", "Leader declared rest stop")
            } else {
                val requesterName = memberUsernames[currentUserId] ?: currentUserId
                val requestMessage = "$requesterName has requested a rest stop"
                rideManager.sendRequestToLeader(requestMessage, currentUserId, leaderId!!)
                Toast.makeText(this, "Rest stop request sent to leader", Toast.LENGTH_SHORT).show()
            }
        }
        btn5.setOnClickListener { showChatDialog() }
        routeButton.setOnClickListener {
            if (endLocation != null && auth.currentUser?.uid == leaderId) {
                if (!isRideStarted) {
                    isRideStarted = true
                    isNavigating = true
                    routeButton.visibility = View.GONE
                    endRideButton.visibility = View.VISIBLE
                    rideManager.updateRideStatus("started")
                    startNavigation()
                    enableRideFeatures()
                    Toast.makeText(this, "Ride started for all members", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Only the leader can start the ride", Toast.LENGTH_SHORT).show()
            }
        }
        endRideButton.setOnClickListener {
            val currentUserId = auth.currentUser?.uid ?: return@setOnClickListener
            if (leaderId == null || currentUserId != leaderId) {
                Log.w("MainActivity", "Non-leader $currentUserId attempted to end ride or leaderId null")
                Toast.makeText(this, "Only the ride leader can end the ride", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            Log.d("MainActivity", "Leader $currentUserId ending ride $rideId")
            endRideButton.isEnabled = false
            Toast.makeText(this, "Ending ride...", Toast.LENGTH_SHORT).show()

            isNavigating = false
            isRideStarted = false
            stopNavigation()
            navigationInstructionText.visibility = View.GONE
            rideManager.updateRideStatus("ended")
            endRideInFirestore()
        }
    }

    private fun playSOSSound() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val originalVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        val targetVolume = maxVolume / 2

        try {
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, targetVolume, 0)
            Log.d("MainActivity", "Set SOS volume to $targetVolume")
        } catch (e: SecurityException) {
            Log.e("MainActivity", "Permission error adjusting volume: ${e.message}")
            return
        }

        val mediaPlayer = MediaPlayer().apply {
            try {
                setDataSource(applicationContext, android.net.Uri.parse("android.resource://com.example.triptogether/" + R.raw.siren))
                setAudioStreamType(AudioManager.STREAM_ALARM)
                setOnPreparedListener {
                    Log.d("MainActivity", "SOS MediaPlayer prepared, starting playback")
                    start()
                }
                setOnErrorListener { _, what, extra ->
                    Log.e("MainActivity", "SOS MediaPlayer error: what=$what, extra=$extra")
                    release()
                    restoreVolume(audioManager, originalVolume)
                    false
                }
                setOnCompletionListener {
                    Log.d("MainActivity", "SOS sound playback completed")
                    release()
                    restoreVolume(audioManager, originalVolume)
                }
                prepareAsync()
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to prepare SOS sound: ${e.message}")
                release()
                restoreVolume(audioManager, originalVolume)
            }
        }
    }

    private fun playRestStopSound() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val originalVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val targetVolume = maxVolume / 2

        try {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, 0)
            Log.d("MainActivity", "Set rest stop volume to $targetVolume")
        } catch (e: SecurityException) {
            Log.e("MainActivity", "Permission error adjusting volume: ${e.message}")
            Toast.makeText(this, "Cannot adjust volume for rest stop sound", Toast.LENGTH_SHORT).show()
            return
        }

        restStopMediaPlayer?.release()
        restStopMediaPlayer = MediaPlayer().apply {
            try {
                val uri = android.net.Uri.parse("android.resource://com.example.triptogether/" + R.raw.rest)
                Log.d("MainActivity", "Attempting to load rest stop sound from URI: $uri")
                setDataSource(applicationContext, uri)
                setAudioStreamType(AudioManager.STREAM_MUSIC)
                setOnPreparedListener {
                    Log.d("MainActivity", "Rest stop MediaPlayer prepared, starting playback")
                    start()
                }
                setOnErrorListener { _, what, extra ->
                    Log.e("MainActivity", "Rest stop MediaPlayer error: what=$what, extra=$extra")
                    Toast.makeText(this@MainActivity, "Error playing rest stop sound", Toast.LENGTH_SHORT).show()
                    release()
                    restStopMediaPlayer = null
                    restoreVolume(audioManager, originalVolume)
                    false
                }
                setOnCompletionListener {
                    Log.d("MainActivity", "Rest stop sound playback completed")
                    release()
                    restStopMediaPlayer = null
                    restoreVolume(audioManager, originalVolume)
                }
                prepareAsync()
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to prepare rest stop sound: ${e.message}")
                Toast.makeText(this@MainActivity, "Failed to play rest stop sound: ${e.message}", Toast.LENGTH_LONG).show()
                release()
                restStopMediaPlayer = null
                restoreVolume(audioManager, originalVolume)
            }
        }
    }

    private fun playFuelStopSound() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val originalVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val targetVolume = maxVolume / 2

        try {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, 0)
            Log.d("MainActivity", "Set fuel stop volume to $targetVolume")
        } catch (e: SecurityException) {
            Log.e("MainActivity", "Permission error adjusting volume: ${e.message}")
            Toast.makeText(this, "Cannot adjust volume for fuel stop sound", Toast.LENGTH_SHORT).show()
            return
        }

        fuelStopMediaPlayer?.release()
        fuelStopMediaPlayer = MediaPlayer().apply {
            try {
                val uri = android.net.Uri.parse("android.resource://com.example.triptogether/" + R.raw.fuel)
                Log.d("MainActivity", "Attempting to load fuel stop sound from URI: $uri")
                setDataSource(applicationContext, uri)
                setAudioStreamType(AudioManager.STREAM_MUSIC)
                setOnPreparedListener {
                    Log.d("MainActivity", "Fuel stop MediaPlayer prepared, starting playback")
                    start()
                }
                setOnErrorListener { _, what, extra ->
                    Log.e("MainActivity", "Fuel stop MediaPlayer error: what=$what, extra=$extra")
                    Toast.makeText(this@MainActivity, "Error playing fuel stop sound", Toast.LENGTH_SHORT).show()
                    release()
                    fuelStopMediaPlayer = null
                    restoreVolume(audioManager, originalVolume)
                    false
                }
                setOnCompletionListener {
                    Log.d("MainActivity", "Fuel stop sound playback completed")
                    release()
                    fuelStopMediaPlayer = null
                    restoreVolume(audioManager, originalVolume)
                }
                prepareAsync()
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to prepare fuel stop sound: ${e.message}")
                Toast.makeText(this@MainActivity, "Failed to play fuel stop sound: ${e.message}", Toast.LENGTH_LONG).show()
                release()
                fuelStopMediaPlayer = null
                restoreVolume(audioManager, originalVolume)
            }
        }
    }

    private fun playConvoySound() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val originalVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val targetVolume = maxVolume / 2

        try {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, 0)
            Log.d("MainActivity", "Set convoy volume to $targetVolume")
        } catch (e: SecurityException) {
            Log.e("MainActivity", "Permission error adjusting volume: ${e.message}")
            return
        }

        convoyMediaPlayer?.release()
        convoyMediaPlayer = MediaPlayer().apply {
            try {
                setDataSource(applicationContext, android.net.Uri.parse("android.resource://com.example.triptogether/" + R.raw.convoy))
                setAudioStreamType(AudioManager.STREAM_MUSIC)
                setOnPreparedListener {
                    Log.d("MainActivity", "Convoy MediaPlayer prepared, starting playback")
                    start()
                }
                setOnErrorListener { _, what, extra ->
                    Log.e("MainActivity", "Convoy MediaPlayer error: what=$what, extra=$extra")
                    release()
                    convoyMediaPlayer = null
                    restoreVolume(audioManager, originalVolume)
                    false
                }
                setOnCompletionListener {
                    Log.d("MainActivity", "Convoy sound playback completed")
                    release()
                    convoyMediaPlayer = null
                    restoreVolume(audioManager, originalVolume)
                }
                prepareAsync()
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to prepare convoy sound: ${e.message}")
                release()
                convoyMediaPlayer = null
                restoreVolume(audioManager, originalVolume)
            }
        }
    }

    private fun fetchNearbyPlaces() {
        if (!::placesClient.isInitialized) {
            Log.e("MainActivity", "PlacesClient not initialized")
            return
        }

        if (googleMap == null) {
            Log.e("MainActivity", "GoogleMap not initialized")
            return
        }

        val placeFields = listOf(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG, Place.Field.TYPES)
        val request = FindCurrentPlaceRequest.newInstance(placeFields)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.w("MainActivity", "Location permission not granted, requesting")
            checkLocationPermission()
            return
        }

        Log.d("MainActivity", "Fetching nearby places with request: $request")
        isCameraUpdatePaused = true
        placesClient.findCurrentPlace(request).addOnSuccessListener { response ->
            Log.d("MainActivity", "Places API response received, likelihoods size: ${response.placeLikelihoods.size}")

            val places = response.placeLikelihoods
                .filter { likelihood ->
                    val types = likelihood.place.types ?: emptyList()
                    val isRelevantType = types.contains(Place.Type.CAFE) ||
                            types.contains(Place.Type.RESTAURANT) ||
                            types.contains(Place.Type.LODGING) ||
                            types.contains(Place.Type.GAS_STATION)
                    Log.d("MainActivity", "Place ${likelihood.place.name} - Relevant type: $isRelevantType, LatLng: ${likelihood.place.latLng}")
                    isRelevantType
                }
                .map { it.place }
                .filter { place ->
                    val placeLatLng = place.latLng
                    if (placeLatLng == null) {
                        Log.w("MainActivity", "Place ${place.name} has no LatLng, skipping")
                        return@filter false
                    }
                    if (startLocation == null || endLocation == null) {
                        Log.w("MainActivity", "Start or end location null, skipping bearing filter for ${place.name}")
                        return@filter true
                    }
                    val bearingToDest = calculateBearing(startLocation!!, endLocation!!)
                    val bearingToPlace = calculateBearing(startLocation!!, placeLatLng)
                    val bearingDiff = Math.abs(bearingToDest - bearingToPlace)
                    val isTowardsDestination = bearingDiff < 90
                    Log.d("MainActivity", "Place ${place.name} - Bearing to dest: $bearingToDest, to place: $bearingToPlace, diff: $bearingDiff, towards: $isTowardsDestination")
                    isTowardsDestination
                }

            runOnUiThread {
                Log.d("MainActivity", "Processing ${places.size} places on UI thread")
                nearbyPlaceMarkers.forEach { it.remove() }
                nearbyPlaceMarkers.clear()

                if (places.isEmpty()) {
                    Log.w("MainActivity", "No places to display after filtering")
                    isCameraUpdatePaused = false
                    return@runOnUiThread
                }

                places.forEach { place ->
                    val latLng = place.latLng
                    val name = place.name
                    if (latLng != null && name != null) {
                        val marker = googleMap?.addMarker(
                            MarkerOptions()
                                .position(latLng)
                                .title(name)
                        )
                        if (marker != null) {
                            nearbyPlaceMarkers.add(marker)
                            Log.d("MainActivity", "Marker added for $name at $latLng")
                            marker.showInfoWindow()
                        } else {
                            Log.e("MainActivity", "Failed to add marker for $name at $latLng")
                        }
                    } else {
                        Log.w("MainActivity", "Invalid data for place - Name: $name, LatLng: $latLng")
                    }
                }

                places.firstOrNull()?.latLng?.let { latLng ->
                    googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                    Log.d("MainActivity", "Zoomed to $latLng")
                }

                Handler(Looper.getMainLooper()).postDelayed({
                    isCameraUpdatePaused = false
                    Log.d("MainActivity", "Camera updates resumed")
                }, 5000)
            }
        }.addOnFailureListener { exception ->
            Log.e("MainActivity", "Failed to fetch nearby places: ${exception.message}", exception)
            isCameraUpdatePaused = false
        }
    }

    private fun calculateBearing(from: LatLng, to: LatLng): Float {
        val lat1 = Math.toRadians(from.latitude)
        val lon1 = Math.toRadians(from.longitude)
        val lat2 = Math.toRadians(to.latitude)
        val lon2 = Math.toRadians(to.longitude)

        val dLon = lon2 - lon1
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        val bearing = Math.toDegrees(atan2(y, x)).toFloat()
        return (bearing + 360) % 360
    }

    private fun restoreVolume(audioManager: AudioManager, originalVolume: Int) {
        try {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, originalVolume, 0)
            Log.d("MainActivity", "Restored volume to $originalVolume")
        } catch (e: SecurityException) {
            Log.e("MainActivity", "Permission error restoring volume: ${e.message}")
        }
    }

    private fun showChatDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_chat_input, null)
        val messageInput = dialogView.findViewById<EditText>(R.id.message_input)
        val sendButton = dialogView.findViewById<Button>(R.id.send_button)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Send a Message")
            .setView(dialogView)
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .create()

        sendButton.setOnClickListener {
            val messageText = messageInput.text.toString().trim()
            if (messageText.isNotEmpty()) {
                rideManager.sendMessage(messageText, auth.currentUser!!.uid)
                Toast.makeText(this, "Message sent", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            } else {
                Toast.makeText(this, "Please enter a message", Toast.LENGTH_SHORT).show()
            }
        }
        dialog.show()
    }

    private fun displayChatMessage(message: ChatMsg) {
        val senderName = memberUsernames[message.senderId] ?: message.senderId
        runOnUiThread {
            val messageView = LayoutInflater.from(this)
                .inflate(R.layout.chat_message_item, chatMessagesContainer, false)

            val senderText = messageView.findViewById<TextView>(R.id.senderText)
            val messageText = messageView.findViewById<TextView>(R.id.messageText)

            senderText.text = senderName
            messageText.text = message.text

            chatMessagesContainer.addView(messageView)
            chatMessagesContainer.visibility = View.VISIBLE

            Handler(Looper.getMainLooper()).postDelayed({
                chatMessagesContainer.removeView(messageView)
                if (chatMessagesContainer.childCount == 0) {
                    chatMessagesContainer.visibility = View.GONE
                }
            }, 5000)
        }
    }

    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), locationPermissionCode)
            Log.d("MainActivity", "Requesting location permission")
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == locationPermissionCode && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                Log.d("MainActivity", "Permission granted, enabling map features")
                googleMap?.isMyLocationEnabled = true
                showMyLocation()
                setupLocationUpdates()
            }
        } else {
            Log.w("MainActivity", "Location permission denied")
            Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showMyLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    val currentLatLng = LatLng(it.latitude, it.longitude)
                    googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, defaultZoom))
                    Log.d("MainActivity", "Showing my location: $currentLatLng")
                } ?: Log.w("MainActivity", "No last location available")
            }.addOnFailureListener {
                Log.e("MainActivity", "Failed to get location: ${it.message}")
            }
        }
    }

    private fun startNavigation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            if (startLocation != null && endLocation != null) {
                navigationInstructionText.visibility = View.VISIBLE
                isNavigating = true
                drawRoute()
                Log.d("MainActivity", "Navigation started for user ${auth.currentUser?.uid}")
            } else {
                Log.w("MainActivity", "Cannot start navigation: startLocation or endLocation is null")
                Toast.makeText(this, "Route not ready", Toast.LENGTH_SHORT).show()
            }
        } else {
            Log.w("MainActivity", "Location permission not granted for navigation")
            checkLocationPermission()
        }
    }

    private fun stopNavigation() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        routeButton.visibility = if (auth.currentUser?.uid == leaderId) View.VISIBLE else View.GONE
        endRideButton.visibility = View.GONE
        isNavigating = false
        routePolyline?.remove()
        routePolyline = null
        startMarker?.remove()
        startMarker = null
        endMarker?.remove()
        endMarker = null
        nearbyPlaceMarkers.forEach { it.remove() }
        nearbyPlaceMarkers.clear()
        Log.d("MainActivity", "Navigation stopped")
    }

    private fun togglePopup() {
        if (isPopupVisible) hidePopup() else showPopup()
    }

    private fun showPopup() {
        popupContainer.visibility = View.VISIBLE
        val fadeIn = AnimationUtils.loadAnimation(this, android.R.anim.fade_in)
        listOf(btn1, btn2, btn3, btn4, btn5).forEach { it.apply { visibility = View.VISIBLE; startAnimation(fadeIn) } }
        isPopupVisible = true
        Log.d("MainActivity", "Popup shown")
    }

    private fun hidePopup() {
        val fadeOut = AnimationUtils.loadAnimation(this, android.R.anim.fade_out)
        listOf(btn1, btn2, btn3, btn4, btn5).forEach { it.apply { startAnimation(fadeOut); visibility = View.GONE } }
        popupContainer.visibility = View.GONE
        isPopupVisible = false
        Log.d("MainActivity", "Popup hidden")
    }

    private fun fetchRideDetails() {
        val currentUser = auth.currentUser ?: return
        db.collection("user_rides").document(currentUser.email!!).get()
            .addOnSuccessListener { doc ->
                rideId = doc.getString("rideId")
                if (rideId != null) {
                    rideManager = RideManager(rideId!!)
                    Log.d("MainActivity", "Ride ID fetched: $rideId")
                    db.collection("rides").document(rideId!!).get()
                        .addOnSuccessListener { rideDoc ->
                            if (rideDoc.exists()) {
                                groupName.text = rideDoc.getString("name") ?: "Unnamed Ride"
                                isRideCreator = rideDoc.getString("email") == currentUser.email
                                leaderId = if (isRideCreator) currentUser.uid else rideDoc.getString("leaderId") ?: ""
                                Log.d("MainActivity", "leaderId set to $leaderId")

                                // Setup listeners and check status only after leaderId is set
                                checkInitialRideStatus()
                                setupRideStatusListener()

                                if (isRideStarted && leaderId != null) {
                                    enableRideFeatures()
                                }

                                routeButton.visibility = if (isRideCreator) View.VISIBLE else View.GONE
                                endRideButton.visibility = if (isRideCreator && isNavigating) View.VISIBLE else View.GONE

                                val startPointMap = rideDoc.get("startPoint") as? Map<String, Double>
                                val destinationMap = rideDoc.get("destination") as? Map<String, Double>
                                if (startPointMap != null && destinationMap != null) {
                                    startLocation = LatLng(startPointMap["latitude"]!!, startPointMap["longitude"]!!)
                                    endLocation = LatLng(destinationMap["latitude"]!!, destinationMap["longitude"]!!)
                                    drawRoute()
                                    Log.d("MainActivity", "Route drawn from $startLocation to $endLocation")
                                }

                                setupButtonListeners()
                                setupRideListeners()
                            } else {
                                Log.w("MainActivity", "Ride document does not exist for rideId: $rideId")
                                Toast.makeText(this, "Ride not found", Toast.LENGTH_SHORT).show()
                                leaderId = currentUser.uid
                                disableRideFeatures()
                                setupButtonListeners()
                                navigateToHomeScreen()
                            }
                        }.addOnFailureListener { e ->
                            Log.e("MainActivity", "Failed to fetch ride details: ${e.message}")
                            Toast.makeText(this, "Failed to fetch ride details: ${e.message}", Toast.LENGTH_SHORT).show()
                            leaderId = currentUser.uid
                            disableRideFeatures()
                            setupButtonListeners()
                            navigateToHomeScreen()
                        }

                    db.collection("rides").document(rideId!!).collection("members")
                        .addSnapshotListener { snapshot, e ->
                            if (e != null) {
                                Log.e("MainActivity", "Members listener failed: ${e.message}")
                                return@addSnapshotListener
                            }
                            if (snapshot != null) {
                                memberUsernames.clear()
                                for (memberDoc in snapshot.documents) {
                                    val uuid = memberDoc.id
                                    val username = memberDoc.getString("username") ?: uuid
                                    memberUsernames[uuid] = username
                                }
                                Log.d("MainActivity", "Updated member usernames: $memberUsernames")
                            }
                        }
                } else {
                    Log.w("MainActivity", "No ride associated with user")
                    Toast.makeText(this, "No ride associated", Toast.LENGTH_SHORT).show()
                    leaderId = currentUser.uid
                    disableRideFeatures()
                    setupButtonListeners()
                    navigateToHomeScreen()
                }
            }.addOnFailureListener { e ->
                Log.e("MainActivity", "Failed to fetch user_rides: ${e.message}")
                Toast.makeText(this, "Failed to fetch user_rides: ${e.message}", Toast.LENGTH_SHORT).show()
                leaderId = currentUser.uid
                disableRideFeatures()
                setupButtonListeners()
                navigateToHomeScreen()
            }
    }

    private fun drawRoute() {
        if (startLocation == null || endLocation == null) return
        Thread {
            try {
                val directionsResult = DirectionsApi.newRequest(geoApiContext)
                    .origin(com.google.maps.model.LatLng(startLocation!!.latitude, startLocation!!.longitude))
                    .destination(com.google.maps.model.LatLng(endLocation!!.latitude, endLocation!!.longitude))
                    .mode(TravelMode.DRIVING)
                    .await()

                runOnUiThread {
                    routePolyline?.remove()
                    startMarker?.remove()
                    endMarker?.remove()

                    val path = mutableListOf<LatLng>()
                    val legs = directionsResult.routes[0].legs[0]
                    navigationSteps = legs.steps.map { step ->
                        "${android.text.Html.fromHtml(step.htmlInstructions, android.text.Html.FROM_HTML_MODE_LEGACY)} (${step.distance.humanReadable})"
                    }
                    stepEndLocations = legs.steps.map { LatLng(it.endLocation.lat, it.endLocation.lng) }
                    currentStepIndex = 0
                    if (navigationSteps.isNotEmpty()) navigationInstructionText.text = navigationSteps[currentStepIndex]

                    legs.steps.forEach { step -> path.addAll(step.polyline.decodePath().map { LatLng(it.lat, it.lng) }) }
                    routePolyline = googleMap?.addPolyline(PolylineOptions().addAll(path).width(10f).color(Color.BLUE))
                    startMarker = googleMap?.addMarker(MarkerOptions().position(startLocation!!).title("Start"))
                    endMarker = googleMap?.addMarker(MarkerOptions().position(endLocation!!).title("Destination"))

                    distanceText.text = "Distance: ${legs.distance.humanReadable}"
                    estimatedTimeText.text = "Estimated Time: ${legs.duration.humanReadable}"
                    if (!isNavigating) googleMap?.animateCamera(CameraUpdateFactory.newLatLngBounds(
                        com.google.android.gms.maps.model.LatLngBounds.builder().include(startLocation!!).include(endLocation!!).build(), 100))
                    Log.d("MainActivity", "Route drawn successfully")
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Log.e("MainActivity", "Failed to get directions: ${e.message}")
                    Toast.makeText(this, "Failed to get directions: ${e.message}", Toast.LENGTH_SHORT).show()
                    distanceText.text = "Distance: --"
                    estimatedTimeText.text = "Estimated Time: --"
                    navigationInstructionText.text = "Navigation unavailable"
                }
            }
        }.start()
    }

    private fun updateNavigationInstruction(currentLocation: LatLng) {
        if (navigationSteps.isEmpty() || currentStepIndex >= navigationSteps.size) return
        stepEndLocations.getOrNull(currentStepIndex)?.let { nextStep ->
            if (calculateDistance(currentLocation, nextStep) < 50) {
                currentStepIndex++
                navigationInstructionText.text = if (currentStepIndex < navigationSteps.size) navigationSteps[currentStepIndex]
                else "You have arrived at your destination".also {
                    isNavigating = false
                    isRideStarted = false
                    stopNavigation()
                    rideManager.updateRideStatus("ended")
                    disableRideFeatures()
                    if (auth.currentUser?.uid == leaderId && isConvoyModeOn) {
                        isConvoyModeOn = false
                        rideManager.toggleConvoyMode(false)
                        Toast.makeText(this, "Convoy mode disabled (destination reached)", Toast.LENGTH_SHORT).show()
                    }
                    Toast.makeText(this, "Ride ended: Destination reached", Toast.LENGTH_SHORT).show()
                    endRideInFirestore()
                }
                Log.d("MainActivity", "Navigation step updated: $currentStepIndex")
            }
        }
    }

    private fun calculateDistance(from: LatLng, to: LatLng): Float {
        val results = FloatArray(1)
        Location.distanceBetween(from.latitude, from.longitude, to.latitude, to.longitude, results)
        return results[0]
    }

    private fun endRideInFirestore() {
        rideId?.let { id ->
            Log.d("MainActivity", "Starting endRideInFirestore for ride ID: $id")
            val batch = db.batch()
            val currentUser = auth.currentUser ?: run {
                Log.e("MainActivity", "No authenticated user")
                runOnUiThread {
                    Toast.makeText(this, "Authentication error", Toast.LENGTH_SHORT).show()
                    navigateToHomeScreen()
                }
                return@let
            }
            val leaderEmail = currentUser.email?.trim()?.lowercase() ?: "unknown@example.com"
            Log.d("MainActivity", "Current user: $leaderEmail, UID: ${currentUser.uid}")

            val rideRef = db.collection("rides").document(id)
            batch.update(rideRef, mapOf("status" to "ended"))
            Log.d("MainActivity", "Added ride status update: rides/$id/status = ended")

            db.collection("rides").document(id).get()
                .addOnSuccessListener { rideDoc ->
                    if (!rideDoc.exists()) {
                        Log.e("MainActivity", "Ride $id does not exist")
                        runOnUiThread {
                            Toast.makeText(this, "Ride not found", Toast.LENGTH_SHORT).show()
                            navigateToHomeScreen()
                        }
                        return@addOnSuccessListener
                    }

                    val rideName = rideDoc.getString("name") ?: "Unnamed Ride"
                    val startName = rideDoc.getString("startName") ?: "Unknown"
                    val destinationName = rideDoc.getString("destinationName") ?: "Unknown"
                    val startPoint = rideDoc.get("startPoint") as? Map<String, Double> ?: emptyMap()
                    val destination = rideDoc.get("destination") as? Map<String, Double> ?: emptyMap()
                    val distance = calculateDist(startPoint, destination)
                    val endTimestamp = System.currentTimeMillis()
                    Log.d("MainActivity", "Ride details - name: $rideName, startName: $startName, destinationName: $destinationName, distance: $distance km, end: $endTimestamp")

                    db.collection("rides").document(id).collection("members").get()
                        .addOnSuccessListener { snapshot ->
                            Log.d("MainActivity", "Fetched ${snapshot.size()} members")
                            val groupSize = snapshot.size().toLong()

                            var operationCount = 1
                            val processedEmails = mutableListOf<String>()

                            for (memberDoc in snapshot.documents) {
                                val uid = memberDoc.id
                                val username = memberDoc.getString("username") ?: "Unknown"
                                val email = memberDoc.getString("email")?.trim()?.lowercase()
                                if (email.isNullOrBlank()) {
                                    Log.w("MainActivity", "No email for UID $uid, skipping")
                                    continue
                                }

                                Log.d("MainActivity", "Processing member - UID: $uid, username: $username, email: $email")
                                processedEmails.add(email)

                                val userRideRef = db.collection("user_rides").document(email)
                                batch.delete(userRideRef)
                                Log.d("MainActivity", "Added delete: user_rides/$email")
                                operationCount++

                                val pastRideData = mapOf(
                                    "rideId" to id,
                                    "rideName" to rideName,
                                    "username" to username,
                                    "endedAt" to endTimestamp,
                                    "email" to email,
                                    "startPoint" to startName,
                                    "destination" to destinationName,
                                    "distance" to distance,
                                    "groupSize" to groupSize
                                )
                                val userPastRideRef = db.collection("user_past_rides")
                                    .document(email).collection("rides").document(id)
                                batch.set(userPastRideRef, pastRideData)
                                Log.d("MainActivity", "Added set: user_past_rides/$email/rides/$id -> $pastRideData")
                                operationCount++

                                if (operationCount > 500) {
                                    Log.e("MainActivity", "Batch limit exceeded: $operationCount operations")
                                    runOnUiThread {
                                        Toast.makeText(this, "Too many members", Toast.LENGTH_SHORT).show()
                                        navigateToHomeScreen()
                                    }
                                    return@addOnSuccessListener
                                }
                            }

                            if (processedEmails.isEmpty()) {
                                Log.w("MainActivity", "No members with valid emails for ride $id")
                            } else {
                                Log.d("MainActivity", "Processed emails: $processedEmails")
                            }

                            Log.d("MainActivity", "Committing batch with $operationCount operations")
                            batch.commit()
                                .addOnSuccessListener {
                                    Log.d("MainActivity", "Batch committed successfully")
                                    runOnUiThread {
                                        Toast.makeText(this, "Ride ended and archived", Toast.LENGTH_SHORT).show()
                                        navigateToHomeScreen()
                                    }
                                }
                                .addOnFailureListener { e ->
                                    Log.e("MainActivity", "Batch commit failed: ${e.message}", e)
                                    runOnUiThread {
                                        Toast.makeText(this, "Failed to archive ride: ${e.message}", Toast.LENGTH_LONG).show()
                                        navigateToHomeScreen()
                                    }
                                }
                        }
                        .addOnFailureListener { e ->
                            Log.e("MainActivity", "Failed to fetch members: ${e.message}", e)
                            runOnUiThread {
                                Toast.makeText(this, "Error fetching members", Toast.LENGTH_SHORT).show()
                                navigateToHomeScreen()
                            }
                        }
                }
                .addOnFailureListener { e ->
                    Log.e("MainActivity", "Failed to fetch ride: ${e.message}", e)
                    runOnUiThread {
                        Toast.makeText(this, "Error fetching ride", Toast.LENGTH_SHORT).show()
                        navigateToHomeScreen()
                    }
                }
        } ?: run {
            Log.e("MainActivity", "No rideId provided")
            runOnUiThread {
                Toast.makeText(this, "No ride to end", Toast.LENGTH_SHORT).show()
                navigateToHomeScreen()
            }
        }
    }

    private fun navigateToHomeScreen() {
        val intent = Intent(this, home_screen::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        finish()
    }

    private fun updateRiderLocations(locations: Map<String, RiderLocation>) {
        runOnUiThread {
            val currentUserId = auth.currentUser?.uid ?: run {
                Log.w("MainActivity", "No current user ID, skipping rider location update")
                return@runOnUiThread
            }
            if (leaderId == null) {
                Log.w("MainActivity", "leaderId is null, deferring rider location update")
                return@runOnUiThread
            }
            val updatedUserIds = mutableSetOf<String>()
            Log.d("MainActivity", "Processing locations: $locations, currentUserId: $currentUserId, leaderId: $leaderId")

            locations.forEach { (userId, loc) ->
                val latLng = LatLng(loc.latitude, loc.longitude)
                val username = memberUsernames[userId] ?: userId
                val isLeader = userId == leaderId
                val isCurrentUser = userId == currentUserId

                val markerIcon = createCustomMarker(username, isLeader)

                val existingMarker = riderMarkers[userId]
                if (existingMarker != null) {
                    if (existingMarker.position != latLng) {
                        existingMarker.position = latLng
                        Log.d("MainActivity", "Updated marker position for $username at $latLng")
                    }
                    if (existingMarker.title != username) {
                        existingMarker.title = username
                        Log.d("MainActivity", "Updated marker title for $username")
                    }
                    val currentRole = existingMarker.tag as? String
                    val newRole = if (isLeader) "leader" else "member"
                    if (currentRole != newRole) {
                        existingMarker.setIcon(markerIcon)
                        existingMarker.tag = newRole
                        Log.d("MainActivity", "Updated marker icon for $username (isLeader: $isLeader, isCurrentUser: $isCurrentUser)")
                    }
                } else {
                    val marker = googleMap?.addMarker(
                        MarkerOptions()
                            .position(latLng)
                            .title(username)
                            .icon(markerIcon)
                    )
                    if (marker != null) {
                        marker.tag = if (isLeader) "leader" else "member"
                        riderMarkers[userId] = marker
                        Log.d("MainActivity", "Added marker for $username at $latLng (isLeader: $isLeader, isCurrentUser: $isCurrentUser)")
                    } else {
                        Log.e("MainActivity", "Failed to add marker for $username at $latLng")
                    }
                }
                updatedUserIds.add(userId)
            }

            riderMarkers.keys.toList().forEach { userId ->
                if (userId !in updatedUserIds) {
                    riderMarkers[userId]?.remove()
                    riderMarkers.remove(userId)
                    Log.d("MainActivity", "Removed marker for userId: $userId")
                }
            }

            if (locations.isNotEmpty() && !isNavigating && !isCameraUpdatePaused) {
                val boundsBuilder = com.google.android.gms.maps.model.LatLngBounds.Builder()
                locations.values.forEach { loc ->
                    boundsBuilder.include(LatLng(loc.latitude, loc.longitude))
                }
                googleMap?.animateCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 100))
            }

            Log.d("MainActivity", "Rider locations updated: ${locations.size} markers")
        }
    }

    private fun createCustomMarker(username: String, isLeader: Boolean): BitmapDescriptor {
        val width = 200
        val height = 100
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        canvas.drawColor(Color.TRANSPARENT)

        val paint = Paint().apply {
            isAntiAlias = true
            color = if (isLeader) Color.parseColor("#B33F40") else Color.parseColor("#28282B")
            style = Paint.Style.FILL
        }
        val oval = RectF(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawOval(oval, paint)

        val textPaint = Paint().apply {
            isAntiAlias = true
            color = Color.WHITE
            textSize = 30f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(username, width / 2f, height / 2f + 10f, textPaint)

        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    override fun onResume() {
        super.onResume()
        if (rideId != null) {
            checkInitialRideStatus()
            if (leaderId != null) {
                setupRideListeners()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isNavigating) stopNavigation()
        if (::fusedLocationClient.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
        convoyMediaPlayer?.release()
        convoyMediaPlayer = null
        restStopMediaPlayer?.release()
        restStopMediaPlayer = null
        fuelStopMediaPlayer?.release()
        fuelStopMediaPlayer = null
        rideId?.let { rideManager.cleanupListeners() }
        handler.removeCallbacks(cleanupRunnable)
        coroutineScope.launch(Dispatchers.IO) {
            geoApiContext.shutdown()
        }
        Log.d("MainActivity", "Activity destroyed")
    }

    private fun calculateDist(start: Map<String, Double>, end: Map<String, Double>): Double {
        val startLat = start["latitude"]?.toDouble() ?: return 0.0
        val startLon = start["longitude"]?.toDouble() ?: return 0.0
        val endLat = end["latitude"]?.toDouble() ?: return 0.0
        val endLon = end["longitude"]?.toDouble() ?: return 0.0

        val earthRadius = 6371.0
        val dLat = Math.toRadians(endLat - startLat)
        val dLon = Math.toRadians(endLon - startLon)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(startLat)) * cos(Math.toRadians(endLat)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }
}