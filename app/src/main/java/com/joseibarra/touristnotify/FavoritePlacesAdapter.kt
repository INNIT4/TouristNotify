package com.joseibarra.touristnotify

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.joseibarra.touristnotify.databinding.ListItemFavoritePlaceBinding

class FavoritePlacesAdapter(
    private val onItemClicked: (TouristSpot) -> Unit,
    private val onRemoveClicked: (Favorite) -> Unit
) : ListAdapter<Pair<Favorite, TouristSpot>, FavoritePlacesAdapter.FavoriteViewHolder>(FavoriteDiffCallback()) {

    class FavoriteDiffCallback : DiffUtil.ItemCallback<Pair<Favorite, TouristSpot>>() {
        override fun areItemsTheSame(
            oldItem: Pair<Favorite, TouristSpot>,
            newItem: Pair<Favorite, TouristSpot>
        ): Boolean = oldItem.first.id == newItem.first.id

        override fun areContentsTheSame(
            oldItem: Pair<Favorite, TouristSpot>,
            newItem: Pair<Favorite, TouristSpot>
        ): Boolean = oldItem == newItem
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FavoriteViewHolder {
        val binding = ListItemFavoritePlaceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FavoriteViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FavoriteViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class FavoriteViewHolder(private val binding: ListItemFavoritePlaceBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: Pair<Favorite, TouristSpot>) {
            val (favorite, place) = item

            binding.placeNameTextView.text = place.nombre
            binding.placeCategoryTextView.text = place.categoria
            binding.placeRatingTextView.text = String.format("%.1f ⭐", place.rating)

            val infoText = buildString {
                if (place.telefono.isNotBlank()) append("📞 ")
                if (place.sitioWeb.isNotBlank()) append("🌐 ")
                if (place.horarios.isNotBlank()) append("🕐 ")
            }
            binding.placeInfoTextView.text = infoText.ifBlank { place.direccion }

            binding.root.setOnClickListener {
                onItemClicked(place)
            }

            binding.buttonRemove.setOnClickListener {
                onRemoveClicked(favorite)
            }
        }
    }
}
