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

        setupLockedFeaturesUI()
        updateUsageDisplay()

        binding.generateRouteButton.setOnClickListener {
            // Verificar autenticación antes de generar ruta
            AuthManager.requireAuth(this, AuthManager.AuthRequired.GENERATE_ROUTES) {
                checkUsageLimitAndGenerate()
            }
        }
    }

    /**
     * Verifica límites de uso antes de generar ruta
     */
    private fun checkUsageLimitAndGenerate() {
        lifecycleScope.launch {
            val (canGenerate, message) = UsageManager.canGenerateRoute(this@PreferencesActivity)

            if (!canGenerate) {
                // Límite alcanzado
                androidx.appcompat.app.AlertDialog.Builder(this@PreferencesActivity)
                    .setTitle("⚠️ Límite diario alcanzado")
                    .setMessage(message + "\n\nEsto ayuda a controlar los costos del servicio de IA.")
                    .setPositiveButton("Entendido", null)
                    .show()
                return@launch
            }

            // Puede generar, proceder
            generateRouteWithAuth()
        }
    }

    override fun onResume() {
        super.onResume()
        updateUsageDisplay()
    }

    /**
     * Actualiza el display de uso en la UI
     */
    private fun updateUsageDisplay() {
        val stats = UsageManager.getUsageStats(this)
        val usageText = "${stats.routesUsedToday}/${stats.routesLimitToday} rutas IA usadas hoy"

        // Mostrar en el subtítulo o crear un TextView nuevo
        binding.subtitleText.text = "La IA creará la ruta perfecta para ti\n$usageText"
    }

    private fun setupLockedFeaturesUI() {
        // Si el usuario no está autenticado, aplicar estilo bloqueado
        if (!AuthManager.isAuthenticated()) {
            binding.generateRouteButton.alpha = 0.5f
            binding.generateRouteButton.icon = getDrawable(R.drawable.ic_lock_outline_black_24dp)
        }
    }

    private fun generateRouteWithAuth() {
        val budget = binding.budgetEditText.text.toString()
        val time = binding.timeEditText.text.toString()
        val interests = getSelectedInterests()
        val travelType = getSelectedTravelType()
        val pace = getSelectedPace()
        val mobility = getSelectedMobility()
        val customRequest = binding.customRequestEditText.text.toString().trim()

        if (budget.isBlank() || time.isBlank()) {
            Toast.makeText(this, "Por favor, ingresa presupuesto y tiempo disponible", Toast.LENGTH_SHORT).show()
            return
        }

        if (interests.isEmpty() && customRequest.isBlank()) {
            Toast.makeText(this, "Selecciona al menos un interés o describe lo que buscas", Toast.LENGTH_SHORT).show()
            return
        }

        fetchPlacesAndThenGenerateRoute(budget, time, interests, travelType, pace, mobility, customRequest)
    }

    private fun fetchPlacesAndThenGenerateRoute(
        budget: String,
        time: String,
        interests: List<String>,
        travelType: String,
        pace: String,
        mobility: String,
        customRequest: String
    ) {
        Toast.makeText(this, "Consultando lugares disponibles...", Toast.LENGTH_SHORT).show()
        db.collection("lugares")
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    Toast.makeText(this, "Base de datos vacía. Creando datos de ejemplo...", Toast.LENGTH_LONG).show()
                    seedDatabaseWithSampleData(budget, time, interests, travelType, pace, mobility, customRequest)
                    return@addOnSuccessListener
                }

                // Obtener detalles completos de los lugares
                val placesDetails = documents.mapNotNull { doc ->
                    val nombre = doc.getString("nombre") ?: return@mapNotNull null
                    val descripcion = doc.getString("descripcion") ?: ""
                    val categoria = doc.getString("categoria") ?: ""
                    "$nombre (Categoría: $categoria - $descripcion)"
                }

                val placeNamesFromDb = documents.mapNotNull { it.getString("nombre") }
                val placesForPrompt = placesDetails.joinToString("\n- ")

                generateRouteWithAI(budget, time, interests, travelType, pace, mobility, customRequest, placeNamesFromDb, placesForPrompt)
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "Error al cargar lugares", e)
                Toast.makeText(this, "Error crítico al cargar lugares de la base de datos.", Toast.LENGTH_LONG).show()
            }
    }

    private fun generateRouteWithAI(
        budget: String,
        time: String,
        interests: List<String>,
        travelType: String,
        pace: String,
        mobility: String,
        customRequest: String,
        knownPlaceNames: List<String>,
        placesForPrompt: String
    ) {
        val apiKey = BuildConfig.GEMINI_API_KEY

        if (apiKey.isEmpty()) {
            Toast.makeText(this, "Error: API key no configurada", Toast.LENGTH_LONG).show()
            return
        }

        val generativeModel = GenerativeModel(
            modelName = "gemini-2.5-flash",
            apiKey = apiKey
        )

        // ============ PROMPT MEJORADO CON TÉCNICAS PROFESIONALES ============
        val prompt = buildString {
            appendLine("# ROL")
            appendLine("Eres un guía turístico experto en Álamos, Sonora, México, con amplio conocimiento de la historia, cultura y geografía local.")
            appendLine()

            appendLine("# CONTEXTO")
            appendLine("Álamos es un Pueblo Mágico colonial en Sonora, conocido por su arquitectura del siglo XVIII, calles empedradas, y rica historia minera.")
            appendLine("Los turistas buscan experiencias auténticas que combinen historia, cultura y belleza natural.")
            appendLine()

            appendLine("# TAREA")
            appendLine("Crea una ruta turística PERSONALIZADA basada en las siguientes preferencias del usuario:")
            appendLine()

            appendLine("## Perfil del Viajero:")
            appendLine("- Presupuesto: $${budget} MXN")
            appendLine("- Tiempo disponible: ${time} horas")
            if (interests.isNotEmpty()) {
                appendLine("- Intereses: ${interests.joinToString(", ")}")
            }
            appendLine("- Tipo de viaje: $travelType")
            appendLine("- Ritmo preferido: $pace")
            appendLine("- Movilidad: $mobility")
            if (customRequest.isNotBlank()) {
                appendLine()
                appendLine("## Petición Específica del Usuario:")
                appendLine("\"$customRequest\"")
            }
            appendLine()

            appendLine("## Lugares Disponibles en Álamos:")
            appendLine("- $placesForPrompt")
            appendLine()

            appendLine("# INSTRUCCIONES")
            appendLine("1. Analiza cuidadosamente el presupuesto, tiempo y preferencias del usuario")
            appendLine("2. Selecciona entre 3 y 6 lugares que mejor se adapten a sus necesidades")
            appendLine("3. Ordena los lugares de forma lógica (proximidad geográfica, horarios, flujo natural)")
            appendLine("4. Asegúrate de que el tiempo total se ajuste a las ${time} horas disponibles")
            appendLine("5. Considera el presupuesto de $${budget} MXN (entradas, comidas, transporte)")
            appendLine("6. Si el usuario mencionó algo específico, prioriza cumplir esa petición")
            appendLine("7. Varía el tipo de actividades (no todo museos, ni todo aire libre)")
            appendLine()

            appendLine("# FORMATO DE RESPUESTA")
            appendLine("Responde en un párrafo natural y entusiasta, mencionando EXACTAMENTE los nombres de los lugares tal como aparecen en la lista.")
            appendLine("Explica brevemente por qué cada lugar es ideal para este viajero.")
            appendLine()

            appendLine("Ejemplo de respuesta:")
            appendLine("\"Para tu experiencia de ${time} horas en Álamos, te recomiendo comenzar en la Plaza de Armas para sentir el corazón colonial de la ciudad, luego visitar el Museo Costumbrista de Sonora donde conocerás la rica historia regional, y finalizar en el Mirador El Perico para capturar vistas espectaculares al atardecer. Esta ruta se ajusta perfectamente a tu presupuesto de $${budget} MXN y combina historia con naturaleza.\"")
            appendLine()

            appendLine("# RESTRICCIONES")
            appendLine("- USA ÚNICAMENTE los nombres exactos de la lista de lugares disponibles")
            appendLine("- NO inventes lugares que no estén en la lista")
            appendLine("- NO uses formato de lista numerada, escribe en párrafo natural")
            appendLine("- SÉ ESPECÍFICO sobre por qué recomiendas cada lugar")
            appendLine("- Menciona los lugares en el orden lógico de visita")
        }

        Toast.makeText(this, "Generando ruta personalizada con IA...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            try {
                val response = generativeModel.generateContent(prompt)
                val responseText = response.text
                Log.d("AI_Raw_Response", "Respuesta completa de la IA: $responseText")

                if (responseText.isNullOrBlank()) {
                    throw Exception("La respuesta de la IA está vacía.")
                }

                // Buscar lugares mencionados en la respuesta
                val foundPlaceNames = ArrayList<String>()
                knownPlaceNames.forEach { placeName ->
                    if (responseText.contains(placeName, ignoreCase = true)) {
                        foundPlaceNames.add(placeName)
                    }
                }

                if (foundPlaceNames.isNotEmpty()) {
                    Log.d("AI_Parsed_Places", "Lugares encontrados en la respuesta: $foundPlaceNames")

                    // Registrar uso exitoso de la generación de ruta
                    UsageManager.recordRouteGeneration(this@PreferencesActivity)

                    // Mostrar la respuesta de la IA al usuario
                    val remaining = UsageManager.getRemainingRoutes(this@PreferencesActivity)
                    Toast.makeText(this@PreferencesActivity,
                        "✨ Ruta creada con ${foundPlaceNames.size} destinos\nTe quedan $remaining rutas IA hoy",
                        Toast.LENGTH_LONG).show()

                    navigateToMapWithRoute(foundPlaceNames)
                } else {
                    Log.w("AI_Response_Error", "No se encontró ninguno de los lugares conocidos en la respuesta de la IA: $responseText")
                    Toast.makeText(this@PreferencesActivity,
                        "La IA no pudo generar una ruta con esos criterios. Intenta con diferentes preferencias.",
                        Toast.LENGTH_LONG).show()
                }

            } catch (e: Exception) {
                Log.e("AI_Error", "Error al generar contenido: ${e.message}", e)
                Toast.makeText(this@PreferencesActivity,
                    "Error de IA: ${e.message}",
                    Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun seedDatabaseWithSampleData(
        budget: String,
        time: String,
        interests: List<String>,
        travelType: String,
        pace: String,
        mobility: String,
        customRequest: String
    ) {
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
                fetchPlacesAndThenGenerateRoute(budget, time, interests, travelType, pace, mobility, customRequest)
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
        if (binding.chkComida.isChecked) interests.add("Comida y Gastronomía")
        if (binding.chkCultura.isChecked) interests.add("Cultura y Arte")
        if (binding.chkHistoria.isChecked) interests.add("Historia y Patrimonio")
        if (binding.chkAireLibre.isChecked) interests.add("Aire Libre y Naturaleza")
        if (binding.chkFotografia.isChecked) interests.add("Fotografía y Paisajes")
        if (binding.chkArquitectura.isChecked) interests.add("Arquitectura Colonial")
        if (binding.chkCompras.isChecked) interests.add("Compras y Artesanías")
        if (binding.chkRelax.isChecked) interests.add("Relajación y Bienestar")
        return interests
    }

    private fun getSelectedTravelType(): String {
        return when (binding.radioGroupTravelType.checkedRadioButtonId) {
            binding.radioSolo.id -> "Solo/a"
            binding.radioPareja.id -> "En pareja"
            binding.radioFamilia.id -> "Familia con niños"
            binding.radioAmigos.id -> "Grupo de amigos"
            else -> "No especificado"
        }
    }

    private fun getSelectedPace(): String {
        return when (binding.radioGroupPace.checkedRadioButtonId) {
            binding.radioRelajado.id -> "Relajado (muchas pausas, sin prisa)"
            binding.radioModerado.id -> "Moderado (equilibrado)"
            binding.radioIntenso.id -> "Intenso (máximo aprovechamiento del tiempo)"
            else -> "Moderado"
        }
    }

    private fun getSelectedMobility(): String {
        return when (binding.radioGroupMobility.checkedRadioButtonId) {
            binding.radioPie.id -> "A pie (todo caminando)"
            binding.radioAuto.id -> "Auto propio"
            binding.radioMixto.id -> "Mixto (combinar caminata y transporte)"
            else -> "A pie"
        }
    }
}
