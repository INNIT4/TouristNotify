package com.joseibarra.touristnotify

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

/**
 * Adapter para mostrar lugares útiles para viajeros de negocios
 */
class BusinessPlacesAdapter(
    private var places: List<TouristSpot>,
    private val onPlaceClick: (TouristSpot) -> Unit
) : RecyclerView.Adapter<BusinessPlacesAdapter.BusinessPlaceViewHolder>() {

    private val businessServices = listOf("WiFi", "Wi-Fi", "Internet", "Zona de trabajo", "Enchufes")

    inner class BusinessPlaceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val card: MaterialCardView = itemView.findViewById(R.id.place_card)
        val nameTextView: TextView = itemView.findViewById(R.id.place_name_text_view)
        val categoryTextView: TextView = itemView.findViewById(R.id.place_category_text_view)
        val ratingTextView: TextView = itemView.findViewById(R.id.place_rating_text_view)
        val addressTextView: TextView = itemView.findViewById(R.id.place_address_text_view)
        val servicesChipGroup: ChipGroup = itemView.findViewById(R.id.services_chip_group)
        val hoursTextView: TextView = itemView.findViewById(R.id.place_hours_text_view)
        val businessBadge: TextView = itemView.findViewById(R.id.business_badge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BusinessPlaceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_business_place, parent, false)
        return BusinessPlaceViewHolder(view)
    }

    override fun onBindViewHolder(holder: BusinessPlaceViewHolder, position: Int) {
        val place = places[position]

        holder.nameTextView.text = place.nombre
        holder.categoryTextView.text = CategoryUtils.getCategoryEmoji(place.categoria) + " " + place.categoria
        holder.ratingTextView.text = if (place.rating > 0) {
            "⭐ ${place.rating} (${place.reviewCount})"
        } else {
            "Sin calificación"
        }

        holder.addressTextView.text = if (place.direccion.isNotEmpty()) {
            "📍 ${place.direccion}"
        } else {
            "📍 Álamos, Sonora"
        }

        holder.hoursTextView.text = if (place.horarios.isNotEmpty()) {
            "🕐 ${place.horarios}"
        } else {
            "🕐 Horarios no disponibles"
        }

        // Mostrar servicios relevantes para negocios
        holder.servicesChipGroup.removeAllViews()
        val relevantServices = place.servicios.filter { service ->
            businessServices.any { service.contains(it, ignoreCase = true) }
        }.take(3) // Máximo 3 chips

        if (relevantServices.isNotEmpty()) {
            holder.servicesChipGroup.visibility = View.VISIBLE
            for (service in relevantServices) {
                val chip = Chip(holder.itemView.context)
                chip.text = service
                chip.isClickable = false
                chip.isCheckable = false
                holder.servicesChipGroup.addView(chip)
            }
        } else {
            holder.servicesChipGroup.visibility = View.GONE
        }

        // Badge si tiene WiFi
        val hasWifi = place.servicios.any {
            it.contains("WiFi", ignoreCase = true) ||
            it.contains("Wi-Fi", ignoreCase = true) ||
            it.contains("Internet", ignoreCase = true)
        }
        holder.businessBadge.visibility = if (hasWifi) View.VISIBLE else View.GONE

        // Click listener
        holder.card.setOnClickListener {
            onPlaceClick(place)
        }
    }

    override fun getItemCount(): Int = places.size

    fun updatePlaces(newPlaces: List<TouristSpot>) {
        places = newPlaces
        notifyDataSetChanged()
    }
}
