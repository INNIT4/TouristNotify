package com.joseibarra.touristnotify

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.joseibarra.touristnotify.databinding.ActivityBlogBinding

/**
 * Activity para mostrar el blog de consejos y tips para turistas
 */
class BlogActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBlogBinding
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val blogPosts = mutableListOf<BlogPost>()
    private lateinit var adapter: BlogPostAdapter

    private var currentCategory: String = "Todos"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBlogBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        loadBlogPosts()
    }

    private fun setupUI() {
        // RecyclerView setup
        adapter = BlogPostAdapter(blogPosts,
            onPostClick = { post ->
                openPostDetails(post)
            },
            onLikeClick = { post ->
                toggleLike(post)
            }
        )
        binding.blogRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.blogRecyclerView.adapter = adapter

        // Category filters
        binding.filterAllChip.setOnClickListener {
            selectCategory("Todos")
        }

        binding.filterTipsChip.setOnClickListener {
            selectCategory("Consejos")
        }

        binding.filterHistoryChip.setOnClickListener {
            selectCategory("Historia")
        }

        binding.filterFoodChip.setOnClickListener {
            selectCategory("Gastronomía")
        }

        binding.filterCultureChip.setOnClickListener {
            selectCategory("Cultura")
        }

        // Admin button (visible solo para admins)
        checkAdminAccess()

        binding.fabAddPost.setOnClickListener {
            val intent = Intent(this, AdminBlogActivity::class.java)
            startActivity(intent)
        }
    }

    private fun selectCategory(category: String) {
        currentCategory = category

        // Update chip selection
        binding.filterAllChip.isChecked = category == "Todos"
        binding.filterTipsChip.isChecked = category == "Consejos"
        binding.filterHistoryChip.isChecked = category == "Historia"
        binding.filterFoodChip.isChecked = category == "Gastronomía"
        binding.filterCultureChip.isChecked = category == "Cultura"

        filterPosts()
    }

    private fun filterPosts() {
        val filteredPosts = if (currentCategory == "Todos") {
            blogPosts
        } else {
            blogPosts.filter { it.category == currentCategory }
        }

        adapter.updatePosts(filteredPosts)
        binding.postsCountTextView.text = "${filteredPosts.size} artículos"
    }

    private fun loadBlogPosts() {
        binding.progressBar.visibility = View.VISIBLE
        binding.blogRecyclerView.visibility = View.GONE

        db.collection("blog_posts")
            .orderBy("isFeatured", Query.Direction.DESCENDING)
            .orderBy("publishedAt", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { documents ->
                blogPosts.clear()

                for (document in documents) {
                    val post = document.toObject(BlogPost::class.java).copy(id = document.id)
                    blogPosts.add(post)
                }

                binding.progressBar.visibility = View.GONE

                if (blogPosts.isEmpty()) {
                    binding.emptyStateContainer.visibility = View.VISIBLE
                    binding.blogRecyclerView.visibility = View.GONE
                    createSamplePosts() // Crear posts de ejemplo
                } else {
                    binding.emptyStateContainer.visibility = View.GONE
                    binding.blogRecyclerView.visibility = View.VISIBLE
                    adapter.notifyDataSetChanged()
                    binding.postsCountTextView.text = "${blogPosts.size} artículos"
                }
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                binding.emptyStateContainer.visibility = View.VISIBLE
                NotificationHelper.error(binding.root, "Error al cargar posts: ${e.message}")
            }
    }

    private fun createSamplePosts() {
        // Crear posts de ejemplo solo si está vacío
        val samplePosts = listOf(
            BlogPost(
                id = "sample1",
                title = "10 Lugares Imperdibles en Álamos",
                content = "Descubre los rincones más hermosos de nuestro Pueblo Mágico. Desde la Plaza de Armas hasta el Mirador de La Colorada, cada lugar tiene una historia que contar.",
                category = "Consejos",
                authorName = "Oficina de Turismo de Álamos",
                imageUrl = "",
                isFeatured = true,
                likes = 45,
                viewCount = 320
            ),
            BlogPost(
                id = "sample2",
                title = "Historia del Templo de la Purísima Concepción",
                content = "Conoce la fascinante historia de este emblemático templo construido en el siglo XVIII, joya arquitectónica del barroco novohispano.",
                category = "Historia",
                authorName = "Oficina de Turismo de Álamos",
                imageUrl = "",
                isFeatured = true,
                likes = 38,
                viewCount = 215
            ),
            BlogPost(
                id = "sample3",
                title = "Dónde Comer: Los Mejores Restaurantes",
                content = "Una guía completa de los restaurantes más deliciosos de Álamos. Desde comida tradicional sonorense hasta cocina internacional.",
                category = "Gastronomía",
                authorName = "Oficina de Turismo de Álamos",
                imageUrl = "",
                isFeatured = false,
                likes = 62,
                viewCount = 450
            ),
            BlogPost(
                id = "sample4",
                title = "Festivales Culturales de Álamos",
                content = "Álamos es sede de eventos culturales durante todo el año. Descubre el Festival Alfonso Ortiz Tirado, el Festival del Cine y muchos más.",
                category = "Cultura",
                authorName = "Oficina de Turismo de Álamos",
                imageUrl = "",
                isFeatured = false,
                likes = 29,
                viewCount = 180
            ),
            BlogPost(
                id = "sample5",
                title = "Consejos para Tu Primera Visita",
                content = "¿Primera vez en Álamos? Aquí te compartimos tips esenciales: mejor época para visitar, qué empacar, transporte, y más.",
                category = "Consejos",
                authorName = "Oficina de Turismo de Álamos",
                imageUrl = "",
                isFeatured = false,
                likes = 51,
                viewCount = 390
            )
        )

        // Agregar a la UI sin guardar en Firebase (fallback)
        blogPosts.addAll(samplePosts)
        binding.emptyStateContainer.visibility = View.GONE
        binding.blogRecyclerView.visibility = View.VISIBLE
        adapter.notifyDataSetChanged()
        binding.postsCountTextView.text = "${blogPosts.size} artículos"
    }

    private fun openPostDetails(post: BlogPost) {
        // Incrementar contador de vistas
        db.collection("blog_posts").document(post.id)
            .update("viewCount", post.viewCount + 1)

        val intent = Intent(this, BlogPostDetailActivity::class.java)
        intent.putExtra("POST_ID", post.id)
        intent.putExtra("POST_TITLE", post.title)
        startActivity(intent)
    }

    private fun toggleLike(post: BlogPost) {
        val userId = auth.currentUser?.uid ?: return

        db.collection("blog_likes")
            .whereEqualTo("userId", userId)
            .whereEqualTo("postId", post.id)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    // Agregar like
                    val like = hashMapOf(
                        "userId" to userId,
                        "postId" to post.id,
                        "timestamp" to System.currentTimeMillis()
                    )
                    db.collection("blog_likes").add(like)

                    // Incrementar contador
                    db.collection("blog_posts").document(post.id)
                        .update("likes", post.likes + 1)

                    NotificationHelper.success(binding.root, "Te gusta este artículo")
                } else {
                    // Quitar like
                    for (doc in documents) {
                        db.collection("blog_likes").document(doc.id).delete()
                    }

                    // Decrementar contador
                    db.collection("blog_posts").document(post.id)
                        .update("likes", maxOf(0, post.likes - 1))

                    NotificationHelper.info(binding.root, "Ya no te gusta este artículo")
                }

                // Recargar posts
                loadBlogPosts()
            }
    }

    private fun checkAdminAccess() {
        // Mostrar FAB solo para personal autorizado de la Oficina de Turismo
        val userEmail = auth.currentUser?.email
        binding.fabAddPost.visibility = if (AdminConfig.canCreateBlogPosts(userEmail)) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        loadBlogPosts() // Recargar al volver
    }
}
