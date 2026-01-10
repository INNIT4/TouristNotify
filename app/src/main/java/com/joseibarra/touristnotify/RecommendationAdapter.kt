package com.joseibarra.touristnotify

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.joseibarra.touristnotify.databinding.ListItemRecommendationBinding

/**
 * Adapter para mostrar recomendaciones personalizadas de IA
 */
class RecommendationAdapter(
    private val onRecommendationClick: (AIRecommendation) -> Unit
) : ListAdapter<AIRecommendation, RecommendationAdapter.RecommendationViewHolder>(RecommendationDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecommendationViewHolder {
        val binding = ListItemRecommendationBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return RecommendationViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RecommendationViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class RecommendationViewHolder(
        private val binding: ListItemRecommendationBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(recommendation: AIRecommendation) {
            binding.placeNameTextView.text = recommendation.placeName
            binding.placeCategoryTextView.text = recommendation.placeCategory
            binding.reasonTextView.text = recommendation.reason

            // Score como porcentaje
            val scorePercentage = (recommendation.score * 100).toInt()
            binding.scoreTextView.text = "Match: $scorePercentage%"
            binding.scoreProgressBar.progress = scorePercentage

            // Color del progress bar según score
            binding.scoreProgressBar.progressDrawable.setTint(
                when {
                    recommendation.score >= 0.8 -> 0xFF4CAF50.toInt() // Verde
                    recommendation.score >= 0.6 -> 0xFFFFC107.toInt() // Amarillo
                    else -> 0xFFFF9800.toInt() // Naranja
                }
            )

            // Duración estimada
            binding.durationTextView.text = "⏱️ ${recommendation.estimatedDuration}"

            // Mejor hora para visitar
            binding.bestTimeTextView.text = "🕐 ${recommendation.bestTimeToVisit}"

            // Weather suitable indicator
            if (recommendation.weatherSuitable) {
                binding.weatherIndicatorTextView.text = "☀️ Clima ideal"
                binding.weatherIndicatorTextView.setTextColor(0xFF4CAF50.toInt())
            } else {
                binding.weatherIndicatorTextView.text = "🌧️ Clima no óptimo"
                binding.weatherIndicatorTextView.setTextColor(0xFFFF9800.toInt())
            }

            // Click en la card
            binding.root.setOnClickListener {
                onRecommendationClick(recommendation)
            }
        }
    }

    class RecommendationDiffCallback : DiffUtil.ItemCallback<AIRecommendation>() {
        override fun areItemsTheSame(oldItem: AIRecommendation, newItem: AIRecommendation): Boolean {
            return oldItem.placeId == newItem.placeId
        }

        override fun areContentsTheSame(oldItem: AIRecommendation, newItem: AIRecommendation): Boolean {
            return oldItem == newItem
        }
    }
}
