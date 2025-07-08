package com.example.triptogether

import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.io.InputStream

class UploadDocumentsFragment : Fragment() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // Map to store selected file URIs
    private val selectedFiles = mutableMapOf<String, Uri?>(
        "rc" to null,
        "insurance" to null,
        "pollution" to null,
        "license" to null,
        "other" to null
    )

    // File picker launchers
    private val rcPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        selectedFiles["rc"] = uri
        updateButtonText("rc", uri)
    }
    private val insurancePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        selectedFiles["insurance"] = uri
        updateButtonText("insurance", uri)
    }
    private val pollutionPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        selectedFiles["pollution"] = uri
        updateButtonText("pollution", uri)
    }
    private val licensePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        selectedFiles["license"] = uri
        updateButtonText("license", uri)
    }
    private val otherPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        selectedFiles["other"] = uri
        updateButtonText("other", uri)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_upload_documents, container, false)

        // Hide Bottom Navigation Bar
        hideBottomNavigation()

        // Back Button
        view.findViewById<ImageView>(R.id.backButton)?.setOnClickListener {
            activity?.onBackPressed()
        }

        // File picker buttons
        view.findViewById<Button>(R.id.rcButton).setOnClickListener {
            rcPicker.launch("application/pdf,image/*")
        }
        view.findViewById<Button>(R.id.insuranceButton).setOnClickListener {
            insurancePicker.launch("application/pdf,image/*")
        }
        view.findViewById<Button>(R.id.pollutionButton).setOnClickListener {
            pollutionPicker.launch("application/pdf,image/*")
        }
        view.findViewById<Button>(R.id.licenseButton).setOnClickListener {
            licensePicker.launch("application/pdf,image/*")
        }
        view.findViewById<Button>(R.id.otherButton).setOnClickListener {
            otherPicker.launch("application/pdf,image/*")
        }

        // Upload button
        view.findViewById<Button>(R.id.uploadButton).setOnClickListener {
            uploadDocuments()
        }

        return view
    }

    override fun onStart() {
        super.onStart()
        hideBottomNavigation()
    }

    private fun hideBottomNavigation() {
        val bottomNav = activity?.findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav?.visibility = View.GONE
    }

    private fun updateButtonText(documentType: String, uri: Uri?) {
        val button = when (documentType) {
            "rc" -> view?.findViewById<Button>(R.id.rcButton)
            "insurance" -> view?.findViewById<Button>(R.id.insuranceButton)
            "pollution" -> view?.findViewById<Button>(R.id.pollutionButton)
            "license" -> view?.findViewById<Button>(R.id.licenseButton)
            "other" -> view?.findViewById<Button>(R.id.otherButton)
            else -> null
        }
        button?.text = if (uri != null) "File Selected" else "Select File"
    }

    private fun uploadDocuments() {
        val user = auth.currentUser ?: return showError("Please log in to upload documents")
        val email = user.email ?: return showError("User email not found")

        selectedFiles.forEach { (docType, uri) ->
            uri?.let { uploadFileToFirestore(email, docType, it) }
        }
    }

    private fun uploadFileToFirestore(email: String, docType: String, uri: Uri) {
        try {
            // Read file as byte array
            val inputStream: InputStream? = requireContext().contentResolver.openInputStream(uri)
            val bytes = inputStream?.readBytes()
            inputStream?.close()

            if (bytes != null && bytes.size <= 1_000_000) { // Limit to 1MB to fit Firestore
                // Convert to Base64
                val base64String = Base64.encodeToString(bytes, Base64.DEFAULT)
                val mimeType = requireContext().contentResolver.getType(uri) ?: "application/octet-stream"

                // Prepare document data
                val documentData = mapOf(
                    "base64" to base64String,
                    "mimeType" to mimeType,
                    "uploadedAt" to System.currentTimeMillis()
                )

                // Save to Firestore
                db.collection("users")
                    .document(email)
                    .collection("documents")
                    .document(docType)
                    .set(documentData)
                    .addOnSuccessListener {
                        showSuccess("$docType uploaded successfully")
                        Log.d("UploadDocumentsFragment", "$docType uploaded to Firestore")
                    }
                    .addOnFailureListener { e ->
                        showError("Failed to upload $docType: ${e.message}")
                        Log.e("UploadDocumentsFragment", "Firestore upload failed for $docType", e)
                    }
            } else {
                showError("$docType file is too large (max 1MB)")
            }
        } catch (e: Exception) {
            showError("Error processing $docType: ${e.message}")
            Log.e("UploadDocumentsFragment", "Error processing $docType", e)
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