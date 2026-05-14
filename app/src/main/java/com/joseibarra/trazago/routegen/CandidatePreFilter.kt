package com.joseibarra.trazago.routegen

import android.util.Log
import com.joseibarra.trazago.TouristSpot
import com.joseibarra.trazago.model.TipoActividad
import com.joseibarra.trazago.model.UserRoutePreferences

private const val TAG = "CandidatePreFilter"

/**
 * Filtra el catálogo completo de lugares antes de enviarlo al prompt de IA.
 *
 * El pipeline completo:
 * 1. Eliminar lugares sin coordenadas (no se pueden mostrar en mapa).
 * 2. Eliminar lugares cerrados temporalmente.
 * 3. Filtrar por horarios (¿estará abierto durante la visita?).
 * 4. Filtrar por accesibilidad si el usuario la requiere.
 * 5. Filtrar por opciones dietéticas si el usuario las requiere y quiere comida.
 * 6. Puntuar negativamente lugares exteriores si llueve (no eliminar, solo bajar puntaje).
 *
 * Relajación progresiva: si después de aplicar todos los filtros quedan <3 lugares,
 * se relajan los filtros en orden (primero horarios, luego dietéticos, luego accesibilidad)
 * hasta tener al menos 3 o un mínimo viable. Si quedan <2, lanzar excepción.
 */
object CandidatePreFilter {

    data class FilterContext(
        val prefs: UserRoutePreferences,
        /** Clima actual: true si hay lluvia/tormenta */
        val isRaining: Boolean = false,
        /** Millis desde epoch para validar horarios */
        val visitTimeMs: Long = System.currentTimeMillis()
    )

    data class FilterResult(
        val candidates: List<TouristSpot>,
        val excluded: List<ExcludedSpot>,
        val wasRelaxed: Boolean = false
    )

    data class ExcludedSpot(val spot: TouristSpot, val reason: String)

    /**
     * Aplica todos los filtros y retorna el conjunto de candidatos para el prompt.
     * @throws IllegalStateException si quedan <2 lugares tras la relajación máxima.
     */
    fun filter(allSpots: List<TouristSpot>, ctx: FilterContext): FilterResult {
        val excluded = mutableListOf<ExcludedSpot>()

        // 1. Sin coordenadas → siempre excluir
        val withCoords = allSpots.filter { spot ->
            if (spot.ubicacion == null) {
                excluded.add(ExcludedSpot(spot, "sin_coordenadas"))
                false
            } else true
        }

        // 2. Cerrados temporalmente → siempre excluir
        val notClosed = withCoords.filter { spot ->
            if (spot.cerradoTemporalmente) {
                excluded.add(ExcludedSpot(spot, "cerrado_temporalmente"))
                false
            } else true
        }

        // 3. Filtro de horarios
        val openSpots = notClosed.filter { spot ->
            val open = HoursValidator.isOpenDuring(
                spot,
                ctx.prefs.horaInicioMin,
                ctx.prefs.horaFinMin,
                ctx.visitTimeMs
            )
            if (!open) excluded.add(ExcludedSpot(spot, "cerrado_en_horario"))
            open
        }

        // 4. Accesibilidad
        val accessibleSpots = if (ctx.prefs.accesibilidadRequerida.requiereAlgo) {
            openSpots.filter { spot ->
                val acc = spot.accesibilidad
                val ok = (!ctx.prefs.accesibilidadRequerida.sillaRuedas || acc.sillaRuedas) &&
                         (!ctx.prefs.accesibilidadRequerida.banoAccesible || acc.banoAccesible) &&
                         (!ctx.prefs.accesibilidadRequerida.estacionamientoAccesible || acc.estacionamientoAccesible)
                if (!ok) excluded.add(ExcludedSpot(spot, "no_accesible"))
                ok
            }
        } else openSpots

        // 5. Dietético (solo si incluirComida y hay restricciones)
        val dietarySpots = if (ctx.prefs.incluirComida && ctx.prefs.restriccionesDieteticas.isNotEmpty()) {
            accessibleSpots.filter { spot ->
                val esRestaurante = spot.categoria.lowercase().contains("restaurante") ||
                                    spot.categoria.lowercase().contains("gastro")
                if (!esRestaurante) return@filter true  // solo aplicar a restaurantes

                val opcionesLugar = spot.restaurante.opcionesDieteticasEnum().toSet()
                val ok = ctx.prefs.restriccionesDieteticas.all { it in opcionesLugar }
                if (!ok) excluded.add(ExcludedSpot(spot, "no_opcion_dietetica"))
                ok
            }
        } else accessibleSpots

        // 6. Peso por clima (no excluir, solo reordenar — la IA decide)
        val scored = if (ctx.isRaining) {
            scoreByWeather(dietarySpots)
        } else dietarySpots

        Log.d(TAG, "Filtrado: ${allSpots.size} → ${scored.size} candidatos (excluidos: ${excluded.size})")

        // Relajación progresiva si quedan <3
        if (scored.size >= 3) {
            return FilterResult(scored, excluded)
        }

        Log.w(TAG, "Pocos candidatos (${scored.size}), relajando filtros...")

        // Relajar horarios + dietético → usar accessibleSpots
        if (accessibleSpots.size >= 3) {
            Log.w(TAG, "Relajado: usando accessibleSpots sin filtro horario/dietético")
            return FilterResult(accessibleSpots, excluded, wasRelaxed = true)
        }

        // Relajar todo → usar notClosed (solo quitar los definitivamente cerrados)
        if (notClosed.size >= 2) {
            Log.w(TAG, "Relajado máximo: usando notClosed")
            return FilterResult(notClosed, excluded, wasRelaxed = true)
        }

        throw IllegalStateException(
            "No hay suficientes lugares disponibles en Álamos para generar una ruta " +
            "(mínimo 2 requeridos, encontrados: ${notClosed.size}). " +
            "Intenta cambiar la fecha, hora o requisitos de accesibilidad."
        )
    }

    /**
     * Reordena la lista priorizando lugares interiores cuando llueve.
     * Los exteriores se mueven al final pero no se eliminan.
     */
    private fun scoreByWeather(spots: List<TouristSpot>): List<TouristSpot> {
        return spots.sortedBy { spot ->
            when (spot.tipoActividadEnum()) {
                TipoActividad.INTERIOR -> 0
                TipoActividad.MIXTO    -> 1
                TipoActividad.EXTERIOR -> 2
            }
        }
    }
}
