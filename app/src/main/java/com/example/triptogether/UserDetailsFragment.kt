package com.example.triptogether

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore

class UserDetailsFragment : Fragment() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    @SuppressLint("MissingInflatedId")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_user_details, container, false)

        // Hide Bottom Navigation Bar
        hideBottomNavigation()

        // Initialize UI elements
        val greetingText: TextView = view.findViewById(R.id.profileName)
        val profileImage: ImageView = view.findViewById(R.id.profileImage)
        val user = getCurrentUser()

        // Set profile name
        greetingText.text = user?.displayName?.toString() ?: "User"

        // Fetch and display profile image
        if (user != null) {
            fetchProfileImage(user, profileImage)
        } else {
            Log.e("UserDetailsFragment", "No user logged in")
            showError("Please log in to view profile")
        }

        // Back Button Functionality
        view.findViewById<ImageView>(R.id.backButton)?.setOnClickListener {
            Log.d("DEBUG", "Back Button Clicked")
            // Delegate to activity’s back handling instead of popping directly
            activity?.onBackPressed()
        }

        // Logout Button Functionality
        view.findViewById<Button>(R.id.logout)?.setOnClickListener {
            logoutUser()
            Log.d("DEBUG", "Logout Button Clicked")
        }

        // Set click listeners for buttons
        val personal = view.findViewById<LinearLayout>(R.id.personalInfoItem)
        val vehicle = view.findViewById<LinearLayout>(R.id.vehicleInfoItem)
        val help = view.findViewById<LinearLayout>(R.id.helpItem)
        val report = view.findViewById<LinearLayout>(R.id.report)
        personal.setOnClickListener {
            (activity as? home_screen)?.navigateToPersonalDetails()
        }
        vehicle.setOnClickListener {
            (activity as? home_screen)?.navigateToVehicleDetails()
        }
        help.setOnClickListener {
            (activity as? home_screen)?.navigateToHelp()
        }
        report.setOnClickListener { reportApp() }
        // In UserDetailsFragment.kt, inside onCreateView
        val uploadDocuments = view.findViewById<LinearLayout>(R.id.uploadDocumentsItem) // Note: Rename ID to uploadDocumentsItem for clarity
        uploadDocuments.setOnClickListener {
            (activity as? home_screen)?.navigateToUploadDocuments()
        }

        return view
    }

    override fun onStart() {
        super.onStart()
        // Ensure BottomNav stays hidden
        hideBottomNavigation()
    }

    private fun hideBottomNavigation() {
        val bottomNav = activity?.findViewById<BottomNavigationView>(R.id.bottom_navigation)
        if (bottomNav != null) {
            bottomNav.visibility = View.GONE
            Log.d("UserDetailsFragment", "BottomNav set to GONE")
        } else {
            Log.d("UserDetailsFragment", "BottomNav not found!")
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
                        // Keep default image (baseline_account_circle_24)
                    }
                } else {
                    Log.d("UserDetailsFragment", "No user document found for: $email")
                    // Keep default image
                }
            }
            .addOnFailureListener { e ->
                showError("Failed to fetch profile image")
                Log.e("UserDetailsFragment", "Error fetching user document: $email", e)
            }
    }

    private fun logoutUser() {
        val activity = requireActivity()
        val sharedPreferences: SharedPreferences = activity.getSharedPreferences("UserSession", 0)
        sharedPreferences.edit().clear().apply()

        try {
            FirebaseAuth.getInstance().signOut()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val intent = Intent(activity, redirect::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        activity.finish()
    }

    private fun reportApp() {
        val emailIntent = Intent(Intent.ACTION_SEND).apply {
            type = "message/rfc822"
            putExtra(Intent.EXTRA_EMAIL, arrayOf("triptogether@example.com")) // Replace with your email
            putExtra(Intent.EXTRA_SUBJECT, "Report Issue - TripTogether")
            putExtra(Intent.EXTRA_TEXT, " ")
        }

        try {
            startActivity(Intent.createChooser(emailIntent, "Send Email"))
        } catch (e: ActivityNotFoundException) {
            println(e)
        }
    }

    private fun getCurrentUser(): FirebaseUser? {
        return FirebaseAuth.getInstance().currentUser
    }

    private fun showError(message: String) {
        context?.let {
            Toast.makeText(it, message, Toast.LENGTH_SHORT).show()
        }
    }

}