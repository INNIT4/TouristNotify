package com.joseibarra.touristnotify

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.FirebaseFirestore
import com.joseibarra.touristnotify.databinding.ActivityAiRecommendationsBinding
import kotlinx.coroutines.launch

/**
 * Actividad para mostrar recomendaciones personalizadas generadas por IA
 */
class AIRecommendationsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAiRecommendationsBinding
    private lateinit var recommendationAdapter: RecommendationAdapter
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAiRecommendationsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.title = "Recomendaciones para Ti"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        setupRecyclerView()
        initializeEngine()
        loadRecommendations()

        binding.refreshButton.setOnClickListener {
            loadRecommendations()
        }
    }

    private fun setupRecyclerView() {
        recommendationAdapter = RecommendationAdapter { recommendation ->
            // Al hacer click en una recomendación, abrir detalles del lugar
            openPlaceDetails(recommendation)
        }

        binding.recommendationsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@AIRecommendationsActivity)
            adapter = recommendationAdapter
        }
    }

    private fun initializeEngine() {
        try {
            val apiKey = BuildConfig.GEMINI_API_KEY
            if (apiKey.isBlank()) {
                NotificationHelper.error(binding.root, "API Key no configurada")
                return
            }
            RecommendationEngine.initialize(apiKey)
        } catch (e: Exception) {
            NotificationHelper.error(binding.root, "Error al inicializar motor de recomendaciones")
        }
    }

    private fun loadRecommendations() {
        binding.progressBar.visibility = View.VISIBLE
        binding.emptyStateTextView.visibility = View.GONE
        binding.refreshButton.isEnabled = false

        lifecycleScope.launch {
            val result = RecommendationEngine.generateRecommendations()

            result.onSuccess { recommendations ->
                binding.progressBar.visibility = View.GONE
                binding.refreshButton.isEnabled = true

                if (recommendations.isEmpty()) {
                    binding.emptyStateTextView.visibility = View.VISIBLE
                    binding.emptyStateTextView.text = "🎯\n\nNo hay suficientes datos\n\n" +
                            "Explora más lugares y agrega favoritos para recibir recomendaciones personalizadas"
                } else {
                    recommendationAdapter.submitList(recommendations)
                    NotificationHelper.success(binding.root, "✨ ${recommendations.size} recomendaciones generadas")
                }
            }.onFailure { error ->
                binding.progressBar.visibility = View.GONE
                binding.refreshButton.isEnabled = true
                binding.emptyStateTextView.visibility = View.VISIBLE
                binding.emptyStateTextView.text = "❌\n\nError al generar recomendaciones\n\n${error.message}"
                NotificationHelper.error(binding.root, "Error: ${error.message}")
            }
        }
    }

    private fun openPlaceDetails(recommendation: AIRecommendation) {
        // Buscar el lugar en Firestore para obtener todos sus datos
        binding.progressBar.visibility = View.VISIBLE

        db.collection("lugares_turisticos")
            .document(recommendation.placeId)
            .get()
            .addOnSuccessListener { document ->
                binding.progressBar.visibility = View.GONE

                if (document.exists()) {
                    val place = document.toObject(TouristSpot::class.java)
                    if (place != null) {
                        val intent = Intent(this, PlaceDetailsActivity::class.java).apply {
                            putExtra("PLACE_ID", place.id)
                            putExtra("PLACE_NAME", place.nombre)
                            putExtra("PLACE_CATEGORY", place.categoria)
                            putExtra("PLACE_DESCRIPTION", place.descripcion)
                            place.ubicacion?.let { location ->
                                putExtra("PLACE_LATITUDE", location.latitude)
                                putExtra("PLACE_LONGITUDE", location.longitude)
                            }
                        }
                        startActivity(intent)
                    }
                } else {
                    NotificationHelper.error(binding.root, "Lugar no encontrado")
                }
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                NotificationHelper.error(binding.root, "Error al cargar lugar: ${e.message}")
            }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
