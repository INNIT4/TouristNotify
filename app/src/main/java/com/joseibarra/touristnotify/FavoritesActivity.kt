package com.joseibarra.touristnotify

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.joseibarra.touristnotify.databinding.ActivityFavoritesBinding
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class FavoritesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFavoritesBinding
    private lateinit var db: FirebaseFirestore
    private lateinit var adapter: FavoritePlacesAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Verificar autenticación antes de acceder a favoritos
        if (!AuthManager.requireAuth(this, AuthManager.AuthRequired.MY_FAVORITES) {
                initializeActivity()
            }) {
            // Si no está autenticado, cerrar activity
            finish()
            return
        }
    }

    private fun initializeActivity() {
        binding = ActivityFavoritesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = FirebaseFirestore.getInstance()

        setupRecyclerView()
        loadFavorites()
    }

    private fun setupRecyclerView() {
        adapter = FavoritePlacesAdapter(
            onItemClicked = { place ->
                openPlaceDetails(place)
            },
            onRemoveClicked = { favorite ->
                removeFavorite(favorite)
            }
        )
        binding.favoritesRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.favoritesRecyclerView.adapter = adapter
    }

    private fun loadFavorites() {
        binding.progressBar.visibility = View.VISIBLE
        binding.favoritesRecyclerView.visibility = View.GONE
        binding.emptyTextView.visibility = View.GONE

        lifecycleScope.launch {
            // CR-006: migración one-time del path legacy antes de cargar favoritos
            FavoritesManager.instance.migrateFromLegacyPath(this@FavoritesActivity)

            val result = FavoritesManager.instance.getFavorites()

            result.onSuccess { favoritesList ->
                if (favoritesList.isEmpty()) {
                    binding.progressBar.visibility = View.GONE
                    binding.emptyTextView.visibility = View.VISIBLE
                } else {
                    loadPlaceDetails(favoritesList)
                }
            }.onFailure { e ->
                binding.progressBar.visibility = View.GONE
                binding.emptyTextView.visibility = View.VISIBLE
                e.handleFirestoreError(
                    context = this@FavoritesActivity,
                    view = binding.root,
                    operation = "cargar tus favoritos"
                )
            }
        }
    }

    private fun loadPlaceDetails(favoritesList: List<Favorite>) {
        if (favoritesList.isEmpty()) {
            binding.progressBar.visibility = View.GONE
            binding.emptyTextView.visibility = View.VISIBLE
            return
        }
        lifecycleScope.launch {
            try {
                // PERF-002: whereIn en lugar de N reads secuenciales (1 round-trip por chunk de 30 IDs)
                val idToFavorite = favoritesList.associateBy { it.placeId }
                val loadedFavorites = mutableListOf<Pair<Favorite, TouristSpot>>()

                idToFavorite.keys.toList().chunked(30).forEach { chunk ->
                    val docs = db.collection(FirestoreCollections.PLACES)
                        .whereIn(FieldPath.documentId(), chunk)
                        .get()
                        .await()
                    docs.documents.forEach { doc ->
                        val place = doc.toObject(TouristSpot::class.java)?.copy(id = doc.id)
                        val favorite = idToFavorite[doc.id]
                        if (place != null && favorite != null) {
                            loadedFavorites.add(Pair(favorite, place))
                        }
                    }
                }

                binding.progressBar.visibility = View.GONE
                if (loadedFavorites.isEmpty()) {
                    binding.emptyTextView.visibility = View.VISIBLE
                } else {
                    binding.favoritesRecyclerView.visibility = View.VISIBLE
                    adapter.submitList(loadedFavorites)
                }
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                android.util.Log.e("FavoritesActivity", "Error cargando detalles de favoritos", e)
                binding.emptyTextView.visibility = View.VISIBLE
            }
        }
    }

    private fun removeFavorite(favorite: Favorite) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.remove_favorite_title))
            .setMessage(getString(R.string.remove_favorite_message, favorite.placeName))
            .setPositiveButton(getString(R.string.delete)) { _, _ -> doRemoveFavorite(favorite) }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun doRemoveFavorite(favorite: Favorite) {
        lifecycleScope.launch {
            val result = FavoritesManager.instance.removeFavorite(favorite.placeId)

            result.onSuccess {
                val updatedList = adapter.currentList.filter { it.first.placeId != favorite.placeId }
                adapter.submitList(updatedList)
                NotificationHelper.success(binding.root, getString(R.string.favorite_removed))

                if (updatedList.isEmpty()) {
                    binding.favoritesRecyclerView.visibility = View.GONE
                    binding.emptyTextView.visibility = View.VISIBLE
                }
            }.onFailure { e ->
                android.util.Log.e("FavoritesActivity", "Error al eliminar favorito: ${e.message}", e)
                NotificationHelper.error(binding.root, getString(R.string.error_generic))
            }
        }
    }

    private fun openPlaceDetails(place: TouristSpot) {
        val intent = Intent(this, PlaceDetailsActivity::class.java).apply {
            putExtra(PlaceSummary.EXTRA_KEY, PlaceSummary.fromTouristSpot(place))
        }
        startActivity(intent)
    }
}
