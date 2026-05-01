package com.example.trigo

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.app.NotificationCompat
import android.content.Context


class DriverBookingAdapter(private val bookings: List<Booking>) :
    RecyclerView.Adapter<DriverBookingAdapter.BookingViewHolder>() {

    class BookingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val passengerName: TextView = itemView.findViewById(R.id.textPassengerName)
        val pickupAddress: TextView = itemView.findViewById(R.id.textPickup)
        val dropoffAddress: TextView = itemView.findViewById(R.id.textDropoff)
        val fare: TextView = itemView.findViewById(R.id.textFare)
        val status: TextView = itemView.findViewById(R.id.textStatus)
        val btnAccept: Button = itemView.findViewById(R.id.btnAccept)
        val btnReject: Button = itemView.findViewById(R.id.btnReject)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookingViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_driver_booking, parent, false)
        return BookingViewHolder(view)
    }

    override fun onBindViewHolder(holder: BookingViewHolder, position: Int) {
        val booking = bookings[position]

        holder.passengerName.text = booking.passengerName ?: "Unknown"
        holder.pickupAddress.text = "Pickup: ${booking.pickup?.get("address") ?: "No pickup"}"
        holder.dropoffAddress.text = "Dropoff: ${booking.dropoff?.get("address") ?: "No dropoff"}"
        holder.fare.text = "₱${String.format("%.2f", booking.fare ?: 0.0)}"
        holder.status.text = booking.status ?: "Pending"

        val bookingRef = FirebaseDatabase.getInstance()
            .getReference("bookings")
            .child(booking.bookingID ?: return)

        holder.btnAccept.setOnClickListener {
            val driverId = FirebaseAuth.getInstance().currentUser?.uid ?: return@setOnClickListener
            val bookingId = booking.bookingID ?: return@setOnClickListener
            val bookingRef = FirebaseDatabase.getInstance()
                .getReference("bookings")
                .child(bookingId)

            bookingRef.runTransaction(object : Transaction.Handler {
                override fun doTransaction(currentData: MutableData): Transaction.Result {
                    // Get existing value (can be null or a Map)
                    val currentValue = currentData.value
                    // Build a mutable map copy we can safely modify
                    val newMap = mutableMapOf<String, Any?>()

                    if (currentValue is Map<*, *>) {
                        for ((k, v) in currentValue) {
                            if (k is String) newMap[k] = v
                        }
                    }

                    // Check status (default to "pending")
                    val status = (newMap["status"] as? String) ?: "pending"

                    // Only allow accept when still pending
                    if (status == "pending") {
                        newMap["status"] = "accepted"
                        newMap["driverID"] = driverId
                        // commit back
                        currentData.value = newMap
                    }
                    // if not pending, do nothing (other driver already took it)
                    return Transaction.success(currentData)
                }

                override fun onComplete(
                    error: DatabaseError?,
                    committed: Boolean,
                    snapshot: DataSnapshot?
                ) {
                    if (error != null) {
                        Toast.makeText(holder.itemView.context, "Failed to accept: ${error.message}", Toast.LENGTH_SHORT).show()
                        return
                    }
                    if (!committed) {
                        Toast.makeText(holder.itemView.context, "Booking already taken.", Toast.LENGTH_SHORT).show()
                        return
                    }


                    // transaction committed succesfully
                    Toast.makeText(holder.itemView.context, "Booking Accepted", Toast.LENGTH_SHORT).show()
                    showBookingNotification(holder.itemView.context,"Booking Accepted", "Your Booking has been Accepted . Driver is on the way!")

                    // Launch driver UI to tracking (defensive extraction)
                    val context = holder.itemView.context
                    if (context is AppCompatActivity) {
                        val intent = Intent(context, DriverMainActivity::class.java)
                        intent.putExtra("showTracking", true)
                        intent.putExtra("pickupAddress", booking.pickup?.get("address")?.toString())
                        intent.putExtra("dropoffAddress", booking.dropoff?.get("address")?.toString())
                        intent.putExtra("fare", booking.fare ?: 0.0)
                        intent.putExtra("passengerName", booking.passengerName)

                        fun getDouble(map: Map<String, Any>?, vararg keys: String): Double {
                            map ?: return 0.0
                            for (k in keys) {
                                val v = map[k] ?: continue
                                when (v) {
                                    is Number -> return v.toDouble()
                                    is String -> return v.toDoubleOrNull() ?: 0.0
                                }
                            }
                            return 0.0
                        }

                        val pickupLat = getDouble(booking.pickup as? Map<String, Any>, "latitude", "lat")
                        val pickupLng = getDouble(booking.pickup as? Map<String, Any>, "longitude", "lng", "lon")
                        val dropoffLat = getDouble(booking.dropoff as? Map<String, Any>, "latitude", "lat")
                        val dropoffLng = getDouble(booking.dropoff as? Map<String, Any>, "longitude", "lng", "lon")

                        intent.putExtra("pickupLat", pickupLat)
                        intent.putExtra("pickupLng", pickupLng)
                        intent.putExtra("dropoffLat", dropoffLat)
                        intent.putExtra("dropoffLng", dropoffLng)

                        context.startActivity(intent)
                    }
                }
            })
        }
        holder.btnReject.setOnClickListener {
            val currentDriverId = FirebaseAuth.getInstance().currentUser?.uid ?: return@setOnClickListener
            val bookingId = booking.bookingID ?: return@setOnClickListener

            val bookingRef = FirebaseDatabase.getInstance()
                .getReference("bookings")
                .child(bookingId)

            // Mark this driver as one who rejected the booking
            bookingRef.child("rejectedBy").child(currentDriverId).setValue(true)
                .addOnSuccessListener {
                    Toast.makeText(holder.itemView.context, "Booking Rejected", Toast.LENGTH_SHORT).show()

                    // Safely remove from adapter list
                    val safePosition = holder.adapterPosition
                    if (safePosition != RecyclerView.NO_POSITION && safePosition < bookings.size) {
                        if (bookings is MutableList<*>) {
                            (bookings as MutableList<Booking>).removeAt(safePosition)
                            notifyItemRemoved(safePosition)
                        } else {
                            notifyDataSetChanged()
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(holder.itemView.context, "Failed to reject booking: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }



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
            .setSmallIcon(android.R.drawable.ic_dialog_info) // ✅ built-in icon
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }








        override fun getItemCount(): Int = bookings.size
}
