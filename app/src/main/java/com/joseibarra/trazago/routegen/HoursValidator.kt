package com.joseibarra.trazago.routegen

import com.joseibarra.trazago.TouristSpot
import com.joseibarra.trazago.model.DayPeriod
import com.joseibarra.trazago.model.OpeningHours
import java.util.Calendar
import java.util.TimeZone

/**
 * Valida si un lugar turístico está abierto en una fecha y hora específicas.
 *
 * El campo `horariosEstructurados` del modelo enriquecido es lo que usamos.
 * Si un TouristSpot todavía tiene el campo legacy `horarios: String`,
 * el parser intenta extraer información básica de él.
 *
 * Regla de negocio: si no hay información de horarios, el lugar se considera
 * "potencialmente abierto" (no se filtra) para no perder lugares válidos.
 */
object HoursValidator {

    private val TZ_ALAMOS = TimeZone.getTimeZone("America/Hermosillo")

    /**
     * Retorna true si el lugar está abierto en el momento dado.
     *
     * @param spot       TouristSpot con `horariosEstructurados` poblado.
     * @param timeMs     Momento a evaluar en millis desde epoch. Default = ahora.
     */
    fun isOpen(spot: TouristSpot, timeMs: Long = System.currentTimeMillis()): Boolean {
        // Si está marcado como cerrado temporalmente, bloquear siempre
        if (spot.cerradoTemporalmente) return false

        val hours = spot.horariosEstructurados
        if (hours == null || hours.isEmpty) {
            // Sin información: asumir abierto para no descartar lugar válido
            return true
        }

        val cal = Calendar.getInstance(TZ_ALAMOS).apply { timeInMillis = timeMs }
        // Calendar.DAY_OF_WEEK: 1=Domingo, 2=Lunes, ..., 7=Sábado
        // Convertir a nuestro formato 1=Lunes..7=Domingo
        val diaSemana = when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY    -> 1
            Calendar.TUESDAY   -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY  -> 4
            Calendar.FRIDAY    -> 5
            Calendar.SATURDAY  -> 6
            Calendar.SUNDAY    -> 7
            else               -> 1
        }

        val periodo = hours.periodos.find { it.diaSemana == diaSemana } ?: return true
        if (periodo.cerrado) return false

