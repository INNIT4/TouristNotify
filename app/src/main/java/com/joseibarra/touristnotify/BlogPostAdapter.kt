package com.joseibarra.touristnotify

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import java.text.SimpleDateFormat
import java.util.*

/**
 * Adapter para mostrar posts del blog
 */
class BlogPostAdapter(
    private var posts: List<BlogPost>,
    private val onPostClick: (BlogPost) -> Unit,
    private val onLikeClick: (BlogPost) -> Unit
) : RecyclerView.Adapter<BlogPostAdapter.BlogPostViewHolder>() {

    inner class BlogPostViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val card: MaterialCardView = itemView.findViewById(R.id.post_card)
        val titleTextView: TextView = itemView.findViewById(R.id.post_title_text_view)
        val categoryTextView: TextView = itemView.findViewById(R.id.post_category_text_view)
        val contentPreviewTextView: TextView = itemView.findViewById(R.id.post_content_preview_text_view)
        val authorTextView: TextView = itemView.findViewById(R.id.post_author_text_view)
        val dateTextView: TextView = itemView.findViewById(R.id.post_date_text_view)
        val likesTextView: TextView = itemView.findViewById(R.id.post_likes_text_view)
        val viewsTextView: TextView = itemView.findViewById(R.id.post_views_text_view)
        val featuredBadge: TextView = itemView.findViewById(R.id.featured_badge)
        val likeButton: ImageView = itemView.findViewById(R.id.like_button)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BlogPostViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_blog_post, parent, false)
        return BlogPostViewHolder(view)
    }

    override fun onBindViewHolder(holder: BlogPostViewHolder, position: Int) {
        val post = posts[position]

        holder.titleTextView.text = post.title
        holder.categoryTextView.text = getCategoryEmoji(post.category) + " " + post.category

        // Content preview (primeras 150 caracteres)
        holder.contentPreviewTextView.text = if (post.content.length > 150) {
            post.content.substring(0, 150) + "..."
        } else {
            post.content
        }

        holder.authorTextView.text = "Por ${post.authorName}"

        // Format date
        holder.dateTextView.text = if (post.publishedAt != null) {
            formatDate(post.publishedAt)
        } else {
            "Hace poco"
        }

        holder.likesTextView.text = "❤️ ${post.likes}"
        holder.viewsTextView.text = "👁️ ${post.viewCount}"

        // Featured badge
        holder.featuredBadge.visibility = if (post.isFeatured) View.VISIBLE else View.GONE

        // Click listeners
        holder.card.setOnClickListener {
            onPostClick(post)
        }

        holder.likeButton.setOnClickListener {
            onLikeClick(post)
        }
    }

    override fun getItemCount(): Int = posts.size

    fun updatePosts(newPosts: List<BlogPost>) {
        posts = newPosts
        notifyDataSetChanged()
    }

    private fun getCategoryEmoji(category: String): String {
        return when (category) {
            "Consejos" -> "💡"
            "Historia" -> "📜"
            "Gastronomía" -> "🍽️"
            "Cultura" -> "🎭"
            "Naturaleza" -> "🌿"
            "Eventos" -> "🎉"
            else -> "📝"
        }
    }

    private fun formatDate(date: Date): String {
        val now = System.currentTimeMillis()
        val diff = now - date.time

        return when {
            diff < 60000 -> "Hace un momento"
            diff < 3600000 -> "Hace ${diff / 60000} min"
            diff < 86400000 -> "Hace ${diff / 3600000}h"
            diff < 604800000 -> "Hace ${diff / 86400000} días"
            else -> {
                val sdf = SimpleDateFormat("dd MMM yyyy", Locale("es", "MX"))
                sdf.format(date)
            }
        }
    }
}
