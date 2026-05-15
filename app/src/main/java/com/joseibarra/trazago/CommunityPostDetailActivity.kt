package com.joseibarra.trazago

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.auth.FirebaseAuth
import com.joseibarra.trazago.databinding.ActivityCommunityPostDetailBinding
import com.joseibarra.trazago.ui.BaseActivity
import kotlinx.coroutines.launch

class CommunityPostDetailActivity : BaseActivity() {

    private lateinit var binding: ActivityCommunityPostDetailBinding
    private lateinit var commentAdapter: CommentAdapter

    private var post: CommunityPost? = null
    private var currentUserId: String? = null
    private var isAdmin = false
    private var isLiked = false

    companion object {
        const val EXTRA_POST_ID = "post_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCommunityPostDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsets(binding.root, applyTop = true, applyBottom = true)

        currentUserId = FirebaseAuth.getInstance().currentUser?.uid

        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { finish() }

        val postId = intent.getStringExtra(EXTRA_POST_ID) ?: run { finish(); return }

        lifecycleScope.launch {
            isAdmin = AuthManager.isAdmin()
            loadPost(postId)
            setupCommentRecycler(postId)
        }
    }

    private suspend fun loadPost(postId: String) {
        CommunityRepository.getPost(postId).onSuccess { loadedPost ->
            post = loadedPost
            bindPost(loadedPost)

            // Check liked status
            currentUserId?.let { uid ->
                CommunityRepository.isLikedBy(postId, uid).onSuccess { liked ->
                    isLiked = liked
                    updateLikeButton()
                }
            }
            // Setup toolbar menu
            setupToolbarMenu(loadedPost)
            // Load comments
            loadComments(postId)
        }.onFailure { e ->
            FirestoreErrorHandler.handleError(this, binding.root, e, "cargar publicación")
            finish()
        }
    }

    private fun bindPost(p: CommunityPost) {
        // Photos
        if (p.photoUrls.isNotEmpty()) {
            binding.photosPager.visibility = View.VISIBLE
            val photoAdapter = PostPhotoPageAdapter(p.photoUrls)
            binding.photosPager.adapter = photoAdapter
            if (p.photoUrls.size > 1) {
                binding.photosIndicator.visibility = View.VISIBLE
                TabLayoutMediator(binding.photosIndicator, binding.photosPager) { _, _ -> }.attach()
            }
        } else {
            binding.photosPager.visibility = View.GONE
            binding.photosIndicator.visibility = View.GONE
        }

        // Author
        val safeAvatar = if (p.authorPhotoUrl.isNotBlank())
            SafeImageUrl.sanitize(p.authorPhotoUrl) else null
        com.bumptech.glide.Glide.with(this).load(safeAvatar)
            .circleCrop()
            .placeholder(R.drawable.bg_profile_avatar)
            .into(binding.authorAvatar)

        binding.authorName.text = p.authorName
        binding.postTimestamp.text = if (p.createdAt != null) {
            android.text.format.DateUtils.getRelativeTimeSpanString(
                p.createdAt.time, System.currentTimeMillis(),
                android.text.format.DateUtils.MINUTE_IN_MILLIS
            )
        } else ""

        binding.postTitle.text = p.title
        binding.postContent.text = p.content

        // Place chip
        if (p.taggedPlaceName.isNotBlank()) {
            binding.taggedPlaceChip.visibility = View.VISIBLE
            binding.taggedPlaceChip.text = p.taggedPlaceName
            binding.taggedPlaceChip.contentDescription =
                getString(R.string.community_tagged_place_a11y, p.taggedPlaceName)
            binding.taggedPlaceChip.setOnClickListener {
                startActivity(
                    Intent(this, PlaceDetailsActivity::class.java)
                        .putExtra("PLACE_ID", p.taggedPlaceId)
                )
            }
        } else {
            binding.taggedPlaceChip.visibility = View.GONE
        }

        // Like
        binding.likeCount.text = if (p.likeCount > 0) getString(R.string.community_like_count, p.likeCount) else ""
        binding.likeButton.setOnClickListener {
            AuthManager.requireAuth(this, AuthManager.AuthRequired.LIKE_POSTS) {
                toggleLike(p.id)
            }
        }
    }

    private fun updateLikeButton() {
        binding.likeButton.setImageResource(
            if (isLiked) R.drawable.ic_thumb_up_filled else R.drawable.ic_thumb_up_outline
        )
        binding.likeButton.contentDescription =
            getString(if (isLiked) R.string.community_unlike_a11y else R.string.community_like_a11y)
    }

