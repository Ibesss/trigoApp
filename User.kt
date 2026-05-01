package com.example.trigo  // make sure this matches your package name

// Model class for Firebase Realtime Database
data class User(
    var fullName: String? = null,
    var email: String? = null,
    var role: String? = null

)