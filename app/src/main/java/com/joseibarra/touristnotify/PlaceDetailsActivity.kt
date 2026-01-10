package com.joseibarra.touristnotify

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.joseibarra.touristnotify.databinding.ActivityPlaceDetailsBinding
import kotlinx.coroutines.launch

class PlaceDetailsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlaceDetailsBinding
    private lateinit var placesClient: PlacesClient
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var reviewAdapter: ReviewAdapter
    private var placeId: String? = null
    private var isFavorite: Boolean = false
    private var placeName: String = ""
    private var placeCategory: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlaceDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        placeId = intent.getStringExtra("PLACE_ID")
        placeName = intent.getStringExtra("PLACE_NAME") ?: ""
        placeCategory = intent.getStringExtra("PLACE_CATEGORY") ?: ""

        if (placeId == null) {
            Toast.makeText(this, "Error: No se encontró el lugar.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setupUI()
        loadPlaceDetails()
        setupReviews()
        loadReviews()
        checkFavoriteStatus()
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

        binding.favoriteButton.setOnClickListener {
            toggleFavorite()
        }

        binding.checkInButton.setOnClickListener {
            performCheckIn()
        }

        binding.viewGalleryButton.setOnClickListener {
            val intent = Intent(this, PhotoGalleryActivity::class.java).apply {
                putExtra("PLACE_ID", placeId)
                putExtra("PLACE_NAME", placeName)
            }
            startActivity(intent)
        }
    }

    private fun checkFavoriteStatus() {
        val currentPlaceId = placeId ?: return

        lifecycleScope.launch {
            isFavorite = FavoritesManager.isFavorite(currentPlaceId)
            updateFavoriteButton()
        }
    }

    private fun updateFavoriteButton() {
        binding.favoriteButton.text = if (isFavorite) "⭐ Favorito" else "☆ Agregar a Favoritos"
    }

    private fun toggleFavorite() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            NotificationHelper.info(binding.root, "Debes iniciar sesión para agregar favoritos")
            return
        }

        val currentPlaceId = placeId ?: return

        lifecycleScope.launch {
            val result = if (isFavorite) {
                FavoritesManager.removeFavorite(currentPlaceId)
            } else {
                FavoritesManager.addFavorite(currentPlaceId, placeName, placeCategory)
            }

            result.onSuccess {
                isFavorite = !isFavorite
                updateFavoriteButton()
                val message = if (isFavorite) "Agregado a favoritos" else "Eliminado de favoritos"
                NotificationHelper.success(binding.root, message)
            }.onFailure { e ->
                NotificationHelper.error(binding.root, "Error: ${e.message}")
            }
        }
    }

    private fun performCheckIn() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            NotificationHelper.info(binding.root, "Debes iniciar sesión para hacer check-in")
            return
        }

        val currentPlaceId = placeId ?: return

        lifecycleScope.launch {
            // Verificar si ya hizo check-in hoy
            val hasCheckedIn = CheckInManager.hasCheckedInToday(currentPlaceId)
            if (hasCheckedIn) {
                NotificationHelper.warning(binding.root, "Ya hiciste check-in aquí hoy")
                return@launch
            }

            val result = CheckInManager.checkIn(currentPlaceId, placeName, placeCategory)

            result.onSuccess {
                NotificationHelper.success(binding.root, "✅ Check-in exitoso!")
            }.onFailure { e ->
                NotificationHelper.error(binding.root, "Error al hacer check-in: ${e.message}")
            }
        }
    }

    private fun loadPlaceDetails() {
        placeId?.let {
            db.collection("lugares").document(it).get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        val spot = document.toObject(TouristSpot::class.java)
                        spot?.let { place ->
                            binding.averageRatingBar.rating = place.rating.toFloat()
                            binding.totalReviewsTextView.text = "(Basado en ${place.reviewCount} reseñas)"

                            // Mostrar horarios
                            if (place.horarios.isNotBlank()) {
                                binding.scheduleContainer.visibility = View.VISIBLE
                                binding.placeScheduleTextView.text = place.horarios
                            }

                            // Mostrar teléfono
                            if (place.telefono.isNotBlank()) {
                                binding.phoneContainer.visibility = View.VISIBLE
                                binding.placePhoneTextView.text = place.telefono
                                binding.phoneContainer.setOnClickListener {
                                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${place.telefono}"))
                                    startActivity(intent)
                                }
                            }

                            // Mostrar sitio web
                            if (place.sitioWeb.isNotBlank()) {
                                binding.websiteContainer.visibility = View.VISIBLE
                                binding.placeWebsiteTextView.text = place.sitioWeb
                                binding.websiteContainer.setOnClickListener {
                                    var url = place.sitioWeb
                                    if (!url.startsWith("http://") && !url.startsWith("https://")) {
                                        url = "https://$url"
                                    }
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    startActivity(intent)
                                }
                            }

                            // Mostrar dirección
                            if (place.direccion.isNotBlank()) {
                                binding.addressContainer.visibility = View.VISIBLE
                                binding.placeAddressTextView.text = place.direccion
                            }

                            // Mostrar precio
                            if (place.precioEstimado.isNotBlank()) {
                                binding.priceContainer.visibility = View.VISIBLE
                                binding.placePriceTextView.text = place.precioEstimado
                            }

                            // Incrementar contador de visitas
                            incrementVisitCount(it)
                        }
                    }
                }
        }
    }

    private fun incrementVisitCount(placeId: String) {
        db.collection("lugares").document(placeId)
            .update("visitCount", FieldValue.increment(1))
            .addOnFailureListener { e ->
                Log.w("PlaceDetails", "Error incrementando visitas", e)
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
            NotificationHelper.info(binding.root, "Debes iniciar sesión para dejar una reseña")
            return
        }

        val rating = binding.submitRatingBar.rating
        if (rating == 0f) {
            NotificationHelper.warning(binding.root, "Por favor, selecciona una calificación")
            return
        }

        val currentPlaceId = placeId
        if (currentPlaceId == null) {
            NotificationHelper.error(binding.root, "Error: ID del lugar no disponible")
            return
        }

        val comment = binding.reviewEditText.text.toString()
        val placeRef = db.collection("lugares").document(currentPlaceId)

        // Verificar si el usuario ya dejó una reseña
        placeRef.collection("reviews")
            .whereEqualTo("userId", currentUser.uid)
            .get()
            .addOnSuccessListener { existingReviews ->
                if (!existingReviews.isEmpty) {
                    NotificationHelper.show(
                        binding.root,
                        "Ya dejaste una reseña para este lugar",
                        NotificationHelper.NotificationType.WARNING,
                        Snackbar.LENGTH_LONG,
                        "Actualizar"
                    ) {
                        updateExistingReview(existingReviews.documents[0].id, rating, comment)
                    }
                } else {
                    submitNewReview(placeRef, currentUser.uid, currentUser.displayName ?: "Anónimo", rating, comment)
                }
            }
            .addOnFailureListener { e ->
                NotificationHelper.error(binding.root, "Error al verificar reseñas: ${e.message}")
            }
    }

    private fun submitNewReview(placeRef: com.google.firebase.firestore.DocumentReference, userId: String, userName: String, rating: Float, comment: String) {
        db.runTransaction { transaction ->
            val snapshot = transaction.get(placeRef)
            val spot = snapshot.toObject(TouristSpot::class.java)
                ?: throw Exception("No se pudo cargar la información del lugar")

            val newReviewCount = spot.reviewCount + 1
            val newRating = ((spot.rating * spot.reviewCount) + rating) / newReviewCount

            transaction.update(placeRef, "rating", newRating)
            transaction.update(placeRef, "reviewCount", newReviewCount)

            val review = Review(
                userId = userId,
                userName = userName,
                rating = rating,
                comment = comment
            )
            transaction.set(placeRef.collection("reviews").document(), review)
            null
        }.addOnSuccessListener {
            NotificationHelper.reviewSubmitted(binding.root)
            binding.submitRatingBar.rating = 0f
            binding.reviewEditText.text.clear()
            loadPlaceDetails()
            loadReviews()
        }.addOnFailureListener { e ->
            NotificationHelper.error(binding.root, "Error al enviar la reseña: ${e.message}")
        }
    }

    private fun updateExistingReview(reviewId: String, rating: Float, comment: String) {
        val currentPlaceId = placeId ?: return
        val placeRef = db.collection("lugares").document(currentPlaceId)

        db.runTransaction { transaction ->
            val snapshot = transaction.get(placeRef)
            val spot = snapshot.toObject(TouristSpot::class.java)
                ?: throw Exception("No se pudo cargar la información del lugar")

            // Obtener la reseña anterior
            val oldReviewSnapshot = transaction.get(placeRef.collection("reviews").document(reviewId))
            val oldReview = oldReviewSnapshot.toObject(Review::class.java)
                ?: throw Exception("No se encontró la reseña anterior")

            // Recalcular rating
            val totalRating = spot.rating * spot.reviewCount
            val newTotalRating = totalRating - oldReview.rating + rating
            val newRating = newTotalRating / spot.reviewCount

            transaction.update(placeRef, "rating", newRating)

            // Actualizar la reseña
            transaction.update(
                placeRef.collection("reviews").document(reviewId),
                mapOf(
                    "rating" to rating,
                    "comment" to comment
                )
            )
            null
        }.addOnSuccessListener {
            NotificationHelper.success(binding.root, "Reseña actualizada exitosamente")
            binding.submitRatingBar.rating = 0f
            binding.reviewEditText.text.clear()
            loadPlaceDetails()
            loadReviews()
        }.addOnFailureListener { e ->
            NotificationHelper.error(binding.root, "Error al actualizar la reseña: ${e.message}")
        }
    }
}