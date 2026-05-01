package com.example.trigo

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class HistoryAdapter(private val list: MutableList<Booking>) :
    RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

    class HistoryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.historyPassengerName)
        val pickup: TextView = view.findViewById(R.id.historyPickup)
        val dropoff: TextView = view.findViewById(R.id.historyDropoff)
        val fare: TextView = view.findViewById(R.id.historyFare)
        val status: TextView = view.findViewById(R.id.historyStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history, parent, false)
        return HistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {

        val item = list[position]

        holder.name.text = item.passengerName ?: "Unknown"
        holder.pickup.text = "Pickup: ${item.pickup?.get("address") ?: "N/A"}"
        holder.dropoff.text = "Dropoff: ${item.dropoff?.get("address") ?: "N/A"}"
        holder.fare.text = "₱${item.fare ?: 0.0}"
        holder.status.text = item.status ?: "N/A"
    }

    override fun getItemCount(): Int = list.size
}