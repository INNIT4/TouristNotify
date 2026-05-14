package com.joseibarra.trazago

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.joseibarra.trazago.databinding.ActivityPlaceDetailsBinding
import com.joseibarra.trazago.ui.BaseActivity
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class PlaceDetailsActivity : BaseActivity() {

    private lateinit var binding: ActivityPlaceDetailsBinding
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var reviewAdapter: ReviewAdapter
    private lateinit var reviewRepository: ReviewRepository
    private var placeId: String? = null
    private var isFavorite: Boolean = false
    private var placeName: String = ""
    private var placeCategory: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        // Postpone enter transition para esperar la imagen hero. Si no se carga
        // en 500ms (caso actual sin red o sin URL), igual se inicia.
        supportPostponeEnterTransition()
        super.onCreate(savedInstanceState)
        binding = ActivityPlaceDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Timeout de fallback para shared element: nunca dejar la transición postpuesta indefinidamente
        binding.placeHeroImage.postDelayed({ supportStartPostponedEnterTransition() }, 500L)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        reviewRepository = ReviewRepository(db)

        // Obtener placeId de varias fuentes.
        // PEN-008: Esta Activity es `exported="true"` porque acepta deep links.
        // Eso significa que cualquier app externa puede invocarla con extras
        // arbitrarios. Estrategia defensiva:
        //   - Validar `PLACE_ID` con el mismo regex que los deep links.
        //   - NO confiar en `PLACE_NAME` / `PLACE_CATEGORY` cuando la fuente es
        //     externa: vamos a re-cargar los datos desde Firestore en
        //     loadPlaceDetails() y sobreescribir lo que se muestra.
        // Leer PlaceSummary (callers internos) con fallback a extras sueltos (deep links / externos).
        val summary = intent?.getParcelableExtra<PlaceSummary>(PlaceSummary.EXTRA_KEY)

        val rawId = when {
            intent?.data != null -> {
                val id = DeepLinkResolver.resolvePlaceId(intent.data!!)
                if (id != null && BuildConfig.DEBUG) Log.d(TAG, "Deep link detected: placeId = $id")
                if (id != null) Toast.makeText(this, getString(R.string.qr_opening), Toast.LENGTH_SHORT).show()
                id
            }
            summary != null -> summary.id
            intent.hasExtra("PLACE_ID") -> intent.getStringExtra("PLACE_ID")
            else -> null
        }

        placeId = when {
            rawId.isNullOrBlank() -> null
            PLACE_ID_PATTERN.matches(rawId) -> rawId
            else -> {
                if (BuildConfig.DEBUG) Log.w(TAG, "PLACE_ID rejected: invalid format")
                null
            }
        }

        // Estos valores son solo "hint" mientras Firestore carga los datos reales.
        // Sanitizamos para evitar inyectar markdown/control en la UI.
        placeName = (summary?.nombre ?: intent.getStringExtra("PLACE_NAME") ?: "").take(120)
            .replace(Regex("""[\n\r\t]"""), " ")
        placeCategory = (summary?.categoria ?: intent.getStringExtra("PLACE_CATEGORY") ?: "").take(60)
            .replace(Regex("""[\n\r\t]"""), " ")

        if (placeId == null) {
            Toast.makeText(this, getString(R.string.place_not_found), Toast.LENGTH_LONG).show()
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

        binding.shareButton.setOnClickListener {
            sharePlaceLink()
        }
    }

    private fun sharePlaceLink() {
        val id = placeId ?: return
        val name = placeName.ifBlank { getString(R.string.app_name) }
        val deepLink = "trazago://place/$id"
        val shareText = getString(R.string.share_place_text, name, deepLink)

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_via)))
    }

    private fun checkFavoriteStatus() {
        val currentPlaceId = placeId ?: return

        lifecycleScope.launch {
            isFavorite = FavoritesManager.instance.isFavorite(currentPlaceId)
            updateFavoriteButton()
        }
    }

    private fun updateFavoriteButton() {
        binding.favoriteButton.text = if (isFavorite) getString(R.string.favorite_label_active) else getString(R.string.favorite_label_inactive)
    }

    private fun toggleFavorite() {
        AuthManager.requireAuth(this, AuthManager.AuthRequired.SAVE_FAVORITES) {
            val currentPlaceId = placeId ?: return@requireAuth

            lifecycleScope.launch {
                val result = if (isFavorite) {
                    FavoritesManager.instance.removeFavorite(currentPlaceId)
                } else {
                    FavoritesManager.instance.addFavorite(currentPlaceId, placeName, placeCategory)
                }

                result.onSuccess {
                    isFavorite = !isFavorite
                    updateFavoriteButton()
                    val message = if (isFavorite) getString(R.string.favorite_added) else getString(R.string.favorite_removed)
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
                val hasCheckedIn = CheckInManager.instance.hasCheckedInToday(currentPlaceId)
                if (hasCheckedIn) {
                    NotificationHelper.warning(binding.root, getString(R.string.checkin_already_today))
                    return@launch
                }

                val result = CheckInManager.instance.checkIn(currentPlaceId, placeName, placeCategory)

                result.onSuccess {
                    NotificationHelper.success(binding.root, getString(R.string.checkin_success))
                }.onFailure { e ->
                    NotificationHelper.error(binding.root, getString(R.string.checkin_error, e.message))
                }
            }
        }
    }

    private fun loadPlaceDetails() {
        val id = placeId ?: return
        lifecycleScope.launch {
            try {
                val document = db.collection("lugares").document(id).get().await()
                if (document != null && document.exists()) {
                    val spot = document.toObject(TouristSpot::class.java)
                    spot?.let { place ->
                        binding.averageRatingBar.rating = place.rating.coerceIn(0.0, 5.0).toFloat()
                        binding.totalReviewsTextView.text = getString(R.string.reviews_based_on, place.reviewCount)

                        if (placeName.isBlank() && place.nombre.isNotBlank()) {
                            placeName = place.nombre
                            binding.placeNameTextView.text = placeName
                        }

                        if (placeCategory.isBlank() && place.categoria.isNotBlank()) {
                            placeCategory = place.categoria
                            binding.placeCategoryTextView.text = placeCategory
                        }

                        if (place.horarios.isNotBlank()) {
                            binding.scheduleContainer.visibility = View.VISIBLE
                            binding.placeScheduleTextView.text = place.horarios
                        }

                        if (place.telefono.isNotBlank()) {
                            binding.phoneContainer.visibility = View.VISIBLE
                            binding.placePhoneTextView.text = place.telefono
                            binding.phoneContainer.setOnClickListener {
                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${place.telefono}"))
                                startActivity(intent)
                            }
                        }

                        if (place.sitioWeb.isNotBlank()) {
                            binding.websiteContainer.visibility = View.VISIBLE
                            binding.placeWebsiteTextView.text = place.sitioWeb
                            binding.placeWebsiteTextView.paintFlags =
                                binding.placeWebsiteTextView.paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG
                            binding.websiteContainer.contentDescription =
                                getString(R.string.a11y_website_link, place.sitioWeb)
                            binding.websiteContainer.setOnClickListener {
                                var url = place.sitioWeb
                                if (url.startsWith("http://")) {
                                    url = url.replaceFirst("http://", "https://")
                                } else if (!url.startsWith("https://")) {
                                    url = "https://$url"
                                }
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    startActivity(intent)
                                } catch (e: Exception) {
                                    NotificationHelper.error(binding.root, getString(R.string.url_invalid))
                                }
                            }
                        }

                        if (place.direccion.isNotBlank()) {
                            binding.addressContainer.visibility = View.VISIBLE
                            binding.placeAddressTextView.text = place.direccion
                        }

                        if (place.precioEstimado.isNotBlank()) {
                            binding.priceContainer.visibility = View.VISIBLE
                            binding.placePriceTextView.text = place.precioEstimado
                        }

                        incrementVisitCount(id)
                    }
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.w(TAG, "Error cargando detalles del lugar", e)
                NotificationHelper.error(binding.root, getString(R.string.error_loading_place))
            }
        }
    }

    private fun incrementVisitCount(placeId: String) {
        db.collection("lugares").document(placeId)
            .update("visitCount", FieldValue.increment(1))
            .addOnFailureListener { e ->
                if (BuildConfig.DEBUG) Log.w(TAG, "Error incrementando visitas", e)
            }
    }

    private fun setupReviews() {
        reviewAdapter = ReviewAdapter(emptyList())
        binding.reviewsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.reviewsRecyclerView.adapter = reviewAdapter
    }

    private fun loadReviews() {
        val id = placeId ?: return
        lifecycleScope.launch {
            reviewRepository.loadReviews(id)
                .onSuccess { reviews -> reviewAdapter.updateReviews(reviews) }
                .onFailure { NotificationHelper.error(binding.root, getString(R.string.error_loading_reviews)) }
        }
    }

    private fun submitReview() {
        AuthManager.requireAuth(this, AuthManager.AuthRequired.LEAVE_REVIEWS) {
            val currentUser = auth.currentUser ?: return@requireAuth
            val rating = binding.submitRatingBar.rating
            if (rating == 0f) {
                NotificationHelper.warning(binding.root, getString(R.string.select_rating))
                return@requireAuth
            }
            val currentPlaceId = placeId ?: run {
                NotificationHelper.error(binding.root, getString(R.string.place_id_unavailable))
                return@requireAuth
            }
            val comment = binding.reviewEditText.text.toString()
            binding.submitReviewButton.isEnabled = false
            lifecycleScope.launch {
                try {
                    val existingId = reviewRepository.findExistingReview(currentPlaceId, currentUser.uid)
                    if (existingId != null) {
                        binding.submitReviewButton.isEnabled = true
                        NotificationHelper.show(
                            binding.root,
                            getString(R.string.review_already_exists),
                            NotificationHelper.NotificationType.WARNING,
                            Snackbar.LENGTH_LONG,
                            getString(R.string.review_update)
                        ) {
                            lifecycleScope.launch {
                                reviewRepository.updateExistingReview(currentPlaceId, existingId, rating, comment)
                                    .onSuccess {
                                        NotificationHelper.success(binding.root, getString(R.string.review_updated))
                                        binding.submitRatingBar.rating = 0f
                                        binding.reviewEditText.text.clear()
                                        loadPlaceDetails()
                                        loadReviews()
                                    }
                                    .onFailure { e ->
                                        NotificationHelper.error(binding.root, getString(R.string.error_update_review, e.message))
                                    }
                            }
                        }
                    } else {
                        val userName = currentUser.displayName ?: getString(R.string.anonymous)
                        reviewRepository.submitNewReview(currentPlaceId, currentUser.uid, userName, rating, comment)
                            .onSuccess {
                                binding.submitReviewButton.isEnabled = true
                                NotificationHelper.reviewSubmitted(binding.root)
                                binding.submitRatingBar.rating = 0f
                                binding.reviewEditText.text.clear()
                                loadPlaceDetails()
                                loadReviews()
                            }
                            .onFailure { e ->
                                binding.submitReviewButton.isEnabled = true
                                NotificationHelper.error(binding.root, getString(R.string.error_submit_review, e.message))
                            }
                    }
                } catch (e: Exception) {
                    binding.submitReviewButton.isEnabled = true
                    NotificationHelper.error(binding.root, getString(R.string.error_verify_reviews, e.message))
                }
            }
        }
    }

    companion object {
        private const val TAG = "PlaceDetailsActivity"

        /**
         * Formato permitido para placeId en deep links.
         * 1-64 caracteres alfanuméricos, guion bajo o medio.
         * Bloquea inyecciones de URL/HTML/whitespace en el texto compartido.
         */
        private val PLACE_ID_PATTERN = Regex("^[A-Za-z0-9_-]{1,64}$")
    }
}