package com.joseibarra.touristnotify

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.joseibarra.touristnotify.databinding.ListItemFavoritePlaceBinding

class FavoritePlacesAdapter(
    private val favorites: List<Pair<Favorite, TouristSpot>>,
    private val onItemClicked: (TouristSpot) -> Unit,
    private val onRemoveClicked: (Favorite) -> Unit
) : RecyclerView.Adapter<FavoritePlacesAdapter.FavoriteViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FavoriteViewHolder {
        val binding = ListItemFavoritePlaceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FavoriteViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FavoriteViewHolder, position: Int) {
        holder.bind(favorites[position])
    }

    override fun getItemCount() = favorites.size

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
