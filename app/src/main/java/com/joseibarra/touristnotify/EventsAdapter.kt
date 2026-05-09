package com.joseibarra.touristnotify

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.joseibarra.touristnotify.databinding.ListItemEventBinding
import java.text.SimpleDateFormat
import java.util.*

/**
 * Adapter para mostrar la lista de eventos en un RecyclerView
 */
class EventsAdapter(
    private val onEventClick: (Event) -> Unit
) : ListAdapter<Event, EventsAdapter.EventViewHolder>(EventDiffCallback()) {

    private val dateFormat = SimpleDateFormat("d 'de' MMMM, yyyy", Locale.getDefault())

    class EventDiffCallback : DiffUtil.ItemCallback<Event>() {
        override fun areItemsTheSame(oldItem: Event, newItem: Event): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: Event, newItem: Event): Boolean =
            oldItem == newItem
    }

    inner class EventViewHolder(private val binding: ListItemEventBinding) :
        RecyclerView.ViewHolder(binding.root) {

        // PERF-009: carga única por ViewHolder en lugar de una por bind (evita parsear XML por ítem)
        private val fadeAnimation = AnimationUtils.loadAnimation(binding.root.context, R.anim.fade_in)

        fun bind(event: Event) {
            binding.eventTitleTextView.text = event.title
            binding.eventCategoryTextView.text = CategoryUtils.getCategoryEmoji(event.category) + " " + event.category
            binding.eventLocationTextView.text = "📍 " + event.location
            binding.eventLocationTextView.contentDescription = event.location
            binding.eventDescriptionTextView.text = event.description

            // Formatear fechas
            val dateText = when {
                event.startDate == null -> binding.root.context.getString(R.string.date_to_confirm)
                event.endDate == null || isSameDay(event.startDate, event.endDate!!) -> {
                    dateFormat.format(event.startDate)
                }
                else -> {
                    "${dateFormat.format(event.startDate)} - ${dateFormat.format(event.endDate)}"
                }
            }
            binding.eventDateTextView.text = "📅 $dateText"
            binding.eventDateTextView.contentDescription = dateText

            // Mostrar badge si es destacado
            binding.featuredBadge.visibility = if (event.isFeatured) View.VISIBLE else View.GONE

            // A11Y-007: descrición completa para TalkBack en lugar de leer emojis sueltos
            binding.eventCard.contentDescription = binding.root.context.getString(
                R.string.a11y_event_item,
                event.title,
                event.category,
                event.location,
                dateText
            )

            // Click listener
            binding.eventCard.setOnClickListener {
                onEventClick(event)
            }

            binding.root.startAnimation(fadeAnimation)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val binding = ListItemEventBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return EventViewHolder(binding)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    private fun isSameDay(date1: Date, date2: Date): Boolean {
        val cal1 = Calendar.getInstance().apply { time = date1 }
        val cal2 = Calendar.getInstance().apply { time = date2 }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }
}
