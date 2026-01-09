package com.joseibarra.touristnotify

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.FirebaseFirestore
import com.joseibarra.touristnotify.databinding.ActivityComparatorBinding

/**
 * Activity para seleccionar y comparar lugares turísticos
 * Permite comparar hasta 3 lugares lado a lado
 */
class ComparatorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityComparatorBinding
    private lateinit var db: FirebaseFirestore
    private lateinit var adapter: PlaceSelectionAdapter
    private val allPlaces = mutableListOf<TouristSpot>()
    private val selectedPlaces = mutableListOf<TouristSpot>()

    companion object {
        private const val MAX_SELECTION = 3
        const val EXTRA_SELECTED_PLACES = "selected_places"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityComparatorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = FirebaseFirestore.getInstance()

        setupRecyclerView()
        setupCompareButton()
        loadPlaces()
    }

    private fun setupRecyclerView() {
        adapter = PlaceSelectionAdapter(allPlaces, selectedPlaces, MAX_SELECTION) { place, isSelected ->
            handlePlaceSelection(place, isSelected)
        }
        binding.placesRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.placesRecyclerView.adapter = adapter
    }

    private fun setupCompareButton() {
        binding.compareButton.setOnClickListener {
            if (selectedPlaces.size >= 2) {
                openComparison()
            } else {
                NotificationHelper.warning(binding.root, "Selecciona al menos 2 lugares para comparar")
            }
        }
    }

    private fun loadPlaces() {
        binding.progressBar.visibility = View.VISIBLE
        binding.emptyStateContainer.visibility = View.GONE

        db.collection("lugares")
            .get()
            .addOnSuccessListener { documents ->
                binding.progressBar.visibility = View.GONE
                allPlaces.clear()

                if (documents.isEmpty) {
                    showEmptyState()
                    return@addOnSuccessListener
                }

                for (document in documents) {
                    try {
                        val place = document.toObject(TouristSpot::class.java).copy(id = document.id)
                        allPlaces.add(place)
                    } catch (e: Exception) {
                        android.util.Log.e("ComparatorActivity", "Error al parsear lugar: ${e.message}")
                    }
                }

                // Ordenar por rating
                allPlaces.sortByDescending { it.rating }
                adapter.notifyDataSetChanged()

                if (allPlaces.isEmpty()) {
                    showEmptyState()
                }
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                showEmptyState()
                NotificationHelper.error(binding.root, "Error al cargar lugares: ${e.message}")
            }
    }

    private fun handlePlaceSelection(place: TouristSpot, isSelected: Boolean) {
        if (isSelected) {
            if (selectedPlaces.size < MAX_SELECTION) {
                selectedPlaces.add(place)
                updateSelectionCount()
            }
        } else {
            selectedPlaces.remove(place)
            updateSelectionCount()
        }
    }

    private fun updateSelectionCount() {
        val count = selectedPlaces.size
        binding.selectionCountText.text = "Seleccionados: $count/$MAX_SELECTION"

        // Mostrar/ocultar botón de comparar
        binding.compareButton.isEnabled = count >= 2

        if (count >= 2) {
            binding.compareButton.visibility = View.VISIBLE
        }
    }

    private fun showEmptyState() {
        binding.emptyStateContainer.visibility = View.VISIBLE
        binding.placesRecyclerView.visibility = View.GONE
    }

    private fun openComparison() {
        val intent = Intent(this, PlaceComparisonActivity::class.java)
        intent.putStringArrayListExtra(EXTRA_SELECTED_PLACES, ArrayList(selectedPlaces.map { it.id }))
        startActivity(intent)
    }
}
