package com.joseibarra.touristnotify

import android.app.Activity
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.joseibarra.touristnotify.databinding.ActivityPlaceDetailsBinding

class PlaceDetailsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlaceDetailsBinding
    private lateinit var placesClient: PlacesClient
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var reviewAdapter: ReviewAdapter
    private var placeId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlaceDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        placeId = intent.getStringExtra("PLACE_ID")

        if (placeId == null) {
            Toast.makeText(this, "Error: No se encontró el lugar.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setupUI()
        loadPlaceDetails()
        setupReviews()
        loadReviews()
    }

    private fun setupUI() {
        binding.placeNameTextView.text = intent.getStringExtra("PLACE_NAME")
        binding.placeCategoryTextView.text = intent.getStringExtra("PLACE_CATEGORY")
        binding.placeDescriptionTextView.text = intent.getStringExtra("PLACE_DESCRIPTION")

        val placeLat = intent.getDoubleExtra("PLACE_LATITUDE", -1.0)
        val placeLng = intent.getDoubleExtra("PLACE_LONGITUDE", -1.0)

        if (placeLat != -1.0 && placeLng != -1.0) {
            binding.getDirectionsButton.setOnClickListener {
                val resultIntent = Intent().apply {
                    putExtra("DESTINATION_LAT", placeLat)
                    putExtra("DESTINATION_LNG", placeLng)
                }
                setResult(Activity.RESULT_OK, resultIntent)
                finish()
            }
        } else {
            binding.getDirectionsButton.visibility = View.GONE
        }

        binding.submitReviewButton.setOnClickListener {
            submitReview()
        }
    }

    private fun loadPlaceDetails() {
        placeId?.let {
            db.collection("lugares").document(it).get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        val spot = document.toObject(TouristSpot::class.java)
                        spot?.let {
                            binding.averageRatingBar.rating = it.rating.toFloat()
                            binding.totalReviewsTextView.text = "(Basado en ${it.reviewCount} reseñas)"
                        }
                    }
                }
        }
    }

    private fun setupReviews() {
        reviewAdapter = ReviewAdapter(emptyList())
        binding.reviewsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.reviewsRecyclerView.adapter = reviewAdapter
    }

    private fun loadReviews() {
        placeId?.let {
            db.collection("lugares").document(it).collection("reviews")
                .orderBy("timestamp")
                .limit(20)
                .get()
                .addOnSuccessListener { documents ->
                    val reviews = documents.map { doc -> doc.toObject(Review::class.java) }
                    reviewAdapter.updateReviews(reviews)
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error al cargar reseñas: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun submitReview() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "Debes iniciar sesión para dejar una reseña.", Toast.LENGTH_SHORT).show()
            return
        }

        val rating = binding.submitRatingBar.rating
        if (rating == 0f) {
            Toast.makeText(this, "Por favor, selecciona una calificación.", Toast.LENGTH_SHORT).show()
            return
        }

        val comment = binding.reviewEditText.text.toString()
        val placeRef = db.collection("lugares").document(placeId!!)

        db.runTransaction { transaction ->
            val snapshot = transaction.get(placeRef)
            val spot = snapshot.toObject(TouristSpot::class.java)!!

            val newReviewCount = spot.reviewCount + 1
            val newRating = ((spot.rating * spot.reviewCount) + rating) / newReviewCount

            transaction.update(placeRef, "rating", newRating)
            transaction.update(placeRef, "reviewCount", newReviewCount)

            val review = Review(
                userId = currentUser.uid,
                userName = currentUser.displayName ?: "Anónimo",
                rating = rating,
                comment = comment
            )
            transaction.set(placeRef.collection("reviews").document(), review)
            null
        }.addOnSuccessListener {
            Toast.makeText(this, "Reseña enviada con éxito", Toast.LENGTH_SHORT).show()
            binding.submitRatingBar.rating = 0f
            binding.reviewEditText.text.clear()
            loadPlaceDetails() // Recargar datos para mostrar la nueva calificación promedio
            loadReviews()      // Recargar las reseñas para mostrar la nueva
        }.addOnFailureListener { e ->
            Toast.makeText(this, "Error al enviar la reseña: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}