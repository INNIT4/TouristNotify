package com.joseibarra.touristnotify

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.google.ai.client.generativeai.GenerativeModel
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.joseibarra.touristnotify.databinding.ActivityPreferencesBinding
import kotlinx.coroutines.launch
import java.util.ArrayList

class PreferencesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPreferencesBinding
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPreferencesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = FirebaseFirestore.getInstance()

        binding.generateRouteButton.setOnClickListener {
            val budget = binding.budgetEditText.text.toString()
            val time = binding.timeEditText.text.toString()
            val interests = getSelectedInterests()

            if (budget.isBlank() || time.isBlank() || interests.isEmpty()) {
                Toast.makeText(this, "Por favor, rellena todos los campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            fetchPlacesAndThenGenerateRoute(budget, time, interests)
        }
    }

    private fun fetchPlacesAndThenGenerateRoute(budget: String, time: String, interests: List<String>) {
        Toast.makeText(this, "Consultando lugares disponibles...", Toast.LENGTH_SHORT).show()
        db.collection("lugares")
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    // --- CORRECCIÓN: Si no hay datos, los creamos aquí ---
                    Toast.makeText(this, "Base de datos vacía. Creando datos de ejemplo...", Toast.LENGTH_LONG).show()
                    seedDatabaseWithSampleData(budget, time, interests)
                    return@addOnSuccessListener
                }

                val placeNamesFromDb = documents.mapNotNull { it.getString("nombre") }
                val placesForPrompt = placeNamesFromDb.joinToString(", ")

                generateRouteWithAI(budget, time, interests, placeNamesFromDb, placesForPrompt)
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "Error al cargar lugares", e)
                Toast.makeText(this, "Error crítico al cargar lugares de la base de datos.", Toast.LENGTH_LONG).show()
            }
    }

    private fun generateRouteWithAI(budget: String, time: String, interests: List<String>, knownPlaceNames: List<String>, placesForPrompt: String) {
        // Usar API key desde BuildConfig para mayor seguridad
        val apiKey = BuildConfig.GEMINI_API_KEY

        if (apiKey.isEmpty()) {
            Toast.makeText(this, "Error: API key no configurada", Toast.LENGTH_LONG).show()
            return
        }

        val generativeModel = GenerativeModel(
            modelName = "gemini-2.5-flash",
            apiKey = apiKey
        )

        val interestsString = interests.joinToString()
        val prompt = "A partir de la siguiente lista de lugares reales en Álamos, Sonora: ${placesForPrompt}. " +
                "Sugiere 3 lugares para un turista con presupuesto de $${budget} pesos, $time horas de tiempo, y con intereses en ${interestsString}. " +
                "Describe tu sugerencia en un párrafo natural. Ejemplo: Para tu viaje, te recomiendo visitar la Plaza de Armas, el Museo Costumbrista de Sonora y el Mirador El Perico."


        Toast.makeText(this, "Generando ruta con IA...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            try {
                val response = generativeModel.generateContent(prompt)
                val responseText = response.text
                Log.d("AI_Raw_Response", "Respuesta completa de la IA: $responseText")

                if (responseText.isNullOrBlank()) {
                    throw Exception("La respuesta de la IA está vacía.")
                }

                val foundPlaceNames = ArrayList<String>()
                knownPlaceNames.forEach { placeName ->
                    if (responseText.contains(placeName, ignoreCase = true)) {
                        foundPlaceNames.add(placeName)
                    }
                }

                if (foundPlaceNames.isNotEmpty()) {
                    Log.d("AI_Parsed_Places", "Lugares encontrados en la respuesta: $foundPlaceNames")
                    navigateToMapWithRoute(foundPlaceNames)
                } else {
                    Log.w("AI_Response_Error", "No se encontró ninguno de los lugares conocidos en la respuesta de la IA: $responseText")
                    Toast.makeText(this@PreferencesActivity, "La IA no pudo generar una ruta con esos criterios. Inténtalo de nuevo.", Toast.LENGTH_LONG).show()
                }

            } catch (e: Exception) {
                Log.e("AI_Error", "Error al generar contenido: ${e.message}", e)
                Toast.makeText(this@PreferencesActivity, "Error de IA: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun seedDatabaseWithSampleData(budget: String, time: String, interests: List<String>) {
        val sampleSpots = listOf(
            TouristSpot(
                nombre = "Plaza de Armas",
                descripcion = "El corazón histórico y social de Álamos, rodeado de arcos y edificios coloniales.",
                categoria = "Historia",
                ubicacion = GeoPoint(27.0275, -108.9400),
                googlePlaceId = "ChIJ-c-fJ8rS1oYREj9T7o-Z1A0"
            ),
            TouristSpot(
                nombre = "Museo Costumbrista de Sonora",
                descripcion = "Un viaje a través de la historia, cultura y tradiciones de la región de Sonora.",
                categoria = "Cultura",
                ubicacion = GeoPoint(27.0267, -108.9395),
                googlePlaceId = "ChIJ-c-fJ8rS1oYRY2q9JdE4j2s"
            ),
            TouristSpot(
                nombre = "Mirador El Perico",
                descripcion = "Ofrece las vistas panorámicas más espectaculares de Álamos y sus alrededores.",
                categoria = "Aire Libre",
                ubicacion = GeoPoint(27.0315, -108.9344),
                googlePlaceId = "ChIJy1Z5zsrS1oYRmY3G8F0B2Fw"
            ),
            TouristSpot(
                nombre = "Templo de la Purísima Concepción",
                descripcion = "Una joya arquitectónica de tres naves y una imponente fachada barroca.",
                categoria = "Historia",
                ubicacion = GeoPoint(27.0279, -108.9393),
                googlePlaceId = "ChIJc-cfJ8rS1oYR4B-ZJdE4j2s"
            ),
            TouristSpot(
                nombre = "Palacio Municipal de Álamos",
                descripcion = "Edificio histórico sede del gobierno local, con un característico reloj en su torre.",
                categoria = "Cultura",
                ubicacion = GeoPoint(27.0272, -108.9404),
                googlePlaceId = "ChIJe-cfJ8rS1oYReJ9T7o-Z1A0"
            ),
            TouristSpot(
                nombre = "Callejón del Beso",
                descripcion = "Un estrecho y romántico callejón lleno de leyendas locales.",
                categoria = "Cultura",
                ubicacion = GeoPoint(27.0258, -108.9398),
                googlePlaceId = null
            )
        )

        val batch = db.batch()
        sampleSpots.forEach { spot ->
            val docRef = db.collection("lugares").document()
            batch.set(docRef, spot)
        }

        batch.commit()
            .addOnSuccessListener {
                Log.d("Firestore", "Datos de ejemplo añadidos con éxito.")
                // Después de añadir los datos, volvemos a intentar generar la ruta
                Toast.makeText(this, "Datos creados. Re-intentando generar ruta...", Toast.LENGTH_SHORT).show()
                fetchPlacesAndThenGenerateRoute(budget, time, interests)
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "Error al añadir datos de ejemplo", e)
                Toast.makeText(this, "Error al inicializar la base de datos.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun navigateToMapWithRoute(placeNames: ArrayList<String>) {
        val intent = Intent(this, MapsActivity::class.java).apply {
            putStringArrayListExtra("ROUTE_PLACES", placeNames)
        }
        startActivity(intent)
    }

    private fun getSelectedInterests(): List<String> {
        val interests = ArrayList<String>()
        if (binding.chkComida.isChecked) interests.add("Comida")
        if (binding.chkCultura.isChecked) interests.add("Cultura")
        if (binding.chkHistoria.isChecked) interests.add("Historia")
        if (binding.chkAireLibre.isChecked) interests.add("Aire Libre")
        return interests
    }
}