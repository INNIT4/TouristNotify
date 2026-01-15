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

        // Obtener IDs de la intención (ajustado a la clave correcta)
        val selectedPlaceIds = intent.getStringArrayListExtra("SELECTED_PLACES")

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
                        val place = document.toObject(TouristSpot::class.java)?.copy(id = document.id)
                        if (place != null) {
                            places.add(place)
                        }
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
            else -> if (places.size >= 2) displayTwoPlaces() else finish()
        }
    }

    private fun displayTwoPlaces() {
        binding.comparisonTable2.root.visibility = View.VISIBLE
        binding.comparisonTable3.root.visibility = View.GONE

        val p1 = places[0]
        val p2 = places[1]

        val t2 = binding.comparisonTable2

        // Place 1
        t2.place1Name.text = p1.nombre
        t2.place1Category.text = p1.categoria
        t2.place1Rating.text = String.format("%.1f ⭐", p1.rating)
        t2.place1Reviews.text = "${p1.reviewCount} reseñas"
        t2.place1Price.text = p1.precioEstimado.ifBlank { "No especificado" }
        t2.place1Schedule.text = p1.horarios.ifBlank { "No especificado" }
        t2.place1Phone.text = p1.telefono.ifBlank { "No especificado" }
        t2.place1Address.text = p1.direccion.ifBlank { "No especificado" }
        t2.place1Visits.text = "${p1.visitCount} visitas"

        // Place 2
        t2.place2Name.text = p2.nombre
        t2.place2Category.text = p2.categoria
        t2.place2Rating.text = String.format("%.1f ⭐", p2.rating)
        t2.place2Reviews.text = "${p2.reviewCount} reseñas"
        t2.place2Price.text = p2.precioEstimado.ifBlank { "No especificado" }
        t2.place2Schedule.text = p2.horarios.ifBlank { "No especificado" }
        t2.place2Phone.text = p2.telefono.ifBlank { "No especificado" }
        t2.place2Address.text = p2.direccion.ifBlank { "No especificado" }
        t2.place2Visits.text = "${p2.visitCount} visitas"

        // Destacar el mejor rating
        val primaryColor = getColor(R.color.md_theme_light_primary)
        if (p1.rating > p2.rating) t2.place1Rating.setTextColor(primaryColor)
        else if (p2.rating > p1.rating) t2.place2Rating.setTextColor(primaryColor)

        // Destacar el más visitado
        if (p1.visitCount > p2.visitCount) t2.place1Visits.setTextColor(primaryColor)
        else if (p2.visitCount > p1.visitCount) t2.place2Visits.setTextColor(primaryColor)
    }

    private fun displayThreePlaces() {
        binding.comparisonTable2.root.visibility = View.GONE
        binding.comparisonTable3.root.visibility = View.VISIBLE

        val p1 = places[0]
        val p2 = places[1]
        val p3 = places[2]

        val t3 = binding.comparisonTable3

        // Place 1
        t3.place31Name.text = p1.nombre
        t3.place31Rating.text = String.format("%.1f ⭐", p1.rating)
        t3.place31Price.text = p1.precioEstimado.ifBlank { "-" }
        t3.place31Visits.text = "${p1.visitCount}"

        // Place 2
        t3.place32Name.text = p2.nombre
        t3.place32Rating.text = String.format("%.1f ⭐", p2.rating)
        t3.place32Price.text = p2.precioEstimado.ifBlank { "-" }
        t3.place32Visits.text = "${p2.visitCount}"

        // Place 3
        t3.place33Name.text = p3.nombre
        t3.place33Rating.text = String.format("%.1f ⭐", p3.rating)
        t3.place33Price.text = p3.precioEstimado.ifBlank { "-" }
        t3.place33Visits.text = "${p3.visitCount}"

        // Destacar el mejor rating
        val primaryColor = getColor(R.color.md_theme_light_primary)
        val maxRating = maxOf(p1.rating, p2.rating, p3.rating)
        if (p1.rating == maxRating) t3.place31Rating.setTextColor(primaryColor)
        if (p2.rating == maxRating) t3.place32Rating.setTextColor(primaryColor)
        if (p3.rating == maxRating) t3.place33Rating.setTextColor(primaryColor)

        // Destacar el más visitado
        val maxVisits = maxOf(p1.visitCount, p2.visitCount, p3.visitCount)
        if (p1.visitCount == maxVisits) t3.place31Visits.setTextColor(primaryColor)
        if (p2.visitCount == maxVisits) t3.place32Visits.setTextColor(primaryColor)
        if (p3.visitCount == maxVisits) t3.place33Visits.setTextColor(primaryColor)
    }
}
