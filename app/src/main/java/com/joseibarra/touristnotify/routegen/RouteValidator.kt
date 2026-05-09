package com.joseibarra.touristnotify.routegen

import com.joseibarra.touristnotify.AppConstants
import com.joseibarra.touristnotify.TouristSpot
import com.joseibarra.touristnotify.model.GeneratedRoute

/**
 * Valida la ruta generada por Gemini antes de optimizarla geográficamente.
 *
 * Chequeos:
 * 1. Todos los placeIds existen en el catálogo de candidatos.
 * 2. Sin placeIds duplicados.
 * 3. Conteo de paradas en rango [ROUTE_MIN_STOPS, ROUTE_MAX_STOPS].
 * 4. Los placeIds no están vacíos.
 */
object RouteValidator {

    data class ValidationResult(
        val isValid: Boolean,
        val errors: List<String>,
        val warnings: List<String>
    )

    /**
     * Valida la ruta contra el catálogo de candidatos.
     *
     * @param route       Ruta generada por Gemini.
     * @param candidatos  Lista de TouristSpots que se le enviaron al modelo.
     * @throws IllegalStateException si hay errores críticos.
     */
    fun validate(route: GeneratedRoute, candidatos: List<TouristSpot>): ValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        val candidateIds = candidatos.map { it.id }.toSet()
        val seenIds = mutableSetOf<String>()

        for (stop in route.paradas) {
            // placeId vacío
            if (stop.placeId.isBlank()) {
                errors.add("Una parada tiene placeId vacío")
                continue
            }
            // placeId desconocido
            if (stop.placeId !in candidateIds) {
                errors.add("PlaceId desconocido: '${stop.placeId}' (no estaba en el catálogo enviado a Gemini)")
            }
            // Duplicado
            if (!seenIds.add(stop.placeId)) {
                errors.add("PlaceId duplicado: '${stop.placeId}'")
            }
        }

        // Conteo
        val count = route.paradas.size
        when {
            count < AppConstants.ROUTE_MIN_STOPS ->
                errors.add("Muy pocas paradas: $count (mínimo ${AppConstants.ROUTE_MIN_STOPS})")
            count > AppConstants.ROUTE_MAX_STOPS ->
                warnings.add("Muchas paradas: $count (recomendado máximo ${AppConstants.ROUTE_MAX_STOPS}). Se tomarán las primeras ${AppConstants.ROUTE_MAX_STOPS}.")
        }

        // Advertencias suaves
        if (route.resumen.titulo.isBlank()) warnings.add("La ruta no tiene título")
        route.paradas.forEach { stop ->
            if (stop.razonSeleccion.isBlank()) warnings.add("Parada '${stop.placeId}' sin razón de selección")
        }

        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings
        )
    }

    /**
     * Devuelve la ruta con paradas saneadas:
     * - Elimina placeIds desconocidos o vacíos.
     * - Elimina duplicados (preserva primera aparición).
     * - Trunca a ROUTE_MAX_STOPS.
     */
    fun sanitize(route: GeneratedRoute, candidatos: List<TouristSpot>): GeneratedRoute {
        val candidateIds = candidatos.map { it.id }.toSet()
        val seenIds = mutableSetOf<String>()

        val cleanStops = route.paradas
            .filter { it.placeId.isNotBlank() && it.placeId in candidateIds }
            .filter { seenIds.add(it.placeId) }
            .take(AppConstants.ROUTE_MAX_STOPS)
            .mapIndexed { idx, stop -> stop.copy(ordenSugerido = idx + 1) }

        return route.copy(paradas = cleanStops)
    }
}
