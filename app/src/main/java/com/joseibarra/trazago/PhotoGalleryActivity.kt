package com.joseibarra.trazago

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.joseibarra.trazago.databinding.ActivityPhotoGalleryBinding
import com.joseibarra.trazago.ui.BaseActivity

/**
 * Actividad para mostrar la galería de fotos de un lugar turístico
 */
class PhotoGalleryActivity : BaseActivity() {

    private lateinit var binding: ActivityPhotoGalleryBinding
    private lateinit var db: FirebaseFirestore
    private lateinit var photoAdapter: PhotoGalleryAdapter
    private var placeId: String? = null
    private var placeName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPhotoGalleryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = FirebaseFirestore.getInstance()

        placeId = intent.getStringExtra("PLACE_ID")
        placeName = intent.getStringExtra("PLACE_NAME")

        if (placeId == null) {
            NotificationHelper.error(binding.root, getString(R.string.place_not_found_error))
            finish()
            return
        }

        supportActionBar?.title = getString(R.string.gallery_title, placeName)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        setupRecyclerView()
        loadPhotos()
    }

    private fun setupRecyclerView() {
        photoAdapter = PhotoGalleryAdapter { photo, position ->
            // Abrir foto en pantalla completa
            val intent = Intent(this, FullScreenPhotoActivity::class.java).apply {
                putExtra("PLACE_ID", placeId)
                putExtra("PHOTO_POSITION", position)
            }
            startActivity(intent)
        }

        binding.photosRecyclerView.apply {
            layoutManager = GridLayoutManager(this@PhotoGalleryActivity, 2)
            adapter = photoAdapter
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
                    binding.emptyStateTextView.text = getString(R.string.no_photos_yet)
                } else {
                    photoAdapter.submitList(photos)
                }
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                binding.emptyStateTextView.visibility = View.VISIBLE
                binding.emptyStateTextView.text = getString(R.string.photos_load_error, e.message)
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
