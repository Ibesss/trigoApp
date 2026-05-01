package com.example.trigo

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class LoginActivity : AppCompatActivity() {

    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var loginButton: Button
    private lateinit var signupLink: TextView
    private lateinit var btnPassenger: Button
    private lateinit var btnDriver: Button

    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase

    private lateinit var forgotPassword: TextView

    private var selectedRole = "Passenger" // default role

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()

        // Initialize views
        emailInput = findViewById(R.id.emailInput)
        passwordInput = findViewById(R.id.passwordInput)
        loginButton = findViewById(R.id.loginButton)
        forgotPassword = findViewById(R.id.forgotPassword)
        signupLink = findViewById(R.id.signupLink)
        btnPassenger = findViewById(R.id.btnPassenger)
        btnDriver = findViewById(R.id.btnDriver)

        // ===== Role toggle logic =====
        fun updateRoleSelection() {
            if (selectedRole == "Passenger") {
                btnPassenger.setBackgroundResource(R.drawable.btn_role_selected)
                btnDriver.setBackgroundResource(R.drawable.btn_role_unselected)
            } else {
                btnDriver.setBackgroundResource(R.drawable.btn_role_selected)
                btnPassenger.setBackgroundResource(R.drawable.btn_role_unselected)
            }
        }

        btnPassenger.setOnClickListener {
            selectedRole = "Passenger"
            updateRoleSelection()
        }

        btnDriver.setOnClickListener {
            selectedRole = "Driver"
            updateRoleSelection()
        }

        // Default selection
        updateRoleSelection()

        forgotPassword.setOnClickListener {

            val email = emailInput.text.toString().trim()

            if (email.isEmpty()) {
                Toast.makeText(this, "Enter your email first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            auth.sendPasswordResetEmail(email)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Toast.makeText(
                            this,
                            "Password reset link sent to your Gmail",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(
                            this,
                            "Error: ${task.exception?.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
        }




        // ===== Login button click =====
        loginButton.setOnClickListener {
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Firebase Authentication login
            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {

                        val userId = auth.currentUser?.uid

                        val user = auth.currentUser

                        user?.reload()

                        if (user != null && user.isEmailVerified) {

                            val userId = user.uid


                            val userRef = database.getReference("accounts")
                                .child(selectedRole.lowercase()) // passenger or driver
                                .child(userId)

                            userRef.get().addOnSuccessListener { snapshot ->
                                if (snapshot.exists()) {
                                    val role = snapshot.child("role").value.toString()

                                    Toast.makeText(this, "Login successful as $role", Toast.LENGTH_SHORT).show()

                                    if (role == "Driver") {
                                        startActivity(Intent(this, DriverMainActivity::class.java))
                                    } else {
                                        startActivity(Intent(this, MainActivity::class.java))
                                    }
                                    finish()
                                } else {
                                    Toast.makeText(this, "Account not found under $selectedRole role", Toast.LENGTH_SHORT).show()
                                }
                            }
                                .addOnFailureListener { e ->
                                    Toast.makeText(this, "Error retrieving user data: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                        } else {
                            Toast.makeText(this, "Email not verified!", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Toast.makeText(this, "Login failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                    }
                }
        }

        // ===== Signup redirect =====
        signupLink.setOnClickListener {
            startActivity(Intent(this, SignupActivity::class.java))
            finish()
        }
    }
}
