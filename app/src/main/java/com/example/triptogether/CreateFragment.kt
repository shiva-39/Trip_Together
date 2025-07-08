package com.example.bottomnav

import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.fragment.app.Fragment
import com.example.triptogether.R
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.AutocompleteSupportFragment
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*

class CreateFragment : Fragment(R.layout.fragment_create) {

    private lateinit var createRideLayout: View
    private lateinit var joinRideLayout: View
    private lateinit var groupDetailsLayout: View
    private lateinit var membersList: LinearLayout

    private lateinit var btnCreateRide: Button
    private lateinit var btnJoinRide: Button
    private lateinit var btnRideDetails: Button

    private lateinit var lineCreate: View
    private lateinit var lineJoin: View
    private lateinit var lineDetails: View

    private lateinit var createSubmitButton: Button
    private lateinit var joinSubmitButton: Button
    private lateinit var rideIdView: TextView
    private lateinit var joinLinkView: TextView
    private lateinit var joinRideIdInput: EditText

    private lateinit var detailsRideName: TextView
    private lateinit var detailsGroupSize: TextView
    private lateinit var detailsStartDate: TextView
    private lateinit var detailsStartTime: TextView
    private lateinit var detailsStartPoint: TextView
    private lateinit var detailsDestination: TextView
    private lateinit var leaveGroupButton: Button
    private lateinit var startDate: EditText
    private lateinit var startTime: EditText

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private var generatedRideId: String? = null
    private var generatedJoinLink: String? = null
    private var selectedStartPoint: LatLng? = null // Changed to LatLng
    private var selectedDestination: LatLng? = null // Changed to LatLng
    private var startPointName: String? = null // Store name for display
    private var destinationName: String? = null // Store name for display

    @SuppressLint("CutPasteId", "DefaultLocale")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize Places API
        if (!Places.isInitialized()) {
            Places.initialize(requireContext(), "AIzaSyA-V4L9_na1mUx_hb0cpvqMes4xTWF1fLk") // Replace with your API key
        }

        membersList = view.findViewById(R.id.membersList)
        createRideLayout = view.findViewById(R.id.createRideLayout)
        joinRideLayout = view.findViewById(R.id.joinRideLayout)
        groupDetailsLayout = view.findViewById(R.id.groupDetailsLayout)

        btnCreateRide = view.findViewById(R.id.btnCreateRide)
        btnJoinRide = view.findViewById(R.id.btnJoinRide)
        btnRideDetails = view.findViewById(R.id.btnRideDetails)

        lineCreate = view.findViewById(R.id.lineCreate)
        lineJoin = view.findViewById(R.id.lineJoin)
        lineDetails = view.findViewById(R.id.lineDetails)

        createSubmitButton = view.findViewById(R.id.createSubmitButton)
        joinSubmitButton = view.findViewById(R.id.joinSubmitButton)
        rideIdView = view.findViewById(R.id.rideId)
        joinLinkView = view.findViewById(R.id.joinLink)
        joinRideIdInput = view.findViewById(R.id.joinRideIdInput)

        detailsRideName = view.findViewById(R.id.detailsRideName)
        detailsGroupSize = view.findViewById(R.id.detailsGroupSize)
        detailsStartDate = view.findViewById(R.id.detailsStartDate)
        detailsStartTime = view.findViewById(R.id.detailsStartTime)
        detailsStartPoint = view.findViewById(R.id.detailsStartPoint)
        detailsDestination = view.findViewById(R.id.detailsDestination)
        leaveGroupButton = view.findViewById(R.id.leaveGroupButton)
        startDate = view.findViewById(R.id.startDate)
        startTime = view.findViewById(R.id.startTime)

        val copyButton = view.findViewById<ImageButton>(R.id.copyButton)
        val joinLink = view.findViewById<EditText>(R.id.joinLink)
        val rideIdInput = view.findViewById<EditText>(R.id.joinRideIdInput)
        val rideLinkInput = view.findViewById<EditText>(R.id.joinRideLinkInput)
        val joinButton = view.findViewById<Button>(R.id.joinSubmitButton)

