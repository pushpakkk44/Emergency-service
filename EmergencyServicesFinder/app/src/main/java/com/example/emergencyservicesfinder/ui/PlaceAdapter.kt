package com.example.emergencyservicesfinder.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.emergencyservicesfinder.R
import com.example.emergencyservicesfinder.data.model.EmergencyPlace
import com.example.emergencyservicesfinder.databinding.ItemPlaceBinding
import java.util.Locale
import kotlin.math.roundToInt

class PlaceAdapter(
    private val onDirectionsClicked: (EmergencyPlace) -> Unit,
    private val onCallClicked: (EmergencyPlace) -> Unit,
    private val onFavoriteClicked: (EmergencyPlace) -> Unit
) : ListAdapter<EmergencyPlace, PlaceAdapter.PlaceViewHolder>(PlaceDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaceViewHolder {
        val binding = ItemPlaceBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PlaceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PlaceViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class PlaceViewHolder(private val binding: ItemPlaceBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(place: EmergencyPlace) {
            binding.tvPlaceName.text = place.name

            if (!place.address.isNullOrEmpty()) {
                binding.tvPlaceAddress.text = place.address
                binding.tvPlaceAddress.visibility = View.VISIBLE
            } else {
                binding.tvPlaceAddress.visibility = View.GONE
            }

            val distanceMeters = place.distanceMeters
            val distanceText = if (distanceMeters < 1000 && distanceMeters > 0) {
                "${distanceMeters.roundToInt()} m"
            } else {
                val km = if (distanceMeters <= 0) 0.5f else distanceMeters / 1000f
                String.format(Locale.US, "%.1f km", km)
            }
            binding.tvDistance.text = distanceText

            if (place.isFavorite) {
                binding.ivFavoriteIcon.setImageResource(R.drawable.ic_favorite)
                binding.ivFavoriteIcon.setColorFilter(Color.parseColor("#FF4D6D"))
                binding.tvFavoriteText.setTextColor(Color.parseColor("#FF4D6D"))
                binding.tvFavoriteText.text = "Saved"
            } else {
                binding.ivFavoriteIcon.setImageResource(R.drawable.ic_favorite_border)
                binding.ivFavoriteIcon.setColorFilter(Color.parseColor("#94A3B8"))
                binding.tvFavoriteText.setTextColor(Color.parseColor("#94A3B8"))
                binding.tvFavoriteText.text = "Favourite"
            }

            if (!place.phone.isNullOrEmpty()) {
                binding.btnCall.visibility = View.VISIBLE
                binding.btnCall.setOnClickListener { onCallClicked(place) }
            } else {
                binding.btnCall.visibility = View.VISIBLE
                binding.btnCall.setOnClickListener { onCallClicked(place.copy(phone = "108")) }
            }

            binding.btnDirections.setOnClickListener { onDirectionsClicked(place) }
            binding.btnFavorite.setOnClickListener { onFavoriteClicked(place) }
        }
    }

    class PlaceDiffCallback : DiffUtil.ItemCallback<EmergencyPlace>() {
        override fun areItemsTheSame(oldItem: EmergencyPlace, newItem: EmergencyPlace): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: EmergencyPlace, newItem: EmergencyPlace): Boolean {
            return oldItem == newItem
        }
    }
}
