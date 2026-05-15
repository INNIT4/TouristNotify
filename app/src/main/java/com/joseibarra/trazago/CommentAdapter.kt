package com.joseibarra.trazago

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.joseibarra.trazago.databinding.ItemPostCommentBinding

class CommentAdapter(
    private val currentUserId: String?,
    private val isAdmin: Boolean,
    private val onDeleteClick: (PostComment) -> Unit,
) : ListAdapter<PostComment, CommentAdapter.CommentViewHolder>(DiffCallback()) {

    inner class CommentViewHolder(private val b: ItemPostCommentBinding) :
        RecyclerView.ViewHolder(b.root) {

        fun bind(comment: PostComment) {
            val ctx = b.root.context

            val safeAvatar = if (comment.authorPhotoUrl.isNotBlank())
                SafeImageUrl.sanitize(comment.authorPhotoUrl) else null
            Glide.with(ctx).load(safeAvatar)
                .circleCrop()
                .placeholder(R.drawable.bg_profile_avatar)
                .error(R.drawable.bg_profile_avatar)
                .into(b.commentAvatar)

            b.commentAuthor.text = comment.authorName
            b.commentContent.text = comment.content

            b.commentTimestamp.text = if (comment.createdAt != null) {
                DateUtils.getRelativeTimeSpanString(
                    comment.createdAt.time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS
                )
            } else ""

            val canDelete = currentUserId != null &&
                    (comment.authorId == currentUserId || isAdmin)
            b.deleteCommentButton.visibility = if (canDelete) View.VISIBLE else View.GONE
            b.deleteCommentButton.setOnClickListener { onDeleteClick(comment) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val b = ItemPostCommentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CommentViewHolder(b)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class DiffCallback : DiffUtil.ItemCallback<PostComment>() {
        override fun areItemsTheSame(old: PostComment, new: PostComment) = old.id == new.id
        override fun areContentsTheSame(old: PostComment, new: PostComment) = old == new
    }
}