    private fun setupToolbarMenu(p: CommunityPost) {
        val isOwner = currentUserId != null && p.authorId == currentUserId
        binding.toolbar.inflateMenu(R.menu.menu_community_post_detail)
        binding.toolbar.menu.findItem(R.id.action_edit_post)?.isVisible = isOwner || isAdmin
        binding.toolbar.menu.findItem(R.id.action_delete_post)?.isVisible = isOwner || isAdmin
        binding.toolbar.menu.findItem(R.id.action_report_post)?.isVisible = !isOwner

        binding.toolbar.setOnMenuItemClickListener { item -> handleMenuItem(item, p) }
    }

    private fun handleMenuItem(item: MenuItem, p: CommunityPost): Boolean {
        return when (item.itemId) {
            R.id.action_edit_post -> {
                startActivity(Intent(this, CreateCommunityPostActivity::class.java)
                    .putExtra(CreateCommunityPostActivity.EXTRA_EDIT_POST_ID, p.id))
                true
            }
            R.id.action_delete_post -> {
                confirmDelete(p)
                true
            }
            R.id.action_report_post -> {
                AuthManager.requireAuth(this, AuthManager.AuthRequired.REPORT_POSTS) {
                    val uid = currentUserId ?: return@requireAuth
                    ReportPostBottomSheet.newInstance(p.id, uid)
                        .show(supportFragmentManager, ReportPostBottomSheet.TAG)
                }
                true
            }
            else -> false
        }
    }

    private fun confirmDelete(p: CommunityPost) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.community_delete_post_title)
            .setMessage(R.string.community_delete_post_message)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                lifecycleScope.launch {
                    CommunityRepository.deletePost(p.id, p.photoUrls).onSuccess {
                        finish()
                    }.onFailure { e ->
                        NotificationHelper.error(binding.root, getString(R.string.community_delete_error))
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun setupCommentRecycler(postId: String) {
        commentAdapter = CommentAdapter(
            currentUserId = currentUserId,
            isAdmin = isAdmin,
            onDeleteClick = { comment -> deleteComment(postId, comment) }
        )
        binding.commentsRecycler.layoutManager = LinearLayoutManager(this)
        binding.commentsRecycler.adapter = commentAdapter
        binding.commentsRecycler.isNestedScrollingEnabled = false

        binding.btnSendComment.setOnClickListener {
            AuthManager.requireAuth(this, AuthManager.AuthRequired.COMMENT_POSTS) {
                sendComment(postId)
            }
        }
    }

    private fun loadComments(postId: String) {
        lifecycleScope.launch {
            CommunityRepository.getComments(postId).onSuccess { comments ->
                commentAdapter.submitList(comments)
                binding.noCommentsText.visibility =
                    if (comments.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun sendComment(postId: String) {
        val text = binding.commentInput.text?.toString()?.trim().orEmpty()
        if (text.isBlank()) return

        val user = FirebaseAuth.getInstance().currentUser ?: return
        val comment = PostComment(
            authorId = user.uid,
            authorName = user.displayName ?: user.email ?: "Usuario",
            authorPhotoUrl = user.photoUrl?.toString() ?: "",
            content = text
        )

        binding.btnSendComment.isEnabled = false
        lifecycleScope.launch {
            CommunityRepository.addComment(postId, comment).onSuccess {
                binding.commentInput.setText("")
                binding.btnSendComment.isEnabled = true
                loadComments(postId)
                // Update local count
                val p = post ?: return@onSuccess
                binding.likeCount.text = getString(R.string.community_like_count, p.likeCount)
            }.onFailure { e ->
                binding.btnSendComment.isEnabled = true
                NotificationHelper.error(binding.root, getString(R.string.community_comment_error))
            }
        }
    }

    private fun deleteComment(postId: String, comment: PostComment) {
        lifecycleScope.launch {
            CommunityRepository.deleteComment(postId, comment.id).onSuccess {
                loadComments(postId)
            }.onFailure {
                NotificationHelper.error(binding.root, getString(R.string.community_delete_error))
            }
        }
    }

    private fun toggleLike(postId: String) {
        val uid = currentUserId ?: return
        // Optimistic
        isLiked = !isLiked
        updateLikeButton()

        lifecycleScope.launch {
            CommunityRepository.togglePostLike(postId, uid).onFailure {
                // Revert
                isLiked = !isLiked
                updateLikeButton()
                NotificationHelper.error(binding.root, getString(R.string.community_like_error))
            }
        }
    }
}
