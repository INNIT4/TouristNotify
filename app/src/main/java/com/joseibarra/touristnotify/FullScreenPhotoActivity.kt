package com.joseibarra.touristnotify

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import androidx.viewpager2.widget.ViewPager2
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.joseibarra.touristnotify.databinding.ActivityFullScreenPhotoBinding

/**
 * Actividad para ver fotos en pantalla completa con navegación por deslizamiento
 */
class FullScreenPhotoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFullScreenPhotoBinding
    private lateinit var db: FirebaseFirestore
    private lateinit var photoAdapter: FullScreenPhotoAdapter
    private var placeId: String? = null
    private var initialPosition: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFullScreenPhotoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = FirebaseFirestore.getInstance()

        placeId = intent.getStringExtra("PLACE_ID")
        initialPosition = intent.getIntExtra("PHOTO_POSITION", 0)

        if (placeId == null) {
            finish()
            return
        }

        // Ocultar la barra de acción para pantalla completa
        supportActionBar?.hide()

        setupViewPager()
        loadPhotos()

        // Botón de cerrar
        binding.closeButton.setOnClickListener {
            finish()
        }
    }

    private fun setupViewPager() {
        photoAdapter = FullScreenPhotoAdapter()

        binding.viewPager.apply {
            adapter = photoAdapter
            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    updatePhotoInfo(position)
                }
            })
        }
    }

    private fun loadPhotos() {
        binding.progressBar.visibility = View.VISIBLE

        db.collection("place_photos")
            .whereEqualTo("placeId", placeId)
            .orderBy("uploadedAt", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { documents ->
                binding.progressBar.visibility = View.GONE

                val photos = documents.map { it.toObject(PlacePhoto::class.java) }

                if (photos.isNotEmpty()) {
                    photoAdapter.submitList(photos)
                    binding.viewPager.setCurrentItem(initialPosition, false)
                    updatePhotoInfo(initialPosition)
                } else {
                    finish()
                }
            }
            .addOnFailureListener {
                binding.progressBar.visibility = View.GONE
                finish()
            }
    }

    private fun updatePhotoInfo(position: Int) {
        val photos = photoAdapter.currentList
        if (position in photos.indices) {
            val photo = photos[position]
            binding.photoCounterTextView.text = "${position + 1} / ${photos.size}"

            // Mostrar caption si existe
            if (photo.caption.isNotBlank()) {
                binding.captionTextView.text = photo.caption
                binding.captionTextView.visibility = View.VISIBLE
            } else {
                binding.captionTextView.visibility = View.GONE
            }

            // Mostrar uploader
            binding.uploaderTextView.text = "Por: ${photo.uploaderName}"
        }
    }
}
