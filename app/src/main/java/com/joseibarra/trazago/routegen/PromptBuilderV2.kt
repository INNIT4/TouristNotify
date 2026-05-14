package com.joseibarra.trazago.routegen

import com.joseibarra.trazago.AppConstants
import com.joseibarra.trazago.PromptSanitizer
import com.joseibarra.trazago.TouristSpot
import com.joseibarra.trazago.model.ROUTE_RESPONSE_SCHEMA
import com.joseibarra.trazago.model.TipoActividad
import com.joseibarra.trazago.model.UserRoutePreferences

/**
 * Construye el prompt para Gemini V2.
 *
 * Diferencias clave respecto a RouteGenerator.buildPrompt():
 * - Los lugares incluyen su `placeId` → Gemini devuelve placeIds, no nombres → sin substring bugs.
 * - El output es JSON estricto (responseSchema aplicado en RouteGeneratorV2).
 * - El perfil del viajero incluye fecha, hora, niños, dieta, accesibilidad, clima.
 * - Las instrucciones de planificación usan `duracionEstimadaMin` real del lugar.
 */
object PromptBuilderV2 {

    data class PromptInput(
        val prefs: UserRoutePreferences,
        val candidatos: List<TouristSpot>,
        val climateBrief: String = "",     // ej: "Soleado, 28°C" o "Lluvia moderada, 22°C"
        val isRaining: Boolean = false
    )

