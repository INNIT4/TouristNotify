package com.joseibarra.touristnotify

import android.animation.ValueAnimator
import android.util.Log
import android.view.animation.AccelerateDecelerateInterpolator
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.*
import com.google.maps.android.PolyUtil
import com.google.maps.android.SphericalUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Renderizador de rutas sobre Google Maps usando Routes API v2
 *
 * Encapsula la lógica de:
 * - Llamadas a Routes API v2 para calcular rutas
 * - Dibujo de polylines con animación progresiva
 * - Rutas multi-parada con intermediates
 * - Fallback a líneas rectas cuando la API falla
 *
 * Usa OkHttp para las llamadas HTTP y PolyUtil para decodificar polylines.
 */
class RouteRenderer(
    private val map: GoogleMap,
    private val okHttpClient: OkHttpClient = OkHttpClient()
) {

    companion object {
        private const val TAG = "RouteRenderer"
        private const val ROUTES_API_URL = "https://routes.googleapis.com/directions/v2:computeRoutes"
        private const val ANIMATION_DURATION_MS = 1200L

        // Colores de polyline
        const val ROUTE_COLOR = 0xCC1A73E8.toInt()    // Azul semitransparente
        const val NAVIGATION_COLOR = 0xFFEA4335.toInt() // Rojo opaco
        const val ROUTE_WIDTH = 14f
        const val NAVIGATION_WIDTH = 12f
    }

    /**
     * Calcula y dibuja una ruta entre dos puntos usando Routes API v2
     *
     * @param origin Punto de origen
     * @param destination Punto de destino
     * @return Polyline dibujada, o null si falló
     */
    suspend fun drawNavigationRoute(origin: LatLng, destination: LatLng): Polyline? {
        val body = """
        {
            "origin":{"location":{"latLng":{"latitude":${origin.latitude},"longitude":${origin.longitude}}}},
            "destination":{"location":{"latLng":{"latitude":${destination.latitude},"longitude":${destination.longitude}}}},
            "travelMode":"WALK",
            "routingPreference":"ROUTING_PREFERENCE_UNSPECIFIED"
        }
        """.trimIndent()

        return try {
            val encoded = callRoutesApi(body)
            val path = PolyUtil.decode(encoded)
            withContext(Dispatchers.Main) {
                animatePolylineDraw(path, NAVIGATION_COLOR, NAVIGATION_WIDTH)
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Error Routes API v2 (navegación): ${e.message}", e)
            null
        }
    }

    /**
     * Dibuja la polyline de una ruta turística completa con múltiples paradas
     *
     * @param spots Lista de TouristSpot en orden de visita
     * @return Polyline dibujada (animada o recta como fallback), o null
     */
    suspend fun drawTouristRoute(spots: List<TouristSpot>): Polyline? {
        if (spots.size < 2) return null

        val validSpots = spots.filter { it.ubicacion != null }
        if (validSpots.size < 2) return null

        return try {
            val body = buildMultiStopBody(validSpots)
            val encoded = callRoutesApi(body)
            val path = PolyUtil.decode(encoded)
            withContext(Dispatchers.Main) {
                animatePolylineDraw(path, ROUTE_COLOR, ROUTE_WIDTH)
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Routes API v2 error (ruta): ${e.message}", e)
            withContext(Dispatchers.Main) {
                drawStraightLine(validSpots)
            }
        }
    }

    /**
     * Construye el cuerpo JSON para Routes API v2 con múltiples paradas
     */
    private fun buildMultiStopBody(spots: List<TouristSpot>): String {
        val origin = spots.first().ubicacion ?: throw IllegalArgumentException("Origin ubicacion is null")
        val destination = spots.last().ubicacion ?: throw IllegalArgumentException("Destination ubicacion is null")

        val intermediatesJson = if (spots.size > 2) {
            spots.subList(1, spots.size - 1).mapNotNull { spot ->
                spot.ubicacion?.let { loc ->
                    """{"location":{"latLng":{"latitude":${loc.latitude},"longitude":${loc.longitude}}}}"""
                }
            }.joinToString(",")
        } else ""

        return """
        {
            "origin":{"location":{"latLng":{"latitude":${origin.latitude},"longitude":${origin.longitude}}}},
            "destination":{"location":{"latLng":{"latitude":${destination.latitude},"longitude":${destination.longitude}}}},
            "intermediates":[$intermediatesJson],
            "travelMode":"WALK",
            "routingPreference":"ROUTING_PREFERENCE_UNSPECIFIED"
        }
        """.trimIndent()
    }

    /**
     * Llama a Routes API v2 y devuelve el polyline encoded
     */
    private suspend fun callRoutesApi(body: String): String {
        val request = Request.Builder()
            .url(ROUTES_API_URL)
            .post(body.toRequestBody("application/json".toMediaType()))
            .header("X-Goog-Api-Key", BuildConfig.DIRECTIONS_API_KEY)
            .header("X-Goog-FieldMask", "routes.polyline.encodedPolyline")
            .build()

        return withContext(Dispatchers.IO) {
            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: throw Exception("Respuesta vacía")
            val json = JSONObject(responseBody)

            val routes = json.optJSONArray("routes")
            if (routes == null || routes.length() == 0) {
                throw Exception("Sin rutas disponibles")
            }

            routes.getJSONObject(0)
                .getJSONObject("polyline")
                .getString("encodedPolyline")
        }
    }

    /**
     * Dibuja una línea recta como fallback cuando Routes API no responde
     */
    private fun drawStraightLine(spots: List<TouristSpot>): Polyline {
        val polylineOptions = PolylineOptions()
            .color(ROUTE_COLOR)
            .width(ROUTE_WIDTH)
            .startCap(RoundCap())
            .endCap(RoundCap())
            .jointType(JointType.ROUND)
        spots.forEach { spot ->
            spot.ubicacion?.let {
                polylineOptions.add(LatLng(it.latitude, it.longitude))
            }
        }
        return map.addPolyline(polylineOptions)
    }

    /**
     * Anima el dibujo progresivo de una polyline desde el inicio hasta el final
     * Duración: 1200ms con interpolación suave
     */
    private fun animatePolylineDraw(
        fullPath: List<LatLng>,
        color: Int,
        width: Float
    ): Polyline {
        val polyline = map.addPolyline(
            PolylineOptions()
                .color(color)
                .width(width)
                .startCap(RoundCap())
                .endCap(RoundCap())
                .jointType(JointType.ROUND)
                .zIndex(1f)
        )

        if (fullPath.size < 2) {
            polyline.points = fullPath
            return polyline
        }

        // Pre-calcular distancias acumuladas
        val cumDist = mutableListOf(0.0)
        for (i in 0 until fullPath.size - 1) {
            cumDist.add(cumDist.last() + SphericalUtil.computeDistanceBetween(fullPath[i], fullPath[i + 1]))
        }
        val totalDist = cumDist.last()
        if (totalDist == 0.0) { polyline.points = fullPath; return polyline }

        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ANIMATION_DURATION_MS
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { anim ->
                val target = totalDist * anim.animatedFraction
                val partial = mutableListOf(fullPath[0])
                for (i in 0 until fullPath.size - 1) {
                    if (cumDist[i + 1] <= target) {
                        partial.add(fullPath[i + 1])
                    } else {
                        val segFrac = (target - cumDist[i]) / (cumDist[i + 1] - cumDist[i])
                        partial.add(SphericalUtil.interpolate(fullPath[i], fullPath[i + 1], segFrac))
                        break
                    }
                }
                polyline.points = partial
            }
            start()
        }

        return polyline
    }

    /**
     * Calcula tiempo estimado de recorrido para una ruta
     *
     * @param placeCount Número de lugares en la ruta
     * @return Minutos estimados (15min/lugar + 5min entre lugares)
     */
    fun calculateEstimatedTime(placeCount: Int): Int {
        val timePerPlace = 15
        val timeBetweenPlaces = 5
        return (placeCount * timePerPlace) + ((placeCount - 1) * timeBetweenPlaces)
    }
}
