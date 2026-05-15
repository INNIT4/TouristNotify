package com.joseibarra.trazago

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.joseibarra.trazago.databinding.ItemCommunityPostBinding

data class CommunityPostCallbacks(
    val onPostClick: (CommunityPost) -> Unit,
    val onLikeClick: (CommunityPost) -> Unit,
    val onCommentClick: (CommunityPost) -> Unit,
    val onPlaceClick: (String) -> Unit,
    val onEditClick: (CommunityPost) -> Unit,
    val onDeleteClick: (CommunityPost) -> Unit,
    val onReportClick: (CommunityPost) -> Unit,
)

class CommunityPostAdapter(
    private val currentUserId: String?,
    private val isAdmin: Boolean,
    private val callbacks: CommunityPostCallbacks,
) : ListAdapter<CommunityPost, CommunityPostAdapter.PostViewHolder>(DiffCallback()) {

    private val likedPostIds = mutableSetOf<String>()

    fun setLikedPostIds(ids: Set<String>) {
        likedPostIds.clear()
        likedPostIds.addAll(ids)
        notifyItemRangeChanged(0, itemCount)
    }

    fun markLiked(postId: String, liked: Boolean) {
        if (liked) likedPostIds.add(postId) else likedPostIds.remove(postId)
        val index = currentList.indexOfFirst { it.id == postId }
        if (index >= 0) notifyItemChanged(index)
    }

    inner class PostViewHolder(private val b: ItemCommunityPostBinding) :
        RecyclerView.ViewHolder(b.root) {

        fun bind(post: CommunityPost) {
            bindHeader(post)
            bindPhotos(post)
            bindFooter(post)
        }

        private fun bindHeader(post: CommunityPost) {
            val ctx = b.root.context
            val safeAvatar = if (post.authorPhotoUrl.isNotBlank())
                SafeImageUrl.sanitize(post.authorPhotoUrl) else null
            Glide.with(ctx).load(safeAvatar)
                .circleCrop()
                .placeholder(R.drawable.bg_profile_avatar)
                .error(R.drawable.bg_profile_avatar)
                .into(b.authorAvatar)

            b.authorName.text = post.authorName
            b.postTimestamp.text = if (post.createdAt != null) {
                DateUtils.getRelativeTimeSpanString(
                    post.createdAt.time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS
                )
            } else ""
            b.postTitle.text = post.title
            b.postContent.text = post.content

            b.postCard.setOnClickListener { callbacks.onPostClick(post) }
            b.postCard.contentDescription = ctx.getString(
                R.string.community_post_a11y, post.authorName, b.postTimestamp.text, post.title
            )
        }

        private fun bindPhotos(post: CommunityPost) {
            val ctx = b.root.context
            val photos = post.photoUrls.filter { it.isNotBlank() }
            when {
                photos.isEmpty() -> b.photosContainer.visibility = View.GONE
                photos.size == 1 -> {
                    b.photosContainer.visibility = View.VISIBLE
                    b.photoSingle.visibility = View.VISIBLE
                    b.photoGrid.visibility = View.GONE
                    Glide.with(ctx).load(SafeImageUrl.sanitize(photos[0]))
                        .centerCrop().into(b.photoSingle)
                }
                else -> bindPhotoGrid(ctx, photos)
            }
        }

        private fun bindPhotoGrid(ctx: android.content.Context, photos: List<String>) {
            b.photosContainer.visibility = View.VISIBLE
            b.photoSingle.visibility = View.GONE
            b.photoGrid.visibility = View.VISIBLE
            val gridViews = listOf(b.photoGrid1, b.photoGrid2, b.photoGrid3)
            gridViews.forEachIndexed { i, iv ->
                if (i < photos.size) {
                    iv.visibility = View.VISIBLE
                    Glide.with(ctx).load(SafeImageUrl.sanitize(photos[i])).centerCrop().into(iv)
                } else {
                    iv.visibility = View.GONE
                }
            }
            if (photos.size >= 4) {
                b.photoGrid4Container.visibility = View.VISIBLE
                Glide.with(ctx).load(SafeImageUrl.sanitize(photos[3])).centerCrop().into(b.photoGrid4)
                b.morePhotosOverlay.visibility = View.GONE
            } else {
                b.photoGrid4Container.visibility = View.GONE
            }
        }

        private fun bindFooter(post: CommunityPost) {
            val ctx = b.root.context
            // Place chip
            if (post.taggedPlaceName.isNotBlank()) {
                b.taggedPlaceChip.visibility = View.VISIBLE
                b.taggedPlaceChip.text = post.taggedPlaceName
                b.taggedPlaceChip.contentDescription =
                    ctx.getString(R.string.community_tagged_place_a11y, post.taggedPlaceName)
                b.taggedPlaceChip.setOnClickListener { callbacks.onPlaceClick(post.taggedPlaceId) }
            } else {
                b.taggedPlaceChip.visibility = View.GONE
            }
            // Like
            val liked = likedPostIds.contains(post.id)
            b.likeButton.setImageResource(
                if (liked) R.drawable.ic_thumb_up_filled else R.drawable.ic_thumb_up_outline
            )
            b.likeButton.contentDescription =
                ctx.getString(if (liked) R.string.community_unlike_a11y else R.string.community_like_a11y)
            b.likeCount.text = if (post.likeCount > 0) post.likeCount.toString() else ""
            b.likeButton.setOnClickListener { callbacks.onLikeClick(post) }
            // Comments
            b.commentCount.text = if (post.commentCount > 0) post.commentCount.toString() else ""
            b.commentButton.setOnClickListener { callbacks.onCommentClick(post) }
            // Overflow
            bindOverflow(ctx, post)
        }

        private fun bindOverflow(ctx: android.content.Context, post: CommunityPost) {
            val isOwner = currentUserId != null && post.authorId == currentUserId
            b.overflowMenu.setOnClickListener { anchor ->
                val popup = PopupMenu(ctx, anchor)
                if (isOwner || isAdmin) {
                    popup.menu.add(0, R.id.action_edit_post, 0, ctx.getString(R.string.community_action_edit))
                    popup.menu.add(0, R.id.action_delete_post, 1, ctx.getString(R.string.community_action_delete))
                }
                if (!isOwner) {
                    popup.menu.add(0, R.id.action_report_post, 2, ctx.getString(R.string.community_action_report))
                }
                if (popup.menu.size() == 0) return@setOnClickListener
                popup.setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        R.id.action_edit_post -> callbacks.onEditClick(post)
                        R.id.action_delete_post -> callbacks.onDeleteClick(post)
                        R.id.action_report_post -> callbacks.onReportClick(post)
                    }
                    true
                }
                popup.show()
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val b = ItemCommunityPostBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PostViewHolder(b)
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class DiffCallback : DiffUtil.ItemCallback<CommunityPost>() {
        override fun areItemsTheSame(old: CommunityPost, new: CommunityPost) = old.id == new.id
        override fun areContentsTheSame(old: CommunityPost, new: CommunityPost) = old == new
    }
}
