package com.joseibarra.touristnotify

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import java.text.SimpleDateFormat
import java.util.*

/**
 * Adapter para mostrar la lista de eventos en un RecyclerView
 */
class EventsAdapter(
    private val onEventClick: (Event) -> Unit
) : ListAdapter<Event, EventsAdapter.EventViewHolder>(EventDiffCallback()) {

    private val dateFormat = SimpleDateFormat("d 'de' MMMM, yyyy", Locale("es", "MX"))

    inner class EventViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val card: MaterialCardView = itemView.findViewById(R.id.event_card)
        val titleTextView: TextView = itemView.findViewById(R.id.event_title_text_view)
        val categoryTextView: TextView = itemView.findViewById(R.id.event_category_text_view)
        val dateTextView: TextView = itemView.findViewById(R.id.event_date_text_view)
        val locationTextView: TextView = itemView.findViewById(R.id.event_location_text_view)
        val descriptionTextView: TextView = itemView.findViewById(R.id.event_description_text_view)
        val featuredBadge: TextView = itemView.findViewById(R.id.featured_badge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_event, parent, false)
        return EventViewHolder(view)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        val event = getItem(position)

        holder.titleTextView.text = event.title
        holder.categoryTextView.text = CategoryUtils.getCategoryEmoji(event.category) + " " + event.category
        holder.locationTextView.text = "📍 " + event.location
        holder.descriptionTextView.text = event.description

        // Formatear fechas
        val dateText = when {
            event.startDate == null -> "Fecha por confirmar"
            event.endDate == null || isSameDay(event.startDate, event.endDate!!) -> {
                dateFormat.format(event.startDate)
            }
            else -> {
                "${dateFormat.format(event.startDate)} - ${dateFormat.format(event.endDate)}"
            }
        }
        holder.dateTextView.text = "📅 $dateText"

        // Mostrar badge si es destacado
        holder.featuredBadge.visibility = if (event.isFeatured) View.VISIBLE else View.GONE

        // Click listener
        holder.card.setOnClickListener {
            onEventClick(event)
        }
    }

    private fun isSameDay(date1: Date, date2: Date): Boolean {
        val cal1 = Calendar.getInstance().apply { time = date1 }
        val cal2 = Calendar.getInstance().apply { time = date2 }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    class EventDiffCallback : DiffUtil.ItemCallback<Event>() {
        override fun areItemsTheSame(oldItem: Event, newItem: Event): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Event, newItem: Event): Boolean {
            return oldItem == newItem
        }
    }
}
