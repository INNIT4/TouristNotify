package com.joseibarra.trazago.routegen

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.joseibarra.trazago.AppConstants
import com.joseibarra.trazago.ConfigManager
import com.joseibarra.trazago.FirestoreCollections
import com.joseibarra.trazago.TouristSpot
import com.joseibarra.trazago.model.GeneratedRoute
import com.joseibarra.trazago.model.UserRoutePreferences
import kotlinx.coroutines.tasks.await

private const val TAG = "RouteCoordinator"

/**
 * Orquesta el pipeline completo de generación de rutas V2:
 *
 *   1. Cargar lugares desde Firestore.
 *   2. Pre-filtrar candidatos (horarios, accesibilidad, clima, dietético).
 *   3. Construir prompt y llamar a Gemini V2 (JSON structured output).
 *   4. Validar y sanear la respuesta.
 *   5. Optimizar geográficamente (TSP/2-opt).
 *   6. Devolver la ruta final.
 *
 * Si GEMINI_API_KEY no está disponible, delega a la Cloud Function `generateRoute`.
 * Actívable/desactivable vía Remote Config `use_v2_route_generator`.
 */
object RouteGenerationCoordinator {

    sealed class GenerationState {
        object LoadingPlaces : GenerationState()
        object FilteringCandidates : GenerationState()
        object CallingAI : GenerationState()
        object Optimizing : GenerationState()
        data class Success(val route: GeneratedRoute, val spots: List<TouristSpot>) : GenerationState()
        data class Error(val message: String, val cause: Throwable? = null) : GenerationState()
    }

    data class GenerationRequest(
        val prefs: UserRoutePreferences,
        val climateBrief: String = "",
        val isRaining: Boolean = false,
        /** Para regeneración: ruta previa + feedback del usuario */
        val prevRoute: GeneratedRoute? = null,
        val feedback: String = ""
    )

    /**
     * Genera o regenera una ruta. Emite estados de progreso via [onProgress].
     *
     * @param request    Preferencias y contexto de generación.
     * @param onProgress Callback con el estado actual (UI puede mostrar progress).
     * @return           La ruta generada o lanza excepción con mensaje amigable.
     */
    suspend fun generate(
        request: GenerationRequest,
        onProgress: (GenerationState) -> Unit = {}
    ): Pair<GeneratedRoute, List<TouristSpot>> {

        // ── 1. Cargar lugares ─────────────────────────────────────────────────
        onProgress(GenerationState.LoadingPlaces)
        val allSpots = loadSpots()
        Log.d(TAG, "Loaded ${allSpots.size} spots from Firestore")

        // ── 2. Pre-filtrar candidatos ─────────────────────────────────────────
        onProgress(GenerationState.FilteringCandidates)
        val filterResult = CandidatePreFilter.filter(
            allSpots,
            CandidatePreFilter.FilterContext(
                prefs = request.prefs,
                isRaining = request.isRaining,
                visitTimeMs = request.prefs.fechaViajeMs
            )
        )
        val candidatos = filterResult.candidates
        Log.d(TAG, "Candidates after filter: ${candidatos.size} (relaxed=${filterResult.wasRelaxed})")

        if (filterResult.wasRelaxed) {
            Log.w(TAG, "Filtros relajados por pocos candidatos. Excluidos: ${filterResult.excluded.size}")
        }

        // ── 3. Llamar a Gemini ────────────────────────────────────────────────
        onProgress(GenerationState.CallingAI)

        val promptInput = PromptBuilderV2.PromptInput(
            prefs = request.prefs,
            candidatos = candidatos,
            climateBrief = request.climateBrief,
            isRaining = request.isRaining
        )

        val apiKey = ConfigManager.getGeminiApiKey()
        val rawRoute = if (apiKey.isNotBlank()) {
            if (request.prevRoute != null && request.feedback.isNotBlank()) {
                RouteGeneratorV2.regenerate(request.prevRoute, request.feedback, promptInput, apiKey)
            } else {
                RouteGeneratorV2.generate(promptInput, apiKey)
            }
        } else {
            throw IllegalStateException(
                "No hay API key de Gemini disponible. Verifica la configuración en Remote Config."
            )
        }

        // ── 4. Validar y sanear ───────────────────────────────────────────────
        val validation = RouteValidator.validate(rawRoute, candidatos)
        if (!validation.isValid) {
            Log.e(TAG, "Route validation errors: ${validation.errors}")
            throw IllegalStateException(
                "La IA generó una ruta con errores: ${validation.errors.joinToString(". ")}. " +
                "Intenta regenerar la ruta."
            )
        }
        validation.warnings.forEach { Log.w(TAG, "Route warning: $it") }

        val sanitizedRoute = RouteValidator.sanitize(rawRoute, candidatos)

        // ── 5. Optimizar geográficamente ──────────────────────────────────────
        onProgress(GenerationState.Optimizing)

        // El restaurante al mediodía se protege como última parada si incluirComida
        val fijarUltimo = request.prefs.incluirComida && sanitizedRoute.paradas.any { stop ->
            val spot = candidatos.find { it.id == stop.placeId }
            spot?.categoria?.lowercase()?.contains("restaurante") == true
        }

        val optimizationResult = RouteOptimizer.optimize(
            RouteOptimizer.OptimizationRequest(
                route = sanitizedRoute,
                spots = candidatos,
                fijarUltimo = fijarUltimo,
                isRaining = request.isRaining
            )
        )

        Log.d(TAG, "Optimization: wasOptimized=${optimizationResult.wasOptimized}, " +
                   "distance=${optimizationResult.totalDistanceMeters.toInt()}m")

        // Resolver spots en el orden final de paradas
        val spotsById = candidatos.associateBy { it.id }
        val orderedSpots = optimizationResult.route.paradas.mapNotNull { spotsById[it.placeId] }

        // ── 6. Enriquecer con distancias/tiempos reales (Routes API v2) ──────
        val enrichedRoute = RouteEnricher.enrich(
            route  = optimizationResult.route,
            spots  = orderedSpots,
            apiKey = ConfigManager.getDirectionsApiKey()
        )
        Log.d(TAG, "Final metrics: dist=${enrichedRoute.metricas.distanciaCaminadaMetros}m, " +
                   "time=${enrichedRoute.metricas.tiempoTotalMin}min")

        return enrichedRoute to orderedSpots
    }

    // ─── Carga de Firestore ───────────────────────────────────────────────────

    private suspend fun loadSpots(): List<TouristSpot> {
        val snapshot = FirebaseFirestore.getInstance()
            .collection(FirestoreCollections.PLACES)
            .limit(200)
            .get()
            .await()

        return snapshot.documents.mapNotNull { doc ->
            try {
                doc.toObject(TouristSpot::class.java)?.copy(id = doc.id)
            } catch (e: Exception) {
                Log.w(TAG, "Error deserializando lugar ${doc.id}: ${e.message}")
                null
            }
        }
    }
}