        // Set up Autocomplete for Start Point
        val startPointAutocomplete = childFragmentManager.findFragmentById(R.id.startPointAutocomplete) as AutocompleteSupportFragment
        startPointAutocomplete.setPlaceFields(listOf(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG))
        startPointAutocomplete.setHint("Enter starting location")
        startPointAutocomplete.setOnPlaceSelectedListener(object : PlaceSelectionListener {
            override fun onPlaceSelected(place: Place) {
                selectedStartPoint = place.latLng // Store LatLng
                startPointName = place.name // Store name for display
            }

            override fun onError(status: com.google.android.gms.common.api.Status) {
                Toast.makeText(requireContext(), "Error: ${status.statusMessage}", Toast.LENGTH_SHORT).show()
            }
        })

        // Set up Autocomplete for Destination
        val destinationAutocomplete = childFragmentManager.findFragmentById(R.id.destinationAutocomplete) as AutocompleteSupportFragment
        destinationAutocomplete.setPlaceFields(listOf(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG))
        destinationAutocomplete.setHint("Enter destination")
        destinationAutocomplete.setOnPlaceSelectedListener(object : PlaceSelectionListener {
            override fun onPlaceSelected(place: Place) {
                selectedDestination = place.latLng // Store LatLng
                destinationName = place.name // Store name for display
            }

            override fun onError(status: com.google.android.gms.common.api.Status) {
                Toast.makeText(requireContext(), "Error: ${status.statusMessage}", Toast.LENGTH_SHORT).show()
            }
        })

        copyButton.setOnClickListener {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Join Link", joinLink.text.toString())
            clipboard.setPrimaryClip(clip)
            Toast.makeText(requireContext(), "Link copied to clipboard", Toast.LENGTH_SHORT).show()
        }

        startDate.setOnClickListener {
            val calendar = Calendar.getInstance()
            val year = calendar.get(Calendar.YEAR)
            val month = calendar.get(Calendar.MONTH)
            val day = calendar.get(Calendar.DAY_OF_MONTH)

            val datePicker = DatePickerDialog(requireContext(), { _, selectedYear, selectedMonth, selectedDay ->
                val date = "${(selectedMonth + 1).toString().padStart(2, '0')}/" +
                        "${selectedDay.toString().padStart(2, '0')}/" +
                        "$selectedYear"
                startDate.setText(date)
            }, year, month, day)
            datePicker.show()
        }

        startTime.setOnClickListener {
            val calendar = Calendar.getInstance()
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            val minute = calendar.get(Calendar.MINUTE)

            val timePicker = TimePickerDialog(requireContext(), { _, selectedHour, selectedMinute ->
                val amPm = if (selectedHour >= 12) "PM" else "AM"
                val hour12 = if (selectedHour % 12 == 0) 12 else selectedHour % 12
                val time = String.format("%02d:%02d %s", hour12, selectedMinute, amPm)
                startTime.setText(time)
            }, hour, minute, false)
            timePicker.show()
        }

