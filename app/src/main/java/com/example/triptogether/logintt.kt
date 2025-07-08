package com.example.triptogether

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.triptogether.databinding.ActivityLoginttBinding
import com.google.firebase.auth.FirebaseAuth

class logintt : AppCompatActivity() {

    private lateinit var binding: ActivityLoginttBinding
    private lateinit var firebaseAuth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding=ActivityLoginttBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firebaseAuth=FirebaseAuth.getInstance()

        binding.btnSubmit.setOnClickListener {
            val email=binding.etEmail.text.toString()
            val password=binding.etSetPassword.text.toString()

            if(email.isNotEmpty() && password.isNotEmpty()){
                firebaseAuth.signInWithEmailAndPassword(email,password).addOnCompleteListener(this){
                    task->
                    if(task.isSuccessful){
                        Toast.makeText(this,"Login Successful!!",Toast.LENGTH_SHORT).show()
                        val intent=Intent(this, home_screen::class.java)
                        startActivity(intent)
                        finish()
                    }
                    else{
                        Toast.makeText(this,"Login Failed!",Toast.LENGTH_SHORT).show()
                    }
                }
            }
            else{
                Toast.makeText(this,"Please Enter Email and Password!",Toast.LENGTH_SHORT).show()
            }
        }
    }
}