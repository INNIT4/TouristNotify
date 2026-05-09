package com.joseibarra.touristnotify.routegen

import android.util.Log
import com.joseibarra.touristnotify.TouristSpot
import com.joseibarra.touristnotify.model.GeneratedRoute
import com.joseibarra.touristnotify.model.RouteMetrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private const val TAG = "RouteEnricher"

/**
 * Reemplaza las métricas estimadas de la ruta con distancias y tiempos
 * reales usando Routes API v2 (caminando).
 *
 * Llamar tras [RouteOptimizer.optimize]; si la API falla, devuelve la ruta
 * original sin modificar (no es bloqueante para el usuario).
 */
object RouteEnricher {

    private val http = OkHttpClient.Builder()
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    private const val ROUTES_API_URL =
        "https://routes.googleapis.com/directions/v2:computeRoutes"

    /**
     * Enriquece [route] con distancias/tiempos reales.
     * Los [spots] deben estar en el mismo orden que [route.paradas].
     *
     * @param apiKey clave de Routes API (= DIRECTIONS_API_KEY en este proyecto)
     * @return ruta con [RouteMetrics.distanciaCaminadaMetros] y
     *         [RouteMetrics.tiempoTotalMin] actualizados, o la ruta original si falla.
     */
    suspend fun enrich(
        route: GeneratedRoute,
        spots: List<TouristSpot>,
        apiKey: String
    ): GeneratedRoute = withContext(Dispatchers.IO) {
        val validSpots = spots.filter { it.ubicacion != null }
        if (validSpots.size < 2 || apiKey.isBlank()) return@withContext route

        try {
            val body = buildRequestBody(validSpots)
            val request = Request.Builder()
                .url(ROUTES_API_URL)
                .post(body.toRequestBody("application/json".toMediaType()))
                .header("X-Goog-Api-Key", apiKey)
                .header("X-Goog-FieldMask", "routes.legs.distanceMeters,routes.legs.duration")
                .build()

            val responseText = http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Routes API error ${response.code}")
                    return@withContext route
                }
                response.body?.string() ?: return@withContext route
            }

            val legs = JSONObject(responseText)
                .optJSONArray("routes")
                ?.optJSONObject(0)
                ?.optJSONArray("legs")
                ?: return@withContext route

            var totalDistanceM = 0
            var totalWalkingSec = 0
            for (i in 0 until legs.length()) {
                val leg = legs.getJSONObject(i)
                totalDistanceM += leg.optInt("distanceMeters", 0)
                // duration field is like "300s"
                val durationStr = leg.optString("duration", "0s")
                totalWalkingSec += durationStr.removeSuffix("s").toIntOrNull() ?: 0
            }

            val walkingMinutes = (totalWalkingSec + 59) / 60  // redondear hacia arriba
            val visitMinutes   = route.paradas.sumOf { it.duracionEstimadaMin }
            val totalMinutes   = walkingMinutes + visitMinutes

            Log.d(TAG, "Enriched: ${totalDistanceM}m walk, ${walkingMinutes}min walking, ${visitMinutes}min visits")

            route.copy(
                metricas = route.metricas.copy(
                    distanciaCaminadaMetros = totalDistanceM,
                    tiempoTotalMin = totalMinutes
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "RouteEnricher failed, using estimates: ${e.message}")
            route
        }
    }

    private fun buildRequestBody(spots: List<TouristSpot>): String {
        val origin = spots.first().ubicacion!!
        val dest   = spots.last().ubicacion!!
        val intermediates = spots.subList(1, spots.size - 1).joinToString(",") { spot ->
            val loc = spot.ubicacion!!
            """{"location":{"latLng":{"latitude":${loc.latitude},"longitude":${loc.longitude}}}}"""
        }
        return """
        {
          "origin":{"location":{"latLng":{"latitude":${origin.latitude},"longitude":${origin.longitude}}}},
          "destination":{"location":{"latLng":{"latitude":${dest.latitude},"longitude":${dest.longitude}}}},
          "intermediates":[$intermediates],
          "travelMode":"WALK",
          "routingPreference":"ROUTING_PREFERENCE_UNSPECIFIED"
        }
        """.trimIndent()
    }
}
