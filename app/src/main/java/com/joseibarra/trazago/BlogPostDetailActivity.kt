package com.joseibarra.trazago

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.google.firebase.firestore.FirebaseFirestore
import com.joseibarra.trazago.databinding.ActivityBlogPostDetailBinding
import com.joseibarra.trazago.ui.BaseActivity
import io.noties.markwon.Markwon
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class BlogPostDetailActivity : BaseActivity() {

    private lateinit var binding: ActivityBlogPostDetailBinding
    private val db = FirebaseFirestore.getInstance()
    private var postId: String = ""
    private var currentPost: BlogPost? = null
    private var isAdmin = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBlogPostDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        @Suppress("DEPRECATION")
        val post = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("POST_OBJECT", BlogPost::class.java)
        } else {
            intent.getParcelableExtra("POST_OBJECT")
        }

        if (post != null) {
            postId = post.id
            supportActionBar?.title = post.title
            displayPost(post)
        } else {
            postId = intent.getStringExtra("POST_ID") ?: ""
            val postTitle = intent.getStringExtra("POST_TITLE") ?: "Post"
            supportActionBar?.title = postTitle
            if (postId.isNotEmpty()) loadPostDetails() else finish()
        }

        lifecycleScope.launch {
            isAdmin = AuthManager.isAdmin()
            invalidateOptionsMenu()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (isAdmin) {
            menu.add(Menu.NONE, MENU_EDIT, Menu.NONE, "Editar")
            menu.add(Menu.NONE, MENU_DELETE, Menu.NONE, "Eliminar")
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            MENU_EDIT -> {
                currentPost?.let { post ->
                    val intent = Intent(this, AdminBlogActivity::class.java)
                    intent.putExtra(AdminBlogActivity.EXTRA_POST_ID, post.id)
                    startActivity(intent)
                }
                true
            }
            MENU_DELETE -> {
                confirmDelete()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.blog_admin_delete_confirm_title))
            .setMessage(getString(R.string.blog_admin_delete_confirm_message))
            .setPositiveButton("Eliminar") { _, _ -> deletePost() }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun deletePost() {
        db.collection("blog_posts").document(postId).delete()
            .addOnSuccessListener {
                NotificationHelper.success(binding.root, "Post eliminado")
                finish()
            }
            .addOnFailureListener {
                NotificationHelper.error(binding.root, "Error al eliminar")
            }
    }

    private fun loadPostDetails() {
        binding.progressBar.visibility = View.VISIBLE
        binding.contentScrollView.visibility = View.GONE

        db.collection("blog_posts").document(postId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val post = document.toObject(BlogPost::class.java)?.copy(id = document.id)
                    if (post != null) displayPost(post) else finish()
                } else {
                    finish()
                }
            }
            .addOnFailureListener {
                NotificationHelper.error(binding.root, "Error al cargar el post")
                finish()
            }
    }

    private fun displayPost(post: BlogPost) {
        currentPost = post
        binding.progressBar.visibility = View.GONE
        binding.contentScrollView.visibility = View.VISIBLE

        binding.postTitleTextView.text = post.title
        binding.postCategoryTextView.text = CategoryUtils.getCategoryEmoji(post.category) + " " + post.category
        binding.postAuthorTextView.text = "Por ${post.authorName}"

        binding.postDateTextView.text = if (post.publishedAt != null) {
            SimpleDateFormat("dd 'de' MMMM 'de' yyyy", Locale("es", "MX")).format(post.publishedAt)
        } else {
            "Fecha no disponible"
        }

        binding.postLikesTextView.text = "❤️ ${post.likes}"
        binding.postViewsTextView.text = "👁️ ${post.viewCount}"
        binding.featuredBadge.visibility = if (post.isFeatured) View.VISIBLE else View.GONE

        val markwon = Markwon.create(this)
        markwon.setMarkdown(binding.postContentTextView, post.content)

        val safeHero = SafeImageUrl.sanitize(post.imageUrl)
        if (safeHero != null) {
            binding.postHeroImage.visibility = View.VISIBLE
            Glide.with(this).load(safeHero).centerCrop().into(binding.postHeroImage)
        }

        val galleryUrls = post.galleryImages.mapNotNull { SafeImageUrl.sanitize(it) }
        if (galleryUrls.isNotEmpty()) {
            binding.galleryLabel.visibility = View.VISIBLE
            binding.galleryRecycler.visibility = View.VISIBLE
            binding.galleryRecycler.layoutManager =
                LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
            binding.galleryRecycler.adapter = BlogGalleryAdapter(galleryUrls) { url ->
                val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (intent.resolveActivity(packageManager) != null) startActivity(intent)
            }
        }

        binding.relatedCategoryTextView.text = "Ver más de ${post.category}"
        binding.relatedCategoryCard.setOnClickListener { finish() }
    }

    companion object {
        private const val MENU_EDIT = 1
        private const val MENU_DELETE = 2
    }
}
