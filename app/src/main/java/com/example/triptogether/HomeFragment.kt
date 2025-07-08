package com.example.triptogether

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.triptogether.MainActivity
import com.example.triptogether.Revgpt
import com.example.triptogether.R
import com.example.triptogether.home_screen
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeFragment : Fragment() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var welcomeMessage: TextView

    // Views for sections
    private lateinit var liveRideSection: RelativeLayout
    private lateinit var pastRidesSection: RelativeLayout
    private lateinit var bikeSection: RelativeLayout
    private lateinit var performanceSection: RelativeLayout
    private lateinit var chatgptSection: RelativeLayout
    private lateinit var noResultsText: TextView

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    @SuppressLint("SetTextI18n")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        // Initialize location client
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())

        // Initialize views
        val profileImage: ImageView = view.findViewById(R.id.profile_image)
        val greetingText: TextView = view.findViewById(R.id.greeting)
        welcomeMessage = view.findViewById(R.id.welcome_message)
        val liveRideText: LinearLayout = view.findViewById(R.id.go_to_live_ride)
        val rideStatusText: TextView = view.findViewById(R.id.ride_status_text)
        val rideStatusSubText: TextView = view.findViewById(R.id.ride_status_subtext)
        val mapIcon: ImageView = view.findViewById(R.id.mapIcon)
        val arrowRight: ImageView = view.findViewById(R.id.arrowRight)
        val gpt: LinearLayout = view.findViewById(R.id.go_to_gpt)
        val pastRidesRecycler: RecyclerView = view.findViewById(R.id.past_rides_recycler)
        val bikePerformanceRecycler: RecyclerView = view.findViewById(R.id.bike_performance_recycler)
        val performanceRecycler: RecyclerView = view.findViewById(R.id.performance_recycler)
        val searchInput: EditText = view.findViewById(R.id.search_input)
        liveRideSection = view.findViewById(R.id.liveRideSection)
        pastRidesSection = view.findViewById(R.id.past_rides_section)
        bikeSection = view.findViewById(R.id.bike_section)
        performanceSection = view.findViewById(R.id.performance_section)
        chatgptSection = view.findViewById(R.id.chatgpt_section)
        noResultsText = view.findViewById(R.id.no_results_text)

        // Setup RecyclerView for past rides
        val pastRidesAdapter = PastRidesAdapter()
        pastRidesRecycler.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        pastRidesRecycler.adapter = pastRidesAdapter

        // Setup RecyclerView for vehicle performance
        val vehiclePerformanceAdapter = VehiclePerformanceAdapter()
        bikePerformanceRecycler.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        bikePerformanceRecycler.adapter = vehiclePerformanceAdapter

        // Setup RecyclerView for rider performance
        val riderPerformanceAdapter = RiderPerformanceAdapter()
        performanceRecycler.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        performanceRecycler.adapter = riderPerformanceAdapter

        // Fetch and set user name
        val user = getCurrentUser()
        greetingText.text = "Hello, ${user?.displayName ?: "User"}!"
        if (user != null) {
            fetchProfileImage(user, profileImage)
            // Fetch user location
            fetchUserLocation()
        } else {
            Log.e("UserDetailsFragment", "No user logged in")
            showError("Please log in to view profile")
            welcomeMessage.text = "Please log in"
        }

        // Check Firestore for missing details and ride status
        user?.let {
            checkUserDetailsFromFirestore(it.email.toString())
            checkUserRideStatus(liveRideText, rideStatusText, rideStatusSubText, mapIcon, arrowRight)
            fetchPastRides(it.email.toString(), pastRidesAdapter)
            // Load static vehicle performance data
            loadStaticVehiclePerformance(vehiclePerformanceAdapter)
            // Load static rider performance data
            loadStaticRiderPerformance(riderPerformanceAdapter)
        }

        // Navigate to UserDetailsFragment when profile image is clicked
        profileImage.setOnClickListener {
            (activity as? home_screen)?.navigateToUserDetails()
        }

        gpt.setOnClickListener {
            navigateToGpt()
        }

        // Navigate to MainActivity when "live_ride" is clicked (only if there’s a ride)
        liveRideText.setOnClickListener {
            if (rideStatusText.text == "Continue The Ride") {
                navigateToMainActivity()
            }
        }

        // Setup search functionality
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filterSections(s.toString())
            }
        })

        return view
    }

    // Request location permissions
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            fetchUserLocation()
        } else {
            welcomeMessage.text = "Location permission denied"
            showError("Location permission is required to display your current location")
        }
    }

    // Fetch user's current location
    @SuppressLint("MissingPermission")
    private fun fetchUserLocation() {
        // Check permissions
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            // Get last known location
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location ->
                    if (location != null) {
                        // Convert location to area name
                        getAreaNameFromLocation(location.latitude, location.longitude)
                    } else {
                        welcomeMessage.text = "Unable to get location"
                        Log.e("HomeFragment", "Location is null")
                    }
                }
                .addOnFailureListener { e ->
                    welcomeMessage.text = "Failed to get location"
                    Log.e("HomeFragment", "Failed to get location: ${e.message}")
                }
        } else {
            // Request permissions
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    // Convert latitude and longitude to area name
    private fun getAreaNameFromLocation(latitude: Double, longitude: Double) {
        try {
            val geocoder = Geocoder(requireContext(), Locale.getDefault())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                geocoder.getFromLocation(latitude, longitude, 1) { addresses ->
                    handleAreaName(addresses)
                }
            } else {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                handleAreaName(addresses ?: emptyList())
            }
        } catch (e: Exception) {
            welcomeMessage.text = "Unable to get area"
            Log.e("HomeFragment", "Geocoder error: ${e.message}")
        }
    }

    // Handle the area name result
    private fun handleAreaName(addresses: List<Address>) {
        activity?.runOnUiThread {
            if (addresses.isNotEmpty()) {
                val address = addresses[0]
                val areaName = address.locality ?: address.subLocality ?: "Unknown area"
                welcomeMessage.text = areaName
            } else {
                welcomeMessage.text = "Area not found"
            }
        }
    }

    // Filter sections based on search query
    private fun filterSections(query: String) {
        val searchText = query.trim().lowercase()
        var visibleSectionCount = 0

        // List of sections with their corresponding views and titles
        val sections = listOf(
            Pair(liveRideSection, "Live Ride"),
            Pair(pastRidesSection, "Past Rides"),
            Pair(bikeSection, "Vehicle Performance"),
            Pair(performanceSection, "Rider Performance"),
            Pair(chatgptSection, "RevGPT")
        )

        // Show or hide sections based on search query
        for ((sectionView, title) in sections) {
            val isVisible = searchText.isEmpty() || title.lowercase().contains(searchText)
            sectionView.isVisible = isVisible
            if (isVisible) visibleSectionCount++
        }

        // Show "No results" message if no sections are visible
        noResultsText.isVisible = visibleSectionCount == 0
    }

    // Get current authenticated user
    private fun getCurrentUser(): FirebaseUser? {
        return auth.currentUser
    }

    // Navigate to MainActivity
    private fun navigateToMainActivity() {
        try {
            val intent = Intent(requireContext(), MainActivity::class.java)
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("HomeFragment", "Error navigating to MainActivity", e)
        }
    }

    private fun navigateToGpt() {
        try {
            val intent = Intent(requireContext(), Revgpt::class.java)
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("HomeFragment", "Error navigating to Revgpt", e)
        }
    }

    // Check if user has a ride
    @SuppressLint("RestrictedApi")
    private fun checkUserRideStatus(
        liveRideView: LinearLayout,
        rideStatusText: TextView,
        rideStatusSubText: TextView,
        mapIcon: ImageView,
        arrowRight: ImageView
    ) {
        val currentUser = auth.currentUser ?: return
        val email = currentUser.email?.trim()?.lowercase() ?: return
        Log.d("HomeFragment", "Checking ride status for email: $email")

        db.collection("user_rides")
            .document(email)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e("HomeFragment", "Error checking ride status: ${e.message}", e)
                    activity?.runOnUiThread {
                        rideStatusText.text = "You have no live rides"
                        rideStatusSubText.isVisible = false
                        mapIcon.isVisible = false
                        arrowRight.isVisible = false
                        liveRideView.isClickable = false
                    }
                    return@addSnapshotListener
                }

                activity?.runOnUiThread {
                    if (snapshot != null && snapshot.exists()) {
                        val rideId = snapshot.getString("rideId")
                        Log.d("HomeFragment", "Found rideId: $rideId")
                        if (!rideId.isNullOrEmpty()) {
                            db.collection("rides").document(rideId).get()
                                .addOnSuccessListener { rideDoc ->
                                    if (!rideDoc.exists()) {
                                        Log.w(
                                            "HomeFragment",
                                            "Ride $rideId does not exist, clearing user_rides/$email"
                                        )
                                        db.collection("user_rides").document(email).delete()
                                        activity?.runOnUiThread {
                                            rideStatusText.text = "You have no live rides"
                                            rideStatusSubText.isVisible = false
                                            mapIcon.isVisible = false
                                            arrowRight.isVisible = false
                                            liveRideView.isClickable = false
                                        }
                                        return@addOnSuccessListener
                                    }
                                    val status = rideDoc.getString("status") ?: "unknown"
                                    val creatorUid = rideDoc.getString("creatorUid")
                                    Log.d(
                                        "HomeFragment",
                                        "Ride $rideId status: $status, creatorUid: $creatorUid"
                                    )
                                    activity?.runOnUiThread {
                                        if (status == "ended") {
                                            Log.d(
                                                "HomeFragment",
                                                "Ride ended, clearing user_rides/$email"
                                            )
                                            db.collection("user_rides").document(email).delete()
                                            rideStatusText.text = "You have no live rides"
                                            rideStatusSubText.isVisible = false
                                            mapIcon.isVisible = false
                                            arrowRight.isVisible = false
                                            liveRideView.isClickable = false
                                        } else {
                                            Log.d(
                                                "HomeFragment",
                                                "Ride active, showing continue option"
                                            )
                                            rideStatusText.text = "Continue The Ride"
                                            rideStatusSubText.isVisible = true
                                            mapIcon.isVisible = true
                                            arrowRight.isVisible = true
                                            liveRideView.isClickable = true
                                        }
                                    }
                                }
                                .addOnFailureListener { e ->
                                    Log.e(
                                        "HomeFragment",
                                        "Failed to fetch ride $rideId: ${e.message}"
                                    )
                                    activity?.runOnUiThread {
                                        rideStatusText.text = "You have no live rides"
                                        rideStatusSubText.isVisible = false
                                        mapIcon.isVisible = false
                                        arrowRight.isVisible = false
                                        liveRideView.isClickable = false
                                    }
                                }
                        } else {
                            Log.d("HomeFragment", "No rideId in user_rides for $email")
                            rideStatusText.text = "You have no live rides"
                            rideStatusSubText.isVisible = false
                            mapIcon.isVisible = false
                            arrowRight.isVisible = false
                            liveRideView.isClickable = false
                        }
                    } else {
                        Log.d("HomeFragment", "No user_rides document for $email")
                        rideStatusText.text = "You have no live rides"
                        rideStatusSubText.isVisible = false
                        mapIcon.isVisible = false
                        arrowRight.isVisible = false
                        liveRideView.isClickable = false
                    }
                }
            }
    }

    // Check if user details are present in Firestore
    private fun checkUserDetailsFromFirestore(userId: String) {
        var personaldet = false
        var vehdet = false
        var personalCheckDone = false
        var vehicleCheckDone = false

        fun checkBothCompleted() {
            if (personalCheckDone && vehicleCheckDone) {
                if (!vehdet && !personaldet) {
                    showAlertDialog("Please fill in your personal and vehicle details to continue.")
                } else if (!vehdet) {
                    showAlertDialog("Please fill in your vehicle details to continue.")
                } else if (!personaldet) {
                    showAlertDialog("Please fill in your personal details to continue.")
                }
            }
        }

        db.collection("users").document(userId)
            .get()
            .addOnSuccessListener { document ->
                personaldet = document != null && document.exists()
                personalCheckDone = true
                checkBothCompleted()
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "Error fetching document", e)
                personalCheckDone = true
                checkBothCompleted()
            }

        db.collection("vehicles").document(userId)
            .get()
            .addOnSuccessListener { document ->
                vehdet = document != null && document.exists()
                vehicleCheckDone = true
                checkBothCompleted()
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "Error fetching document", e)
                vehicleCheckDone = true
                checkBothCompleted()
            }
    }

    // Display an alert dialog if details are missing
    private fun showAlertDialog(msg: String) {
        if (isAdded) {  // Ensure the fragment is attached to avoid crash
            AlertDialog.Builder(requireContext())
                .setTitle("Incomplete Details")
                .setMessage(msg)
                .setPositiveButton("Go to Details") { _, _ ->
                    (activity as? home_screen)?.navigateToUserDetails()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    // Data class for past rides
    data class PastRide(
        val rideId: String,
        val rideName: String,
        val distance: Double,
        val endedAt: Long,
        val startLat: Double?,
        val startLon: Double?,
        val endLat: Double?,
        val endLon: Double?,
        val startPlace: String,
        val endPlace: String,
        val groupSize: Long
    )

    // Data class for vehicle metric
    data class VehicleMetric(
        val name: String,
        val value: Double,
        val unit: String,
        val maxRange: Double
    )

    // Data class for rider performance metric
    data class RiderMetric(
        val name: String,
        val value: Double,
        val unit: String,
        val maxRange: Double
    )

    // Load static vehicle performance data
    private fun loadStaticVehiclePerformance(adapter: VehiclePerformanceAdapter) {
        val metrics = listOf(
            VehicleMetric("Mileage", 22.5, "km/L", 40.0),
            VehicleMetric("Fuel Tank", 17.5, "L", 30.0),
            VehicleMetric("Oil Life", 75.0, "%", 100.0),
            VehicleMetric("Tire Pressure", 2.5, "bar", 3.5)
        )
        Log.d("HomeFragment", "Loaded ${metrics.size} static vehicle metrics")
        activity?.runOnUiThread {
            adapter.updateMetrics(metrics)
        }
    }

    // Load static rider performance data
    private fun loadStaticRiderPerformance(adapter: RiderPerformanceAdapter) {
        val metrics = listOf(
            RiderMetric("Average Speed", 45.0, "km/h", 80.0),
            RiderMetric("Braking Efficiency", 85.0, "%", 100.0),
            RiderMetric("Cornering Skill", 70.0, "%", 100.0),
            RiderMetric("Ride Smoothness", 60.0, "%", 100.0)
        )
        Log.d("HomeFragment", "Loaded ${metrics.size} static rider performance metrics")
        activity?.runOnUiThread {
            adapter.updateMetrics(metrics)
        }
    }

    // Fetch past rides from Firestore
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun fetchPastRides(email: String, adapter: PastRidesAdapter) {
        Log.d("HomeFragment", "Fetching past rides for email: $email")
        db.collection("user_past_rides")
            .document(email)
            .collection("rides")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e("HomeFragment", "Error fetching past rides: ${e.message}", e)
                    activity?.runOnUiThread {
                        adapter.updateRides(emptyList())
                    }
                    return@addSnapshotListener
                }

                val rides = mutableListOf<PastRide>()
                if (snapshot != null && !snapshot.isEmpty) {
                    for (doc in snapshot.documents) {
                        try {
                            val rideId = doc.getString("rideId") ?: continue
                            val rideName = doc.getString("rideName") ?: "Unnamed Ride"
                            val distance = doc.getDouble("distance") ?: 0.0
                            val endedAt = doc.getLong("endedAt") ?: 0L
                            val startLat = doc.getDouble("startLat")
                            val startLon = doc.getDouble("startLon")
                            val endLat = doc.getDouble("endLat")
                            val endLon = doc.getDouble("endLon")
                            val groupSize = doc.getLong("groupSize") ?: 1L
                            val startName = doc.getString("startPoint") ?: "Unknown"
                            val destName = doc.getString("destination") ?: "Unknown"

                            rides.add(
                                PastRide(
                                    rideId,
                                    rideName,
                                    distance,
                                    endedAt,
                                    startLat,
                                    startLon,
                                    endLat,
                                    endLon,
                                    startName,
                                    destName,
                                    groupSize
                                )
                            )
                        } catch (ex: Exception) {
                            Log.e("HomeFragment", "Error parsing ride ${doc.id}: ${ex.message}")
                        }
                    }
                    Log.d("HomeFragment", "Fetched ${rides.size} past rides")
                } else {
                    Log.d("HomeFragment", "No past rides found for $email")
                }

                activity?.runOnUiThread {
                    adapter.updateRides(rides.sortedByDescending { it.endedAt })
                }
            }
    }

    // RecyclerView Adapter for past rides
    inner class PastRidesAdapter : RecyclerView.Adapter<PastRidesAdapter.PastRideViewHolder>() {

        private var rides: List<PastRide> = emptyList()

        fun updateRides(newRides: List<PastRide>) {
            rides = newRides
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PastRideViewHolder {
            val size = (150 * parent.context.resources.displayMetrics.density).toInt()
            val dp4 = (4 * parent.context.resources.displayMetrics.density).toInt()

            val cardView = MaterialCardView(parent.context).apply {
                layoutParams = ViewGroup.MarginLayoutParams(size, size).apply {
                    setMargins(8, 8, 8, 8)
                }
                radius = 16f
                cardElevation = 6f
                setCardBackgroundColor(android.graphics.Color.WHITE)
                strokeColor = android.graphics.Color.LTGRAY
                strokeWidth = 1
            }

            val mainLayout = LinearLayout(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                orientation = LinearLayout.VERTICAL
                weightSum = 100f
                setPadding(10, 10, 10, 10)
            }

            val imageView = ImageView(parent.context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0
                ).apply {
                    weight = 35f
                }
                setImageResource(R.drawable.ride_placeholder)
                scaleType = ImageView.ScaleType.CENTER_CROP
            }

            val titleTextView = TextView(parent.context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    0
                ).apply {
                    weight = 20f
                    setMargins(0, 4, 0, 4)
                    gravity = android.view.Gravity.CENTER
                }
                textSize = 12f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(android.graphics.Color.BLACK)
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
                gravity = android.view.Gravity.CENTER
            }

            val startDestTextView = TextView(parent.context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0
                ).apply {
                    weight = 10f
                    setMargins(0, 4, 0, 4)
                }
                textSize = 10f
                setTextColor(android.graphics.Color.DKGRAY)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }

            val distanceTextView = TextView(parent.context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0
                ).apply {
                    weight = 10f
                    setMargins(0, 2, 0, 2)
                }
                textSize = 10f
                setTextColor(android.graphics.Color.DKGRAY)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }

            val membersTextView = TextView(parent.context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0
                ).apply {
                    weight = 10f
                    setMargins(0, 2, 0, 2)
                }
                textSize = 10f
                setTextColor(android.graphics.Color.DKGRAY)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }

            val endTimeTextView = TextView(parent.context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0
                ).apply {
                    weight = 10f
                }
                textSize = 10f
                setTextColor(android.graphics.Color.DKGRAY)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }

            mainLayout.addView(imageView)
            mainLayout.addView(titleTextView)
            mainLayout.addView(startDestTextView)
            mainLayout.addView(distanceTextView)
            mainLayout.addView(membersTextView)
            mainLayout.addView(endTimeTextView)
            cardView.addView(mainLayout)

            return PastRideViewHolder(
                cardView,
                titleTextView,
                startDestTextView,
                distanceTextView,
                membersTextView,
                endTimeTextView
            )
        }

        override fun onBindViewHolder(holder: PastRideViewHolder, position: Int) {
            val ride = rides[position]
            holder.titleTextView.text = ride.rideName
            holder.startDestTextView.text = "${ride.startPlace} - ${ride.endPlace}"
            holder.distanceTextView.text = String.format("%.1f km", ride.distance)
            holder.membersTextView.text = "${ride.groupSize} riders"
            holder.endTimeTextView.text =
                SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(ride.endedAt))
        }

        override fun getItemCount(): Int = rides.size

        inner class PastRideViewHolder(
            itemView: View,
            val titleTextView: TextView,
            val startDestTextView: TextView,
            val distanceTextView: TextView,
            val membersTextView: TextView,
            val endTimeTextView: TextView
        ) : RecyclerView.ViewHolder(itemView)
    }

    // RecyclerView Adapter for vehicle performance
    inner class VehiclePerformanceAdapter : RecyclerView.Adapter<VehiclePerformanceAdapter.VehicleMetricViewHolder>() {

        private var metrics: List<VehicleMetric> = emptyList()

        fun updateMetrics(newMetrics: List<VehicleMetric>) {
            metrics = newMetrics
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VehicleMetricViewHolder {
            val size = (150 * parent.context.resources.displayMetrics.density).toInt()
            val dp4 = (2 * parent.context.resources.displayMetrics.density).toInt()
            val dp5 = (6 * parent.context.resources.displayMetrics.density).toInt()
            val meterSize = (60 * parent.context.resources.displayMetrics.density).toInt()

            val cardView = MaterialCardView(parent.context).apply {
                layoutParams = ViewGroup.MarginLayoutParams(size, size).apply {
                    setMargins(8, 8, 8, 8)
                }
                radius = 16f
                cardElevation = 6f
                setCardBackgroundColor(android.graphics.Color.WHITE)
                strokeColor = android.graphics.Color.LTGRAY
                strokeWidth = 1
                setContentPadding(dp4, dp4, dp4, dp4)
            }

            val mainLayout = LinearLayout(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                orientation = LinearLayout.VERTICAL
                weightSum = 90f
                setPadding(dp4, dp4, dp4, dp4)
            }

            val nameTextView = TextView(parent.context).apply {
                id = View.generateViewId()
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    0
                ).apply {
                    weight = 25f
                    gravity = android.view.Gravity.CENTER
                    bottomMargin = dp4
                    setPadding(dp4, dp4, dp4, dp4)
                }
                textSize = 12f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(android.graphics.Color.BLACK)
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
                gravity = android.view.Gravity.CENTER
            }

            val meterView = CircularMeterView(parent.context).apply {
                id = View.generateViewId()
                layoutParams = LinearLayout.LayoutParams(
                    meterSize,
                    meterSize
                ).apply {
                    weight = 30f
                    gravity = android.view.Gravity.CENTER
                    setMargins(dp5, dp5, dp5, dp5)
                }
            }

            val valueTextView = TextView(parent.context).apply {
                id = View.generateViewId()
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    0
                ).apply {
                    weight = 20f
                    gravity = android.view.Gravity.CENTER
                }
                textSize = 10f
                setTextColor(android.graphics.Color.DKGRAY)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                gravity = android.view.Gravity.CENTER
            }

            mainLayout.addView(nameTextView)
            mainLayout.addView(meterView)
            mainLayout.addView(valueTextView)
            cardView.addView(mainLayout)

            return VehicleMetricViewHolder(cardView, nameTextView, meterView, valueTextView)
        }

        override fun onBindViewHolder(holder: VehicleMetricViewHolder, position: Int) {
            val metric = metrics[position]
            holder.nameTextView.text = metric.name
            val progress = ((metric.value / metric.maxRange) * 100).toFloat().coerceIn(0f, 100f)
            Log.d("VehiclePerformanceAdapter", "Metric: ${metric.name}, Progress: $progress")
            holder.meterView.setProgress(progress)
            holder.valueTextView.text = String.format("%.1f %s", metric.value, metric.unit)
        }

        override fun getItemCount(): Int = metrics.size

        inner class VehicleMetricViewHolder(
            itemView: View,
            val nameTextView: TextView,
            val meterView: CircularMeterView,
            val valueTextView: TextView
        ) : RecyclerView.ViewHolder(itemView)
    }

    // RecyclerView Adapter for rider performance
    inner class RiderPerformanceAdapter : RecyclerView.Adapter<RiderPerformanceAdapter.RiderMetricViewHolder>() {

        private var metrics: List<RiderMetric> = emptyList()

        fun updateMetrics(newMetrics: List<RiderMetric>) {
            metrics = newMetrics
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RiderMetricViewHolder {
            val size = (150 * parent.context.resources.displayMetrics.density).toInt()
            val dp4 = (2 * parent.context.resources.displayMetrics.density).toInt()
            val meterSize = (60 * parent.context.resources.displayMetrics.density).toInt()

            val cardView = MaterialCardView(parent.context).apply {
                layoutParams = ViewGroup.MarginLayoutParams(size, size).apply {
                    setMargins(8, 8, 8, 8)
                }
                radius = 16f
                cardElevation = 6f
                setCardBackgroundColor(android.graphics.Color.WHITE)
                strokeColor = android.graphics.Color.LTGRAY
                strokeWidth = 1
                setContentPadding(dp4, dp4, dp4, dp4)
            }

            val mainLayout = LinearLayout(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                orientation = LinearLayout.VERTICAL
                weightSum = 90f
                setPadding(dp4, dp4, dp4, dp4)
            }

            val nameTextView = TextView(parent.context).apply {
                id = View.generateViewId()
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    0
                ).apply {
                    weight = 25f
                    gravity = android.view.Gravity.CENTER
                    bottomMargin = dp4
                    setPadding(dp4, dp4, dp4, dp4)
                }
                textSize = 12f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(android.graphics.Color.BLACK)
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
                gravity = android.view.Gravity.CENTER
            }

            val meterView = CircularMeterView(parent.context).apply {
                id = View.generateViewId()
                layoutParams = LinearLayout.LayoutParams(
                    meterSize,
                    meterSize
                ).apply {
                    weight = 30f
                    gravity = android.view.Gravity.CENTER
                    setMargins(dp4, dp4, dp4, dp4)
                }
            }

            val valueTextView = TextView(parent.context).apply {
                id = View.generateViewId()
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    0
                ).apply {
                    weight = 20f
                    gravity = android.view.Gravity.CENTER
                }
                textSize = 10f
                setTextColor(android.graphics.Color.DKGRAY)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                gravity = android.view.Gravity.CENTER
            }

            mainLayout.addView(nameTextView)
            mainLayout.addView(meterView)
            mainLayout.addView(valueTextView)
            cardView.addView(mainLayout)

            return RiderMetricViewHolder(cardView, nameTextView, meterView, valueTextView)
        }

        override fun onBindViewHolder(holder: RiderMetricViewHolder, position: Int) {
            val metric = metrics[position]
            holder.nameTextView.text = metric.name
            val progress = ((metric.value / metric.maxRange) * 100).toFloat().coerceIn(0f, 100f)
            Log.d("RiderPerformanceAdapter", "Metric: ${metric.name}, Progress: $progress")
            holder.meterView.setProgress(progress)
            holder.valueTextView.text = String.format("%.1f %s", metric.value, metric.unit)
        }

        override fun getItemCount(): Int = metrics.size

        inner class RiderMetricViewHolder(
            itemView: View,
            val nameTextView: TextView,
            val meterView: CircularMeterView,
            val valueTextView: TextView
        ) : RecyclerView.ViewHolder(itemView)
    }

    private fun showError(message: String) {
        context?.let {
            Toast.makeText(it, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun fetchProfileImage(user: FirebaseUser, profileImage: ImageView) {
        val email = user.email ?: return showError("User email not found")
        db.collection("users")
            .document(email)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val base64Image = document.getString("profileImageBase64")
                    if (!base64Image.isNullOrEmpty()) {
                        try {
                            val imageBytes = Base64.decode(base64Image, Base64.DEFAULT)
                            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                            profileImage.setImageBitmap(bitmap)
                            Log.d("UserDetailsFragment", "Profile image loaded successfully")
                        } catch (e: Exception) {
                            showError("Failed to load profile image")
                            Log.e("UserDetailsFragment", "Image decode failed", e)
                        }
                    } else {
                        Log.d("UserDetailsFragment", "No profile image found in Firestore")
                    }
                } else {
                    Log.d("UserDetailsFragment", "No user document found for: $email")
                }
            }
            .addOnFailureListener { e ->
                showError("Failed to fetch profile image")
                Log.e("UserDetailsFragment", "Error fetching user document: $email", e)
            }
    }
}