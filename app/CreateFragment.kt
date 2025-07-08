package com.example.bottomnav

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.fragment.app.Fragment
import com.example.triptogether.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*

class CreateFragment : Fragment(R.layout.fragment_create) {

    private lateinit var createRideLayout: View
    private lateinit var joinRideLayout: View
    private lateinit var groupDetailsLayout: View

    private lateinit var btnCreateRide: Button
    private lateinit var btnJoinRide: Button
    private lateinit var lineCreate: View
    private lateinit var lineJoin: View
    private lateinit var createSubmitButton: Button
    private lateinit var joinSubmitButton: Button
    private lateinit var rideIdView: TextView
    private lateinit var joinLinkView: TextView
    private lateinit var joinRideIdInput: EditText

    private lateinit var detailsRideId: TextView
    private lateinit var detailsRideName: TextView
    private lateinit var detailsGroupSize: TextView
    private lateinit var detailsJoinLink: TextView
    private lateinit var detailsStartDate: TextView
    private lateinit var detailsStartTime: TextView
    private lateinit var detailsStartPoint: TextView
    private lateinit var detailsDestination: TextView
    private lateinit var leaveGroupButton: Button

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    companion object {
        const val TAG_RIDE_ID = R.id.rideId
        const val TAG_JOIN_LINK = R.id.joinLink
    }

    @SuppressLint("CutPasteId")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews(view)
        setupTopButtons()
        setupCreateRide()
        setupJoinRide()
        setupLeaveGroup()

        auth.currentUser?.email?.let { email ->
            db.collection("user_rides").document(email).get()
                .addOnSuccessListener { doc ->
                    doc.getString("rideId")?.let { loadGroupDetails(it) }
                        ?: showLayout(createRideLayout)
                }
        }

