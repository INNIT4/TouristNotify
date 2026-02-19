package com.joseibarra.touristnotify

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.joseibarra.touristnotify.databinding.ActivityPlaceDetailsBinding
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.UUID

class PlaceDetailsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlaceDetailsBinding
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var storage: FirebaseStorage
    private lateinit var reviewAdapter: ReviewAdapter
    private var placeId: String? = null
    private var isFavorite: Boolean = false
    private var placeName: String = ""
    private var placeCategory: String = ""
    private var reviewImageUri: Uri? = null

    private val reviewImagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                reviewImageUri = uri
                binding.reviewImagePreview.setImageURI(uri)
                binding.reviewImagePreview.visibility = View.VISIBLE
                binding.removeReviewImageButton.visibility = View.VISIBLE
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlaceDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        storage = FirebaseStorage.getInstance()

        // Obtener placeId de varias fuentes
        placeId = when {
            // 1. Desde deep link (QR escaneado con cámara nativa)
            intent?.data != null -> {
                handleDeepLink(intent.data)
            }
            // 2. Desde navegación normal de la app
            intent.hasExtra("PLACE_ID") -> {
                intent.getStringExtra("PLACE_ID")
            }
            else -> null
        }

        placeName = intent.getStringExtra("PLACE_NAME") ?: ""
        placeCategory = intent.getStringExtra("PLACE_CATEGORY") ?: ""

        if (placeId == null) {
            Toast.makeText(this, "Error: No se encontró el lugar.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setupUI()
        setupLockedFeaturesUI()
        loadPlaceDetails()
        setupReviews()
        loadReviews()
        checkFavoriteStatus()
    }

    /**
     * Procesa deep links de códigos QR
     * Soporta formatos:
     * - touristnotify://place/{placeId}
     * - https://touristnotify.app/place/{placeId}
     */
    private fun handleDeepLink(uri: Uri?): String? {
        if (uri == null) return null

        return try {
            when (uri.scheme) {
                // touristnotify://place/abc123
                "touristnotify" -> {
                    if (uri.host == "place") {
                        uri.lastPathSegment
                    } else {
                        null
                    }
                }
                // https://touristnotify.app/place/abc123
                "https", "http" -> {
                    if (uri.host != "touristnotify.app") return null
                    val pathSegments = uri.pathSegments
                    if (pathSegments.size >= 2 && pathSegments[0] == "place") {
                        pathSegments[1]
                    } else {
                        null
                    }
                }
                else -> null
            }?.also {
                Log.d(TAG, "Deep link detected: placeId = $it")
                Toast.makeText(
                    this,
                    "📱 Abriendo lugar desde código QR...",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing deep link", e)
            null
        }
    }

    private fun setupLockedFeaturesUI() {
        // Si el usuario no está autenticado, aplicar estilo bloqueado
        if (!AuthManager.isAuthenticated()) {
            // Favoritos
            binding.favoriteButton.alpha = 0.5f
            binding.favoriteButton.icon = getDrawable(R.drawable.ic_lock_outline_black_24dp)

            // Check-in
            binding.checkInButton.alpha = 0.5f
            binding.checkInButton.icon = getDrawable(R.drawable.ic_lock_outline_black_24dp)

            // Reseñas
            binding.submitReviewButton.alpha = 0.5f
            binding.submitRatingBar.alpha = 0.5f
            binding.reviewEditText.alpha = 0.5f
        }
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
        AuthManager.requireAuth(this, AuthManager.AuthRequired.SAVE_FAVORITES) {
            val currentPlaceId = placeId ?: return@requireAuth

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
    }

    private fun performCheckIn() {
        AuthManager.requireAuth(this, AuthManager.AuthRequired.CHECK_IN) {
            val currentPlaceId = placeId ?: return@requireAuth

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

                                    // SEGURIDAD: Forzar HTTPS para proteger datos del usuario
                                    if (url.startsWith("http://")) {
                                        url = url.replaceFirst("http://", "https://")
                                    } else if (!url.startsWith("https://")) {
                                        url = "https://$url"
                                    }

                                    // Validar formato de URL
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                        startActivity(intent)
                                    } catch (e: Exception) {
                                        NotificationHelper.error(binding.root, "URL no válida")
                                    }
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

        binding.addReviewImageButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK).apply { type = "image/*" }
            reviewImagePickerLauncher.launch(intent)
        }
        binding.removeReviewImageButton.setOnClickListener {
            reviewImageUri = null
            binding.reviewImagePreview.setImageDrawable(null)
            binding.reviewImagePreview.visibility = View.GONE
            binding.removeReviewImageButton.visibility = View.GONE
        }
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
        AuthManager.requireAuth(this, AuthManager.AuthRequired.LEAVE_REVIEWS) {
            val currentUser = auth.currentUser ?: return@requireAuth

            val rating = binding.submitRatingBar.rating
            if (rating == 0f) {
                NotificationHelper.warning(binding.root, "Por favor, selecciona una calificación")
                return@requireAuth
            }

            val currentPlaceId = placeId
            if (currentPlaceId == null) {
                NotificationHelper.error(binding.root, "Error: ID del lugar no disponible")
                return@requireAuth
            }

            val comment = binding.reviewEditText.text.toString().trim()
            if (comment.isNotEmpty() && comment.length < 10) {
                NotificationHelper.warning(binding.root, "La reseña debe tener al menos 10 caracteres")
                return@requireAuth
            }
            if (comment.length > 500) {
                NotificationHelper.warning(binding.root, "La reseña no puede superar los 500 caracteres")
                return@requireAuth
            }

            val placeRef = db.collection("lugares").document(currentPlaceId)
            val imageUri = reviewImageUri

            // Si hay imagen, comprimirla y subirla primero
            if (imageUri != null) {
                val compressed = compressReviewImage(imageUri)
                if (compressed == null) {
                    NotificationHelper.error(binding.root, "Error al procesar la imagen")
                    return@requireAuth
                }
                val photoId = UUID.randomUUID().toString()
                val storageRef = storage.reference
                    .child("review_photos")
                    .child(currentPlaceId)
                    .child("$photoId.jpg")

                binding.submitReviewButton.isEnabled = false
                storageRef.putBytes(compressed)
                    .continueWithTask { task ->
                        if (!task.isSuccessful) task.exception?.let { throw it }
                        storageRef.downloadUrl
                    }.addOnSuccessListener { downloadUri ->
                        checkAndSubmitReview(placeRef, currentUser, rating, comment, downloadUri.toString())
                    }.addOnFailureListener { e ->
                        binding.submitReviewButton.isEnabled = true
                        NotificationHelper.error(binding.root, "Error al subir la imagen: ${e.message}")
                    }
            } else {
                checkAndSubmitReview(placeRef, currentUser, rating, comment, "")
            }
        }
    }

    private fun checkAndSubmitReview(
        placeRef: com.google.firebase.firestore.DocumentReference,
        currentUser: com.google.firebase.auth.FirebaseUser,
        rating: Float,
        comment: String,
        imageUrl: String
    ) {
        // Verificar si el usuario ya dejó una reseña
        placeRef.collection("reviews")
            .whereEqualTo("userId", currentUser.uid)
            .get()
            .addOnSuccessListener { existingReviews ->
                binding.submitReviewButton.isEnabled = true
                if (!existingReviews.isEmpty) {
                    NotificationHelper.show(
                        binding.root,
                        "Ya dejaste una reseña para este lugar",
                        NotificationHelper.NotificationType.WARNING,
                        Snackbar.LENGTH_LONG,
                        "Actualizar"
                    ) {
                        updateExistingReview(existingReviews.documents[0].id, rating, comment, imageUrl)
                    }
                } else {
                    submitNewReview(placeRef, currentUser.uid, currentUser.displayName ?: "Anónimo", rating, comment, imageUrl)
                }
            }
            .addOnFailureListener { e ->
                binding.submitReviewButton.isEnabled = true
                NotificationHelper.error(binding.root, "Error al verificar reseñas: ${e.message}")
            }
    }

    private fun submitNewReview(placeRef: com.google.firebase.firestore.DocumentReference, userId: String, userName: String, rating: Float, comment: String, imageUrl: String) {
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
                comment = comment,
                imageUrl = imageUrl
            )
            transaction.set(placeRef.collection("reviews").document(), review)
            null
        }.addOnSuccessListener {
            NotificationHelper.reviewSubmitted(binding.root)
            binding.submitRatingBar.rating = 0f
            binding.reviewEditText.text.clear()
            clearReviewImage()
            loadPlaceDetails()
            loadReviews()
        }.addOnFailureListener { e ->
            NotificationHelper.error(binding.root, "Error al enviar la reseña: ${e.message}")
        }
    }

    private fun updateExistingReview(reviewId: String, rating: Float, comment: String, imageUrl: String) {
        if (comment.isNotEmpty() && comment.length < 10) {
            NotificationHelper.warning(binding.root, "La reseña debe tener al menos 10 caracteres")
            return
        }
        if (comment.length > 500) {
            NotificationHelper.warning(binding.root, "La reseña no puede superar los 500 caracteres")
            return
        }
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

            val updateMap = mutableMapOf<String, Any>("rating" to rating, "comment" to comment)
            if (imageUrl.isNotBlank()) updateMap["imageUrl"] = imageUrl

            // Actualizar la reseña
            transaction.update(
                placeRef.collection("reviews").document(reviewId),
                updateMap
            )
            null
        }.addOnSuccessListener {
            NotificationHelper.success(binding.root, "Reseña actualizada exitosamente")
            binding.submitRatingBar.rating = 0f
            binding.reviewEditText.text.clear()
            clearReviewImage()
            loadPlaceDetails()
            loadReviews()
        }.addOnFailureListener { e ->
            NotificationHelper.error(binding.root, "Error al actualizar la reseña: ${e.message}")
        }
    }

    private fun clearReviewImage() {
        reviewImageUri = null
        binding.reviewImagePreview.setImageDrawable(null)
        binding.reviewImagePreview.visibility = View.GONE
        binding.removeReviewImageButton.visibility = View.GONE
    }

    private fun compressReviewImage(uri: Uri): ByteArray? {
        return try {
            val inputStream = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            val maxSize = 1080f
            val scale = if (bitmap.width > bitmap.height) maxSize / bitmap.width else maxSize / bitmap.height
            val scaled = Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt(),
                (bitmap.height * scale).toInt(),
                true
            )
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 80, out)
            bitmap.recycle()
            scaled.recycle()
            out.toByteArray()
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e("PlaceDetails", "Error comprimiendo imagen de reseña", e)
            null
        }
    }

    companion object {
        private const val TAG = "PlaceDetailsActivity"
    }
}