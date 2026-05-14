package com.joseibarra.trazago

import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.joseibarra.trazago.databinding.ActivityAdminBlogBinding
import com.joseibarra.trazago.ui.BaseActivity
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

class AdminBlogActivity : BaseActivity() {

    private lateinit var binding: ActivityAdminBlogBinding
    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private var editPostId: String? = null
    private var pendingCoverUri: Uri? = null
    private var pendingGalleryUris: List<Uri> = emptyList()
    private var existingCoverUrl: String = ""
    private var existingGalleryUrls: List<String> = emptyList()

    private val pickCover = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            pendingCoverUri = uri
            binding.coverImagePreview.visibility = View.VISIBLE
            Glide.with(this).load(uri).centerCrop().into(binding.coverImagePreview)
        }
    }

    private val pickGallery = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 8)
    ) { uris ->
        if (uris.isNotEmpty()) {
            pendingGalleryUris = uris
            updateGalleryPreview()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminBlogBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { finish() }

        editPostId = intent.getStringExtra(EXTRA_POST_ID)

        lifecycleScope.launch {
            if (!AuthManager.isAdmin()) {
                AlertDialog.Builder(this@AdminBlogActivity)
                    .setMessage(getString(R.string.blog_admin_not_authorized))
                    .setPositiveButton("OK") { _, _ -> finish() }
                    .show()
                return@launch
            }

            if (editPostId != null) {
                binding.toolbar.title = getString(R.string.blog_admin_edit_title)
                loadPostForEdit(editPostId!!)
                showDeleteMenuItem()
            } else {
                binding.toolbar.title = getString(R.string.blog_admin_create_title)
            }
        }

        binding.btnSelectCover.setOnClickListener {
            pickCover.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        binding.btnAddGallery.setOnClickListener {
            pickGallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        binding.publishButton.setOnClickListener {
            publishPost()
        }
    }

    private fun showDeleteMenuItem() {
        val item = binding.toolbar.menu.findItem(R.id.action_delete_post)
        item?.isVisible = true
        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            if (menuItem.itemId == R.id.action_delete_post) {
                confirmDelete()
                true
            } else false
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun loadPostForEdit(postId: String) {
        db.collection("blog_posts").document(postId).get()
            .addOnSuccessListener { doc ->
                val post = doc.toObject(BlogPost::class.java) ?: return@addOnSuccessListener
                binding.titleEditText.setText(post.title)
                binding.contentEditText.setText(post.content)
                binding.featuredSwitch.isChecked = post.isFeatured
                existingCoverUrl = post.imageUrl
                existingGalleryUrls = post.galleryImages

                if (existingCoverUrl.isNotBlank()) {
                    binding.coverImagePreview.visibility = View.VISIBLE
                    Glide.with(this).load(existingCoverUrl).centerCrop().into(binding.coverImagePreview)
                }

                selectCategoryChip(post.category)

                if (existingGalleryUrls.isNotEmpty()) {
                    updateGalleryPreview()
                }
            }
    }

    private fun selectCategoryChip(category: String) {
        val chip = when (category) {
            "Consejos" -> binding.categoryTipsChip
            "Historia" -> binding.categoryHistoryChip
            "Gastronomía" -> binding.categoryFoodChip
            "Cultura" -> binding.categoryCultureChip
            "Naturaleza" -> binding.categoryNatureChip
            "Eventos" -> binding.categoryEventsChip
            else -> null
        }
        chip?.isChecked = true
    }

    private fun getSelectedCategory(): String {
        return when (binding.categoryChipGroup.checkedChipId) {
            R.id.category_tips_chip -> "Consejos"
            R.id.category_history_chip -> "Historia"
            R.id.category_food_chip -> "Gastronomía"
            R.id.category_culture_chip -> "Cultura"
            R.id.category_nature_chip -> "Naturaleza"
            R.id.category_events_chip -> "Eventos"
            else -> "General"
        }
    }

    private fun updateGalleryPreview() {
        val urlsToShow = if (pendingGalleryUris.isNotEmpty()) {
            pendingGalleryUris.map { it.toString() }
        } else {
            existingGalleryUrls
        }
        if (urlsToShow.isNotEmpty()) {
            binding.galleryPreviewRecycler.visibility = View.VISIBLE
            binding.galleryPreviewRecycler.layoutManager =
                LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
            binding.galleryPreviewRecycler.adapter = BlogGalleryAdapter(urlsToShow) { }
        }
    }

    private fun publishPost() {
        val title = binding.titleEditText.text?.toString()?.trim() ?: ""
        val content = binding.contentEditText.text?.toString()?.trim() ?: ""

        if (title.isBlank() || content.isBlank()) {
            NotificationHelper.error(binding.root, "Título y contenido son obligatorios")
            return
        }

        binding.publishButton.isEnabled = false
        NotificationHelper.info(binding.root, "Publicando…")

        val postId = editPostId ?: UUID.randomUUID().toString()
        val uid = auth.currentUser?.uid ?: ""
        val authorName = auth.currentUser?.displayName?.takeIf { it.isNotBlank() } ?: "Admin"

        lifecycleScope.launch {
            try {
                val coverUrl = when {
                    pendingCoverUri != null -> uploadFile("blog/$postId/cover.jpg", pendingCoverUri!!)
                    else -> existingCoverUrl
                }

                val galleryUrls = if (pendingGalleryUris.isNotEmpty()) {
                    pendingGalleryUris.mapIndexed { i, uri ->
                        uploadFile("blog/$postId/gallery/$i.jpg", uri)
                    }
                } else {
                    existingGalleryUrls
                }

                val excerpt = content.take(150).trimEnd { !it.isLetter() && !it.isDigit() }

                val post = BlogPost(
                    id = postId,
                    title = title,
                    content = content,
                    excerpt = excerpt,
                    category = getSelectedCategory(),
                    imageUrl = coverUrl,
                    galleryImages = galleryUrls,
                    authorName = authorName,
                    authorId = uid,
                    isFeatured = binding.featuredSwitch.isChecked
                )

                db.collection("blog_posts").document(postId).set(post).await()
                NotificationHelper.success(binding.root, getString(R.string.blog_admin_publish_success))
                finish()
            } catch (e: Exception) {
                binding.publishButton.isEnabled = true
                NotificationHelper.error(binding.root, "Error al publicar: ${e.message}")
            }
        }
    }

    private suspend fun uploadFile(path: String, uri: Uri): String {
        val ref = storage.reference.child(path)
        ref.putFile(uri).await()
        return ref.downloadUrl.await().toString()
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.blog_admin_delete_confirm_title))
            .setMessage(getString(R.string.blog_admin_delete_confirm_message))
            .setPositiveButton("Eliminar") { _, _ ->
                editPostId?.let { id ->
                    db.collection("blog_posts").document(id).delete()
                        .addOnSuccessListener {
                            NotificationHelper.success(binding.root, "Post eliminado")
                            finish()
                        }
                        .addOnFailureListener {
                            NotificationHelper.error(binding.root, "Error al eliminar")
                        }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    companion object {
        const val EXTRA_POST_ID = "EXTRA_POST_ID"
    }
}