        generateUniqueRideId { rideId ->
            val joinLink = "trip://join/$rideId"
            rideIdView.text = rideId
            joinLinkView.text = joinLink
            createSubmitButton.setTag(TAG_RIDE_ID, rideId)
            createSubmitButton.setTag(TAG_JOIN_LINK, joinLink)
        }
    }

    private fun initViews(view: View) {
        createRideLayout = view.findViewById(R.id.createRideLayout)
        joinRideLayout = view.findViewById(R.id.joinRideLayout)
        groupDetailsLayout = view.findViewById(R.id.groupDetailsLayout)

        btnCreateRide = view.findViewById(R.id.btnCreateRide)
        btnJoinRide = view.findViewById(R.id.btnJoinRide)
        lineCreate = view.findViewById(R.id.lineCreate)
        lineJoin = view.findViewById(R.id.lineJoin)

        createSubmitButton = view.findViewById(R.id.createSubmitButton)
        joinSubmitButton = view.findViewById(R.id.joinSubmitButton)
        rideIdView = view.findViewById(R.id.rideId)
        joinLinkView = view.findViewById(R.id.joinLink)
        joinRideIdInput = view.findViewById(R.id.joinRideIdInput)

        detailsRideId = view.findViewById(R.id.detailsRideId)
        detailsRideName = view.findViewById(R.id.detailsRideName)
        detailsGroupSize = view.findViewById(R.id.detailsGroupSize)
        detailsJoinLink = view.findViewById(R.id.detailsJoinLink)
        detailsStartDate = view.findViewById(R.id.detailsStartDate)
        detailsStartTime = view.findViewById(R.id.detailsStartTime)
        detailsStartPoint = view.findViewById(R.id.detailsStartPoint)
        detailsDestination = view.findViewById(R.id.detailsDestination)
        leaveGroupButton = view.findViewById(R.id.leaveGroupButton)
    }

    private fun setupTopButtons() {
        btnCreateRide.setOnClickListener { showLayout(createRideLayout) }
        btnJoinRide.setOnClickListener { showLayout(joinRideLayout) }
    }

    private fun setupCreateRide() {
        createSubmitButton.setOnClickListener {
            val currentUser = auth.currentUser ?: return@setOnClickListener
            val email = currentUser.email ?: return@setOnClickListener

            db.collection("user_rides").document(email).get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        showToast("You already have a ride.")
                    } else {
                        val rideId = createSubmitButton.getTag(TAG_RIDE_ID) as? String
                        val joinLink = createSubmitButton.getTag(TAG_JOIN_LINK) as? String

                        if (rideId != null && joinLink != null) {
                            handleCreateRide(view, rideId, joinLink, email)
                        }
                    }
                }
        }
    }

    private fun setupJoinRide() {
        joinSubmitButton.setOnClickListener {
            val currentUser = auth.currentUser ?: return@setOnClickListener
            val email = currentUser.email ?: return@setOnClickListener
            val inputRideId = joinRideIdInput.text.toString().trim()

            if (inputRideId.isBlank()) {
                showToast("Enter a Ride ID")
                return@setOnClickListener
            }

            db.collection("user_rides").document(email).get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        showToast("You're already in a ride.")
                    } else {
                        db.collection("user_rides").document(email)
                            .set(mapOf("rideId" to inputRideId))
                            .addOnSuccessListener {
                                val uid = currentUser.uid
                                val username = currentUser.displayName ?: "Anonymous"

                                db.collection("rides").document(inputRideId)
                                    .collection("members").document(uid)
                                    .set(mapOf("username" to username))
                                    .addOnSuccessListener {
                                        showToast("Joined ride!")
                                        loadGroupDetails(inputRideId)
                                    }
                            }
                    }
                }
        }
    }

    private fun setupLeaveGroup() {
        leaveGroupButton.setOnClickListener {
            val currentUser = auth.currentUser ?: return@setOnClickListener
            val email = currentUser.email ?: return@setOnClickListener

            db.collection("user_rides").document(email).get().addOnSuccessListener { doc ->
                val rideId = doc.getString("rideId")
                if (rideId != null) {
                    db.collection("user_rides").document(email).delete()
                    db.collection("rides").document(rideId)
                        .collection("members").document(currentUser.uid).delete()
                    showToast("Left the ride")
                    showLayout(createRideLayout)
                }
            }
        }
    }

    private fun showLayout(layoutToShow: View) {
        createRideLayout.visibility = View.GONE
        joinRideLayout.visibility = View.GONE
        groupDetailsLayout.visibility = View.GONE

        lineCreate.visibility = if (layoutToShow == createRideLayout) View.VISIBLE else View.GONE
        lineJoin.visibility = if (layoutToShow == joinRideLayout) View.VISIBLE else View.GONE

        layoutToShow.visibility = View.VISIBLE
    }

    private fun generateUniqueRideId(onComplete: (String) -> Unit) {
        val rideId = UUID.randomUUID().toString().substring(0, 8)
        db.collection("rides").document(rideId).get()
            .addOnSuccessListener {
                if (it.exists()) generateUniqueRideId(onComplete)
                else onComplete(rideId)
            }
    }

    private fun handleCreateRide(view: View, rideId: String, joiningLink: String, email: String) {
        val rideName = view.findViewById<EditText>(R.id.rideName).text.toString()
        val groupSize = view.findViewById<EditText>(R.id.groupSize).text.toString()
        val startDate = view.findViewById<EditText>(R.id.startDate).text.toString()
        val startTime = view.findViewById<EditText>(R.id.startTime).text.toString()
        val startPoint = view.findViewById<EditText>(R.id.startPoint).text.toString()
        val destination = view.findViewById<EditText>(R.id.destination).text.toString()

        if (rideName.isBlank() || groupSize.isBlank() || startDate.isBlank() ||
            startTime.isBlank() || startPoint.isBlank() || destination.isBlank()
        ) {
            showToast("Please fill all fields")
            return
        }

        val ride = mapOf(
            "id" to rideId,
            "email" to email,
            "name" to rideName,
            "groupSize" to groupSize.toInt(),
            "startDate" to startDate,
            "startTime" to startTime,
            "startPoint" to startPoint,
            "destination" to destination,
            "joiningLink" to joiningLink
        )

        val uid = auth.currentUser?.uid ?: return
        val username = auth.currentUser?.displayName ?: "Anonymous"

        db.collection("rides").document(rideId).set(ride)
            .addOnSuccessListener {
                db.collection("user_rides").document(email).set(mapOf("rideId" to rideId))
                    .addOnSuccessListener {
                        db.collection("rides").document(rideId)
                            .collection("members").document(uid)
                            .set(mapOf("username" to username))
                            .addOnSuccessListener {
                                showToast("Ride Created!")
                                loadGroupDetails(rideId)
                            }
                    }
            }
    }

    @SuppressLint("SetTextI18n")
    private fun loadGroupDetails(rideId: String) {
        db.collection("rides").document(rideId).get()
            .addOnSuccessListener { doc ->
                doc?.let {
                    detailsRideId.text = "Ride ID: ${it.getString("id")}"
                    detailsRideName.text = "Ride Name: ${it.getString("name")}"
                    detailsGroupSize.text = "Group Size: ${it.getLong("groupSize")?.toInt() ?: 0}"
                    detailsJoinLink.text = "Join Link: ${it.getString("joiningLink")}"
                    detailsStartDate.text = "Start Date: ${it.getString("startDate")}"
                    detailsStartTime.text = "Start Time: ${it.getString("startTime")}"
                    detailsStartPoint.text = "Start Point: ${it.getString("startPoint")}"
                    detailsDestination.text = "Destination: ${it.getString("destination")}"

                    showLayout(groupDetailsLayout)
                }
            }
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }
}
