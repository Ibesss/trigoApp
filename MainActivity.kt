package com.example.trigo

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.android.material.navigation.NavigationView
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import android.location.Geocoder
import android.os.Build
import android.widget.ImageView
import androidx.activity.OnBackPressedCallback
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

import java.util.Locale


class MainActivity : AppCompatActivity() {

    private lateinit var map: MapView
    private var myLocationOverlay: MyLocationNewOverlay? = null
    private val LOCATION_PERMISSION_REQUEST = 1
    private lateinit var locationHelper: LocationHelper

    private var selectingPickup = false
    private var pickupMarker: Marker? = null
    private var dropoffMarker: Marker? = null
    private var routeLine: Polyline? = null

    // UI
    private lateinit var bookRideButton: Button
    private lateinit var confirmButton: Button
    private lateinit var distanceText: TextView
    private lateinit var fareText: TextView
    private lateinit var fareInfoLayout: LinearLayout

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var toggle: ActionBarDrawerToggle
    private var pickupname: String? = null

    private var dropoffname: String? = null




    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        listenToBookingStatus()

        // OSMDroid config
        Configuration.getInstance().load(this, getSharedPreferences("osm_prefs", MODE_PRIVATE))

        setContentView(R.layout.activity_main)

        // Map setup
        map = findViewById(R.id.mapView)
        map.setMultiTouchControls(true)
        map.setBuiltInZoomControls(false) // pinch only
        map.controller.setZoom(15.0)
        map.controller.setCenter(GeoPoint(10.5995, 120.9842))

        // UI binding
        bookRideButton = findViewById(R.id.bookRideButton)
        confirmButton = findViewById(R.id.confirmButton)
        distanceText = findViewById(R.id.distanceText)
        fareText = findViewById(R.id.fareText)
        fareInfoLayout = findViewById(R.id.fareInfoLayout)

        fareInfoLayout.visibility = View.GONE

        locationHelper = LocationHelper(this)
        locationHelper.checkLocationSettings()

        //PICK / DROP OFF TEXT NAME
        val pickupText = findViewById<TextView>(R.id.pickupText)
        val dropoffText = findViewById<TextView>(R.id.dropoffText)

        // Drawer setup
        drawerLayout = findViewById(R.id.drawerLayout)
        navigationView = findViewById(R.id.navigationView)
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        val headerView = navigationView.getHeaderView(0)
        val avatarImageView = headerView.findViewById<ImageView>(R.id.avatarImageView)
        val usernameTextView = headerView.findViewById<TextView>(R.id.usernameTextView)
        val emailTextView = headerView.findViewById<TextView>(R.id.emailTextView)

        // ======= FETCH CURRENT USER FROM FIREBASE =======
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            val uid = currentUser.uid
            val ref = FirebaseDatabase.getInstance().getReference("accounts/passenger/$uid")

