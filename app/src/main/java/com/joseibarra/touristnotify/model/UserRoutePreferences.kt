package com.joseibarra.touristnotify.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.Date

/**
 * Preferencias capturadas en el wizard multi-paso para generar una ruta con IA.
 * Parcelable para sobrevivir rotación y transferencia entre Activities.
 */
@Parcelize
data class UserRoutePreferences(

    // ── Paso 1: ¿Quiénes y cuándo? ──────────────────────────────────────────
    val tipoViaje: TipoViaje = TipoViaje.PAREJA,
    val numAdultos: Int = 2,
    val numNiños: Int = 0,
    val edadMinNiños: Int = 0,           // 0 = no hay niños
    /** Fecha del viaje en millis desde epoch. Default = hoy. */
    val fechaViajeMs: Long = System.currentTimeMillis(),
    /** Hora de inicio en minutos desde medianoche. Ej: 540 = 09:00. */
    val horaInicioMin: Int = 540,
    /** Duración total deseada en horas (2.0 – 10.0). */
    val duracionHoras: Float = 4f,

    // ── Paso 2: ¿Qué te gusta? ───────────────────────────────────────────────
    val intereses: List<String> = emptyList(),
    val ritmo: Ritmo = Ritmo.MODERADO,
    val temaSolicitudLibre: String = "",  // sanitizado por PromptSanitizer

    // ── Paso 3: Restricciones ────────────────────────────────────────────────
    val presupuestoMxn: Int = 500,
    val movilidad: Movilidad = Movilidad.A_PIE,
    val accesibilidadRequerida: AccesibilidadRequerida = AccesibilidadRequerida(),
    val restriccionesDieteticas: List<OpcionDietetica> = emptyList(),
    val incluirComida: Boolean = false,  // fuerza 1 restaurante en horario de comida
    // idioma reservado para futuro; default español
    val idioma: String = "es"

) : Parcelable {

    /** Hora de inicio formateada como "HH:mm" */
    val horaInicioStr: String get() {
        val h = horaInicioMin / 60
        val m = horaInicioMin % 60
        return "%02d:%02d".format(h, m)
    }

    /** Hora estimada de fin en minutos desde medianoche */
    val horaFinMin: Int get() = horaInicioMin + (duracionHoras * 60).toInt()

    /** Hora de fin formateada como "HH:mm" */
    val horaFinStr: String get() {
        val h = horaFinMin / 60
        val m = horaFinMin % 60
        return "%02d:%02d".format(h, m)
    }

    /** Horario de comida estimado según hora de inicio (minutos desde medianoche) */
    val horaComidaMin: Int get() {
        // Mediodía = 720 min. Si la ruta dura hasta el mediodía, comida al final.
        return if (horaFinMin > 720) 720 else horaFinMin - 30
    }

    fun toSummaryString(): String = buildString {
        appendLine("${tipoViaje.label()} · ${numAdultos + numNiños} personas · ${duracionHoras}h")
        appendLine("Inicio: $horaInicioStr · Presupuesto: $$presupuestoMxn MXN")
        if (intereses.isNotEmpty()) appendLine("Intereses: ${intereses.joinToString(", ")}")
        if (restriccionesDieteticas.isNotEmpty())
            appendLine("Dieta: ${restriccionesDieteticas.joinToString(", ") { it.label() }}")
        if (temaSolicitudLibre.isNotBlank()) appendLine("\"$temaSolicitudLibre\"")
    }
}

// ─── Enums del wizard ────────────────────────────────────────────────────────

enum class TipoViaje {
    SOLO, PAREJA, FAMILIA, AMIGOS;
    fun label(): String = when (this) {
        SOLO -> "Solo/a"
        PAREJA -> "En pareja"
        FAMILIA -> "Familia con niños"
        AMIGOS -> "Grupo de amigos"
    }
}

enum class Ritmo {
    RELAJADO, MODERADO, INTENSO;
    fun label(): String = when (this) {
        RELAJADO -> "Relajado (pausas frecuentes)"
        MODERADO -> "Moderado (equilibrado)"
        INTENSO -> "Intenso (máximo aprovechamiento)"
    }
}

enum class Movilidad {
    A_PIE, AUTO, MIXTO;
    fun label(): String = when (this) {
        A_PIE -> "A pie"
        AUTO -> "Auto propio"
        MIXTO -> "Mixto (caminar + transporte)"
    }
}

/** Necesidades de accesibilidad del grupo */
@Parcelize
data class AccesibilidadRequerida(
    val sillaRuedas: Boolean = false,
    val banoAccesible: Boolean = false,
    val estacionamientoAccesible: Boolean = false
) : Parcelable {
    val requiereAlgo: Boolean get() = sillaRuedas || banoAccesible || estacionamientoAccesible
}