        val abreMin = parseTimeToMinutes(periodo.abre)
        val cierraMin = parseTimeToMinutes(periodo.cierra)
        val ahoraMin = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)

        return if (cierraMin == 0) {
            // Cierra medianoche = abierto hasta fin del día
            ahoraMin >= abreMin
        } else if (cierraMin > abreMin) {
            ahoraMin in abreMin until cierraMin
        } else {
            // Horario que cruza medianoche (ej: 22:00 – 02:00)
            ahoraMin >= abreMin || ahoraMin < cierraMin
        }
    }

    /**
     * Retorna true si el lugar estará abierto en algún momento dentro del rango
     * [startMin, endMin] (ambos en minutos desde medianoche, mismo día).
     *
     * Útil para validar que un lugar esté accesible durante la visita planeada.
     */
    fun isOpenDuring(
        spot: TouristSpot,
        startMin: Int,
        endMin: Int,
        fechaMs: Long = System.currentTimeMillis()
    ): Boolean {
        if (spot.cerradoTemporalmente) return false
        val hours = spot.horariosEstructurados ?: return true
        if (hours.isEmpty) return true

        val cal = Calendar.getInstance(TZ_ALAMOS).apply { timeInMillis = fechaMs }
        val diaSemana = calDayToOurDay(cal.get(Calendar.DAY_OF_WEEK))
        val periodo = hours.periodos.find { it.diaSemana == diaSemana } ?: return true
        if (periodo.cerrado) return false

        val abreMin = parseTimeToMinutes(periodo.abre)
        val cierraMin = if (parseTimeToMinutes(periodo.cierra) == 0) 24 * 60
                        else parseTimeToMinutes(periodo.cierra)

        // El lugar está abierto si hay solapamiento entre [abre, cierra) y [start, end)
        return abreMin < endMin && cierraMin > startMin
    }

    // ─── Parser de horarios legacy (String → OpeningHours) ──────────────────

    /**
     * Intenta parsear el campo `horarios: String` (formato Places API weekdayText)
     * a `OpeningHours`. Ej:
     *   "lunes: 9:00 a.m. – 6:00 p.m."
     *   "martes: cerrado"
     *   "Tuesday: 9:00 AM – 5:00 PM"
     *
     * Retorna OpeningHours vacío si no se puede parsear.
     */
    fun parseLegacyHorariosString(horarios: String): OpeningHours {
        if (horarios.isBlank()) return OpeningHours()

        val lines = horarios.split("\n").map { it.trim() }.filter { it.isNotBlank() }
        val periodos = mutableListOf<DayPeriod>()

        for (line in lines) {
            val dia = extractDayNumber(line) ?: continue
            if (isCerradoLine(line)) {
                periodos.add(DayPeriod(diaSemana = dia, cerrado = true))
                continue
            }
            val (abre, cierra) = extractHours(line) ?: continue
            periodos.add(DayPeriod(diaSemana = dia, abre = abre, cierra = cierra))
        }

        return if (periodos.isEmpty()) OpeningHours()
        else OpeningHours(periodos = periodos)
    }

    // ─── Privados ────────────────────────────────────────────────────────────

    private fun parseTimeToMinutes(time: String): Int {
        if (time.isBlank()) return 0
        val parts = time.split(":").mapNotNull { it.trim().toIntOrNull() }
        return if (parts.size >= 2) parts[0] * 60 + parts[1] else 0
    }

    private fun calDayToOurDay(calDay: Int): Int = when (calDay) {
        Calendar.MONDAY    -> 1
        Calendar.TUESDAY   -> 2
        Calendar.WEDNESDAY -> 3
        Calendar.THURSDAY  -> 4
        Calendar.FRIDAY    -> 5
        Calendar.SATURDAY  -> 6
        Calendar.SUNDAY    -> 7
        else               -> 1
    }

    private val DAY_NAMES_ES = mapOf(
        "lunes" to 1, "martes" to 2, "miércoles" to 3, "miercoles" to 3,
        "jueves" to 4, "viernes" to 5, "sábado" to 6, "sabado" to 6, "domingo" to 7
    )
    private val DAY_NAMES_EN = mapOf(
        "monday" to 1, "tuesday" to 2, "wednesday" to 3, "thursday" to 4,
        "friday" to 5, "saturday" to 6, "sunday" to 7
    )

    private fun extractDayNumber(line: String): Int? {
        val lower = line.lowercase()
        (DAY_NAMES_ES + DAY_NAMES_EN).forEach { (name, num) ->
            if (lower.startsWith(name)) return num
        }
        return null
    }

    private fun isCerradoLine(line: String): Boolean {
        val lower = line.lowercase()
        return "cerrado" in lower || "closed" in lower
    }

    // Regex para extraer horas: "9:00 a.m. – 6:00 p.m." o "9:00 AM – 5:00 PM"
    private val TIME_RANGE_REGEX = Regex(
        """(\d{1,2}:\d{2})\s*(?:a\.m\.|am|AM)?\s*[–\-]\s*(\d{1,2}:\d{2})\s*(?:p\.m\.|pm|PM)?""",
        RegexOption.IGNORE_CASE
    )
    private val TIME_WITH_AMPM_REGEX = Regex(
        """(\d{1,2}:\d{2})\s*(a\.m\.|am|AM|p\.m\.|pm|PM)""",
        RegexOption.IGNORE_CASE
    )

    private fun extractHours(line: String): Pair<String, String>? {
        val match = TIME_RANGE_REGEX.find(line) ?: return null
        val rawAbre = match.groupValues[1]
        val rawCierra = match.groupValues[2]

        // Detectar AM/PM si está explícito
        val amPmMatches = TIME_WITH_AMPM_REGEX.findAll(line).toList()
        val abreAmPm = amPmMatches.getOrNull(0)?.groupValues?.get(2) ?: ""
        val cierraAmPm = amPmMatches.getOrNull(1)?.groupValues?.get(2) ?: ""

        return normalizeTime(rawAbre, abreAmPm) to normalizeTime(rawCierra, cierraAmPm)
    }

    private fun normalizeTime(time: String, amPm: String): String {
        val parts = time.split(":").mapNotNull { it.toIntOrNull() }
        if (parts.size < 2) return time
        var h = parts[0]
        val m = parts[1]
        val isPm = amPm.lowercase().contains("p")
        val isAm = amPm.lowercase().contains("a")
        if (isPm && h != 12) h += 12
        if (isAm && h == 12) h = 0
        return "%02d:%02d".format(h, m)
    }
}
