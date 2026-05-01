package com.example.trigo

import com.google.firebase.Timestamp

data class Booking(
    val bookingID: String? = null,
    val passengerID: String? = null,
    val passengerName: String? = null,
    val pickup: Map<String, Any>? = null,
    val dropoff: Map<String, Any>? = null,
    val fare: Double? = 0.0,
    val status: String? = "pending",
    val timestamp: Long = 0L,
    val arrived: Boolean = true,
    val driverAssigned: String? = null
)
