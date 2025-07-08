package com.example.triptogether

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class redirect : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_redirect)
        val signupbtn=findViewById<Button>(R.id.btnSignUp)
        val btnlogin=findViewById<Button>(R.id.btnLogin)
        btnlogin.setOnClickListener {
            startActivity(Intent(this, logintt::class.java))
        }

        signupbtn.setOnClickListener {
            startActivity(Intent(this, signuptt::class.java))
        }
    }
}