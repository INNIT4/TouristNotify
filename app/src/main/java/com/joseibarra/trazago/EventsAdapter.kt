package com.joseibarra.trazago

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.joseibarra.trazago.databinding.ListItemEventBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class EventsAdapter(
    private val onEventClick: (Event) -> Unit
) : ListAdapter<Event, EventsAdapter.EventViewHolder>(EventDiffCallback()) {

    private val dateFormat = SimpleDateFormat("d 'de' MMMM, yyyy", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val binding = ListItemEventBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return EventViewHolder(binding)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class EventViewHolder(private val binding: ListItemEventBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(event: Event) {
            val context = binding.root.context

            // Hero image
            if (!event.imageUrl.isNullOrBlank()) {
                Glide.with(context)
                    .load(SafeImageUrl.sanitize(event.imageUrl))
                    .placeholder(R.drawable.gradient_event_placeholder)
                    .into(binding.heroImage)
                binding.heroEmoji.isVisible = false
            } else {
                binding.heroImage.setImageResource(R.drawable.gradient_event_placeholder)
                binding.heroEmoji.text = CategoryUtils.getCategoryEmoji(event.category)
                binding.heroEmoji.isVisible = true
            }

            // Badges
            val today = Calendar.getInstance()
            binding.badgeToday.isVisible = false
            binding.badgeThisWeek.isVisible = false
            binding.badgeFeatured.isVisible = event.isFeatured

            event.startDate?.let { start ->
                val startCal = Calendar.getInstance().apply { time = start }
                if (startCal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) &&
                    startCal.get(Calendar.YEAR) == today.get(Calendar.YEAR)) {
                    binding.badgeToday.isVisible = true
                } else {
                    val weekFromNow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 7) }
                    if (start.before(weekFromNow.time) && start.after(today.time)) {
                        binding.badgeThisWeek.isVisible = true
                    }
                }
            }

            // Title
            binding.eventName.text = event.title

            // Category + price combined
            val cat = "${CategoryUtils.getCategoryEmoji(event.category)} ${event.category}"
            val price = event.priceInfo.ifBlank { context.getString(R.string.event_price_free) }
            binding.eventCategoryPrice.text = "$cat · $price"

            // Dates
            val startStr = event.startDate?.let { dateFormat.format(it) } ?: ""
            val endStr = event.endDate?.let { dateFormat.format(it) }
            binding.eventDates.text = if (endStr != null && endStr != startStr) "📅 " + context.getString(R.string.event_date_range, startStr, endStr)
            else "📅 $startStr"

            // Location
            binding.eventLocation.text = "📍 ${event.location}"
            binding.eventLocation.isVisible = event.location.isNotBlank()

            // Description preview
            if (event.description.isNotBlank()) {
                binding.eventDescription.text = event.description
                binding.eventDescription.isVisible = true
            } else {
                binding.eventDescription.isVisible = false
            }

            // Accessibility content description
            val a11y = buildString {
                append(event.title)
                append(". ")
                append(cat)
                append(". ")
                if (event.startDate != null) append("${dateFormat.format(event.startDate)}. ")
                append(event.location)
                append(". ")
                if (event.isFeatured) append("${context.getString(R.string.event_badge_featured)}. ")
                append(context.getString(R.string.item_tap_more))
            }
            binding.root.contentDescription = a11y

            binding.root.setOnClickListener { onEventClick(event) }
        }
    }

    class EventDiffCallback : DiffUtil.ItemCallback<Event>() {
        override fun areItemsTheSame(oldItem: Event, newItem: Event) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Event, newItem: Event) = oldItem == newItem
    }
}
