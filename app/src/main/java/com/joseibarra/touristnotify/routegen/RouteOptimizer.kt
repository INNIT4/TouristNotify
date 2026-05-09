package com.joseibarra.touristnotify.routegen

import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.SphericalUtil
import com.joseibarra.touristnotify.AppConstants
import com.joseibarra.touristnotify.TouristSpot
import com.joseibarra.touristnotify.model.GeneratedRoute
import com.joseibarra.touristnotify.model.RouteStop
import com.joseibarra.touristnotify.model.TipoActividad

/**
 * Optimización geográfica post-IA de las paradas de una ruta.
 *
 * Estrategia:
 * - N ≤ ROUTE_OPTIMIZER_BRUTE_FORCE_MAX (6): TSP exhaustivo con todas las permutaciones.
 * - N > 6: 2-opt con el orden de la IA como semilla.
 * - Métrica: haversine via SphericalUtil (android-maps-utils, ya en el proyecto).
 * - Penalización: si llueve, los segmentos que van a EXTERIOR suman un costo adicional.
 * - Constraints: si `fijarPrimero=true` el primer lugar no se mueve; mismo para `fijarUltimo`.
 *   El usuario puede fijar un restaurante al mediodía marcándolo como primera/última parada.
 */
object RouteOptimizer {

    data class OptimizationRequest(
        val route: GeneratedRoute,
        val spots: List<TouristSpot>,
        /** Si true, la primera parada de la IA no se mueve. */
        val fijarPrimero: Boolean = false,
        /** Si true, la última parada de la IA no se mueve. */
        val fijarUltimo: Boolean = false,
        /** Si true, aplica penalización a segmentos exteriores. */
        val isRaining: Boolean = false
    )

    data class OptimizationResult(
        val route: GeneratedRoute,
        val totalDistanceMeters: Double,
        val wasOptimized: Boolean
    )

    fun optimize(req: OptimizationRequest): OptimizationResult {
        val stops = req.route.paradas
        if (stops.size <= 2) {
            return OptimizationResult(req.route, calcTotalDistance(stops, req.spots), false)
        }

        val spotsById = req.spots.associateBy { it.id }

        // Separar fijos de móviles
        val firstStop = if (req.fijarPrimero) stops.first() else null
        val lastStop = if (req.fijarUltimo && stops.size > 1) stops.last() else null
        val mobileStops = stops.filter { stop ->
            stop != firstStop && stop != lastStop
        }

        if (mobileStops.isEmpty()) {
            return OptimizationResult(req.route, calcTotalDistance(stops, req.spots), false)
        }

        val optimizedMobile = if (mobileStops.size <= AppConstants.ROUTE_OPTIMIZER_BRUTE_FORCE_MAX) {
            tspBruteForce(mobileStops, spotsById, firstStop, lastStop, req.isRaining)
        } else {
            twoOpt(mobileStops, spotsById, req.isRaining)
        }

        val finalStops = buildList {
            firstStop?.let { add(it) }
            addAll(optimizedMobile)
            lastStop?.let { add(it) }
        }.mapIndexed { idx, stop -> stop.copy(ordenSugerido = idx + 1) }

        val totalDistance = calcTotalDistanceFromStops(finalStops, spotsById)
        val originalDistance = calcTotalDistanceFromStops(stops, spotsById)
        val wasOptimized = totalDistance < originalDistance * 0.99 // al menos 1% mejor

        return OptimizationResult(
            route = req.route.copy(paradas = finalStops),
            totalDistanceMeters = totalDistance,
            wasOptimized = wasOptimized
        )
    }

    // ─── TSP brute force (N ≤ 6) ─────────────────────────────────────────────

    private fun tspBruteForce(
        stops: List<RouteStop>,
        spotsById: Map<String, TouristSpot>,
        fixedFirst: RouteStop?,
        fixedLast: RouteStop?,
        isRaining: Boolean
    ): List<RouteStop> {
        var bestOrder = stops
        var bestCost = routeCost(stops, spotsById, fixedFirst, fixedLast, isRaining)

        permutations(stops).forEach { perm ->
            val cost = routeCost(perm, spotsById, fixedFirst, fixedLast, isRaining)
            if (cost < bestCost) {
                bestCost = cost
                bestOrder = perm
            }
        }
        return bestOrder
    }

