package com.example.trigo

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

private val handler = Handler(Looper.getMainLooper())

class DriverBookingFragment : Fragment() {

    private lateinit var recyclerBookings: RecyclerView
    private lateinit var emptyText: TextView

    private lateinit var database: DatabaseReference
    private lateinit var bookingList: MutableList<Booking>
    private lateinit var adapter: DriverBookingAdapter

    private var driverAvailable = false
    private val auth = FirebaseAuth.getInstance()



    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_driver_booking, container, false)


        recyclerBookings = view.findViewById(R.id.recyclerBookings)
        recyclerBookings.layoutManager = LinearLayoutManager(requireContext())

        // Optional: TextView to show "Availability off" or "No bookings"
        emptyText = TextView(requireContext())
        emptyText.text = "Availability off, no booking shown"
        emptyText.textSize = 16f

        bookingList = mutableListOf()
        adapter = DriverBookingAdapter(bookingList)
        recyclerBookings.adapter = adapter
        startAutoExpireChecker()

        checkDriverAvailabilityAndListen()



        return view




        
    }







    private fun checkDriverAvailabilityAndListen() {
        val driverId = auth.currentUser?.uid ?: return
        val driverRef = FirebaseDatabase.getInstance().getReference("drivers/$driverId/available")

        driverRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                driverAvailable = snapshot.getValue(Boolean::class.java) ?: false

                if (driverAvailable) {
                    listenForBookings()
                } else {
                    bookingList.clear()
                    adapter.notifyDataSetChanged()
                    emptyText.text = "Availability off, no booking shown"
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun listenForBookings() {
        val currentDriverId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        database = FirebaseDatabase.getInstance().getReference("bookings")

        database.addValueEventListener(object : ValueEventListener {

            override fun onDataChange(snapshot: DataSnapshot) {

                bookingList.clear()

                for (bookingSnap in snapshot.children) {

                    val map = bookingSnap.value as? Map<*, *> ?: continue

                    val name = map["passengerName"] as? String ?: continue
                    val pickupMap = map["pickup"] as? Map<String, Any> ?: continue
                    val dropoffMap = map["dropoff"] as? Map<String, Any> ?: continue

                    val fareValue = when (val f = map["fare"]) {
                        is Double -> f
                        is Long -> f.toDouble()
                        is String -> f.toDoubleOrNull() ?: 0.0
                        else -> 0.0
                    }

                    val status = map["status"] as? String ?: "pending"
                    val driverID = map["driverID"] as? String ?: ""
                    val rejectedBy = map["rejectedBy"] as? Map<*, *> ?: emptyMap<String, Boolean>()

                    val timestamp = when (val t = map["timestamp"]) {
                        is Long -> t
                        is Double -> t.toLong()
                        is String -> t.toLongOrNull() ?: 0L
                        else -> 0L
                    }

                    val currentTime = System.currentTimeMillis()
                    val isExpired = (currentTime - timestamp) > 1* 60 * 1000 // 5 minutes

                    // 🚫 AUTO EXPIRE
                    if (status == "pending" && isExpired) {
                        bookingSnap.ref.child("status").setValue("expired")




                        val passengerId = map["passengerID"] as? String
                        if (passengerId != null) {
                            sendExpirationNotification(

                                passengerId,
                                "Booking Expired",
                                "A booking was not accepted in time."
                            )
                        }

                        bookingList.removeAll{
                            it.bookingID == bookingSnap.key
                        }
                        adapter.notifyDataSetChanged()


                        continue
                    }

                    // SHOW ONLY VALID BOOKINGS
                    if (
                        status == "pending" &&
                        !isExpired &&
                        driverID.isEmpty() &&
                        !rejectedBy.containsKey(currentDriverId)
                    ) {

                        val booking = Booking(
                            bookingID = bookingSnap.key,
                            passengerID = map["passengerID"] as? String,
                            passengerName = name,
                            pickup = pickupMap as Map<String, String>?,
                            dropoff = dropoffMap as Map<String, String>?,
                            fare = fareValue,
                            status = status,
                            timestamp = timestamp
                        )

                        bookingList.add(booking)
                    }
                }

                emptyText.text = if (bookingList.isEmpty()) {
                    "No bookings yet"
                } else {
                    ""
                }

                adapter.notifyDataSetChanged()
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun sendExpirationNotification(title: String, message: String, string: String) {


        val context = context ?: return
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channelId = "booking_expiration_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Booking Expiration",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(requireContext(), channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun startAutoExpireChecker() {

        handler.postDelayed(object : Runnable {
            override fun run() {

                if (!isAdded) return

                val currentTime = System.currentTimeMillis()

                val expiredIds = mutableListOf<String>()

                for (booking in bookingList) {
                    val isExpired =
                        (currentTime - booking.timestamp) > 1 * 60 * 1000L

                    if (isExpired && booking.status == "pending") {
                        booking.bookingID?.let { expiredIds.add(it) }
                    }
                }

                // ✅ Remove from UI FIRST
                if (expiredIds.isNotEmpty()) {

                    bookingList.removeAll { it.bookingID in expiredIds }
                    adapter.notifyDataSetChanged()

                    // ✅ Then update Firebase
                    for (id in expiredIds) {
                        database.child(id).child("status").setValue("expired")
                    }

                    // ✅ Notify once
                    sendExpirationNotification(
                        "Booking Expired",
                        "Some bookings were not accepted in time.",
                        "A booking was not accepted in time."
                    )
                }

                handler.postDelayed(this, 3000) // faster check (3 sec)
            }
        }, 3000)
    }


}