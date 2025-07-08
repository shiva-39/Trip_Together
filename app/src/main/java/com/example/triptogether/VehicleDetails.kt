package com.example.triptogetherpackage

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.triptogether.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore

class VehicleDetails : Fragment() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private lateinit var year: EditText
    private lateinit var model: EditText
    private lateinit var company: EditText
    private lateinit var vehicledropdown: AutoCompleteTextView
    private lateinit var submit: Button

    @SuppressLint("MissingInflatedId")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.vehicle_details, container, false)

        // Initialize UI components properly
        year = view.findViewById(R.id.year)
        model = view.findViewById(R.id.model)
        company = view.findViewById(R.id.company)
        vehicledropdown = view.findViewById(R.id.vehicle_dropdown)
        submit = view.findViewById(R.id.submitButton)

        val vehicles = arrayOf("Bike", "Car", "Bus", "Cycle", "Walk", "Lorry")
        val vehicleAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, vehicles)
        vehicledropdown.setAdapter(vehicleAdapter)

        vehicledropdown.dropDownWidth = ViewGroup.LayoutParams.WRAP_CONTENT
        vehicledropdown.setOnClickListener { vehicledropdown.showDropDown() }
        vehicledropdown.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) vehicledropdown.showDropDown()
        }

        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.e("VehicleDetails", "No user logged in")
            showError("Please log in to view details")
        } else {
            Log.d("VehicleDetails", "User logged in: ${currentUser.uid}")
            getUserDetails(currentUser) { details ->
                if (details != null) {
                    populateUserDetails(details)
                } else {
                    showError("Failed to load user details")
                }
            }
        }

        // Set the button listener AFTER initializing it
        submit.setOnClickListener { saveVehicleDetails() }

        return view
    }

    private fun saveVehicleDetails() {
        val user = auth.currentUser ?: return showError("No user logged in")

        val userData = hashMapOf(
            "Purchase" to year.text.toString().trim(),
            "Company" to company.text.toString().trim(),
            "Model" to model.text.toString().trim(),
            "Vehicle" to vehicledropdown.text.toString().trim()
        )

        if (userData.values.any { it.isEmpty() }) {
            showError("All fields are required")
            return
        }

        db.collection("vehicles")
            .document(user.email.toString())
            .set(userData)
            .addOnSuccessListener {
                showSuccess("Details saved successfully")
            }
            .addOnFailureListener { e ->
                Log.w("VehicleDetails", "Error saving details", e)
                showError("Failed to save details")
            }
    }

    private fun getUserDetails(user: FirebaseUser, callback: (Map<String, String>?) -> Unit) {
        val email = user.email ?: return callback(null)

        db.collection("vehicles")
            .document(email)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val vehicleDetails = mapOf(
                        "Purchase" to (document.getString("Purchase") ?: ""),
                        "Company" to (document.getString("Company") ?: ""),
                        "Model" to (document.getString("Model") ?: ""),
                        "Vehicle" to (document.getString("Vehicle") ?: "")
                    )
                    callback(vehicleDetails)
                } else {
                    callback(null)
                }
            }
            .addOnFailureListener {
                callback(null)
            }
    }

    private fun populateUserDetails(details: Map<String, String>) {
        year.setText(details["Purchase"])
        company.setText(details["Company"])
        model.setText(details["Model"])
        vehicledropdown.setText(details["Vehicle"])
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