    fun build(input: PromptInput): String = buildString {
        val prefs = input.prefs

        appendLine("# ROL")
        appendLine("Eres un planificador experto de rutas turísticas para Álamos, Sonora, México (Pueblo Mágico colonial, centro histórico ~1 km de radio caminable, lat:${AppConstants.ALAMOS_LAT}, lng:${AppConstants.ALAMOS_LNG}).")
        appendLine("Tu misión: seleccionar 3-6 lugares del catálogo, ordenarlos en circuito geográfico óptimo, y devolver un JSON estructurado.")
        appendLine()

        appendLine("# PERFIL DEL VIAJERO")
        appendLine("- Tipo: ${prefs.tipoViaje.label()}")
        if (prefs.numNiños > 0) appendLine("- Niños: ${prefs.numNiños} (edad mínima: ${prefs.edadMinNiños} años)")
        appendLine("- Total personas: ${prefs.numAdultos + prefs.numNiños}")
        appendLine("- Hora de inicio: ${prefs.horaInicioStr}")
        appendLine("- Duración: ${prefs.duracionHoras}h (termina ~${prefs.horaFinStr})")
        appendLine("- Presupuesto: \$${prefs.presupuestoMxn} MXN total")
        appendLine("- Ritmo: ${prefs.ritmo.label()}")
        appendLine("- Movilidad: ${prefs.movilidad.label()}")
        if (prefs.intereses.isNotEmpty())
            appendLine("- Intereses: ${prefs.intereses.joinToString(", ")}")
        if (prefs.accesibilidadRequerida.requiereAlgo) {
            appendLine("- Accesibilidad requerida: ${buildAccesibilidadStr(prefs)}")
        }
        if (prefs.restriccionesDieteticas.isNotEmpty())
            appendLine("- Restricciones dietéticas: ${prefs.restriccionesDieteticas.joinToString(", ") { it.label() }}")
        if (prefs.incluirComida) appendLine("- DEBE incluir un restaurante en horario de comida (~${minutesToTime(prefs.horaComidaMin)})")
        if (input.climateBrief.isNotBlank())
            appendLine("- Clima del día: ${input.climateBrief}")
        if (input.isRaining)
            appendLine("- ⚠️ LLUVIA: Prioriza lugares con techo/interiores. Los exteriores solo si no hay otra opción.")
        if (prefs.temaSolicitudLibre.isNotBlank()) {
            val sanitized = PromptSanitizer.sanitizeCustomRequest(prefs.temaSolicitudLibre)
            appendLine("- Solicitud especial: \"$sanitized\"")
        }
        appendLine()

        appendLine("# CATÁLOGO DE LUGARES DISPONIBLES")
        appendLine("Formato: ID | Nombre | Coords | Categoría | Tipo | ⭐Rating | Duración sugerida | Precio est. | Horario hoy | Tags")
        appendLine()
        input.candidatos.forEachIndexed { i, spot ->
            appendLine(buildSpotLine(i + 1, spot))
        }
        appendLine()

        appendLine("# INSTRUCCIONES DE PLANIFICACIÓN")
        appendLine("1. SELECCIÓN (3-6 lugares):")
        appendLine("   - Coincidan con los intereses del viajero.")
        appendLine("   - La suma de duracionEstimadaMin + 10 min de traslado entre paradas ≤ duración total en minutos (${(prefs.duracionHoras * 60).toInt()} min).")
        appendLine("   - No superar el presupuesto total (\$${prefs.presupuestoMxn} MXN).")
        appendLine("   - Prioriza lugares con mayor rating cuando haya opciones similares.")
        if (prefs.numNiños > 0) appendLine("   - TODOS los lugares deben ser aptos para niños (aptoNiños=true).")
        if (input.isRaining) appendLine("   - Preferir tipoActividad=INTERIOR siempre que sea posible.")
        appendLine()
        appendLine("2. ORDEN GEOGRÁFICO ÓPTIMO:")
        appendLine("   - Ordena como circuito lógico usando coords — no en zigzag.")
        appendLine("   - Empieza en un extremo y termina en el otro. No regreses sobre tus pasos.")
        if (prefs.incluirComida) appendLine("   - El restaurante debe estar en posición que corresponda a la hora de comida (~${minutesToTime(prefs.horaComidaMin)}).")
        appendLine()
        appendLine("3. TIEMPOS:")
        appendLine("   - Asigna horaSugeridaInicio/Fin a cada parada, comenzando desde ${prefs.horaInicioStr}.")
        appendLine("   - Suma ~10 min de traslado a pie entre cada parada.")
        appendLine("   - Usa la duracionEstimadaMin del lugar como base (ajusta si el ritmo es relajado/intenso).")
        appendLine()
        appendLine("4. RESTRICCIONES ABSOLUTAS:")
        appendLine("   - Usa SOLO los IDs del catálogo — nunca inventes placeIds.")
        appendLine("   - No repitas placeIds.")
        appendLine("   - Respeta los horarios: no pongas como primera parada un lugar que abre después de ${prefs.horaInicioStr}.")
        appendLine()

        appendLine("# FORMATO DE RESPUESTA")
        appendLine("Devuelve SOLO JSON válido que cumpla el siguiente schema:")
        appendLine(ROUTE_RESPONSE_SCHEMA.trim())
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun buildSpotLine(n: Int, spot: TouristSpot): String {
        val lat = spot.ubicacion?.latitude?.let { "%.4f".format(it) } ?: "?"
        val lng = spot.ubicacion?.longitude?.let { "%.4f".format(it) } ?: "?"
        val tipo = spot.tipoActividadEnum().label()
        val rating = if (spot.rating > 0) "⭐%.1f".format(spot.rating) else "sin rating"
        val duracion = "${spot.duracionMinSugeridaMin}-${spot.duracionMaxSugeridaMin} min"
        val precio = when {
            spot.entradaGratuita -> "Gratis"
            spot.precioPromedioMxn > 0 -> "~\$${spot.precioPromedioMxn}"
            spot.precioNivel > 0 -> "${"$".repeat(spot.precioNivel)}"
            spot.precioEstimado.isNotBlank() -> spot.precioEstimado
            else -> "?"
        }
        val horario = buildHorarioStr(spot)
        val tagsStr = spot.tags.take(4).joinToString(", ")
        val nombre = PromptSanitizer.sanitizePlaceField(spot.nombre)
        val categoria = PromptSanitizer.sanitizePlaceField(spot.categoria)

        return "$n. [${spot.id}] $nombre | ($lat,$lng) | $categoria | $tipo | $rating | $duracion | $precio | $horario | $tagsStr"
    }

    private fun buildHorarioStr(spot: TouristSpot): String {
        val h = spot.horariosEstructurados
        if (h != null && !h.isEmpty) {
            // Encontrar el período del día actual (usamos hoy como aproximación)
            val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("America/Hermosillo"))
            val diaSemana = when (cal.get(java.util.Calendar.DAY_OF_WEEK)) {
                java.util.Calendar.MONDAY -> 1; java.util.Calendar.TUESDAY -> 2
                java.util.Calendar.WEDNESDAY -> 3; java.util.Calendar.THURSDAY -> 4
                java.util.Calendar.FRIDAY -> 5; java.util.Calendar.SATURDAY -> 6
                java.util.Calendar.SUNDAY -> 7; else -> 1
            }
            val p = h.periodos.find { it.diaSemana == diaSemana }
            return when {
                p == null -> "horario no disponible"
                p.cerrado -> "CERRADO HOY"
                else -> "${p.abre}–${p.cierra}"
            }
        }
        if (spot.horarios.isNotBlank()) return spot.horarios.lines().firstOrNull()?.take(30) ?: ""
        return "horario no disponible"
    }

    private fun buildAccesibilidadStr(prefs: UserRoutePreferences): String = buildList {
        if (prefs.accesibilidadRequerida.sillaRuedas) add("silla de ruedas")
        if (prefs.accesibilidadRequerida.banoAccesible) add("baño accesible")
        if (prefs.accesibilidadRequerida.estacionamientoAccesible) add("estacionamiento accesible")
    }.joinToString(", ")

    private fun minutesToTime(min: Int): String {
        val h = (min / 60).coerceIn(0, 23)
        val m = min % 60
        return "%02d:%02d".format(h, m)
    }
}
