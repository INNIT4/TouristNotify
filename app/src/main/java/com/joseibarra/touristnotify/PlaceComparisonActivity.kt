package com.joseibarra.touristnotify

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.joseibarra.touristnotify.databinding.ActivityPlaceComparisonBinding

/**
 * Activity que muestra la comparación lado a lado de lugares seleccionados
 */
class PlaceComparisonActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlaceComparisonBinding
    private lateinit var db: FirebaseFirestore
    private val places = mutableListOf<TouristSpot>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlaceComparisonBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = FirebaseFirestore.getInstance()

        val selectedPlaceIds = intent.getStringArrayListExtra(ComparatorActivity.EXTRA_SELECTED_PLACES)

        if (selectedPlaceIds.isNullOrEmpty()) {
            NotificationHelper.error(binding.root, "No se seleccionaron lugares")
            finish()
            return
        }

        loadPlaces(selectedPlaceIds)
    }

    private fun loadPlaces(placeIds: List<String>) {
        binding.progressBar.visibility = View.VISIBLE

        db.collection("lugares")
            .whereIn(FieldPath.documentId(), placeIds)
            .get()
            .addOnSuccessListener { documents ->
                binding.progressBar.visibility = View.GONE
                places.clear()

                for (document in documents) {
                    try {
                        val place = document.toObject(TouristSpot::class.java).copy(id = document.id)
                        places.add(place)
                    } catch (e: Exception) {
                        android.util.Log.e("PlaceComparisonActivity", "Error: ${e.message}")
                    }
                }

                if (places.isNotEmpty()) {
                    displayComparison()
                } else {
                    NotificationHelper.error(binding.root, "No se pudieron cargar los lugares")
                    finish()
                }
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                NotificationHelper.error(binding.root, "Error: ${e.message}")
                finish()
            }
    }

    private fun displayComparison() {
        when (places.size) {
            2 -> displayTwoPlaces()
            3 -> displayThreePlaces()
            else -> displayTwoPlaces() // Fallback
        }
    }

    private fun displayTwoPlaces() {
        binding.comparisonContainer2.visibility = View.VISIBLE
        binding.comparisonContainer3.visibility = View.GONE

        val place1 = places[0]
        val place2 = places[1]

        // Place 1
        binding.place1Name.text = place1.nombre
        binding.place1Category.text = place1.categoria
        binding.place1Rating.text = String.format("%.1f ⭐", place1.rating)
        binding.place1Reviews.text = "${place1.reviewCount} reseñas"
        binding.place1Price.text = place1.precioEstimado.ifBlank { "No especificado" }
        binding.place1Schedule.text = place1.horarios.ifBlank { "No especificado" }
        binding.place1Phone.text = place1.telefono.ifBlank { "No especificado" }
        binding.place1Address.text = place1.direccion.ifBlank { "No especificado" }
        binding.place1Visits.text = "${place1.visitCount} visitas"

        // Place 2
        binding.place2Name.text = place2.nombre
        binding.place2Category.text = place2.categoria
        binding.place2Rating.text = String.format("%.1f ⭐", place2.rating)
        binding.place2Reviews.text = "${place2.reviewCount} reseñas"
        binding.place2Price.text = place2.precioEstimado.ifBlank { "No especificado" }
        binding.place2Schedule.text = place2.horarios.ifBlank { "No especificado" }
        binding.place2Phone.text = place2.telefono.ifBlank { "No especificado" }
        binding.place2Address.text = place2.direccion.ifBlank { "No especificado" }
        binding.place2Visits.text = "${place2.visitCount} visitas"

        // Destacar el mejor rating
        if (place1.rating > place2.rating) {
            binding.place1Rating.setTextColor(getColor(R.color.md_theme_light_primary))
        } else if (place2.rating > place1.rating) {
            binding.place2Rating.setTextColor(getColor(R.color.md_theme_light_primary))
        }

        // Destacar el más visitado
        if (place1.visitCount > place2.visitCount) {
            binding.place1Visits.setTextColor(getColor(R.color.md_theme_light_primary))
        } else if (place2.visitCount > place1.visitCount) {
            binding.place2Visits.setTextColor(getColor(R.color.md_theme_light_primary))
        }
    }

    private fun displayThreePlaces() {
        binding.comparisonContainer2.visibility = View.GONE
        binding.comparisonContainer3.visibility = View.VISIBLE

        val place1 = places[0]
        val place2 = places[1]
        val place3 = places[2]

        // Place 1
        binding.place31Name.text = place1.nombre
        binding.place31Rating.text = String.format("%.1f ⭐", place1.rating)
        binding.place31Price.text = place1.precioEstimado.ifBlank { "-" }
        binding.place31Visits.text = "${place1.visitCount}"

        // Place 2
        binding.place32Name.text = place2.nombre
        binding.place32Rating.text = String.format("%.1f ⭐", place2.rating)
        binding.place32Price.text = place2.precioEstimado.ifBlank { "-" }
        binding.place32Visits.text = "${place2.visitCount}"

        // Place 3
        binding.place33Name.text = place3.nombre
        binding.place33Rating.text = String.format("%.1f ⭐", place3.rating)
        binding.place33Price.text = place3.precioEstimado.ifBlank { "-" }
        binding.place33Visits.text = "${place3.visitCount}"

        // Destacar el mejor rating
        val maxRating = maxOf(place1.rating, place2.rating, place3.rating)
        if (place1.rating == maxRating) binding.place31Rating.setTextColor(getColor(R.color.md_theme_light_primary))
        if (place2.rating == maxRating) binding.place32Rating.setTextColor(getColor(R.color.md_theme_light_primary))
        if (place3.rating == maxRating) binding.place33Rating.setTextColor(getColor(R.color.md_theme_light_primary))

        // Destacar el más visitado
        val maxVisits = maxOf(place1.visitCount, place2.visitCount, place3.visitCount)
        if (place1.visitCount == maxVisits) binding.place31Visits.setTextColor(getColor(R.color.md_theme_light_primary))
        if (place2.visitCount == maxVisits) binding.place32Visits.setTextColor(getColor(R.color.md_theme_light_primary))
        if (place3.visitCount == maxVisits) binding.place33Visits.setTextColor(getColor(R.color.md_theme_light_primary))
    }
}
