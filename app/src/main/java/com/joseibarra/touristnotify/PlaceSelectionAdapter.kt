package com.joseibarra.touristnotify

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

/**
 * Adapter para mostrar lugares con checkboxes para selección múltiple
 */
class PlaceSelectionAdapter(
    private val places: List<TouristSpot>,
    private val selectedPlaces: MutableList<TouristSpot>,
    private val maxSelection: Int,
    private val onSelectionChanged: (TouristSpot, Boolean) -> Unit
) : RecyclerView.Adapter<PlaceSelectionAdapter.PlaceViewHolder>() {

    inner class PlaceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val card: MaterialCardView = itemView.findViewById(R.id.place_card)
        val checkbox: CheckBox = itemView.findViewById(R.id.place_checkbox)
        val nameTextView: TextView = itemView.findViewById(R.id.place_name_text_view)
        val categoryTextView: TextView = itemView.findViewById(R.id.place_category_text_view)
        val ratingTextView: TextView = itemView.findViewById(R.id.place_rating_text_view)
        val reviewsTextView: TextView = itemView.findViewById(R.id.place_reviews_text_view)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_place_selection, parent, false)
        return PlaceViewHolder(view)
    }

    override fun onBindViewHolder(holder: PlaceViewHolder, position: Int) {
        val place = places[position]

        holder.nameTextView.text = place.nombre
        holder.categoryTextView.text = CategoryUtils.getCategoryEmoji(place.categoria) + " " + place.categoria
        holder.ratingTextView.text = String.format("%.1f ⭐", place.rating)
        holder.reviewsTextView.text = "(${place.reviewCount} reseñas)"

        // Estado del checkbox
        holder.checkbox.isChecked = selectedPlaces.contains(place)

        // Deshabilitar si se alcanzó el máximo y no está seleccionado
        val isMaxReached = selectedPlaces.size >= maxSelection
        holder.checkbox.isEnabled = !isMaxReached || selectedPlaces.contains(place)

        // Click en el card o checkbox
        val clickListener = View.OnClickListener {
            if (holder.checkbox.isEnabled) {
                val newState = !holder.checkbox.isChecked
                holder.checkbox.isChecked = newState
                onSelectionChanged(place, newState)
                notifyDataSetChanged() // Actualizar todos para deshabilitar/habilitar
            } else {
                NotificationHelper.warning(
                    holder.itemView,
                    "Máximo $maxSelection lugares para comparar"
                )
            }
        }

        holder.card.setOnClickListener(clickListener)
        holder.checkbox.setOnClickListener(clickListener)
    }

    override fun getItemCount(): Int = places.size
}
