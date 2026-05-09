package com.joseibarra.touristnotify

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.joseibarra.touristnotify.databinding.ListItemPlaceImportBinding

class PlaceImportAdapter(
    private val places: List<PlaceImportItem>,
    private val onImportClicked: (PlaceImportItem) -> Unit,
    private val onDetailsClicked: (PlaceImportItem) -> Unit,
    private val onEditClicked: ((PlaceImportItem) -> Unit)? = null
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

            val infoText = buildString {
                if (place.phoneNumber.isNotBlank()) append("📞 ")
                if (place.website.isNotBlank()) append("🌐 ")
                if (place.openingHours.isNotBlank()) append("🕐 ")
                if (place.hasPhotos) append("📷 ")
            }
            binding.placeInfoTextView.text = infoText.ifBlank { "Sin información adicional" }

            binding.placeCategoryTextView.text = CategoryUtils.guessCategory(place.types)

            binding.buttonEdit.visibility = if (onEditClicked != null) View.VISIBLE else View.GONE
            binding.buttonEdit.setOnClickListener { onEditClicked?.invoke(place) }

            binding.buttonImport.setOnClickListener { onImportClicked(place) }

            binding.buttonDetails.setOnClickListener { onDetailsClicked(place) }

            binding.root.setOnClickListener { onDetailsClicked(place) }
        }
    }
}
