package com.example.triptogether

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import java.io.ByteArrayOutputStream
import java.io.IOException

class PersonalDetails : Fragment() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private lateinit var fullname: EditText
    private lateinit var username: EditText
    private lateinit var phone: EditText
    private lateinit var address: EditText
    private lateinit var altphn: EditText
    private lateinit var bloodGroupDropdown: AutoCompleteTextView
    private lateinit var genderDropdown: AutoCompleteTextView
    private lateinit var email: EditText
    private lateinit var submit: Button
    private lateinit var profileImageView: ImageView
    private var selectedBitmap: Bitmap? = null // Store selected image temporarily

    // Activity result launcher for image picker
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK && result.data != null) {
            val imageUri: Uri? = result.data?.data
            imageUri?.let {
                try {
                    val bitmap = MediaStore.Images.Media.getBitmap(requireContext().contentResolver, it)
                    selectedBitmap = bitmap // Store bitmap, don't update UI or Firestore
                    showSuccess("Image selected. Click Update to apply changes.")
                } catch (e: IOException) {
                    showError("Failed to load image: ${e.message}")
                    Log.e("PersonalDetails", "Image load failed", e)
                }
            }
        }
    }

    // Permission launcher for storage
    private val requestStoragePermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        Log.d("PersonalDetails", "Permission result: isGranted=$isGranted")
        if (isGranted) {
            openImagePicker()
        } else {
            if (!shouldShowRequestPermissionRationale(getStoragePermission())) {
                showError("Storage permission is required. Please enable it in app settings.")
                openAppSettings()
            } else {
                showPermissionRationaleDialog()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.personal_details, container, false)

        // Initialize UI elements
        initializeViews(view)
        hideBottomNavigation()

        // Setup dropdowns
        setupDropdowns(view)

        // Make profile image clickable
        profileImageView.setOnClickListener {
            Log.d("PersonalDetails", "Profile image clicked, checking permission")
            if (checkStoragePermission()) {
                openImagePicker()
            } else {
                requestStoragePermission()
            }
        }

        // Fetch and display user details
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.e("PersonalDetails", "No user logged in")
            showError("Please log in to view details")
        } else {
            Log.d("PersonalDetails", "User logged in: ${currentUser.uid}")
            getUserDetails(currentUser) { details ->
                if (details != null) {
                    populateUserDetails(details)
                } else {
                    showError("Failed to load user details")
                    Log.e("PersonalDetails", "Details are null for user: ${currentUser.uid}")
                }
            }
        }

        // Handle submit button
        submit.setOnClickListener { saveUserDetails() }

        return view
    }

    override fun onDestroyView() {
        super.onDestroyView()
        showBottomNavigation()
    }

    private fun initializeViews(view: View) {
        fullname = view.findViewById(R.id.fullname)
        username = view.findViewById(R.id.userName)
        phone = view.findViewById(R.id.phoneNumber)
        address = view.findViewById(R.id.address)
        altphn = view.findViewById(R.id.alternatephn)
        bloodGroupDropdown = view.findViewById(R.id.blood_group_dropdown)
        genderDropdown = view.findViewById(R.id.gender_dropdown)
        email = view.findViewById(R.id.email)
        submit = view.findViewById(R.id.submitButton)
        profileImageView = view.findViewById(R.id.profileImageView)
    }

    private fun hideBottomNavigation() {
        activity?.findViewById<BottomNavigationView>(R.id.bottom_navigation)?.visibility = View.GONE
    }

    private fun showBottomNavigation() {
        activity?.findViewById<BottomNavigationView>(R.id.bottom_navigation)?.visibility = View.VISIBLE
    }

    private fun setupDropdowns(view: View) {
        val bloodGroups = arrayOf("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-")
        setupAutoCompleteDropdown(bloodGroupDropdown, bloodGroups)

        val genders = arrayOf("Male", "Female", "Other")
        setupAutoCompleteDropdown(genderDropdown, genders)
    }

    private fun setupAutoCompleteDropdown(dropdown: AutoCompleteTextView, items: Array<String>) {
        context?.let {
            val adapter = ArrayAdapter(it, android.R.layout.simple_dropdown_item_1line, items)
            dropdown.setAdapter(adapter)
            dropdown.dropDownWidth = ViewGroup.LayoutParams.WRAP_CONTENT
            dropdown.setOnClickListener { dropdown.showDropDown() }
            dropdown.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) dropdown.showDropDown() }
        } ?: Log.e("PersonalDetails", "Context is null in setupAutoCompleteDropdown")
    }

    private fun getStoragePermission(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }

    private fun checkStoragePermission(): Boolean {
        val permission = getStoragePermission()
        val granted = ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED
        Log.d("PersonalDetails", "Checking permission $permission: granted=$granted")
        return granted
    }

    private fun requestStoragePermission() {
        val permission = getStoragePermission()
        Log.d("PersonalDetails", "Requesting permission: $permission")
        if (shouldShowRequestPermissionRationale(permission)) {
            showPermissionRationaleDialog()
        } else {
            requestStoragePermissionLauncher.launch(permission)
        }
    }

    private fun showPermissionRationaleDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Permission Required")
            .setMessage("This app needs access to your photos to select a profile picture.")
            .setPositiveButton("Grant") { _, _ ->
                requestStoragePermissionLauncher.launch(getStoragePermission())
            }
            .setNegativeButton("Cancel") { _, _ ->
                showError("Permission denied")
            }
            .show()
    }

    private fun openAppSettings() {
        Log.d("PersonalDetails", "Opening app settings")
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", requireContext().packageName, null)
        }
        startActivity(intent)
    }

    private fun openImagePicker() {
        Log.d("PersonalDetails", "Opening image picker")
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        pickImageLauncher.launch(intent)
    }

    private fun getUserDetails(user: FirebaseUser, callback: (Map<String, String>?) -> Unit) {
        val email = user.email ?: return callback(null)
        db.collection("users")
            .document(email)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val userDetails = mapOf(
                        "fullname" to (document.getString("fullname") ?: ""),
                        "username" to (document.getString("username") ?: ""),
                        "phone" to (document.getString("phone") ?: ""),
                        "email" to (document.getString("email") ?: ""),
                        "address" to (document.getString("address") ?: ""),
                        "altphone" to (document.getString("altphone") ?: ""),
                        "bloodgrp" to (document.getString("bloodgrp") ?: ""),
                        "gender" to (document.getString("gender") ?: ""),
                        "profileImageBase64" to (document.getString("profileImageBase64") ?: "")
                    )
                    callback(userDetails)
                } else {
                    Log.d("PersonalDetails", "No document for user: ${user.uid}")
                    callback(null)
                }
            }
            .addOnFailureListener { e ->
                Log.w("PersonalDetails", "Error getting document for user: ${user.uid}", e)
                callback(null)
            }
    }

    private fun populateUserDetails(details: Map<String, String>) {
        fullname.setText(details["fullname"])
        username.setText(details["username"])
        phone.setText(details["phone"])
        address.setText(details["address"])
        altphn.setText(details["altphone"])
        email.setText(details["email"])
        bloodGroupDropdown.setText(details["bloodgrp"], false)
        genderDropdown.setText(details["gender"], false)
        val base64Image = details["profileImageBase64"]
        if (!base64Image.isNullOrEmpty()) {
            try {
                val imageBytes = Base64.decode(base64Image, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                profileImageView.setImageBitmap(bitmap)
            } catch (e: Exception) {
                showError("Failed to load image")
                Log.e("PersonalDetails", "Image decode failed", e)
            }
        }
    }

    private fun saveUserDetails() {
        val user = auth.currentUser ?: return showError("No user logged in")
        val userData = hashMapOf(
            "fullname" to fullname.text.toString().trim(),
            "username" to username.text.toString().trim(),
            "phone" to phone.text.toString().trim(),
            "email" to email.text.toString().trim(),
            "address" to address.text.toString().trim(),
            "altphone" to altphn.text.toString().trim(),
            "bloodgrp" to bloodGroupDropdown.text.toString(),
            "gender" to genderDropdown.text.toString()
        )

        // Basic validation for editable fields
        if (userData["fullname"].isNullOrEmpty()) {
            showError("Full name is required")
            return
        }
        if (userData["username"].isNullOrEmpty()) {
            showError("User name is required")
            return
        }

        // Handle profile image if selected
        selectedBitmap?.let { bitmap ->
            // Compress and convert bitmap to Base64
            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos)
            val imageBytes = baos.toByteArray()
            val base64Image = Base64.encodeToString(imageBytes, Base64.DEFAULT)

            // Check size (Firestore document size limit is 1 MB)
            if (base64Image.length > 0.8 * 1024 * 1024) {
                showError("Image too large. Please choose a smaller image.")
                return
            }
            userData["profileImageBase64"] = base64Image
        }

        // Save all data (user details + image) in one Firestore operation
        db.collection("users")
            .document(user.email.toString())
            .set(userData)
            .addOnSuccessListener {
                // Update UI with new image if it was selected
                selectedBitmap?.let { bitmap ->
                    profileImageView.setImageBitmap(bitmap)
                    selectedBitmap = null // Clear temporary bitmap
                }
                showSuccess("Details saved successfully")
            }
            .addOnFailureListener { e ->
                Log.w("PersonalDetails", "Error saving details", e)
                showError("Failed to save details")
            }
    }

    private fun showError(message: String) {
        context?.let {
            Toast.makeText(it, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showSuccess(message: String) {
        context?.let {
            Toast.makeText(it, message, Toast.LENGTH_SHORT).show()
        }
    }
}