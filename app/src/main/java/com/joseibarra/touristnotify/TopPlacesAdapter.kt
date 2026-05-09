package com.joseibarra.touristnotify

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.joseibarra.touristnotify.databinding.ListItemTopPlaceBinding

class TopPlacesAdapter(
    private val onItemClicked: (TouristSpot) -> Unit
) : ListAdapter<TouristSpot, TopPlacesAdapter.TopPlaceViewHolder>(PlaceDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TopPlaceViewHolder {
        val binding = ListItemTopPlaceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TopPlaceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TopPlaceViewHolder, position: Int) {
        holder.bind(getItem(position), position + 1)
    }

    fun updatePlaces(newPlaces: List<TouristSpot>) {
        submitList(newPlaces)
    }

    inner class TopPlaceViewHolder(private val binding: ListItemTopPlaceBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(place: TouristSpot, rank: Int) {
            binding.rankTextView.text = "#$rank"
            binding.placeNameTextView.text = place.nombre
            binding.placeCategoryTextView.text = place.categoria
            binding.visitCountTextView.text = "${place.visitCount} visitas"
            binding.ratingTextView.text = String.format("%.1f ⭐", place.rating)

            val rankColor = when (rank) {
                1 -> binding.root.context.getColor(R.color.md_theme_light_tertiary)
                2 -> binding.root.context.getColor(R.color.md_theme_light_secondary)
                3 -> binding.root.context.getColor(R.color.md_theme_light_primary)
                else -> binding.root.context.getColor(R.color.md_theme_light_onSurfaceVariant)
            }
            binding.rankTextView.setTextColor(rankColor)

            val emoji = when (rank) {
                1 -> "🥇"
                2 -> "🥈"
                3 -> "🥉"
                else -> ""
            }
            binding.medalTextView.text = emoji

            binding.root.setOnClickListener {
                onItemClicked(place)
            }
        }
    }

    class PlaceDiffCallback : DiffUtil.ItemCallback<TouristSpot>() {
        override fun areItemsTheSame(oldItem: TouristSpot, newItem: TouristSpot): Boolean {
            return oldItem.nombre == newItem.nombre
        }

        override fun areContentsTheSame(oldItem: TouristSpot, newItem: TouristSpot): Boolean {
            return oldItem == newItem
        }
    }
}
