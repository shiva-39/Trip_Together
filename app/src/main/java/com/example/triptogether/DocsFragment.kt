package com.example.triptogether

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.io.File
import java.io.FileOutputStream
import androidx.appcompat.app.AppCompatActivity
import com.example.triptogether.R

class DocsFragment : Fragment() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_docs, container, false)

        // Set transparent ActionBar
        (activity as? AppCompatActivity)?.supportActionBar?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        // Show Bottom Navigation Bar
        showBottomNavigation()

        // Set click listeners for Open buttons
        view.findViewById<Button>(R.id.btnOpenRC).setOnClickListener {
            openDocument("rc")
        }
        view.findViewById<Button>(R.id.btnOpenDL).setOnClickListener {
            openDocument("license")
        }
        view.findViewById<Button>(R.id.btnOpenPollution).setOnClickListener {
            openDocument("pollution")
        }
        view.findViewById<Button>(R.id.btnOpenInsurance).setOnClickListener {
            openDocument("insurance")
        }

        // Set click listener for Upload Other Documents
        view.findViewById<Button>(R.id.btnUploadOtherDocs).setOnClickListener {
            (activity as? home_screen)?.navigateToUploadDocuments()
        }

        return view
    }

    override fun onStart() {
        super.onStart()
        showBottomNavigation()
    }

    private fun showBottomNavigation() {
        val bottomNav = activity?.findViewById<BottomNavigationView>(R.id.bottom_navigation)
        if (bottomNav != null) {
            bottomNav.visibility = View.VISIBLE
            Log.d("DocsFragment", "BottomNav set to VISIBLE")
        } else {
            Log.e("DocsFragment", "BottomNav not found!")
        }
    }

    private fun openDocument(docType: String) {
        val user = auth.currentUser ?: return showError("Please log in to view documents")
        val email = user.email ?: return showError("User email not found")

        db.collection("users")
            .document(email)
            .collection("documents")
            .document(docType)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val base64String = document.getString("base64")
                    val mimeType = document.getString("mimeType") ?: "application/octet-stream"

                    if (!base64String.isNullOrEmpty()) {
                        try {
                            // Decode Base64 to byte array
                            val decodedBytes = Base64.decode(base64String, Base64.DEFAULT)

                            // Use a fixed prefix to ensure 3+ characters
                            val filePrefix = "doc_$docType"

                            // Determine file extension
                            val fileExtension = when {
                                mimeType.startsWith("image/") -> ".jpg"
                                mimeType == "application/pdf" -> ".pdf"
                                else -> ".bin"
                            }

                            // Create a temporary file
                            val tempFile = File.createTempFile(filePrefix, fileExtension, requireContext().cacheDir)
                            Log.d("DocsFragment", "Created temp file: ${tempFile.absolutePath}")
                            FileOutputStream(tempFile).use { output ->
                                output.write(decodedBytes)
                            }

                            // Get URI for the file using FileProvider
                            val fileUri = FileProvider.getUriForFile(
                                requireContext(),
                                "${requireContext().packageName}.fileprovider",
                                tempFile
                            )
                            Log.d("DocsFragment", "File URI: $fileUri")

                            // Open the file with an appropriate app
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(fileUri, mimeType)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            try {
                                startActivity(intent)
                            } catch (e: Exception) {
                                showError("No app found to open $docType document")
                                Log.e("DocsFragment", "Failed to open $docType", e)
                            }
                        } catch (e: Exception) {
                            showError("Failed to process $docType document: ${e.message}")
                            Log.e("DocsFragment", "Error processing $docType", e)
                        }
                    } else {
                        showError("No $docType document data found")
                        Log.e("DocsFragment", "$docType document has empty base64 field")
                    }
                } else {
                    showError("No $docType document found in Firestore")
                    Log.e("DocsFragment", "$docType document does not exist")
                }
            }
            .addOnFailureListener { e ->
                showError("Failed to fetch $docType document: ${e.message}")
                Log.e("DocsFragment", "Error fetching $docType", e)
            }
    }

    private fun showError(message: String) {
        context?.let {
            Toast.makeText(it, message, Toast.LENGTH_SHORT).show()
        }
    }
}