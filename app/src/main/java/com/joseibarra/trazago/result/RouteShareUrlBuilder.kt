package com.joseibarra.trazago.result

import com.joseibarra.trazago.R
import com.joseibarra.trazago.TouristSpot
import com.joseibarra.trazago.model.GeneratedRoute
import java.net.URLEncoder

/**
 * Construye URLs para compartir la ruta:
 *  - Google Maps con waypoints (máx 8 paradas por límite de URL).
 *  - Link interno `trazago://route/{routeId}`.
 */
object RouteShareUrlBuilder {

    private const val MAPS_BASE = "https://www.google.com/maps/dir/?api=1"
    private const val DEEP_LINK_BASE = "trazago://route"

    /**
     * Genera la URL de Google Maps con origen → waypoints intermedios → destino.
     * Usa coordenadas lat,lng para que no dependa de nombres en inglés.
     *
     * @param spots Lista ordenada de spots (mismo orden que [route.paradas]).
     * @return URL lista para abrir o compartir. Null si hay menos de 2 spots.
     */
    fun buildGoogleMapsUrl(
        route: GeneratedRoute,
        spots: List<TouristSpot>
    ): String? {
        val withCoords = spots.filter { it.ubicacion != null }
        if (withCoords.size < 2) return null

        val origin = "${withCoords.first().ubicacion!!.latitude},${withCoords.first().ubicacion!!.longitude}"
        val destination = "${withCoords.last().ubicacion!!.latitude},${withCoords.last().ubicacion!!.longitude}"

        val waypointCoords = withCoords.drop(1).dropLast(1)
            .take(6) // Google Maps acepta hasta 8 waypoints
            .joinToString("|") { "${it.ubicacion!!.latitude},${it.ubicacion!!.longitude}" }

        val sb = StringBuilder(MAPS_BASE)
        sb.append("&origin=").append(URLEncoder.encode(origin, "UTF-8"))
        sb.append("&destination=").append(URLEncoder.encode(destination, "UTF-8"))
        if (waypointCoords.isNotEmpty()) {
            sb.append("&waypoints=").append(URLEncoder.encode(waypointCoords, "UTF-8"))
        }
        sb.append("&travelmode=walking")

        return sb.toString()
    }

    /**
     * Genera un texto completo para compartir la ruta (para WhatsApp, etc.):
     *  - Título y resumen.
     *  - Lista de paradas numeradas con nombre y horario.
     *  - Enlace a Google Maps.
     *  - Link interno.
     */
    fun buildShareText(
        context: android.content.Context,
        route: GeneratedRoute,
        spots: List<TouristSpot>
    ): String = buildString {
        appendLine("🗺️ *${route.resumen.titulo.ifBlank { context.getString(R.string.share_route_default_title) }}*")
        appendLine(route.resumen.descripcion)
        appendLine()

        route.paradas.forEachIndexed { idx, parada ->
            val spot = spots.getOrNull(idx)
            val nombre = spot?.nombre ?: parada.placeId
            appendLine("${idx + 1}. $nombre (${parada.horaSugeridaInicio}–${parada.horaSugeridaFin})")
        }
        appendLine()

        val mapsUrl = buildGoogleMapsUrl(route, spots)
        if (mapsUrl != null) {
            appendLine("📍 ${context.getString(R.string.share_google_maps_label)}: $mapsUrl")
        }
        appendLine("🔗 ${context.getString(R.string.share_app_link_label)}: $DEEP_LINK_BASE/${route.id}")
    }

    /** Link profundo interno para compartir entre usuarios de la app. */
    fun buildDeepLink(routeId: String): String = "$DEEP_LINK_BASE/$routeId"
}