            ref.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val user = snapshot.getValue(User::class.java)
                        usernameTextView.text = user?.fullName ?: "Passenger"
                        emailTextView.text = user?.email ?: "email@example.com"
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@MainActivity, "Failed to load profile", Toast.LENGTH_SHORT).show()
                }
            })
        }

        // Set avatar background (circular)
        avatarImageView.setImageResource(R.drawable.ic_user)
        avatarImageView.background = ContextCompat.getDrawable(this, R.drawable.avatar_bg)

        // Handle avatar click
        avatarImageView.setOnClickListener {
            Toast.makeText(this, "Profile clicked", Toast.LENGTH_SHORT).show()
        }

        toggle = ActionBarDrawerToggle(this, drawerLayout, toolbar, 0, 0)
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_history -> {
                    // TODO: Navigate to history
                }
                R.id.nav_logout -> {
                    Toast.makeText(this, "Logout clicked", Toast.LENGTH_SHORT).show()
                    showLogoutDialog()
                }
            }
            drawerLayout.closeDrawer(GravityCompat.START)
            true
        }

        // Location permission check
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST
            )
        } else {
            handleLocationState()
        }

        //notification permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 100)
            }
        }

        // BOOK NOW - Start new booking
        bookRideButton.setOnClickListener {
            Toast.makeText(this, "New booking started! Tap pickup point, then drop-off.", Toast.LENGTH_SHORT).show()
            selectingPickup = true

            // Clear previous markers & route
            pickupMarker?.let { map.overlays.remove(it); it.closeInfoWindow() }
            dropoffMarker?.let { map.overlays.remove(it); it.closeInfoWindow() }
            routeLine?.let { map.overlays.remove(it) }

            pickupMarker = null
            dropoffMarker = null
            routeLine = null
            fareInfoLayout.visibility = View.GONE

            map.invalidate()
        }

        // Map tap listener for placing pickup/dropoff
        val mapEventsReceiver = object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                if (p == null || !selectingPickup) return false

                if (pickupMarker == null) {
                    pickupMarker = Marker(map).apply {
                        position = p
                        title = "Pickup"
                        icon = ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_pickup)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    }
                    pickupname = getAddressName(p.latitude, p.longitude)
                    pickupText.text = "Pickup: $pickupname"

                    map.overlays.add(pickupMarker)
                    Toast.makeText(this@MainActivity, "Pickup set!", Toast.LENGTH_SHORT).show()
                } else if (dropoffMarker == null) {
                    dropoffMarker = Marker(map).apply {
                        position = p
                        title = "Drop-off"
                        icon = ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_dropoff)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    }
                    dropoffname = getAddressName(p.latitude, p.longitude)
                    dropoffText.text = "Drop-off: $dropoffname"
                    map.overlays.add(dropoffMarker)

                    // Draw route & compute info
                    drawRoute(pickupMarker!!.position, dropoffMarker!!.position)

                    val distance = computeDistanceKm(pickupMarker!!.position, dropoffMarker!!.position)
                    val fare = computeFare(distance)

                    distanceText.text = "Distance: %.2f km".format(distance)
                    fareText.text = "Estimated Fare: ₱%.2f".format(fare)
                    fareInfoLayout.visibility = View.VISIBLE
                    selectingPickup = false
                }
                map.invalidate()
                return true
            }

            override fun longPressHelper(p: GeoPoint?): Boolean = false
        }
        map.overlays.add(MapEventsOverlay(mapEventsReceiver))

        // Confirm booking
        confirmButton.setOnClickListener {
            Toast.makeText(this, "Booking Confirmed!", Toast.LENGTH_SHORT).show()

            val currentUser = FirebaseAuth.getInstance().currentUser
            if (currentUser != null && pickupMarker != null && dropoffMarker != null) {

                val bookingsRef = FirebaseDatabase.getInstance().getReference("bookings")
                val newBookingRef = bookingsRef.push() // Auto-generated ID

                // Safely parse fare from TextView
                val fareValue = fareText.text.toString()
                    .replace("[^0-9.]".toRegex(), "")
                    .toDoubleOrNull() ?: 0.0

                val passengerName = usernameTextView.text.toString().ifEmpty { "Passenger" }

                // Collect all booking data as Map<String, Any>
                val bookingData: Map<String, Any?> = mapOf(
                    "passengerID" to currentUser.uid,
                    "passengerName" to passengerName,
                    "pickup" to mapOf(
                        "lat" to pickupMarker!!.position.latitude,
                        "lon" to pickupMarker!!.position.longitude,
                        "address" to pickupText.text.toString()
                    ),
                    "dropoff" to mapOf(
                        "lat" to dropoffMarker!!.position.latitude,
                        "lon" to dropoffMarker!!.position.longitude,
                        "address" to dropoffText.text.toString()
                    ),
                    "fare" to fareValue,
                    "status" to "pending",
                    "timestamp" to System.currentTimeMillis(),
                    "driverID" to ""
                )

                // Upload to Firebase
                newBookingRef.setValue(bookingData)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Waiting for driver to accept your ride!", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Failed to save booking: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            } else {
                Toast.makeText(this, "Please set pickup and dropoff first!", Toast.LENGTH_SHORT).show()
            }

            // Remove route line if exists
            routeLine?.let { map.overlays.remove(it) }

            // Remove pickup and dropoff markers if they exist
            pickupMarker?.let {
                map.overlays.remove(it)
                it.closeInfoWindow()
            }
            dropoffMarker?.let {
                map.overlays.remove(it)
                it.closeInfoWindow()
            }

            // Reset all marker and route references
            pickupMarker = null
            dropoffMarker = null
            routeLine = null

            // Hide fare info layout
            fareInfoLayout.visibility = View.GONE

            // Redraw map
            map.invalidate()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val builder = androidx.appcompat.app.AlertDialog.Builder(this@MainActivity)
                builder.setTitle("Exit App")
                builder.setMessage("Are you sure you want to close the app?")
                builder.setCancelable(true)
                builder.setPositiveButton("Yes") { _, _ ->
                    finishAffinity()
                }
                builder.setNegativeButton("No") { dialog, _ ->
                    dialog.dismiss()
                }
                builder.create().show()
            }
        })
    }

    private fun getAddressName(lat: Double, lon: Double): String {
        return try {
            val geocoder = Geocoder(this, Locale.getDefault())
            val addresses = geocoder.getFromLocation(lat, lon, 1)

            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]

                val street = address.thoroughfare ?: ""
                val barangay = address.subLocality ?: ""
                val city = address.locality ?: ""
                val province = address.adminArea ?: ""

                listOf(street, barangay, city, province)
                    .filter { it.isNotBlank() }
                    .joinToString(", ")
            } else {
                "Unknown location"
            }
        } catch (e: Exception) {
            "Unknown location"
        }
    }

    // Centralized handling of location state
    private fun handleLocationState() {
        if (isLocationEnabled()) {
            enableMyLocation()
        }
    }

    private fun enableMyLocation() {
        if (myLocationOverlay == null) {
            myLocationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(this), map)
        }
        if (myLocationOverlay?.isMyLocationEnabled == false) {
            myLocationOverlay?.enableMyLocation()
            map.overlays.add(myLocationOverlay)
        }
        myLocationOverlay?.runOnFirstFix {
            runOnUiThread {
                myLocationOverlay?.myLocation?.let {
                    map.controller.setZoom(17.0)
                    map.controller.setCenter(it)
                }
            }
        }
    }

    private fun drawRoute(pickup: GeoPoint, dropoff: GeoPoint) {
        val apiKey = "eyJvcmciOiI1YjNjZTM1OTc4NTExMTAwMDFjZjYyNDgiLCJpZCI6IjllMTFhODM3MzZiMTQ3ZDU4ZDRlNTU4NDRhNDM5ZTBjIiwiaCI6Im11cm11cjY0In0="
        val url = "https://api.openrouteservice.org/v2/directions/driving-car?api_key=$apiKey&start=${pickup.longitude},${pickup.latitude}&end=${dropoff.longitude},${dropoff.latitude}"

        val queue = Volley.newRequestQueue(this)
        val stringRequest = StringRequest(
            com.android.volley.Request.Method.GET, url,
            { response ->
                try {
                    val json = JSONObject(response)
                    val coords = json.getJSONArray("features")
                        .getJSONObject(0)
                        .getJSONObject("geometry")
                        .getJSONArray("coordinates")

                    val route = Polyline().apply {
                        outlinePaint.color = android.graphics.Color.BLUE
                        outlinePaint.strokeWidth = 10f
                    }

                    for (i in 0 until coords.length()) {
                        val point = coords.getJSONArray(i)
                        route.addPoint(GeoPoint(point.getDouble(1), point.getDouble(0)))
                    }

                    routeLine?.let { map.overlays.remove(it) }
                    routeLine = route
                    map.overlays.add(route)
                    map.invalidate()
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this, "Error parsing route", Toast.LENGTH_SHORT).show()
                }
            },
            {
                Toast.makeText(this, "Error fetching route", Toast.LENGTH_SHORT).show()
            })
        queue.add(stringRequest)
    }

    private fun computeDistanceKm(start: GeoPoint, end: GeoPoint): Double {
        val results = FloatArray(1)
        android.location.Location.distanceBetween(
            start.latitude, start.longitude,
            end.latitude, end.longitude,
            results
        )
        return results[0] / 1000.0
    }

    private fun computeFare(distanceKm: Double): Double {
        val baseDistance = 0.57 // in km
        val baseFare = 27.0
        val perKmRate = 7.0
        return if (distanceKm > baseDistance) {
            baseFare + ((distanceKm - baseDistance) * perKmRate)
        } else {
            baseFare
        }
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun logoutUser(){
        val sharedPref = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        sharedPref.edit().clear().apply()

        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun showLogoutDialog(){
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setTitle("Logout")
        builder.setMessage("Are you sure you want to logout?")
        builder.setPositiveButton("Yes"){_,_-> logoutUser()}
        builder.setNegativeButton("No"){dialog,_-> dialog.dismiss()}
        builder.show()
    }

    private var lastBookingId: String? = null
    private var lastStatus: String? = null

    private fun listenToBookingStatus() {
        val passengerId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val database = FirebaseDatabase.getInstance().getReference("bookings")

        database.orderByChild("passengerID").equalTo(passengerId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    var latestBookingSnap: DataSnapshot? = null
                    var latestTime: Long = 0

                    // Find the most recent booking for this passenger
                    for (bookingSnap in snapshot.children) {
                        val timestamp =
                            bookingSnap.child("timestamp").getValue(Long::class.java) ?: 0
                        if (timestamp > latestTime) {
                            latestTime = timestamp
                            latestBookingSnap = bookingSnap
                        }
                    }

                    val bookingSnap = latestBookingSnap ?: return
                    val bookingId = bookingSnap.key ?: return
                    val status =
                        bookingSnap.child("status").getValue(String::class.java) ?: "pending"
                    val driverId = bookingSnap.child("driverID").getValue(String::class.java) ?: ""

                    // Ignore pending (still waiting for driver)
                    if (status == "pending") return

                    // Only notify if new booking or status changed
                    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

                    if (driverId != currentUserId) {
                        if (bookingId != lastBookingId || status != lastStatus) {
                            lastBookingId = bookingId
                            lastStatus = status

                            when (status) {
                                "accepted" -> {
                                    showBookingNotification(
                                        this@MainActivity,
                                        "Booking Accepted",
                                        "Your driver is on the way!"
                                    )
                                }
                                "arrived" -> {
                                    showBookingNotification(
                                        this@MainActivity,
                                        "Driver Arrived",
                                        "Your driver has arrived at the pickup point"
                                    )
                                }
                            }
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun showBookingNotification(context: Context, title: String, message: String) {
        val channelId = "booking_notifications"
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Booking Notifications",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    override fun onResume() {
        super.onResume()
        map.postDelayed({
            handleLocationState()
            if (isLocationEnabled()) enableMyLocation()
        }, 300)
    }
}
