package com.example.triptogether

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.example.triptogether.databinding.ActivitySignupttBinding
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.firestore

class signuptt : AppCompatActivity() {

    private lateinit var binding: ActivitySignupttBinding
    private lateinit var firebaseAuth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivitySignupttBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firebaseAuth = FirebaseAuth.getInstance()

        binding.btnSubmit.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val firstname = binding.firstName.text.toString().trim().replaceFirstChar { it.uppercaseChar() }
            val lastname = binding.LastName.text.toString().trim().replaceFirstChar { it.uppercaseChar() }
            val dob = binding.dob.text.toString().trim()
            val phone = binding.phoneNumber.text.toString().trim()
            val password = binding.etSetPassword.text.toString().trim()
            val repassword = binding.RePassword.text.toString().trim()
            val username = firstname.replaceFirstChar { it.uppercaseChar() }

            if (email.isNotEmpty() && password.isNotEmpty() && repassword.isNotEmpty()) {
                if (password == repassword) {
                    firebaseAuth.createUserWithEmailAndPassword(email, password)
                        .addOnCompleteListener(this) { task ->
                            if (task.isSuccessful) {
                                val user = firebaseAuth.currentUser
                                updateUserProfile(user, firstname, lastname)
                                storeDetails(user,phone,username,dob,email,firstname+" "+lastname)
                                Toast.makeText(this, "Signup Successful!", Toast.LENGTH_SHORT).show()
                                val intent = Intent(this, logintt::class.java)
                                startActivity(intent)
                                finish()
                            } else {
                                val errorMessage = task.exception?.message ?: "Signup Failed!"
                                Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
                                println("Signup Error: $errorMessage") // ✅ Logs error for debugging
                            }
                        }
                } else {
                    Toast.makeText(this, "Passwords don't match!", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Fill all the fields!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateUserProfile(user: FirebaseUser?, firstName: String, lastName: String) {
        if (user != null) {
            val fullName = "$firstName ".replaceFirstChar { it.uppercaseChar() }
            val profileUpdates = UserProfileChangeRequest.Builder()
                .setDisplayName(fullName)
                .build()

            user.updateProfile(profileUpdates)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Toast.makeText(this, "Profile Updated", Toast.LENGTH_SHORT).show()
                    }
                }
        }
    }
    private fun storeDetails(user: FirebaseUser?,phone:String,username:String,dateofbirth:String,email:String,fullname:String){
        if (user == null) {
            println("User is null")
            return
        }
        val doc = email.split("@")

        val db = Firebase.firestore

        // Create a map with user details
        val userDetails = hashMapOf(
            "userID" to user.uid,
            "username" to username,
            "fullname" to fullname,
            "phone" to phone,
            "dateOfBirth" to dateofbirth,
            "email" to email
        )

        db.collection("users").document(email)
            .set(userDetails)
            .addOnSuccessListener {
                println("User details successfully stored for ${doc[0]}")
            }
            .addOnFailureListener { e ->
                println("Error storing user details: $e")
            }
    }
}
