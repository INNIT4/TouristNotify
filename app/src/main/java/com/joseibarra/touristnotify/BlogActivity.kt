package com.joseibarra.touristnotify

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
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

    private var lastDocument: DocumentSnapshot? = null
    private var isLoadingMore = false
    private var hasMorePosts = true

    companion object {
        private const val PAGE_SIZE = 15L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBlogBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        loadBlogPosts()
    }

    private fun setupUI() {
        // RecyclerView setup
        adapter = BlogPostAdapter(
            onPostClick = { post ->
                openPostDetails(post)
            },
            onLikeClick = { post ->
                toggleLike(post)
            }
        )
        val layoutManager = LinearLayoutManager(this)
        binding.blogRecyclerView.layoutManager = layoutManager
        binding.blogRecyclerView.adapter = adapter

        // Cargar más al llegar al final de la lista
        binding.blogRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val lastVisible = layoutManager.findLastVisibleItemPosition()
                val total = layoutManager.itemCount
                if (!isLoadingMore && hasMorePosts && dy > 0 && lastVisible >= total - 3) {
                    loadMorePosts()
                }
            }
        })

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

        adapter.submitList(filteredPosts)
        binding.postsCountTextView.text = "${filteredPosts.size} artículos"
        binding.blogRecyclerView.scrollToPosition(0)
    }

    private fun loadBlogPosts() {
        binding.progressBar.visibility = View.VISIBLE
        binding.blogRecyclerView.visibility = View.GONE

        // Reset pagination state on fresh load
        lastDocument = null
        hasMorePosts = true
        blogPosts.clear()

        db.collection("blog_posts")
            .orderBy("isFeatured", Query.Direction.DESCENDING)
            .orderBy("publishedAt", Query.Direction.DESCENDING)
            .limit(PAGE_SIZE)
            .get()
            .addOnSuccessListener { documents ->
                binding.progressBar.visibility = View.GONE

                for (document in documents) {
                    val post = document.toObject(BlogPost::class.java).copy(id = document.id)
                    blogPosts.add(post)
                }

                // Si se devolvieron menos de PAGE_SIZE, no hay más páginas
                hasMorePosts = documents.size() >= PAGE_SIZE
                lastDocument = documents.documents.lastOrNull()

                if (blogPosts.isEmpty()) {
                    binding.emptyStateContainer.visibility = View.VISIBLE
                    binding.blogRecyclerView.visibility = View.GONE
                    // Removed createSamplePosts() - no more hardcoded sample data
                } else {
                    binding.emptyStateContainer.visibility = View.GONE
                    binding.blogRecyclerView.visibility = View.VISIBLE
                    filterPosts()
                }
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                binding.emptyStateContainer.visibility = View.VISIBLE
                NotificationHelper.error(binding.root, "Error al cargar posts: ${e.message}")
            }
    }

    private fun loadMorePosts() {
        val cursor = lastDocument ?: return
        if (!hasMorePosts || isLoadingMore) return

        isLoadingMore = true

        db.collection("blog_posts")
            .orderBy("isFeatured", Query.Direction.DESCENDING)
            .orderBy("publishedAt", Query.Direction.DESCENDING)
            .startAfter(cursor)
            .limit(PAGE_SIZE)
            .get()
            .addOnSuccessListener { documents ->
                isLoadingMore = false
                for (document in documents) {
                    val post = document.toObject(BlogPost::class.java).copy(id = document.id)
                    blogPosts.add(post)
                }
                hasMorePosts = documents.size() >= PAGE_SIZE
                lastDocument = documents.documents.lastOrNull() ?: lastDocument
                filterPosts()
            }
            .addOnFailureListener {
                isLoadingMore = false
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
        adapter.submitList(blogPosts.toList())
        binding.postsCountTextView.text = "${blogPosts.size} artículos"
    }

    private fun openPostDetails(post: BlogPost) {
        // Incrementar contador de vistas (solo si no es un sample post)
        if (!post.id.startsWith("sample")) {
            db.collection("blog_posts").document(post.id)
                .update("viewCount", post.viewCount + 1)
        }

        val intent = Intent(this, BlogPostDetailActivity::class.java)
        intent.putExtra("POST_OBJECT", post)
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

                // Actualizar localmente sin recargar toda la lista
                val index = blogPosts.indexOfFirst { it.id == post.id }
                if (index != -1) {
                    val newLikes = if (documents.isEmpty) post.likes + 1 else maxOf(0, post.likes - 1)
                    blogPosts[index] = blogPosts[index].copy(likes = newLikes)
                    filterPosts() // Re-aplicar filtro actual (que llama submitList internamente)
                }
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

}