        btnCreateRide.setOnClickListener { showLayout(createRideLayout) }
        btnJoinRide.setOnClickListener { showLayout(joinRideLayout) }
        btnRideDetails.setOnClickListener {
            val currentUser = auth.currentUser
            val email = currentUser?.email
            if (!email.isNullOrEmpty()) {
                db.collection("user_rides").document(email).get().addOnSuccessListener { doc ->
                    val rideId = doc.getString("rideId")
                    if (!rideId.isNullOrEmpty()) {
                        loadGroupDetails(rideId)
                    } else {
                        Toast.makeText(requireContext(), "You're not part of any ride.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        createSubmitButton.setOnClickListener {
            val currentUser = auth.currentUser
            if (currentUser == null) {
                Log.e("CreateFragment", "No authenticated user")
                Toast.makeText(requireContext(), "Please sign in", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val email = currentUser.email?.trim()?.lowercase()
            if (email.isNullOrBlank()) {
                Log.e("CreateFragment", "Invalid user email")
                Toast.makeText(requireContext(), "Invalid email", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            Log.d("CreateFragment", "createSubmitButton clicked: rideId=$generatedRideId, joinLink=$generatedJoinLink")

            db.collection("user_rides").document(email).get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        Log.w("CreateFragment", "User already has a ride: $email")
                        Toast.makeText(requireContext(), "You already have a ride.", Toast.LENGTH_SHORT).show()
                    } else {
                        if (generatedRideId == null || generatedJoinLink == null) {
                            Log.e("CreateFragment", "Ride ID or join link not generated")
                            Toast.makeText(requireContext(), "Ride creation not ready, try again", Toast.LENGTH_SHORT).show()
                            // Regenerate ride ID
                            generateUniqueRideId { rideId ->
                                val joinLink = "trip://join/$rideId"
                                generatedRideId = rideId
                                generatedJoinLink = joinLink
                                rideIdView.text = rideId
                                joinLinkView.text = joinLink
                                handleCreateRide(view, rideId, joinLink, email)
                            }
                        } else {
                            handleCreateRide(view, generatedRideId!!, generatedJoinLink!!, email)
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("CreateFragment", "Failed to check user_rides: ${e.message}", e)
                    Toast.makeText(requireContext(), "Error checking ride status", Toast.LENGTH_SHORT).show()
                }
        }

        joinButton.setOnClickListener {
            val currentUser = auth.currentUser ?: return@setOnClickListener
            val email = currentUser.email?.trim()?.lowercase() ?: return@setOnClickListener
            val rideIdText = rideIdInput.text.toString().trim()
            val rideLinkText = rideLinkInput.text.toString().trim()

            var finalRideId: String? = null

            if (rideIdText.isNotEmpty()) {
                finalRideId = rideIdText
            } else if (rideLinkText.isNotEmpty()) {
                val regex = Regex(".*/join/(\\w+)")
                val match = regex.find(rideLinkText)
                finalRideId = match?.groupValues?.get(1)
            }

            if (!finalRideId.isNullOrEmpty()) {
                db.collection("user_rides").document(email).get()
                    .addOnSuccessListener { doc ->
                        if (doc.exists()) {
                            Toast.makeText(requireContext(), "You're already in a ride.", Toast.LENGTH_SHORT).show()
                        } else {
                            db.collection("user_rides").document(email)
                                .set(mapOf("rideId" to finalRideId))
                                .addOnSuccessListener {
                                    val uid = currentUser.uid
                                    val username = currentUser.displayName ?: "Anonymous"
                                    db.collection("rides").document(finalRideId)
                                        .collection("members").document(uid)
                                        .set(mapOf(
                                            "username" to username,
                                            "email" to email // Add email
                                        ))
                                        .addOnSuccessListener {
                                            Toast.makeText(requireContext(), "Joined ride!", Toast.LENGTH_SHORT).show()
                                            loadGroupDetails(finalRideId)
                                        }
                                        .addOnFailureListener { e ->
                                            Log.e("CreateFragment", "Failed to join ride: ${e.message}")
                                            Toast.makeText(requireContext(), "Failed to join ride", Toast.LENGTH_SHORT).show()
                                        }
                                }
                                .addOnFailureListener { e ->
                                    Log.e("CreateFragment", "Failed to set user_rides: ${e.message}")
                                    Toast.makeText(requireContext(), "Error joining ride", Toast.LENGTH_SHORT).show()
                                }
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e("CreateFragment", "Failed to check user_rides: ${e.message}")
                        Toast.makeText(requireContext(), "Error checking ride status", Toast.LENGTH_SHORT).show()
                    }
            } else {
                Toast.makeText(requireContext(), "Please enter a Ride ID or valid Joining Link", Toast.LENGTH_SHORT).show()
            }
        }

        leaveGroupButton.setOnClickListener {
            val currentUser = auth.currentUser ?: return@setOnClickListener
            val email = currentUser.email?.trim()?.lowercase() ?: return@setOnClickListener

            db.collection("user_rides").document(email).get().addOnSuccessListener { doc ->
                val rideId = doc.getString("rideId")
                if (rideId != null) {
                    db.collection("user_rides").document(email).delete()
                    db.collection("rides").document(rideId)
                        .collection("members").document(currentUser.uid).delete()
                    Toast.makeText(requireContext(), "Left the ride", Toast.LENGTH_SHORT).show()
                    showLayout(createRideLayout)
                }
            }
        }

        generateUniqueRideId { rideId ->
            val joinLink = "trip://join/$rideId"
            generatedRideId = rideId
            generatedJoinLink = joinLink
            rideIdView.text = rideId
            joinLinkView.text = joinLink
        }

        checkUserRideStatus()
    }

    private fun showLayout(layoutToShow: View) {
        createRideLayout.visibility = View.GONE
        joinRideLayout.visibility = View.GONE
        groupDetailsLayout.visibility = View.GONE

        lineCreate.visibility = if (layoutToShow == createRideLayout) View.VISIBLE else View.GONE
        lineJoin.visibility = if (layoutToShow == joinRideLayout) View.VISIBLE else View.GONE
        lineDetails.visibility = if (layoutToShow == groupDetailsLayout) View.VISIBLE else View.GONE

        layoutToShow.visibility = View.VISIBLE
    }

    private fun generateUniqueRideId(onComplete: (String) -> Unit) {
        val rideId = UUID.randomUUID().toString().substring(0, 8)
        db.collection("rides").document(rideId).get()
            .addOnSuccessListener {
                if (it.exists()) {
                    generateUniqueRideId(onComplete)
                } else {
                    onComplete(rideId)
                }
            }
    }

    private fun handleCreateRide(view: View, rideId: String, joiningLink: String, email: String) {
        val rideName = view.findViewById<EditText>(R.id.rideName).text.toString().trim()
        val groupSize = view.findViewById<EditText>(R.id.groupSize).text.toString().trim()
        val startDate = view.findViewById<EditText>(R.id.startDate).text.toString().trim()
        val startTime = view.findViewById<EditText>(R.id.startTime).text.toString().trim()

        Log.d("CreateFragment", "handleCreateRide: rideName=$rideName, groupSize=$groupSize, startDate=$startDate, startTime=$startTime, startPoint=$selectedStartPoint, destination=$selectedDestination, startPointName=$startPointName, destinationName=$destinationName")

        if (rideName.isBlank() || groupSize.isBlank() || startDate.isBlank() || startTime.isBlank() ||
            selectedStartPoint == null || selectedDestination == null || startPointName.isNullOrBlank() || destinationName.isNullOrBlank()) {
            Log.w("CreateFragment", "Validation failed: Missing required fields")
            Toast.makeText(requireContext(), "Please fill all fields, including start and destination", Toast.LENGTH_LONG).show()
            return
        }

        val groupSizeInt = try {
            groupSize.toInt()
        } catch (e: NumberFormatException) {
            Log.w("CreateFragment", "Invalid group size: $groupSize")
            Toast.makeText(requireContext(), "Group size must be a number", Toast.LENGTH_SHORT).show()
            return
        }

        if (groupSizeInt <= 0) {
            Log.w("CreateFragment", "Invalid group size: $groupSizeInt")
            Toast.makeText(requireContext(), "Group size must be at least 1", Toast.LENGTH_SHORT).show()
            return
        }

        val ride = hashMapOf(
            "id" to rideId,
            "email" to email,
            "name" to rideName,
            "groupSize" to groupSizeInt,
            "startDate" to startDate,
            "startTime" to startTime,
            "startPoint" to mapOf(
                "latitude" to selectedStartPoint!!.latitude,
                "longitude" to selectedStartPoint!!.longitude
            ),
            "destination" to mapOf(
                "latitude" to selectedDestination!!.latitude,
                "longitude" to selectedDestination!!.longitude
            ),
            "startName" to startPointName,
            "destinationName" to destinationName,
            "joiningLink" to joiningLink,
            "creatorUid" to auth.currentUser?.uid,
            "leaderId" to auth.currentUser?.uid, // Add leaderId
            "status" to "created"
        )

        val uid = auth.currentUser?.uid ?: run {
            Log.e("CreateFragment", "No user UID")
            Toast.makeText(requireContext(), "Authentication error", Toast.LENGTH_SHORT).show()
            return
        }
        val username = auth.currentUser?.displayName ?: "Anonymous"

        db.collection("rides").document(rideId).set(ride)
            .addOnSuccessListener {
                Log.d("CreateFragment", "Ride created: rides/$rideId")
                db.collection("user_rides").document(email).set(mapOf("rideId" to rideId))
                    .addOnSuccessListener {
                        Log.d("CreateFragment", "User ride set: user_rides/$email")
                        db.collection("rides").document(rideId)
                            .collection("members").document(uid)
                            .set(mapOf(
                                "username" to username,
                                "email" to email
                            ))
                            .addOnSuccessListener {
                                Log.d("CreateFragment", "Creator added to members: rides/$rideId/members/$uid")
                                Toast.makeText(requireContext(), "Ride Created!", Toast.LENGTH_SHORT).show()
                                loadGroupDetails(rideId)
                            }
                            .addOnFailureListener { e ->
                                Log.e("CreateFragment", "Failed to add creator to members: ${e.message}", e)
                                Toast.makeText(requireContext(), "Failed to add creator: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                    }
                    .addOnFailureListener { e ->
                        Log.e("CreateFragment", "Failed to set user_rides: ${e.message}", e)
                        Toast.makeText(requireContext(), "Failed to set user ride: ${e.message}", Toast.LENGTH_LONG).show()
                    }
            }
            .addOnFailureListener { e ->
                Log.e("CreateFragment", "Failed to create ride: ${e.message}", e)
                Toast.makeText(requireContext(), "Failed to create ride: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    @SuppressLint("SetTextI18n")
    private fun loadGroupDetails(rideId: String) {
        db.collection("rides").document(rideId).get().addOnSuccessListener { doc ->
            if (doc.exists()) {
                val creatorEmail = doc.getString("email") ?: ""
                detailsRideName.text = "Ride Name: ${doc.getString("name")}"
                detailsGroupSize.text = "Group Size: ${doc.getLong("groupSize")}"
                detailsStartDate.text = "Start Date: ${doc.getString("startDate")}"
                detailsStartTime.text = "Start Time: ${doc.getString("startTime")}"
                val startPointMap = doc.get("startPoint") as? Map<String, Double>
                val destinationMap = doc.get("destination") as? Map<String, Double>

                detailsStartPoint.text = "Start Point: ${doc.getString("startPointName") ?: "Lat: ${startPointMap?.get("latitude")}, Lon: ${startPointMap?.get("longitude")}"}"
                detailsDestination.text = "Destination: ${doc.getString("destinationName") ?: "Lat: ${destinationMap?.get("latitude")}, Lon: ${destinationMap?.get("longitude")}"}"

                showLayout(groupDetailsLayout)

                membersList.removeAllViews()
                db.collection("rides").document(rideId)
                    .collection("members")
                    .get()
                    .addOnSuccessListener { membersSnapshot ->
                        val members = mutableListOf<Triple<String, String, Boolean>>()
                        for (member in membersSnapshot) {
                            val uid = member.id
                            val username = member.getString("username") ?: continue
                            val isAdmin = auth.currentUser?.email == creatorEmail && auth.currentUser?.uid == uid
                            members.add(Triple(uid, username, isAdmin))
                        }
                        val sortedMembers = members.sortedByDescending { it.third }
                        for ((_, username, isAdmin) in sortedMembers) {
                            val memberView = TextView(requireContext())
                            memberView.text = if (isAdmin) "👑 $username (Admin)" else "• $username"
                            memberView.setPadding(8, 4, 8, 4)
                            membersList.addView(memberView)
                        }
                    }
            }
        }
    }

    private fun checkUserRideStatus() {
        val currentUser = auth.currentUser ?: return
        val email = currentUser.email?.trim()?.lowercase() ?: return
        db.collection("user_rides").document(email).get().addOnSuccessListener { doc ->
            val rideId = doc.getString("rideId")
            if (!rideId.isNullOrEmpty()) {
                loadGroupDetails(rideId)
            } else {
                showLayout(createRideLayout)
            }
        }
    }
}