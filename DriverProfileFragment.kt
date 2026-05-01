package com.example.trigo

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class DriverProfileFragment : Fragment() {

    private lateinit var toggleAvailability: SwitchCompat
    private lateinit var btnLogout: Button

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_driver_profile, container, false)

        toggleAvailability = view.findViewById(R.id.toggleAvailability)
        btnLogout = view.findViewById(R.id.btnLogout)

        // 🔹 Load current availability from Firebase
        val driverId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        val driverRef = FirebaseDatabase.getInstance().getReference("drivers").child(driverId)

        driverRef.child("available").get().addOnSuccessListener { snapshot ->
            val isAvailable = snapshot.getValue(Boolean::class.java) ?: false
            toggleAvailability.isChecked = isAvailable
        }.addOnFailureListener {
            toggleAvailability.isChecked = false
        }

        // 🔹 Update availability in Firebase when toggled
        toggleAvailability.setOnCheckedChangeListener { _, isChecked ->
            driverRef.child("available").setValue(isChecked)
        }

        // 🔹 Logout
        btnLogout.setOnClickListener {
            startActivity(Intent(requireContext(), LoginActivity::class.java))
            requireActivity().finish()
        }

        return view
    }
}