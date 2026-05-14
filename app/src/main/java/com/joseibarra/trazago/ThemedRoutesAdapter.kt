package com.joseibarra.trazago

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.joseibarra.trazago.databinding.ListItemThemedRouteBinding

class ThemedRoutesAdapter(
    private val onRouteClick: (ThemedRoute) -> Unit
) : ListAdapter<ThemedRoute, ThemedRoutesAdapter.RouteViewHolder>(RouteDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RouteViewHolder {
        val binding = ListItemThemedRouteBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RouteViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RouteViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class RouteViewHolder(private val binding: ListItemThemedRouteBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(route: ThemedRoute) {
            val context = binding.root.context

            // Cover image
            if (route.imageUrl.isNotBlank()) {
                binding.routeCoverImage.isVisible = true
                Glide.with(context)
                    .load(SafeImageUrl.sanitize(route.imageUrl))
                    .placeholder(R.drawable.bg_card_elevated)
                    .error(R.drawable.bg_card_elevated)
                    .into(binding.routeCoverImage)
            } else {
                binding.routeCoverImage.isVisible = false
            }

            binding.routeNameTextView.text = route.name
            binding.routeThemeTextView.text = route.theme
            binding.routeDescriptionTextView.text = route.description
            binding.routeDurationTextView.text = route.estimatedDuration

            // Author
            if (route.authorName.isNotBlank()) {
                binding.routeAuthorTextView.isVisible = true
                binding.routeAuthorTextView.text = context.getString(
                    R.string.themed_route_curated_by, route.authorName
                )
            } else {
                binding.routeAuthorTextView.isVisible = false
            }

            // Featured badge
            binding.featuredBadge.isVisible = route.isFeatured

            // Color strip
            try {
                binding.colorStrip.setBackgroundColor(android.graphics.Color.parseColor(route.color))
            } catch (_: Exception) {
                binding.colorStrip.setBackgroundColor(android.graphics.Color.parseColor("#FF6B35"))
            }

            // Emoji
            binding.routeIconTextView.text = route.icon

            binding.root.setOnClickListener { onRouteClick(route) }
        }
    }

    class RouteDiffCallback : DiffUtil.ItemCallback<ThemedRoute>() {
        override fun areItemsTheSame(oldItem: ThemedRoute, newItem: ThemedRoute) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: ThemedRoute, newItem: ThemedRoute) = oldItem == newItem
    }
}
