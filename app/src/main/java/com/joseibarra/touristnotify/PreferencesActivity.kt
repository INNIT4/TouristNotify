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

        val budgetValue = budget.toDoubleOrNull()
        if (budgetValue == null || budgetValue <= 0) {
            Toast.makeText(this, "El presupuesto debe ser un número mayor a 0", Toast.LENGTH_SHORT).show()
            return
        }

        val timeValue = time.toDoubleOrNull()
        if (timeValue == null || timeValue <= 0 || timeValue > 24) {
            Toast.makeText(this, "El tiempo disponible debe ser entre 0.5 y 24 horas", Toast.LENGTH_SHORT).show()
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
        db.collection("lugares")
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    // Primera vez: seed silencioso sin mensajes técnicos visibles al usuario
                    seedDatabaseWithSampleData(budget, time, interests, travelType, pace, mobility, customRequest)
                    return@addOnSuccessListener
                }

                // Obtener detalles completos de los lugares con coordenadas y rating
                val placesDetails = documents.mapNotNull { doc ->
                    val nombre = doc.getString("nombre") ?: return@mapNotNull null
                    val descripcion = doc.getString("descripcion") ?: ""
                    val categoria = doc.getString("categoria") ?: ""
                    val rating = doc.getDouble("rating") ?: 0.0
                    val horarios = doc.getString("horarios") ?: ""
                    val geo = doc.getGeoPoint("ubicacion")
                    val coordStr = if (geo != null) "(lat:${String.format("%.4f", geo.latitude)},lng:${String.format("%.4f", geo.longitude)})" else ""
                    val horarioStr = if (horarios.isNotBlank()) " | Horario: $horarios" else ""
                    val ratingStr = if (rating > 0) " | ⭐${String.format("%.1f", rating)}" else ""
                    "$nombre $coordStr [Categoría: $categoria$ratingStr$horarioStr] — $descripcion"
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
        val apiKey = ConfigManager.getGeminiApiKey()

        if (apiKey.isEmpty()) {
            Toast.makeText(this, "Error: API key de Gemini no configurada", Toast.LENGTH_LONG).show()
            return
        }

        val generativeModel = GenerativeModel(
            modelName = "gemini-2.5-flash",
            apiKey = apiKey
        )

        // Crear y mostrar diálogo de progreso
        val progressDialog = createProgressDialog()

        // ============ PROMPT OPTIMIZADO CON CONTEXTO GEOGRÁFICO Y RATINGS ============
        val prompt = buildString {
            appendLine("# ROL")
            appendLine("Eres un experto planificador de rutas turísticas para Álamos, Sonora, México. Tu especialidad es crear itinerarios eficientes que minimicen distancias caminadas y maximicen la experiencia cultural.")
            appendLine()

            appendLine("# CONTEXTO GEOGRÁFICO")
            appendLine("Álamos es un Pueblo Mágico colonial compacto (~1 km de radio caminable). Las coordenadas del centro son lat:27.0275, lng:-108.9400.")
            appendLine("La mayoría de lugares históricos están a menos de 500 metros entre sí. Agrupa los cercanos para evitar caminatas innecesarias.")
            appendLine()

            appendLine("# PERFIL DEL VIAJERO")
            appendLine("- Presupuesto: $${budget} MXN")
            appendLine("- Tiempo disponible: ${time} horas")
            if (interests.isNotEmpty()) {
                appendLine("- Intereses: ${interests.joinToString(", ")}")
            }
            appendLine("- Tipo de viaje: $travelType")
            appendLine("- Ritmo: $pace")
            appendLine("- Movilidad: $mobility")
            if (customRequest.isNotBlank()) {
                appendLine()
                appendLine("## Petición específica:")
                appendLine("\"$customRequest\"")
            }
            appendLine()

            appendLine("# LUGARES DISPONIBLES")
            appendLine("Cada lugar incluye coordenadas (lat,lng), categoría, rating ⭐ y horarios:")
            appendLine("- $placesForPrompt")
            appendLine()

            appendLine("# INSTRUCCIONES DE PLANIFICACIÓN")
            appendLine("1. SELECCIÓN: Elige 3-6 lugares que coincidan con los intereses y se ajusten al tiempo disponible")
            appendLine("   - Calcula ~15-20 min por lugar en interiores, ~30 min en restaurantes, ~10 min en exteriores")
            appendLine("   - Considera el presupuesto (museos ~$50 MXN, restaurantes ~$150-300 MXN por persona)")
            appendLine("   - Prioriza lugares con mayor rating cuando haya opciones similares")
            appendLine()
            appendLine("2. ORDEN GEOGRÁFICO ÓPTIMO (CRÍTICO):")
            appendLine("   - Usa las coordenadas para agrupar lugares cercanos")
            appendLine("   - Calcula distancias entre lugares: diferencia en lat/lng pequeña = cerca")
            appendLine("   - Ordena como un circuito lógico, NO en zigzag")
            appendLine("   - Empieza en un extremo y termina en el otro, nunca regreses sobre tus pasos")
            appendLine("   - Si hay restaurante, colócalo al mediodía o al final")
            appendLine()
            appendLine("3. VARIEDAD: Mezcla categorías (historia + gastronomía, o cultura + naturaleza)")
            appendLine()

            appendLine("# FORMATO DE RESPUESTA")
            appendLine("Escribe UN párrafo natural y entusiasta. Menciona cada lugar EXACTAMENTE como aparece en la lista.")
            appendLine("Explica brevemente (1 frase) por qué cada uno es ideal para este viajero.")
            appendLine("CRÍTICO: El primer lugar mencionado = primera parada. El último = última parada.")
            appendLine()

            appendLine("Ejemplo de formato:")
            appendLine("\"Para tu visita de ${time} horas en Álamos con interés en ${interests.firstOrNull() ?: "cultura"}, te recomiendo iniciar en [Lugar A] donde [razón breve], continuar hacia [Lugar B] que está a pocos pasos y [razón], luego [Lugar C] perfecto para [razón], y cerrar con [Lugar D] para [razón]. Esta ruta cubre el centro histórico sin retrocesos y se adapta a tu presupuesto de $${budget} MXN.\"")
            appendLine()

            appendLine("# RESTRICCIONES ABSOLUTAS")
            appendLine("- SOLO usa nombres EXACTOS de la lista (copia y pega, sin modificar)")
            appendLine("- NO inventes lugares")
            appendLine("- NO uses listas numeradas ni viñetas")
            appendLine("- NO ignores los horarios si están disponibles")
            appendLine("- El orden de mención ES el orden de visita")
        }

        progressDialog.show()

        // Actualizar mensajes de progreso cada segundo
        val progressMessages = listOf(
            "🔍 Analizando tus preferencias...",
            "🗺️ Explorando ${knownPlaceNames.size} lugares disponibles...",
            "🤔 Calculando la mejor combinación...",
            "✨ Optimizando tu experiencia...",
            "📍 Seleccionando los destinos perfectos..."
        )

        var messageIndex = 0
        val progressView = progressDialog.findViewById<android.widget.TextView>(R.id.progress_message)

        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val progressRunnable = object : Runnable {
            override fun run() {
                if (messageIndex < progressMessages.size && progressDialog.isShowing) {
                    progressView?.text = progressMessages[messageIndex]
                    messageIndex++
                    handler.postDelayed(this, 1500)
                }
            }
        }
        handler.postDelayed(progressRunnable, 1500)

        lifecycleScope.launch {
            try {
                val response = generativeModel.generateContent(prompt)
                val responseText = response.text
                Log.d("AI_Raw_Response", "Respuesta completa de la IA: $responseText")

                if (responseText.isNullOrBlank()) {
                    throw Exception("La respuesta de la IA está vacía.")
                }

                // Buscar lugares mencionados en la respuesta Y MANTENER EL ORDEN DE LA IA
                val foundPlaceNames = ArrayList<String>()
                val placeWithIndex = mutableListOf<Pair<String, Int>>()

                knownPlaceNames.forEach { placeName ->
                    val index = responseText.indexOf(placeName, ignoreCase = true)
                    if (index != -1) {
                        // Guardar el nombre y su posición en la respuesta
                        placeWithIndex.add(Pair(placeName, index))
                    }
                }

                // Ordenar por el índice de aparición (orden en que la IA los mencionó)
                placeWithIndex.sortBy { it.second }
                placeWithIndex.forEach { foundPlaceNames.add(it.first) }

                // Cerrar diálogo de progreso
                handler.removeCallbacks(progressRunnable)
                progressDialog.dismiss()

                if (foundPlaceNames.isNotEmpty()) {
                    Log.d("AI_Parsed_Places", "Lugares encontrados en orden de aparición: $foundPlaceNames")
                    Log.d("AI_Place_Order", "Orden detallado: ${placeWithIndex.joinToString { "${it.first} (pos: ${it.second})" }}")

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
                handler.removeCallbacks(progressRunnable)
                progressDialog.dismiss()

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
                Log.d("Firestore", "Lugares base añadidos con éxito.")
                // Lugares cargados, generar ruta sin notificar al usuario
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

    /**
     * Crea un diálogo de progreso visual y animado
     */
    private fun createProgressDialog(): androidx.appcompat.app.AlertDialog {
        val dialogView = layoutInflater.inflate(R.layout.dialog_route_generation_progress, null)

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        // Hacer el fondo transparente para esquinas redondeadas
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        return dialog
    }
}
