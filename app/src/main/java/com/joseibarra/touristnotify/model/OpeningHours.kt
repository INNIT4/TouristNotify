package com.joseibarra.touristnotify.model

/**
 * Horario estructurado de un lugar turístico.
 * Reemplaza el campo `horarios: String` no parseable.
 * Permite a HoursValidator determinar si un lugar está abierto
 * en una fecha/hora específica antes de mandarlo al prompt de IA.
 */
data class OpeningHours(
    val periodos: List<DayPeriod> = emptyList(),
    val zonaHoraria: String = "America/Hermosillo",
    val notas: String = ""
) {
    /** Retorna true si este OpeningHours no contiene ningún período definido. */
    val isEmpty: Boolean get() = periodos.isEmpty()
}

/**
 * Período de apertura para un día de la semana.
 * @param diaSemana 1=Lunes, 2=Martes, ..., 7=Domingo
 * @param abre  Hora de apertura en formato "HH:mm" (ej: "09:00")
 * @param cierra Hora de cierre en formato "HH:mm" (ej: "17:00"). "00:00" = medianoche.
 * @param cerrado true si el local está cerrado todo el día
 */
data class DayPeriod(
    val diaSemana: Int = 1,
    val abre: String = "00:00",
    val cierra: String = "00:00",
    val cerrado: Boolean = false
)

/** Enum de momentos del día para recomendar visitas */
enum class MomentoDia {
    AMANECER, MAÑANA, MEDIODIA, TARDE, ATARDECER, NOCHE;

    fun label(): String = when (this) {
        AMANECER -> "Amanecer"
        MAÑANA -> "Mañana"
        MEDIODIA -> "Mediodía"
        TARDE -> "Tarde"
        ATARDECER -> "Atardecer"
        NOCHE -> "Noche"
    }
}

/** Tipo de actividad — clave para el filtrado por clima */
enum class TipoActividad {
    INTERIOR, EXTERIOR, MIXTO;

    fun label(): String = when (this) {
        INTERIOR -> "Interior"
        EXTERIOR -> "Exterior"
        MIXTO -> "Mixto"
    }
}

/** Audiencia ideal del lugar */
enum class AudienciaIdeal {
    SOLO, PAREJA, FAMILIA, AMIGOS, NIÑOS, MAYORES;

    fun label(): String = when (this) {
        SOLO -> "Solo/a"
        PAREJA -> "Pareja"
        FAMILIA -> "Familia"
        AMIGOS -> "Amigos"
        NIÑOS -> "Niños"
        MAYORES -> "Adultos mayores"
    }
}

/** Opciones dietéticas para filtrado en restaurantes */
enum class OpcionDietetica {
    VEGETARIANO, VEGANO, SIN_GLUTEN, HALAL, KOSHER, SIN_LACTOSA;

    fun label(): String = when (this) {
        VEGETARIANO -> "Vegetariano"
        VEGANO -> "Vegano"
        SIN_GLUTEN -> "Sin gluten"
        HALAL -> "Halal"
        KOSHER -> "Kosher"
        SIN_LACTOSA -> "Sin lactosa"
    }
}
