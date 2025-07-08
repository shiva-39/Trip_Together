package com.example.triptogether

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.bottomnav.CreateFragment
import com.example.triptogether.DocsFragment
import com.example.triptogether.UserDetailsFragment
import com.example.triptogetherpackage.HelpUser
import com.example.triptogetherpackage.VehicleDetails
import com.google.android.material.bottomnavigation.BottomNavigationView

class home_screen : AppCompatActivity() {

    private lateinit var bottomNav: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home_screen)

        bottomNav = findViewById(R.id.bottom_navigation)

        // Load HomeFragment by default
        if (savedInstanceState == null) {
            loadFragment(HomeFragment())
            showBottomNavigation(true) // Show for HomeFragment
        }

        bottomNav.setOnItemSelectedListener { item ->
            val selectedFragment: Fragment = when (item.itemId) {
                R.id.nav_home -> HomeFragment()
                R.id.nav_create -> CreateFragment()
                R.id.nav_docs -> DocsFragment()
                else -> HomeFragment()
            }
            loadFragment(selectedFragment)
            // Show bottom nav only for main fragments
            showBottomNavigation(
                selectedFragment is HomeFragment ||
                        selectedFragment is CreateFragment ||
                        selectedFragment is DocsFragment
            )
            true
        }
    }

    // Function to load the selected fragment
    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null) // Allows going back to the previous fragment
            .commit()
    }

    // Function to navigate to UserDetailsFragment
    fun navigateToUserDetails() {
        val fragment = UserDetailsFragment()
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
        showBottomNavigation(false) // Hide for UserDetailsFragment
    }

    fun navigateToPersonalDetails() {
        val fragment = PersonalDetails()
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
        showBottomNavigation(false) // Hide for PersonalDetails
    }

    // Function to control BottomNavigationView visibility
    private fun showBottomNavigation(show: Boolean) {
        bottomNav.visibility = if (show) View.VISIBLE else View.GONE
    }

    // Handle back press to restore visibility based on the new top fragment
    override fun onBackPressed() {
        super.onBackPressed()
        val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
        showBottomNavigation(
            currentFragment is HomeFragment ||
                    currentFragment is CreateFragment ||
                    currentFragment is DocsFragment
        )
    }

    fun navigateToVehicleDetails() {
        val fragment = VehicleDetails()
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
        showBottomNavigation(false)
    }

    fun navigateToHelp() {
        val fragment = HelpUser()
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
        showBottomNavigation(false)
    }

    fun navigateToUploadDocuments() {
        val fragment = UploadDocumentsFragment()
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
    }
}