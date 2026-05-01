package com.example.trigo

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

import com.google.firebase.auth.FirebaseUser

class SignupActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase

    private lateinit var btnPassenger: Button
    private lateinit var btnDriver: Button
    private lateinit var btnSignUp: Button
    private lateinit var etFullName: EditText
    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var etConfirmPassword: EditText
    private lateinit var tvLoginRedirect: TextView

private var verifyDialog: BottomSheetDialog? = null
    private var currentuser: FirebaseUser? = null

    private var selectedRole = "Passenger"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        // ====== INIT FIREBASE ======
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()

        // ====== INIT VIEWS ======
        btnPassenger = findViewById(R.id.btnPassenger)
        btnDriver = findViewById(R.id.btnDriver)
        btnSignUp = findViewById(R.id.btnSignUp)
        etFullName = findViewById(R.id.etFullName)
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        etConfirmPassword = findViewById(R.id.etConfirmPassword)
        tvLoginRedirect = findViewById(R.id.tvLoginRedirect)

        // ====== REDIRECT TO LOGIN ======
        tvLoginRedirect.setOnClickListener {

            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)

        }


        // ====== ROLE SELECTION ======
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

        updateRoleSelection()

        // ====== SIGNUP BUTTON ======
        btnSignUp.setOnClickListener {
            val fullName = etFullName.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString()
            val confirmPassword = etConfirmPassword.text.toString()

            if (fullName.isEmpty() || email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password != confirmPassword) {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {

                        val user = auth.currentUser
                        val userId = user?.uid ?: return@addOnCompleteListener

                        val userMap = mapOf(
                            "fullName" to fullName,
                            "email" to email,
                            "role" to selectedRole
                        )

                        // ✅ SAVE ONCE ONLY
                        val userRef = database.getReference("accounts")
                            .child(selectedRole.lowercase())
                            .child(userId)

                        userRef.setValue(userMap).addOnCompleteListener { dbTask ->
                            if (dbTask.isSuccessful) {

                                // ✅ SEND EMAIL VERIFICATION
                                user.sendEmailVerification()
                                    .addOnCompleteListener { verifyTask ->
                                        if (verifyTask.isSuccessful) {

                                            showVerifyBottomSheet(user)

                                        } else {
                                            Toast.makeText(
                                                this,
                                                "Failed to send verification email",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }

                                    }

                                } else {
                                Toast.makeText(this, "Failed to save user data", Toast.LENGTH_SHORT)
                                    .show()
                            }
                        }

                    } else {
                        Toast.makeText(
                            this,
                            "Signup failed: ${task.exception?.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }


        }
    }

    private fun showVerifyBottomSheet(user: FirebaseUser) {

        val dialog = BottomSheetDialog(this,R.style.RoundedBottomSheetDialog)
        val view = layoutInflater.inflate(R.layout.bottomsheet_verify, null)

        val btnOpen = view.findViewById<Button>(R.id.btnOpenGmail)
        val btnResend = view.findViewById<Button>(R.id.btnResend)

        btnOpen.setOnClickListener {
            val intent = Intent(Intent.ACTION_MAIN)
            intent.addCategory(Intent.CATEGORY_APP_EMAIL)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
        }

        btnResend.setOnClickListener {
            user.sendEmailVerification()
            Toast.makeText(this, "Email resent", Toast.LENGTH_SHORT).show()
        }

        dialog.setContentView(view)
        dialog.setCancelable(false)
        dialog.show()

        startAutoCheckVerification(user, dialog)
    }
    private fun startAutoCheckVerification(
        user: FirebaseUser,
        dialog: BottomSheetDialog
    ) {

        val handler = android.os.Handler(mainLooper)

        val runnable = object : Runnable {
            override fun run() {

                user.reload().addOnCompleteListener {

                    if (user.isEmailVerified) {

                        Toast.makeText(this@SignupActivity, "Email verified!", Toast.LENGTH_SHORT).show()

                        dialog.dismiss()

                        auth.signOut()

                        val intent = Intent(this@SignupActivity, LoginActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)

                    } else {
                        handler.postDelayed(this, 3000)
                    }
                }
            }
        }

        handler.post(runnable)
    }


}