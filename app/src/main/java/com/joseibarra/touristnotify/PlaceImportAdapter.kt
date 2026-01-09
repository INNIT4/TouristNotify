package com.joseibarra.touristnotify

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.joseibarra.touristnotify.databinding.ListItemPlaceImportBinding

class PlaceImportAdapter(
    private val places: List<PlaceImportItem>,
    private val onImportClicked: (PlaceImportItem) -> Unit,
    private val onDetailsClicked: (PlaceImportItem) -> Unit
) : RecyclerView.Adapter<PlaceImportAdapter.PlaceImportViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaceImportViewHolder {
        val binding = ListItemPlaceImportBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PlaceImportViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PlaceImportViewHolder, position: Int) {
        holder.bind(places[position])
    }

    override fun getItemCount() = places.size

    inner class PlaceImportViewHolder(private val binding: ListItemPlaceImportBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(place: PlaceImportItem) {
            binding.placeNameTextView.text = place.name
            binding.placeAddressTextView.text = place.address
            binding.placeRatingTextView.text = String.format("%.1f ⭐ (%d reseñas)", place.rating, place.userRatingsTotal)

            // Mostrar información adicional
            val infoText = buildString {
                if (place.phoneNumber.isNotBlank()) append("📞 ")
                if (place.website.isNotBlank()) append("🌐 ")
                if (place.openingHours.isNotBlank()) append("🕐 ")
                if (place.hasPhotos) append("📷 ")
            }
            binding.placeInfoTextView.text = infoText.ifBlank { "Sin información adicional" }

            // Categoría sugerida
            binding.placeCategoryTextView.text = guessCategory(place.types)

            binding.buttonImport.setOnClickListener {
                onImportClicked(place)
            }

            binding.buttonDetails.setOnClickListener {
                onDetailsClicked(place)
            }

            binding.root.setOnClickListener {
                onDetailsClicked(place)
            }
        }

        private fun guessCategory(types: List<String>): String {
            return when {
                types.contains("museum") -> "Museo"
                types.contains("restaurant") || types.contains("food") -> "Restaurante"
                types.contains("lodging") || types.contains("hotel") -> "Hotel"
                types.contains("church") || types.contains("place_of_worship") -> "Iglesia"
                types.contains("park") -> "Parque"
                types.contains("store") || types.contains("shopping_mall") -> "Tienda"
                types.contains("tourist_attraction") -> "Atracción Turística"
                else -> "Otro"
            }
        }
    }
}
