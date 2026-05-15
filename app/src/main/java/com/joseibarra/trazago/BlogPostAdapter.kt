package com.joseibarra.trazago

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.joseibarra.trazago.databinding.ListItemBlogPostBinding
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

            val context = binding.root.context
            binding.postContentPreviewTextView.text = if (post.content.length > 150) {
                post.content.substring(0, 150) + context.getString(R.string.ellipsis)
            } else {
                post.content
            }

            binding.postAuthorTextView.text = context.getString(R.string.blog_by_author_format, post.authorName)

            binding.postDateTextView.text = if (post.publishedAt != null) {
                formatDate(context, post.publishedAt)
            } else {
                context.getString(R.string.blog_time_just_now)
            }

            binding.postLikesTextView.text = context.getString(R.string.blog_likes_format, post.likes)
            binding.postLikesTextView.contentDescription = context.getString(R.string.blog_likes_a11y, post.likes)
            binding.postViewsTextView.text = context.getString(R.string.blog_views_format, post.viewCount)
            binding.postViewsTextView.contentDescription = context.getString(R.string.blog_views_a11y, post.viewCount)

            binding.featuredBadge.visibility = if (post.isFeatured) View.VISIBLE else View.GONE

            val safeUrl = SafeImageUrl.sanitize(post.imageUrl)
            if (safeUrl != null) {
                binding.postCoverImage.visibility = View.VISIBLE
                Glide.with(itemView)
                    .load(safeUrl)
                    .centerCrop()
                    .into(binding.postCoverImage)
            } else {
                binding.postCoverImage.visibility = View.GONE
            }

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

    private fun formatDate(context: android.content.Context, date: Date): String {
        val now = System.currentTimeMillis()
        val diff = now - date.time

        return when {
            diff < 60000 -> context.getString(R.string.blog_time_just_now)
            diff < 3600000 -> context.getString(R.string.blog_time_minutes, diff / 60000)
            diff < 86400000 -> context.getString(R.string.blog_time_hours, diff / 3600000)
            diff < 604800000 -> context.getString(R.string.blog_time_days, diff / 86400000)
            else -> DATE_FORMAT.format(date)
        }
    }

    companion object {
        private val DATE_FORMAT = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
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
