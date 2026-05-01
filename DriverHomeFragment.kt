package com.example.trigo

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

class DriverHomeFragment : Fragment() {

    private lateinit var map: MapView
    private var myLocationOverlay: MyLocationNewOverlay? = null
    private var pickupMarker: Marker? = null
    private var dropoffMarker: Marker? = null
    private var routeLine: Polyline? = null

    private val LOCATION_PERMISSION_REQUEST = 100
    private val PICKUP_RADIUS_METERS = 15.0

    private lateinit var database: DatabaseReference
    private lateinit var auth: FirebaseAuth

    private var bookingInfoLayout: View? = null
    private var txtPassengerName: TextView? = null
    private var txtPickup: TextView? = null
    private var txtDropoff: TextView? = null
    private var txtFare: TextView? = null

    private var pickupPoint: GeoPoint? = null
    private var dropoffPoint: GeoPoint? = null
    private var bookingID: String? = null
    private var bookingListener: ChildEventListener? = null
    private var reachedPickup = false

    private var btnCompleteRide: Button? = null
    private var bookingStatusListener: ValueEventListener?= null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_driver_home, container, false)

        database = FirebaseDatabase.getInstance().getReference("bookings")
        auth = FirebaseAuth.getInstance()

        listenForNewBookings()

        Configuration.getInstance().load(requireContext(), requireContext().getSharedPreferences("osm_prefs", 0))
        map = view.findViewById(R.id.mapView)
        map.setMultiTouchControls(true)
        map.setBuiltInZoomControls(false)
        map.controller.setZoom(18.0)

        bookingInfoLayout = view.findViewById(R.id.bookingInfoLayout)
        txtPassengerName = view.findViewById(R.id.txtPassengerName)
        txtPickup = view.findViewById(R.id.txtPickup)
        txtDropoff = view.findViewById(R.id.txtDropoff)
        txtFare = view.findViewById(R.id.txtFare)

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(requireActivity(),
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST)
        } else {
            enableDriverLocation()
        }


        //Completionbutton
        btnCompleteRide = view.findViewById(R.id.btnCompleteRide)
        btnCompleteRide?.setOnClickListener {

            bookingID?.let { id ->

                val bookingRef = FirebaseDatabase.getInstance()
                    .getReference("bookings")
                    .child(id)

                val historyRef = FirebaseDatabase.getInstance()
                    .getReference("completed_bookings")
                    .child(id)

                AlertDialog.Builder(requireContext())
                    .setTitle("Complete Booking")
                    .setMessage("Are you sure you want to complete this ride?")
                    .setPositiveButton("Yes") { dialog, _ ->

                        bookingRef.get().addOnSuccessListener { snapshot ->

                            if (!snapshot.exists()) {
                                Toast.makeText(requireContext(), "Booking not found", Toast.LENGTH_SHORT).show()
                                return@addOnSuccessListener
                            }

                            // 🔥 STEP 1: COPY TO HISTORY (UNIQUE KEY)
                            val newHistory = historyRef.push()
                            newHistory.setValue(snapshot.value)

                            // 🔥 STEP 2: DELETE FROM ACTIVE BOOKINGS (YOUR DEFAULT BEHAVIOR)
                            bookingRef.removeValue()
                                .addOnSuccessListener {

                                    activity?.intent?.removeExtra("bookingid")
                                    activity?.intent?.removeExtra("showTracking")

                                    // Clear UI
                                    bookingInfoLayout?.visibility = View.GONE
                                    btnCompleteRide?.visibility = View.GONE
                                    pickupMarker?.let { map.overlays.remove(it) }
                                    dropoffMarker?.let { map.overlays.remove(it) }
                                    routeLine?.let { map.overlays.remove(it) }
                                    map.invalidate()

                                    // Clear state
                                    bookingID = null
                                    pickupPoint = null
                                    dropoffPoint = null
                                    reachedPickup = false
                                    arguments = null

                                    Toast.makeText(requireContext(), "Ride Completed!", Toast.LENGTH_SHORT).show()
                                    clearBookingUI()
                                }
                        }

                        dialog.dismiss()
                    }
                    .setNegativeButton("No") { dialog, _ -> dialog.dismiss() }
                    .show()
            }
        }

        val args = arguments
        if (args?.getBoolean("showTracking") == true) {
            val id = args.getString("bookingId") ?: ""

            // Check in Firebase if booking still exists
            database.child(id).get().addOnSuccessListener { snapshot ->
                if (!snapshot.exists()) {
                    // Booking doesn't exist → hide UI
                    clearBookingUI()
                    return@addOnSuccessListener
                }

                val status = snapshot.child("status").getValue(String::class.java) ?: "pending"

                if (status == "completed") {
                    // Booking already completed → hide UI
                    clearBookingUI()
                } else {
                    // Show booking for any other status
                    bookingID = id
                    pickupPoint = GeoPoint(args.getDouble("pickupLat", 0.0), args.getDouble("pickupLng", 0.0))
                    dropoffPoint = GeoPoint(args.getDouble("dropoffLat", 0.0), args.getDouble("dropoffLng", 0.0))

                    showBookingInfo(
                        args.getString("passengerName"),
                        args.getString("pickupAddress"),
                        args.getString("dropoffAddress"),
                        args.getDouble("fare", 0.0)
                    )

                    drawPickupDropoffMarkers(pickupPoint!!, dropoffPoint!!)
                    bookingID?.let { listenToBookingStatus(it) }
                }
            }
        }
        return view
    }

    // ---------------- Driver Location ----------------
    // --------------- Continuous Location Handling ----------------


    private fun enableDriverLocation() {
        val ctx = requireContext()
        val provider = GpsMyLocationProvider(ctx).apply {
            addLocationSource(android.location.LocationManager.GPS_PROVIDER)
            addLocationSource(android.location.LocationManager.NETWORK_PROVIDER)
        }

        myLocationOverlay = MyLocationNewOverlay(provider, map).apply {
            // Keep default yellow person icon
            setDirectionIcon(null)
            enableMyLocation()
            enableFollowLocation()
        }

        map.overlays.add(myLocationOverlay)

        myLocationOverlay?.runOnFirstFix {
            activity?.runOnUiThread {
                myLocationOverlay?.myLocation?.let {
                    map.controller.animateTo(GeoPoint(it.latitude, it.longitude))
                    handleLocationUpdate(it.latitude, it.longitude)
                }
            }
        }

        // Continuous updates
        myLocationOverlay?.enableMyLocation()
        myLocationOverlay?.runOnFirstFix {
            // periodically check location
            map.postDelayed(object : Runnable {
                override fun run() {
                    myLocationOverlay?.myLocation?.let {
                        handleLocationUpdate(it.latitude, it.longitude)
                    }
                    map.postDelayed(this, 2000) // every 2 seconds
                }
            }, 2000)
        }
    }

    private fun handleLocationUpdate(lat: Double, lon: Double) {
        val driverPoint = GeoPoint(lat, lon)

        // Update Firebase driver location
        auth.currentUser?.uid?.let { driverId ->
            database.child("drivers").child(driverId).child("location")
                .setValue(mapOf("latitude" to lat, "longitude" to lon))
        }

        // Draw route
        pickupPoint?.let { pickup ->
            dropoffPoint?.let { dropoff ->
                if (!reachedPickup) {
                    // Driver → Pickup
                    drawRoute(driverPoint, pickup)

                    // Check if reached pickup
                    if (driverPoint.distanceToAsDouble(pickup) <= PICKUP_RADIUS_METERS) {
                        reachedPickup = true
                        drawRoute(pickup, dropoff)
                    }
                } else {
                    // Already reached pickup → always show Pickup → Dropoff
                    drawRoute(pickup, dropoff)
                }
            }
        }
    }

    // ---------------- Pickup & Dropoff ----------------
    private fun drawPickupDropoffMarkers(pickup: GeoPoint, dropoff: GeoPoint) {
        pickupMarker = Marker(map).apply {
            position = pickup
            title = "Pickup"
            icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_pickup)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        }

        dropoffMarker = Marker(map).apply {
            position = dropoff
            title = "Dropoff"
            icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_dropoff)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        }

        pickupMarker?.let { map.overlays.add(it) }
        dropoffMarker?.let { map.overlays.add(it) }

        map.controller.animateTo(pickup)
        map.invalidate()
    }

    // ---------------- Route Drawing ----------------


    private fun listenToBookingStatus(bookingId: String) {
        database.child(bookingId).child("status")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val status = snapshot.getValue(String::class.java) ?: return
                    val driverLoc = myLocationOverlay?.myLocation ?: return
                    val driverPoint = GeoPoint(driverLoc.latitude, driverLoc.longitude)

                    when (status) {
                        "accepted" -> {
                            // Hide Complete Ride button before starting trip
                            btnCompleteRide?.visibility = View.GONE

                            // Draw route from driver → pickup if not yet reached
                            if (!reachedPickup) {
                                pickupPoint?.let { drawRoute(driverPoint, it) }
                            }

                            // Check if driver reached pickup
                            if (!reachedPickup && pickupPoint != null &&
                                driverPoint.distanceToAsDouble(pickupPoint!!) <= PICKUP_RADIUS_METERS
                            ) {
                                reachedPickup = true
                                // Switch route to pickup → dropoff
                                if (dropoffPoint != null) drawRoute(pickupPoint!!, dropoffPoint!!)
                            }
                        }

                        "in_trip" -> {
                            // Show Complete Ride button
                            btnCompleteRide?.visibility = View.VISIBLE
                            // Draw route from driver → dropoff
                            dropoffPoint?.let { drawRoute(driverPoint, it) }
                        }

                        "completed" -> {
                            clearBookingUI()
                            btnCompleteRide?.visibility = View.GONE
                            Toast.makeText(requireContext(), "Ride Completed", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    // ---------------- Route Drawing ----------------
    private fun drawRoute(start: GeoPoint, end: GeoPoint) {
        val apiKey = "eyJvcmciOiI1YjNjZTM1OTc4NTExMTAwMDFjZjYyNDgiLCJpZCI6IjllMTFhODM3MzZiMTQ3ZDU4ZDRlNTU4NDRhNDM5ZTBjIiwiaCI6Im11cm11cjY0In0="
        val url = "https://api.openrouteservice.org/v2/directions/driving-car?api_key=$apiKey" +
                "&start=${start.longitude},${start.latitude}&end=${end.longitude},${end.latitude}"

        val queue = Volley.newRequestQueue(requireContext())
        val stringRequest = StringRequest(Request.Method.GET, url,
            { response ->
                try {
                    val json = JSONObject(response)
                    val features = json.optJSONArray("features")
                    if (features == null || features.length() == 0) return@StringRequest

                    val geometry = features.getJSONObject(0).optJSONObject("geometry") ?: return@StringRequest
                    val coords = geometry.optJSONArray("coordinates") ?: return@StringRequest

                    val polyline = Polyline().apply {
                        outlinePaint.strokeWidth = 10f
                        outlinePaint.color = ContextCompat.getColor(requireContext(), R.color.teal_700)
                        isGeodesic = true
                    }

                    for (i in 0 until coords.length()) {
                        val point = coords.getJSONArray(i)
                        polyline.addPoint(GeoPoint(point.getDouble(1), point.getDouble(0)))
                    }

                    // Remove old route and add new
                    routeLine?.let { map.overlays.remove(it) }
                    routeLine = polyline
                    map.overlays.add(0, polyline)

                    pickupMarker?.let { if (!map.overlays.contains(it)) map.overlays.add(it) }
                    dropoffMarker?.let { if (!map.overlays.contains(it)) map.overlays.add(it) }

                    map.invalidate()
                } catch (e: Exception) {
                    e.printStackTrace()
                    // Avoid showing toast repeatedly; optionally log only
                    Log.e("DriverHome", "Error parsing route: ${e.message}")
                }
            },
            {
                Log.e("DriverHome", "Error fetching route: ${it.message}")
            }
        )
        queue.add(stringRequest)
    }

    // ---------------- Booking Info ----------------
    private fun showBookingInfo(name: String?, pickup: String?, dropoff: String?, fare: Double) {
        bookingInfoLayout?.visibility = View.VISIBLE
        txtPassengerName?.text = "Passenger: $name"
        txtPickup?.text = "Pickup: $pickup"
        txtDropoff?.text = "Dropoff: $dropoff"
        txtFare?.text = "Fare: ₱${"%.2f".format(fare)}"
    }

    // ---------------- Notifications ----------------
    @SuppressLint("ServiceCast")
    private fun showNewBookingNotification(bookingId: String, pickup: String, dropoff: String) {
        val intent = Intent(requireContext(), DriverMainActivity::class.java).apply {
            putExtra("bookingId", bookingId)
        }

        val pendingIntent = PendingIntent.getActivity(
            requireContext(),
            bookingId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(requireContext(), "BOOKING_CHANNEL")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("New Booking Request")
            .setContentText("Pickup: $pickup → Dropoff: $dropoff")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val manager = requireContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(bookingId.hashCode(), notification)
    }

    private fun listenForNewBookings() {
        bookingListener?.let { database.removeEventListener(it) }

        bookingListener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val bookingId = snapshot.key ?: return
                val alreadyNotified = snapshot.child("notified").getValue(Boolean::class.java) ?: false
                if (alreadyNotified) return

                val pickup = snapshot.child("pickupAddress").getValue(String::class.java) ?: ""
                val dropoff = snapshot.child("dropoffAddress").getValue(String::class.java) ?: ""

                showNewBookingNotification(bookingId, pickup, dropoff)
                database.child(bookingId).child("notified").setValue(true)
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        }

        database.orderByChild("status").equalTo("pending")
            .addChildEventListener(bookingListener as ChildEventListener)
    }
    private fun clearBookingUI() {
        bookingInfoLayout?.visibility = View.GONE
        btnCompleteRide?.visibility = View.GONE
        pickupMarker?.let { map.overlays.remove(it) }
        dropoffMarker?.let { map.overlays.remove(it) }
        routeLine?.let { map.overlays.remove(it) }
        map.invalidate()

        // Clear state
        bookingID = null
        pickupPoint = null
        dropoffPoint = null
        reachedPickup = false
        arguments?.clear()
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        bookingListener?.let { database.removeEventListener(it) }
        bookingListener = null
    }
}