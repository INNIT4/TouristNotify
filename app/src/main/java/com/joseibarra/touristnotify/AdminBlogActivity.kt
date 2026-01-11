package com.joseibarra.touristnotify

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.joseibarra.touristnotify.databinding.ActivityAdminBlogBinding
import java.util.*

/**
 * Activity para que admins creen/editen posts del blog
 */
class AdminBlogActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminBlogBinding
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Verificar autorización - Solo personal de la Oficina de Turismo
        if (!AdminConfig.canCreateBlogPosts(auth.currentUser?.email)) {
            NotificationHelper.error(
                window.decorView.rootView,
                "Acceso denegado. Solo personal autorizado de la Oficina de Turismo puede crear posts."
            )
            finish()
            return
        }

        binding = ActivityAdminBlogBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.title = "Crear Post - Oficina de Turismo"

        setupUI()
    }

    private fun setupUI() {
        binding.publishButton.setOnClickListener {
            publishPost()
        }

        // Category selection
        binding.categoryChipGroup.setOnCheckedStateChangeListener { _, _ ->
            // Category selected
        }
    }

    private fun publishPost() {
        val title = binding.titleEditText.text.toString().trim()
        val content = binding.contentEditText.text.toString().trim()
        val category = getSelectedCategory()
        val isFeatured = binding.featuredSwitch.isChecked

        // SEGURIDAD: Validación básica
        if (title.isEmpty()) {
            NotificationHelper.error(binding.root, "El título es requerido")
            return
        }

        if (content.isEmpty()) {
            NotificationHelper.error(binding.root, "El contenido es requerido")
            return
        }

        if (category.isEmpty()) {
            NotificationHelper.error(binding.root, "Selecciona una categoría")
            return
        }

        // SEGURIDAD: Validar longitud máxima
        val MAX_TITLE_LENGTH = 200
        val MAX_CONTENT_LENGTH = 10000

        if (title.length > MAX_TITLE_LENGTH) {
            NotificationHelper.error(binding.root, "Título muy largo (máx. $MAX_TITLE_LENGTH caracteres)")
            return
        }

        if (content.length > MAX_CONTENT_LENGTH) {
            NotificationHelper.error(binding.root, "Contenido muy largo (máx. $MAX_CONTENT_LENGTH caracteres)")
            return
        }

        // SEGURIDAD: Sanitizar contenido para prevenir XSS
        val sanitizedTitle = title
            .replace(Regex("<[^>]*>"), "") // Remover tags HTML
            .replace(Regex("\\p{C}"), "") // Remover caracteres de control
            .replace(Regex("\\s+"), " ") // Normalizar espacios

        val sanitizedContent = content
            .replace(Regex("<script[^>]*>.*?</script>", RegexOption.IGNORE_CASE), "") // Remover scripts
            .replace(Regex("javascript:", RegexOption.IGNORE_CASE), "") // Remover javascript:
            .replace(Regex("on\\w+\\s*=", RegexOption.IGNORE_CASE), "") // Remover event handlers
            .replace(Regex("\\p{C}"), "") // Remover caracteres de control

        binding.publishButton.isEnabled = false

        // El autor siempre será la Oficina de Turismo para posts oficiales
        val authorName = "Oficina de Turismo de Álamos"

        val post = BlogPost(
            id = "",
            title = sanitizedTitle,
            content = sanitizedContent,
            category = category,
            authorName = authorName,
            authorId = auth.currentUser?.uid ?: "",
            imageUrl = "",
            isFeatured = isFeatured,
            publishedAt = Date(),
            viewCount = 0,
            likes = 0
        )

        db.collection("blog_posts")
            .add(post)
            .addOnSuccessListener {
                NotificationHelper.success(binding.root, "Post publicado exitosamente")
                finish()
            }
            .addOnFailureListener {
                binding.publishButton.isEnabled = true
                // SEGURIDAD: Mensaje de error genérico
                NotificationHelper.error(binding.root, "Error al publicar el post. Intenta de nuevo")
            }
    }

    private fun getSelectedCategory(): String {
        return when (binding.categoryChipGroup.checkedChipId) {
            R.id.category_tips_chip -> "Consejos"
            R.id.category_history_chip -> "Historia"
            R.id.category_food_chip -> "Gastronomía"
            R.id.category_culture_chip -> "Cultura"
            R.id.category_nature_chip -> "Naturaleza"
            R.id.category_events_chip -> "Eventos"
            else -> ""
        }
    }
}
