package com.joseibarra.touristnotify

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.joseibarra.touristnotify.databinding.ActivityPhotoGalleryBinding

/**
 * Actividad para mostrar la galería de fotos de un lugar turístico
 */
class PhotoGalleryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPhotoGalleryBinding
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var photoAdapter: PhotoGalleryAdapter
    private var placeId: String? = null
    private var placeName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPhotoGalleryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        placeId = intent.getStringExtra("PLACE_ID")
        placeName = intent.getStringExtra("PLACE_NAME")

        if (placeId == null) {
            NotificationHelper.error(binding.root, "Error: lugar no encontrado")
            finish()
            return
        }

        supportActionBar?.title = "Galería: $placeName"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        setupRecyclerView()
        loadPhotos()

        // FAB para cualquier usuario autenticado
        val currentUser = auth.currentUser
        if (currentUser != null) {
            binding.fabUploadPhoto.visibility = View.VISIBLE
            binding.fabUploadPhoto.setOnClickListener {
                val intent = Intent(this, AdminPhotoUploadActivity::class.java).apply {
                    putExtra("PLACE_ID", placeId)
                    putExtra("PLACE_NAME", placeName)
                }
                startActivity(intent)
            }
        } else {
            binding.fabUploadPhoto.visibility = View.GONE
        }
    }

    private fun setupRecyclerView() {
        photoAdapter = PhotoGalleryAdapter(
            onPhotoClick = { photo, position ->
                val intent = Intent(this, FullScreenPhotoActivity::class.java).apply {
                    putExtra("PLACE_ID", placeId)
                    putExtra("PHOTO_POSITION", position)
                }
                startActivity(intent)
            },
            onLikeClick = { photo -> togglePhotoLike(photo) }
        )

        binding.photosRecyclerView.apply {
            layoutManager = GridLayoutManager(this@PhotoGalleryActivity, 2)
            adapter = photoAdapter
        }
    }

    private fun togglePhotoLike(photo: PlacePhoto) {
        val userId = auth.currentUser?.uid ?: run {
            NotificationHelper.info(binding.root, "Inicia sesión para dar Me gusta")
            return
        }

        db.collection("photo_likes")
            .whereEqualTo("userId", userId)
            .whereEqualTo("photoId", photo.id)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    db.collection("photo_likes").add(
                        hashMapOf("userId" to userId, "photoId" to photo.id)
                    )
                    db.collection("place_photos").document(photo.id)
                        .update("likes", photo.likes + 1)
                    // Actualizar localmente
                    val current = photoAdapter.currentList.toMutableList()
                    val idx = current.indexOfFirst { it.id == photo.id }
                    if (idx != -1) {
                        current[idx] = current[idx].copy(likes = photo.likes + 1)
                        photoAdapter.submitList(current)
                    }
                } else {
                    for (doc in documents) {
                        db.collection("photo_likes").document(doc.id).delete()
                    }
                    db.collection("place_photos").document(photo.id)
                        .update("likes", maxOf(0, photo.likes - 1))
                    val current = photoAdapter.currentList.toMutableList()
                    val idx = current.indexOfFirst { it.id == photo.id }
                    if (idx != -1) {
                        current[idx] = current[idx].copy(likes = maxOf(0, photo.likes - 1))
                        photoAdapter.submitList(current)
                    }
                }
            }
    }

    private fun loadPhotos() {
        binding.progressBar.visibility = View.VISIBLE
        binding.emptyStateTextView.visibility = View.GONE

        db.collection("place_photos")
            .whereEqualTo("placeId", placeId)
            .orderBy("uploadedAt", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { documents ->
                binding.progressBar.visibility = View.GONE

                val photos = documents.map { it.toObject(PlacePhoto::class.java) }

                if (photos.isEmpty()) {
                    binding.emptyStateTextView.visibility = View.VISIBLE
                    binding.emptyStateTextView.text = "📸\n\nNo hay fotos aún\n\nSé el primero en compartir fotos de este lugar"
                } else {
                    photoAdapter.submitList(photos)
                }
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                binding.emptyStateTextView.visibility = View.VISIBLE
                binding.emptyStateTextView.text = "Error al cargar fotos:\n${e.message}"
            }
    }

    override fun onResume() {
        super.onResume()
        // Recargar fotos al volver de subir una foto
        loadPhotos()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