    /** Genera todas las permutaciones de una lista. Máximo usable para N ≤ 8. */
    private fun <T> permutations(list: List<T>): Sequence<List<T>> = sequence {
        if (list.isEmpty()) { yield(emptyList()); return@sequence }
        if (list.size == 1) { yield(list); return@sequence }
        list.forEachIndexed { i, elem ->
            val rest = list.toMutableList().also { it.removeAt(i) }
            permutations(rest).forEach { perm -> yield(listOf(elem) + perm) }
        }
    }

    // ─── 2-opt (N > 6) ───────────────────────────────────────────────────────

    private fun twoOpt(
        stops: List<RouteStop>,
        spotsById: Map<String, TouristSpot>,
        isRaining: Boolean
    ): List<RouteStop> {
        var current = stops.toMutableList()
        var improved = true
        var iterations = 0
        val maxIterations = stops.size * stops.size * 2

        while (improved && iterations < maxIterations) {
            improved = false
            for (i in 0 until current.size - 1) {
                for (j in i + 1 until current.size) {
                    val newRoute = twoOptSwap(current, i, j)
                    val costCurrent = segmentCost(current, i, j, spotsById, isRaining)
                    val costNew = segmentCost(newRoute, i, j, spotsById, isRaining)
                    if (costNew < costCurrent * 0.999) {  // epsilon para evitar ciclos
                        current = newRoute.toMutableList()
                        improved = true
                    }
                }
            }
            iterations++
        }
        return current
    }

    private fun twoOptSwap(route: List<RouteStop>, i: Int, j: Int): List<RouteStop> {
        return route.take(i) + route.subList(i, j + 1).reversed() + route.drop(j + 1)
    }

    // ─── Métricas de costo ───────────────────────────────────────────────────

    private fun routeCost(
        stops: List<RouteStop>,
        spotsById: Map<String, TouristSpot>,
        fixedFirst: RouteStop?,
        fixedLast: RouteStop?,
        isRaining: Boolean
    ): Double {
        var cost = 0.0
        val fullRoute = buildList {
            fixedFirst?.let { add(it) }
            addAll(stops)
            fixedLast?.let { add(it) }
        }
        for (i in 0 until fullRoute.size - 1) {
            cost += segmentDistance(fullRoute[i], fullRoute[i + 1], spotsById, isRaining)
        }
        return cost
    }

    private fun segmentCost(
        route: List<RouteStop>,
        i: Int,
        j: Int,
        spotsById: Map<String, TouristSpot>,
        isRaining: Boolean
    ): Double {
        var cost = 0.0
        for (k in i until j) {
            cost += segmentDistance(route[k], route[k + 1], spotsById, isRaining)
        }
        return cost
    }

    private fun segmentDistance(
        from: RouteStop,
        to: RouteStop,
        spotsById: Map<String, TouristSpot>,
        isRaining: Boolean
    ): Double {
        val fromSpot = spotsById[from.placeId] ?: return 0.0
        val toSpot = spotsById[to.placeId] ?: return 0.0
        val fromLoc = fromSpot.ubicacion ?: return 0.0
        val toLoc = toSpot.ubicacion ?: return 0.0

        val distMeters = SphericalUtil.computeDistanceBetween(
            LatLng(fromLoc.latitude, fromLoc.longitude),
            LatLng(toLoc.latitude, toLoc.longitude)
        )

        // Penalización climática: si llueve y el destino es exterior, añadir 50% al costo
        val penalty = if (isRaining && toSpot.tipoActividadEnum() == TipoActividad.EXTERIOR) 1.5 else 1.0
        return distMeters * penalty
    }

    private fun calcTotalDistance(stops: List<RouteStop>, allSpots: List<TouristSpot>): Double {
        val spotsById = allSpots.associateBy { it.id }
        return calcTotalDistanceFromStops(stops, spotsById)
    }

    private fun calcTotalDistanceFromStops(
        stops: List<RouteStop>,
        spotsById: Map<String, TouristSpot>
    ): Double {
        var total = 0.0
        for (i in 0 until stops.size - 1) {
            total += segmentDistance(stops[i], stops[i + 1], spotsById, false)
        }
        return total
    }
}
