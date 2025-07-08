package com.example.triptogether

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import kotlin.math.*

data class ChatMsg(
    val text: String = "",
    val senderId: String = "",
    val timestamp: Long = 0L
)

data class SOSAlert(
    val senderId: String = "",
    val timestamp: Long = 0L
)

data class StopRequest(
    val senderId: String = "",
    val timestamp: Long = 0L,
    val adminInitiated: Boolean = false // Changed from isAdminInitiated to adminInitiated
)

data class RiderLocation(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val timestamp: Long = 0L
)

data class Request(
    val text: String = "",
    val senderId: String = "",
    val timestamp: Long = 0L,
    val type: String = ""
)

class RideManager(private val rideId: String) {
    private val db = FirebaseDatabase.getInstance().reference
    private val chatRef = db.child("rides").child(rideId).child("liveChat")
    private val sosRef = db.child("rides").child(rideId).child("sosAlerts")
    private val convoyRef = db.child("rides").child(rideId).child("convoyMode")
    val fuelRef = db.child("rides").child(rideId).child("fuelStops")
    val restRef = db.child("rides").child(rideId).child("restStops")
    private val requestRef = db.child("rides").child(rideId).child("requests")
    private val ridersRef = db.child("rides").child(rideId).child("riders")
    private val rideRef = db.child("rides").child(rideId)
    private val statusRef = db.child("rides").child(rideId).child("rideStatus")

    private var sosListener: ChildEventListener? = null
    private var chatListener: ChildEventListener? = null
    private var convoyListener: ValueEventListener? = null
    private var fuelListener: ChildEventListener? = null
    private var restListener: ChildEventListener? = null
    private var requestListener: ChildEventListener? = null
    private var ridersListener: ValueEventListener? = null
    private var statusListener: ValueEventListener? = null

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    fun sendMessage(text: String, senderId: String) {
        val messageId = chatRef.push().key ?: return
        val message = ChatMsg(text, senderId, System.currentTimeMillis())
        chatRef.child(messageId).setValue(message)
            .addOnSuccessListener {
                Log.d("RideManager", "Message sent: $text")
            }
            .addOnFailureListener { e ->
                Log.e("RideManager", "Failed to send message: ${e.message}")
            }
    }

