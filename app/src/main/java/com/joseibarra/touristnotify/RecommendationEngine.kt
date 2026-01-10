package com.joseibarra.touristnotify

import com.google.ai.client.generativeai.GenerativeModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Motor de recomendaciones personalizadas usando IA
 * Analiza el perfil del usuario y genera recomendaciones inteligentes
 */
object RecommendationEngine {

    private lateinit var generativeModel: GenerativeModel
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    fun initialize(apiKey: String) {
        generativeModel = GenerativeModel(
            modelName = "gemini-1.5-flash",
            apiKey = apiKey
        )
    }

    /**
     * Genera recomendaciones personalizadas para el usuario actual
     */
    suspend fun generateRecommendations(): Result<List<AIRecommendation>> = withContext(Dispatchers.IO) {
        try {
            val userId = auth.currentUser?.uid
            if (userId == null) {
                return@withContext Result.failure(Exception("Usuario no autenticado"))
            }

            // 1. Obtener perfil del usuario
            val userProfile = getUserProfile(userId)

            // 2. Obtener clima actual
            val weatherResult = WeatherManager.getCurrentWeather()
            val weather = weatherResult.getOrNull()

            // 3. Obtener todos los lugares disponibles
            val allPlaces = getAllPlaces()

            // 4. Generar prompt para IA
            val prompt = buildRecommendationPrompt(userProfile, weather, allPlaces)

            // 5. Obtener recomendaciones de IA
            val response = generativeModel.generateContent(prompt)
            val aiResponse = response.text ?: throw Exception("No se pudo generar respuesta")

            // 6. Parsear respuesta JSON
            val recommendations = parseRecommendations(aiResponse)

            Result.success(recommendations)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun getUserProfile(userId: String): UserProfile {
        // Obtener stats
        val statsDoc = db.collection("user_stats").document(userId).get().await()
        val stats = if (statsDoc.exists()) {
            statsDoc.toObject(UserStats::class.java) ?: UserStats()
        } else {
            UserStats()
        }

        // Obtener favoritos
        val favoritesSnapshot = db.collection("favorites")
            .whereEqualTo("userId", userId)
            .get()
            .await()
        val favorites = favoritesSnapshot.documents.map { it.toObject(Favorite::class.java)!! }

        // Obtener check-ins recientes (últimos 10)
        val checkInsSnapshot = db.collection("check_ins")
            .whereEqualTo("userId", userId)
            .orderBy("checkInTime", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(10)
            .get()
            .await()
        val recentCheckIns = checkInsSnapshot.documents.map { it.toObject(CheckIn::class.java)!! }

        return UserProfile(stats, favorites, recentCheckIns)
    }

    private suspend fun getAllPlaces(): List<TouristSpot> {
        val placesSnapshot = db.collection("lugares_turisticos").get().await()
        return placesSnapshot.documents.mapNotNull { it.toObject(TouristSpot::class.java) }
    }

    private fun buildRecommendationPrompt(
        profile: UserProfile,
        weather: WeatherInfo?,
        allPlaces: List<TouristSpot>
    ): String {
        val categoriesExplored = profile.stats.categoriesExplored.entries
            .sortedByDescending { it.value }
            .take(3)
            .joinToString(", ") { "${it.key} (${it.value} visitas)" }

        val favoriteCategories = profile.favorites
            .groupBy { it.placeCategory }
            .entries
            .sortedByDescending { it.value.size }
            .take(3)
            .joinToString(", ") { "${it.key} (${it.value.size})" }

        val recentPlaces = profile.recentCheckIns
            .take(5)
            .joinToString(", ") { it.placeName }

        val placesJson = JSONArray()
        allPlaces.forEach { place ->
            val placeObj = JSONObject()
            placeObj.put("id", place.id)
            placeObj.put("nombre", place.nombre)
            placeObj.put("categoria", place.categoria)
            placeObj.put("descripcion", place.descripcion)
            placeObj.put("rating", place.rating)
            placesJson.put(placeObj)
        }

        return """
Eres un sistema de recomendaciones turísticas para Álamos, Sonora. Analiza el perfil del usuario y genera recomendaciones personalizadas.

PERFIL DEL USUARIO:
- Total de check-ins: ${profile.stats.totalCheckIns}
- Lugares favoritos: ${profile.stats.totalFavorites}
- Lugares visitados: ${profile.stats.placesVisited}
- Kilómetros recorridos: ${profile.stats.kmTraveled} km
- Categorías más exploradas: $categoriesExplored
- Categorías favoritas: $favoriteCategories
- Lugares visitados recientemente: $recentPlaces

CLIMA ACTUAL:
${if (weather != null) {
    "- Temperatura: ${weather.temperature}°C\n" +
    "- Sensación térmica: ${weather.feelsLike}°C\n" +
    "- Condiciones: ${weather.description}\n" +
    "- Humedad: ${weather.humidity}%\n" +
    "- Viento: ${weather.windSpeed} km/h"
} else {
    "- No disponible"
}}

LUGARES DISPONIBLES:
$placesJson

INSTRUCCIONES:
1. Analiza el perfil del usuario y sus preferencias
2. Considera el clima actual para recomendar lugares apropiados
3. Evita recomendar lugares que el usuario ya visitó recientemente
4. Prioriza lugares de categorías que le gustan al usuario
5. Sugiere también 1-2 lugares de categorías nuevas para diversificar experiencia
6. Genera exactamente 6 recomendaciones
7. Para cada recomendación incluye una razón personalizada convincente

FORMATO DE RESPUESTA (JSON estricto):
{
  "recommendations": [
    {
      "placeId": "id_del_lugar",
      "placeName": "Nombre del lugar",
      "placeCategory": "Categoría",
      "reason": "Razón personalizada de 1-2 frases explicando por qué se recomienda este lugar basado en el perfil del usuario",
      "score": 0.95,
      "weatherSuitable": true,
      "estimatedDuration": "1-2 horas",
      "bestTimeToVisit": "Mañana"
    }
  ]
}

IMPORTANTE: Responde SOLO con el JSON, sin texto adicional antes o después.
""".trimIndent()
    }

    private fun parseRecommendations(jsonResponse: String): List<AIRecommendation> {
        try {
            // Limpiar respuesta (remover markdown si existe)
            var cleaned = jsonResponse.trim()
            if (cleaned.startsWith("```json")) {
                cleaned = cleaned.removePrefix("```json").removeSuffix("```").trim()
            } else if (cleaned.startsWith("```")) {
                cleaned = cleaned.removePrefix("```").removeSuffix("```").trim()
            }

            val jsonObject = JSONObject(cleaned)
            val recommendationsArray = jsonObject.getJSONArray("recommendations")

            val recommendations = mutableListOf<AIRecommendation>()
            for (i in 0 until recommendationsArray.length()) {
                val item = recommendationsArray.getJSONObject(i)
                recommendations.add(
                    AIRecommendation(
                        placeId = item.getString("placeId"),
                        placeName = item.getString("placeName"),
                        placeCategory = item.getString("placeCategory"),
                        reason = item.getString("reason"),
                        score = item.getDouble("score"),
                        weatherSuitable = item.optBoolean("weatherSuitable", true),
                        estimatedDuration = item.optString("estimatedDuration", "1-2 horas"),
                        bestTimeToVisit = item.optString("bestTimeToVisit", "Cualquier momento")
                    )
                )
            }

            return recommendations
        } catch (e: Exception) {
            throw Exception("Error al parsear recomendaciones: ${e.message}")
        }
    }

    /**
     * Clase auxiliar para el perfil del usuario
     */
    private data class UserProfile(
        val stats: UserStats,
        val favorites: List<Favorite>,
        val recentCheckIns: List<CheckIn>
    )
}
