package com.joseibarra.trazago

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ListenerRegistration
import com.joseibarra.trazago.databinding.ActivityCommunityFeedBinding
import com.joseibarra.trazago.ui.BaseActivity
import kotlinx.coroutines.launch

class CommunityFeedActivity : BaseActivity() {

    private lateinit var binding: ActivityCommunityFeedBinding
    private lateinit var adapter: CommunityPostAdapter

    private var currentUserId: String? = null
    private var isAdmin = false

    private var lastDocument: DocumentSnapshot? = null
    private var isLoadingMore = false
    private var hasMore = true
    private val posts = mutableListOf<CommunityPost>()
    private val likedPostIds = mutableSetOf<String>()

    private var feedRegistration: ListenerRegistration? = null

    companion object {
        private const val PAGE_SIZE = 15L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCommunityFeedBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsets(binding.root, applyTop = true, applyBottom = true)

        currentUserId = FirebaseAuth.getInstance().currentUser?.uid

        lifecycleScope.launch {
            isAdmin = AuthManager.isAdmin()
            setupAdapter()
            setupUI()
            attachFeedListener()
        }
    }

    private fun setupAdapter() {
        adapter = CommunityPostAdapter(
            currentUserId = currentUserId,
            isAdmin = isAdmin,
            onPostClick = { post ->
                startActivity(Intent(this, CommunityPostDetailActivity::class.java)
                    .putExtra(CommunityPostDetailActivity.EXTRA_POST_ID, post.id))
            },
            onLikeClick = { post ->
                AuthManager.requireAuth(this, AuthManager.AuthRequired.LIKE_POSTS) {
                    toggleLike(post)
                }
            },
            onCommentClick = { post ->
                startActivity(Intent(this, CommunityPostDetailActivity::class.java)
                    .putExtra(CommunityPostDetailActivity.EXTRA_POST_ID, post.id))
            },
            onPlaceClick = { placeId ->
                startActivity(Intent(this, PlaceDetailsActivity::class.java)
                    .putExtra("place_id", placeId))
            },
            onEditClick = { post ->
                startActivity(Intent(this, CreateCommunityPostActivity::class.java)
                    .putExtra(CreateCommunityPostActivity.EXTRA_EDIT_POST_ID, post.id))
            },
            onDeleteClick = { post -> confirmDeletePost(post) },
            onReportClick = { post ->
                AuthManager.requireAuth(this, AuthManager.AuthRequired.REPORT_POSTS) {
                    val uid = currentUserId ?: return@requireAuth
                    ReportPostBottomSheet.newInstance(post.id, uid)
                        .show(supportFragmentManager, ReportPostBottomSheet.TAG)
                }
            },
        )
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { finish() }

        val layoutManager = LinearLayoutManager(this)
        binding.feedRecycler.layoutManager = layoutManager
        binding.feedRecycler.adapter = adapter

        binding.feedRecycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                val last = layoutManager.findLastVisibleItemPosition()
                val total = layoutManager.itemCount
                if (!isLoadingMore && hasMore && dy > 0 && last >= total - 3) loadMorePosts()
            }
        })

        binding.swipeRefresh.setOnRefreshListener {
            feedRegistration?.remove()
            posts.clear()
            lastDocument = null
            hasMore = true
            attachFeedListener()
        }

        // FAB crear
        binding.fabCreatePost.setOnClickListener {
            AuthManager.requireAuth(this, AuthManager.AuthRequired.SHARE_POSTS) {
                startActivity(Intent(this, CreateCommunityPostActivity::class.java))
            }
        }

        // FAB admin (moderación)
        if (isAdmin) {
            binding.fabAdminReports.visibility = View.VISIBLE
            binding.fabAdminReports.setOnClickListener {
                startActivity(Intent(this, AdminReportsActivity::class.java))
            }
        }
    }

    private fun attachFeedListener() {
        feedRegistration = CommunityRepository.attachFeedListener(
            pageSize = PAGE_SIZE,
            onUpdate = { updatedPosts, lastDoc ->
                binding.swipeRefresh.isRefreshing = false
                val tail = if (posts.size > updatedPosts.size) posts.drop(updatedPosts.size) else emptyList()
                posts.clear()
                posts.addAll(updatedPosts)
                posts.addAll(tail)
                lastDocument = lastDoc
                hasMore = updatedPosts.size.toLong() >= PAGE_SIZE
                adapter.submitList(posts.toList())
                updateEmptyState()
                loadLikedStatus()
            },
            onError = { e ->
                binding.swipeRefresh.isRefreshing = false
                FirestoreErrorHandler.handleError(this, binding.root, e, "cargar la comunidad")
            }
        )
    }

    private fun loadMorePosts() {
        val cursor = lastDocument ?: return
        isLoadingMore = true
        lifecycleScope.launch {
            val result = CommunityRepository.loadNextPage(cursor, PAGE_SIZE)
            isLoadingMore = false
            result.onSuccess { (newPosts, newCursor) ->
                posts.addAll(newPosts)
                lastDocument = newCursor
                hasMore = newPosts.size.toLong() >= PAGE_SIZE
                adapter.submitList(posts.toList())
            }
        }
    }

    private fun loadLikedStatus() {
        val uid = currentUserId ?: return
        lifecycleScope.launch {
            val ids = CommunityRepository.getLikedPostIds(uid, posts.map { it.id })
            likedPostIds.clear()
            likedPostIds.addAll(ids)
            adapter.setLikedPostIds(likedPostIds)
        }
    }

    private fun toggleLike(post: CommunityPost) {
        val uid = currentUserId ?: return
        val wasLiked = likedPostIds.contains(post.id)
        // Optimistic update
        adapter.markLiked(post.id, !wasLiked)
        if (wasLiked) likedPostIds.remove(post.id) else likedPostIds.add(post.id)

        lifecycleScope.launch {
            CommunityRepository.togglePostLike(post.id, uid).onFailure {
                // Revert
                adapter.markLiked(post.id, wasLiked)
                if (wasLiked) likedPostIds.add(post.id) else likedPostIds.remove(post.id)
                NotificationHelper.error(binding.root, getString(R.string.community_like_error))
            }
        }
    }

    private fun confirmDeletePost(post: CommunityPost) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.community_delete_post_title)
            .setMessage(R.string.community_delete_post_message)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                lifecycleScope.launch {
                    CommunityRepository.deletePost(post.id, post.photoUrls).onFailure {
                        NotificationHelper.error(binding.root, getString(R.string.community_delete_error))
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun updateEmptyState() {
        val empty = posts.isEmpty()
        binding.emptyState.visibility = if (empty) View.VISIBLE else View.GONE
        binding.feedRecycler.visibility = if (empty) View.GONE else View.VISIBLE
    }

    override fun onStop() {
        super.onStop()
        feedRegistration?.remove()
        feedRegistration = null
    }
}
