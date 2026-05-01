package com.example.trigo

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessaging
import android.app.NotificationManager
import android.app.NotificationChannel
import android.widget.TextView
import androidx.compose.ui.text.style.TextAlign
import com.google.android.material.button.MaterialButton

class DriverMainActivity : AppCompatActivity() {

    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var locationHelper: LocationHelper
    private var isHomeLoaded = false // prevent double loading


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_driver_main)

        // ✅ Save FCM token
        FirebaseAuth.getInstance().uid?.let { driverId ->
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (!task.isSuccessful) return@addOnCompleteListener
                val token = task.result
                FirebaseDatabase.getInstance().getReference("drivers")
                    .child(driverId)
                    .child("token")
                    .setValue(token)
            }
        }

        createBookingNotificationChannel()


        locationHelper = LocationHelper(this)
        bottomNavigation = findViewById(R.id.bottomNavigation)

        val showTracking = intent.getBooleanExtra("showTracking", false)
        if (showTracking) {
            val homeFragment = DriverHomeFragment().apply {
                arguments = Bundle().apply {
                    putBoolean("showTracking", true)
                    putDouble("pickupLat", intent.getDoubleExtra("pickupLat", 0.0))
                    putDouble("pickupLng", intent.getDoubleExtra("pickupLng", 0.0))
                    putDouble("dropoffLat", intent.getDoubleExtra("dropoffLat", 0.0))
                    putDouble("dropoffLng", intent.getDoubleExtra("dropoffLng", 0.0))
                    putString("passengerName", intent.getStringExtra("passengerName"))
                    putDouble("fare", intent.getDoubleExtra("fare", 0.0))
                    putString("pickupAddress", intent.getStringExtra("pickupAddress"))
                    putString("dropoffAddress", intent.getStringExtra("dropoffAddress"))
                    putString("bookingId", intent.getStringExtra("bookingId")) // ✅ Add this

                }
            }
            replaceFragment(homeFragment)
            bottomNavigation.selectedItemId = R.id.nav_home
            isHomeLoaded = true
        } else {
            if (locationHelper.hasLocationPermission(this) && locationHelper.isLocationEnabled(this)) {
                loadHomeFragment(forceReload = true)
            } else {
                locationHelper.checkLocationSettings()
            }
        }

        // ✅ BottomNavigation click listener (returns Boolean correctly)
        bottomNavigation.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_home -> {
                    loadHomeFragment(forceReload = true)
                    true
                }
                R.id.nav_booking -> {
                    replaceFragment(DriverBookingFragment())
                    true
                }
                R.id.nav_profile -> {
                    replaceFragment(DriverProfileFragment())
                    true
                }
                else -> false
            }
        }

        // ✅ Handle back press
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val builder = androidx.appcompat.app.AlertDialog.Builder(this@DriverMainActivity)
                builder.setTitle("Exit App")
                builder.setMessage("Are you sure you want to close the app?")
                builder.setCancelable(true)
                builder.setPositiveButton("Yes") { _, _ -> finishAffinity() }
                builder.setNegativeButton("No") { dialog, _ -> dialog.dismiss() }
                builder.create().show()
            }
        })

    }

    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.driverFragmentContainer, fragment)
            .commitAllowingStateLoss()
    }



    private fun loadHomeFragment(forceReload: Boolean = false) {
        if (!isHomeLoaded || forceReload) {
            val fragment = DriverHomeFragment()

            if (intent.getBooleanExtra("showTracking", false)) {
                fragment.arguments = Bundle().apply {
                    putBoolean("showTracking", true)
                    putString("pickupAddress", intent.getStringExtra("pickupAddress"))
                    putString("dropoffAddress", intent.getStringExtra("dropoffAddress"))
                    putDouble("fare", intent.getDoubleExtra("fare", 0.0))
                    putString("passengerName", intent.getStringExtra("passengerName"))
                    putDouble("pickupLat", intent.getDoubleExtra("pickupLat", 0.0))
                    putDouble("pickupLng", intent.getDoubleExtra("pickupLng", 0.0))
                    putDouble("dropoffLat", intent.getDoubleExtra("dropoffLat", 0.0))
                    putDouble("dropoffLng", intent.getDoubleExtra("dropoffLng", 0.0))
                    putString("bookingId",intent.getStringExtra("bookingId"))
                }
            }

            replaceFragment(fragment)
            isHomeLoaded = true
        }
    }

    private fun createBookingNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "BOOKING_CHANNEL",
                "Booking Notifications",
                NotificationManager.IMPORTANCE_HIGH
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }






    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001 && locationHelper.isLocationEnabled(this)) {
            isHomeLoaded = false
            loadHomeFragment(forceReload = true)
        }
    }

    override fun onResume() {
        super.onResume()
        if (locationHelper.isLocationEnabled(this) && !isHomeLoaded) {
            loadHomeFragment(forceReload = true)
        }
    }
}