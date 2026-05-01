package com.example.trigo

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class DriverHistoryActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var tvEarningsTotal: TextView

    private lateinit var adapter: DriverBookingAdapter
    private val bookingList = ArrayList<Booking>()



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        recyclerView = findViewById(R.id.recyclerHistory)
        tvEarningsTotal = findViewById(R.id.tvEarningsTotal)

        adapter = DriverBookingAdapter(bookingList)

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        loadCompletedBookings()
    }

    private fun loadCompletedBookings() {

        val driverId = FirebaseAuth.getInstance().currentUser!!.uid

        val ref = FirebaseDatabase.getInstance()
            .getReference("bookings")

        ref.orderByChild("driverID").equalTo(driverId)
            .addValueEventListener(object : ValueEventListener {

                override fun onDataChange(snapshot: DataSnapshot) {

                    bookingList.clear()

                    var totalEarnings = 0.0

                    for (snap in snapshot.children) {

                        val booking = snap.getValue(Booking::class.java)

                        if (booking != null && booking.status == "completed") {

                            bookingList.add(booking)

                            totalEarnings += booking.fare?.toDouble()?:0.0
                        }
                    }

                    adapter.notifyDataSetChanged()

                    tvEarningsTotal.text =
                        "Total Earnings: ₱${"%.2f".format(totalEarnings)}"
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }
}