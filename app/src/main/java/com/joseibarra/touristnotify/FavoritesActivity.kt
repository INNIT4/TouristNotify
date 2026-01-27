package com.joseibarra.touristnotify

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.FirebaseFirestore
import com.joseibarra.touristnotify.databinding.ActivityFavoritesBinding
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class FavoritesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFavoritesBinding
    private lateinit var db: FirebaseFirestore
    private lateinit var adapter: FavoritePlacesAdapter
    private val favorites = mutableListOf<Pair<Favorite, TouristSpot>>()

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
            favorites = favorites,
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
            val result = FavoritesManager.getFavorites()

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
        lifecycleScope.launch {
            favorites.clear()

            favoritesList.forEach { favorite ->
                try {
                    val doc = db.collection("lugares").document(favorite.placeId).get().await()
                    val place = doc.toObject(TouristSpot::class.java)?.copy(id = doc.id)
                    if (place != null) {
                        favorites.add(Pair(favorite, place))
                    }
                } catch (e: Exception) {
                    // Continuar con el siguiente
                }
            }

            binding.progressBar.visibility = View.GONE
            if (favorites.isEmpty()) {
                binding.emptyTextView.visibility = View.VISIBLE
            } else {
                binding.favoritesRecyclerView.visibility = View.VISIBLE
                adapter.notifyDataSetChanged()
            }
        }
    }

    private fun removeFavorite(favorite: Favorite) {
        lifecycleScope.launch {
            val result = FavoritesManager.removeFavorite(favorite.placeId)

            result.onSuccess {
                favorites.removeIf { it.first.placeId == favorite.placeId }
                adapter.notifyDataSetChanged()
                NotificationHelper.success(binding.root, "Eliminado de favoritos")

                if (favorites.isEmpty()) {
                    binding.favoritesRecyclerView.visibility = View.GONE
                    binding.emptyTextView.visibility = View.VISIBLE
                }
            }.onFailure { e ->
                NotificationHelper.error(binding.root, "Error: ${e.message}")
            }
        }
    }

    private fun openPlaceDetails(place: TouristSpot) {
        val intent = Intent(this, PlaceDetailsActivity::class.java).apply {
            putExtra("PLACE_ID", place.id)
            putExtra("PLACE_NAME", place.nombre)
            putExtra("PLACE_CATEGORY", place.categoria)
            putExtra("PLACE_DESCRIPTION", place.descripcion)
            putExtra("GOOGLE_PLACE_ID", place.googlePlaceId)
            place.ubicacion?.let {
                putExtra("PLACE_LATITUDE", it.latitude)
                putExtra("PLACE_LONGITUDE", it.longitude)
            }
        }
        startActivity(intent)
    }
}
