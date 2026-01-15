package com.joseibarra.touristnotify

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.joseibarra.touristnotify.databinding.ActivityBlogPostDetailBinding
import java.text.SimpleDateFormat
import java.util.*

/**
 * Activity para mostrar el detalle completo de un post del blog
 */
class BlogPostDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBlogPostDetailBinding
    private val db = FirebaseFirestore.getInstance()
    private var postId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBlogPostDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Try to get post object first (new approach)
        val post = intent.getSerializableExtra("POST_OBJECT") as? BlogPost

        if (post != null) {
            postId = post.id
            supportActionBar?.title = post.title
            displayPost(post)
        } else {
            // Fallback: try old approach with POST_ID
            postId = intent.getStringExtra("POST_ID") ?: ""
            val postTitle = intent.getStringExtra("POST_TITLE") ?: "Post"
            supportActionBar?.title = postTitle

            if (postId.isNotEmpty()) {
                loadPostDetails()
            } else {
                finish()
            }
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

                    if (post != null) {
                        displayPost(post)
                    } else {
                        finish()
                    }
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
        binding.progressBar.visibility = View.GONE
        binding.contentScrollView.visibility = View.VISIBLE

        binding.postTitleTextView.text = post.title
        binding.postCategoryTextView.text = CategoryUtils.getCategoryEmoji(post.category) + " " + post.category
        binding.postContentTextView.text = post.content
        binding.postAuthorTextView.text = "Por ${post.authorName}"

        binding.postDateTextView.text = if (post.publishedAt != null) {
            val sdf = SimpleDateFormat("dd 'de' MMMM 'de' yyyy", Locale("es", "MX"))
            sdf.format(post.publishedAt)
        } else {
            "Fecha no disponible"
        }

        binding.postLikesTextView.text = "❤️ ${post.likes}"
        binding.postViewsTextView.text = "👁️ ${post.viewCount}"

        // Featured badge
        binding.featuredBadge.visibility = if (post.isFeatured) View.VISIBLE else View.GONE

        // Relacionado: mostrar categoría para navegar
        binding.relatedCategoryTextView.text = "Ver más de ${post.category}"
        binding.relatedCategoryCard.setOnClickListener {
            finish() // Volver a la lista
        }
    }
}
