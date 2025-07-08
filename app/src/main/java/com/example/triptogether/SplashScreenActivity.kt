package com.example.triptogether

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import com.example.triptogether.HomeFragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser

@SuppressLint("CustomSplashScreen")
class SplashScreenActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash_screen)

        val videoView = findViewById<VideoView>(R.id.videoViewSplash)
        val videoUri = Uri.parse("android.resource://$packageName/${R.raw.splash}")
        videoView.setVideoURI(videoUri)
        videoView.start()

        Handler(Looper.getMainLooper()).postDelayed({
            val currentUser: FirebaseUser? = FirebaseAuth.getInstance().currentUser

            if (currentUser != null) {
                // User is logged in, redirect to Home Screen
                startActivity(Intent(this, home_screen::class.java))
            } else {
                // User is not logged in, redirect to Login Screen
                startActivity(Intent(this, redirect::class.java))
            }
            finish()
        }, 3000) // 3 seconds delay
    }
}
