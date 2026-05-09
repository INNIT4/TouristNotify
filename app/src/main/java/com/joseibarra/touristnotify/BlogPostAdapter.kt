package com.joseibarra.touristnotify

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.joseibarra.touristnotify.databinding.ListItemBlogPostBinding
import java.text.SimpleDateFormat
import java.util.*

/**
 * Adapter para mostrar posts del blog
 */
class BlogPostAdapter(
    initialPosts: List<BlogPost> = emptyList(),
    private val onPostClick: (BlogPost) -> Unit,
    private val onLikeClick: (BlogPost) -> Unit
) : ListAdapter<BlogPost, BlogPostAdapter.BlogPostViewHolder>(BlogPostDiffCallback()) {

    init {
        submitList(initialPosts)
    }

    inner class BlogPostViewHolder(private val binding: ListItemBlogPostBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(post: BlogPost) {
            binding.postTitleTextView.text = post.title
            binding.postCategoryTextView.text = CategoryUtils.getCategoryEmoji(post.category) + " " + post.category

            binding.postContentPreviewTextView.text = if (post.content.length > 150) {
                post.content.substring(0, 150) + "..."
            } else {
                post.content
            }

            binding.postAuthorTextView.text = "Por ${post.authorName}"

            binding.postDateTextView.text = if (post.publishedAt != null) {
                formatDate(post.publishedAt)
            } else {
                "Hace poco"
            }

            binding.postLikesTextView.text = "❤️ ${post.likes}"
            binding.postLikesTextView.contentDescription = "${post.likes} me gusta"
            binding.postViewsTextView.text = "👁️ ${post.viewCount}"
            binding.postViewsTextView.contentDescription = "${post.viewCount} vistas"

            binding.featuredBadge.visibility = if (post.isFeatured) View.VISIBLE else View.GONE

            binding.postCard.setOnClickListener {
                onPostClick(post)
            }

            binding.likeButton.setOnClickListener {
                onLikeClick(post)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BlogPostViewHolder {
        val binding = ListItemBlogPostBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return BlogPostViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BlogPostViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    fun updatePosts(newPosts: List<BlogPost>) {
        submitList(newPosts)
    }

    private fun formatDate(date: Date): String {
        val now = System.currentTimeMillis()
        val diff = now - date.time

        return when {
            diff < 60000 -> "Hace un momento"
            diff < 3600000 -> "Hace ${diff / 60000} min"
            diff < 86400000 -> "Hace ${diff / 3600000}h"
            diff < 604800000 -> "Hace ${diff / 86400000} días"
            // PERF-011: instancia compartida — SimpleDateFormat es costoso de crear por ítem
            else -> DATE_FORMAT.format(date)
        }
    }

    companion object {
        private val DATE_FORMAT = SimpleDateFormat("dd MMM yyyy", Locale("es", "MX"))
    }

    class BlogPostDiffCallback : DiffUtil.ItemCallback<BlogPost>() {
        override fun areItemsTheSame(oldItem: BlogPost, newItem: BlogPost): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: BlogPost, newItem: BlogPost): Boolean {
            return oldItem == newItem
        }
    }
}