    fun listenForMessages(onMessageReceived: (ChatMsg) -> Unit) {
        chatListener?.let { chatRef.removeEventListener(it) }
        chatListener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val message = snapshot.getValue(ChatMsg::class.java)
                message?.let { onMessageReceived(it) }
            }
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {
                Log.e("RideManager", "Messages listener cancelled: ${error.message}")
            }
        }
        chatRef.addChildEventListener(chatListener!!)
    }

    fun sendSOS(senderId: String) {
        val sosId = sosRef.push().key ?: return
        val alert = SOSAlert(senderId, System.currentTimeMillis())
        sosRef.child(sosId).setValue(alert)
            .addOnSuccessListener {
                Log.d("RideManager", "SOS sent by $senderId")
            }
            .addOnFailureListener { e ->
                Log.e("RideManager", "Failed to send SOS: ${e.message}")
            }
        // Clean up SOS alert after 10 seconds
        db.database.getReference(".info/serverTimeOffset").get().addOnSuccessListener { snapshot ->
            val offset = snapshot.getValue(Long::class.java) ?: 0L
            val serverTime = System.currentTimeMillis() + offset
            sosRef.child(sosId).child("expiresAt").setValue(serverTime + 10000)
        }
    }

    fun listenForSOS(onSOSReceived: (SOSAlert) -> Unit) {
        sosListener?.let { sosRef.removeEventListener(it) }
        sosListener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val alert = snapshot.getValue(SOSAlert::class.java)
                alert?.let { onSOSReceived(it) }
            }
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {
                Log.e("RideManager", "SOS listener cancelled: ${error.message}")
            }
        }
        sosRef.addChildEventListener(sosListener!!)
    }

    fun toggleConvoyMode(enabled: Boolean) {
        convoyRef.setValue(enabled)
            .addOnSuccessListener {
                Log.d("RideManager", "Convoy mode set to $enabled")
            }
            .addOnFailureListener { e ->
                Log.e("RideManager", "Failed to toggle convoy mode: ${e.message}")
            }
    }

    fun listenForConvoyMode(onConvoyModeChanged: (Boolean) -> Unit) {
        convoyListener?.let { convoyRef.removeEventListener(it) }
        convoyListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val enabled = snapshot.getValue(Boolean::class.java) ?: false
                onConvoyModeChanged(enabled)
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("RideManager", "Convoy mode listener cancelled: ${error.message}")
            }
        }
        convoyRef.addValueEventListener(convoyListener!!)
    }

    fun requestFuelStop(senderId: String, isAdminInitiated: Boolean = false) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            Log.e("RideManager", "User not authenticated")
            return
        }
        val stopId = fuelRef.push().key ?: return
        val stop = StopRequest(senderId, System.currentTimeMillis(), isAdminInitiated)
        Log.d("RideManager", "Writing fuel stop: $stop")
        fuelRef.child(stopId).setValue(stop)
            .addOnSuccessListener {
                Log.d("RideManager", "Fuel stop requested: sender=$senderId, admin=$isAdminInitiated, key=$stopId")
            }
            .addOnFailureListener { e ->
                Log.e("RideManager", "Failed to request fuel stop: ${e.message}")
            }
        // Clean up fuel stop after 10 seconds
        db.database.getReference(".info/serverTimeOffset").get().addOnSuccessListener { snapshot ->
            val offset = snapshot.getValue(Long::class.java) ?: 0L
            val serverTime = System.currentTimeMillis() + offset
            fuelRef.child(stopId).child("expiresAt").setValue(serverTime + 10000)
        }
    }

    fun listenForFuelStops(onFuelStopReceived: (StopRequest, DataSnapshot) -> Unit) {
        fuelListener?.let { fuelRef.removeEventListener(it) }
        fuelListener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val stop = snapshot.getValue(StopRequest::class.java)
                if (stop != null) {
                    Log.d("RideManager", "Fuel stop detected (raw data): ${snapshot.value}")
                    Log.d("RideManager", "Fuel stop detected: key=${snapshot.key}, sender=${stop.senderId}, timestamp=${stop.timestamp}, admin=${stop.adminInitiated}")
                    onFuelStopReceived(stop, snapshot)
                    // Schedule cleanup after 10 seconds
                    snapshot.child("expiresAt").getValue(Long::class.java)?.let { expiresAt ->
                        val delay = expiresAt - System.currentTimeMillis()
                        if (delay > 0) {
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                snapshot.ref.removeValue()
                                Log.d("RideManager", "Fuel stop cleaned up: key=${snapshot.key}")
                            }, delay)
                        } else {
                            snapshot.ref.removeValue()
                            Log.d("RideManager", "Fuel stop expired, cleaned up: key=${snapshot.key}")
                        }
                    }
                } else {
                    Log.w("RideManager", "Invalid fuel stop data: key=${snapshot.key}, raw=${snapshot.value}")
                }
            }
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {
                Log.e("RideManager", "Fuel stops listener cancelled: ${error.message}")
            }
        }
        fuelRef.addChildEventListener(fuelListener!!)
    }

    fun requestRestStop(senderId: String, isAdminInitiated: Boolean = false) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            Log.e("RideManager", "User not authenticated")
            return
        }
        val stopId = restRef.push().key ?: return
        val stop = StopRequest(senderId, System.currentTimeMillis(), isAdminInitiated)
        Log.d("RideManager", "Writing rest stop: $stop")
        restRef.child(stopId).setValue(stop)
            .addOnSuccessListener {
                Log.d("RideManager", "Rest stop requested: sender=$senderId, admin=$isAdminInitiated, key=$stopId")
            }
            .addOnFailureListener { e ->
                Log.e("RideManager", "Failed to request rest stop: ${e.message}")
            }
        // Clean up rest stop after 10 seconds
        db.database.getReference(".info/serverTimeOffset").get().addOnSuccessListener { snapshot ->
            val offset = snapshot.getValue(Long::class.java) ?: 0L
            val serverTime = System.currentTimeMillis() + offset
            restRef.child(stopId).child("expiresAt").setValue(serverTime + 10000)
        }
    }

    fun listenForRestStops(onRestStopReceived: (StopRequest, DataSnapshot) -> Unit) {
        restListener?.let { restRef.removeEventListener(it) }
        restListener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val stop = snapshot.getValue(StopRequest::class.java)
                if (stop != null) {
                    Log.d("RideManager", "Rest stop detected (raw data): ${snapshot.value}")
                    Log.d("RideManager", "Rest stop detected: key=${snapshot.key}, sender=${stop.senderId}, timestamp=${stop.timestamp}, admin=${stop.adminInitiated}")
                    onRestStopReceived(stop, snapshot)
                    // Schedule cleanup after 10 seconds
                    snapshot.child("expiresAt").getValue(Long::class.java)?.let { expiresAt ->
                        val delay = expiresAt - System.currentTimeMillis()
                        if (delay > 0) {
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                snapshot.ref.removeValue()
                                Log.d("RideManager", "Rest stop cleaned up: key=${snapshot.key}")
                            }, delay)
                        } else {
                            snapshot.ref.removeValue()
                            Log.d("RideManager", "Rest stop expired, cleaned up: key=${snapshot.key}")
                        }
                    }
                } else {
                    Log.w("RideManager", "Invalid rest stop data: key=${snapshot.key}, raw=${snapshot.value}")
                }
            }
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {
                Log.e("RideManager", "Rest stops listener cancelled: ${error.message}")
            }
        }
        restRef.addChildEventListener(restListener!!)
    }

    fun sendRequestToLeader(text: String, senderId: String, leaderId: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            Log.e("RideManager", "User not authenticated")
            return
        }
        val requestId = requestRef.push().key ?: return
        val request = Request(text, senderId, System.currentTimeMillis(), "leader_request")
        requestRef.child(requestId).setValue(request)
            .addOnSuccessListener {
                Log.d("RideManager", "Request sent to leader: $text")
            }
            .addOnFailureListener { e ->
                Log.e("RideManager", "Failed to send request: ${e.message}")
            }
        // Clean up request after 10 seconds
        db.database.getReference(".info/serverTimeOffset").get().addOnSuccessListener { snapshot ->
            val offset = snapshot.getValue(Long::class.java) ?: 0L
            val serverTime = System.currentTimeMillis() + offset
            requestRef.child(requestId).child("expiresAt").setValue(serverTime + 10000)
        }
    }

    fun listenForLeaderRequests(onRequestReceived: (Request) -> Unit) {
        requestListener?.let { requestRef.removeEventListener(it) }
        requestListener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val request = snapshot.getValue(Request::class.java)
                request?.let { onRequestReceived(it) }
                // Schedule cleanup after 10 seconds
                snapshot.child("expiresAt").getValue(Long::class.java)?.let { expiresAt ->
                    val delay = expiresAt - System.currentTimeMillis()
                    if (delay > 0) {
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            snapshot.ref.removeValue()
                            Log.d("RideManager", "Request cleaned up: key=${snapshot.key}")
                        }, delay)
                    } else {
                        snapshot.ref.removeValue()
                        Log.d("RideManager", "Request expired, cleaned up: key=${snapshot.key}")
                    }
                }
            }
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {
                Log.e("RideManager", "Requests listener cancelled: ${error.message}")
            }
        }
        requestRef.addChildEventListener(requestListener!!)
    }

    fun updateRiderLocation(latitude: Double, longitude: Double) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val location = RiderLocation(latitude, longitude, System.currentTimeMillis())
        ridersRef.child(userId).setValue(location)
            .addOnSuccessListener {
                Log.d("RideManager", "Rider location updated: $latitude, $longitude")
            }
            .addOnFailureListener { e ->
                Log.e("RideManager", "Failed to update rider location: ${e.message}")
            }
    }

    fun listenForRiderLocations(onLocationsUpdated: (Map<String, RiderLocation>) -> Unit) {
        ridersListener?.let { ridersRef.removeEventListener(it) }
        ridersListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val locations = mutableMapOf<String, RiderLocation>()
                for (child in snapshot.children) {
                    val location = child.getValue(RiderLocation::class.java)
                    if (location != null) {
                        locations[child.key!!] = location
                    }
                }
                onLocationsUpdated(locations)
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("RideManager", "Rider locations listener cancelled: ${error.message}")
            }
        }
        ridersRef.addValueEventListener(ridersListener!!)
    }

    fun checkConvoyDistance(leaderId: String, onDistanceChecked: (String, Double, Boolean) -> Unit) {
        ridersRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val leaderLocation = snapshot.child(leaderId).getValue(RiderLocation::class.java)
                if (leaderLocation == null) {
                    Log.w("RideManager", "Leader location not found")
                    return
                }
                for (child in snapshot.children) {
                    val userId = child.key ?: continue
                    if (userId == leaderId) continue
                    val location = child.getValue(RiderLocation::class.java) ?: continue
                    val distance = calculateDistance(
                        leaderLocation.latitude, leaderLocation.longitude,
                        location.latitude, location.longitude
                    )
                    onDistanceChecked(userId, distance, false)
                }
                onDistanceChecked(leaderId, 0.0, true)
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("RideManager", "Convoy distance check cancelled: ${error.message}")
            }
        })
    }

    fun checkRideStatus(onStatusReceived: (String) -> Unit) {
        statusRef.get().addOnSuccessListener { snapshot ->
            val status = snapshot.getValue(String::class.java) ?: "unknown"
            Log.d("RideManager", "Ride status fetched: $status")
            onStatusReceived(status)
        }.addOnFailureListener { e ->
            Log.e("RideManager", "Failed to fetch ride status: ${e.message}")
            onStatusReceived("unknown")
        }
    }

    fun updateRideStatus(status: String) {
        statusRef.setValue(status)
            .addOnSuccessListener {
                Log.d("RideManager", "Ride status updated to $status")
            }
            .addOnFailureListener { e ->
                Log.e("RideManager", "Failed to update ride status: ${e.message}")
            }
    }

    fun listenForRideStatus(onStatusChanged: (String) -> Unit) {
        statusListener?.let { statusRef.removeEventListener(it) }
        statusListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val status = snapshot.getValue(String::class.java) ?: "unknown"
                onStatusChanged(status)
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("RideManager", "Ride status listener cancelled: ${error.message}")
            }
        }
        statusRef.addValueEventListener(statusListener!!)
    }

    fun cleanupOldEvents() {
        val currentTime = System.currentTimeMillis()
        listOf(sosRef, fuelRef, restRef, requestRef).forEach { ref ->
            ref.get().addOnSuccessListener { snapshot ->
                for (child in snapshot.children) {
                    val expiresAt = child.child("expiresAt").getValue(Long::class.java) ?: Long.MAX_VALUE
                    if (expiresAt < currentTime) {
                        child.ref.removeValue()
                        Log.d("RideManager", "Cleaned up expired event: ${ref.key}/${child.key}")
                    }
                }
            }
        }
    }

    fun cleanupListeners() {
        sosListener?.let { sosRef.removeEventListener(it) }
        chatListener?.let { chatRef.removeEventListener(it) }
        convoyListener?.let { convoyRef.removeEventListener(it) }
        fuelListener?.let { fuelRef.removeEventListener(it) }
        restListener?.let { restRef.removeEventListener(it) }
        requestListener?.let { requestRef.removeEventListener(it) }
        ridersListener?.let { ridersRef.removeEventListener(it) }
        statusListener?.let { statusRef.removeEventListener(it) }
        Log.d("RideManager", "All listeners removed")
    }
}