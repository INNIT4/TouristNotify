package com.joseibarra.touristnotify

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

/**
 * Adapter para mostrar rutas temáticas en un RecyclerView
 */
class ThemedRoutesAdapter(
    private val routes: List<ThemedRoute>,
    private val onRouteClick: (ThemedRoute) -> Unit
) : RecyclerView.Adapter<ThemedRoutesAdapter.RouteViewHolder>() {

    inner class RouteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val card: MaterialCardView = itemView.findViewById(R.id.route_card)
        val iconTextView: TextView = itemView.findViewById(R.id.route_icon_text_view)
        val nameTextView: TextView = itemView.findViewById(R.id.route_name_text_view)
        val themeTextView: TextView = itemView.findViewById(R.id.route_theme_text_view)
        val descriptionTextView: TextView = itemView.findViewById(R.id.route_description_text_view)
        val durationTextView: TextView = itemView.findViewById(R.id.route_duration_text_view)
        val difficultyTextView: TextView = itemView.findViewById(R.id.route_difficulty_text_view)
        val featuredBadge: TextView = itemView.findViewById(R.id.featured_badge)
        val colorStrip: View = itemView.findViewById(R.id.color_strip)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RouteViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_themed_route, parent, false)
        return RouteViewHolder(view)
    }

    override fun onBindViewHolder(holder: RouteViewHolder, position: Int) {
        val route = routes[position]

        holder.iconTextView.text = route.icon
        holder.nameTextView.text = route.name
        holder.themeTextView.text = route.theme
        holder.descriptionTextView.text = route.description
        holder.durationTextView.text = "⏱️ ${route.estimatedDuration}"
        holder.difficultyTextView.text = getDifficultyEmoji(route.difficulty) + " " + route.difficulty

        // Aplicar color temático
        try {
            holder.colorStrip.setBackgroundColor(Color.parseColor(route.color))
        } catch (e: Exception) {
            holder.colorStrip.setBackgroundColor(Color.parseColor("#FF6B35"))
        }

        // Mostrar badge si es destacada
        holder.featuredBadge.visibility = if (route.isFeatured) View.VISIBLE else View.GONE

        // Click listener
        holder.card.setOnClickListener {
            onRouteClick(route)
        }
    }

    override fun getItemCount(): Int = routes.size

    private fun getDifficultyEmoji(difficulty: String): String {
        return when (difficulty.lowercase()) {
            "fácil", "facil" -> "🟢"
            "moderado", "media" -> "🟡"
            "difícil", "dificil", "difícil" -> "🔴"
            else -> "⚪"
        }
    }
}
