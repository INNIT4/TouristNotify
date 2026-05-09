package com.joseibarra.touristnotify

import com.google.firebase.firestore.GeoPoint
import com.joseibarra.touristnotify.model.AudienciaIdeal
import com.joseibarra.touristnotify.model.MomentoDia
import com.joseibarra.touristnotify.model.OpcionDietetica
import com.joseibarra.touristnotify.model.OpeningHours
import com.joseibarra.touristnotify.model.TipoActividad

/**
 * Modelo principal de un lugar turístico en Álamos, Sonora.
 * Todos los campos nuevos tienen defaults para compatibilidad con
 * documentos Firestore existentes (toObject no falla si falta el campo).
 */
data class TouristSpot(

    // ── Identidad ────────────────────────────────────────────────────────────
    val id: String = "",
    val nombre: String = "",
    val slug: String = "",               // URL-friendly, ej: "museo-costantini"
    val googlePlaceId: String? = null,

    // ── Categorización ───────────────────────────────────────────────────────
    val categoria: String = "General",
    val subcategorias: List<String> = emptyList(),
    /** INTERIOR, EXTERIOR o MIXTO — clave para filtrado por clima */
    val tipoActividad: String = TipoActividad.MIXTO.name,

    // ── Contenido ────────────────────────────────────────────────────────────
    val descripcion: String = "",
    val descripcionCorta: String = "",   // ≤140 chars para cards/markers
    val descripcionLarga: String = "",
    val tipsVisita: List<String> = emptyList(),
    val historiaResumen: String = "",
    val barrio: String = "",

    // ── Multimedia ───────────────────────────────────────────────────────────
    val imagenUrl: String = "",
    val imagenesGaleria: List<String> = emptyList(),

    // ── Ubicación ────────────────────────────────────────────────────────────
    val ubicacion: GeoPoint? = null,
    val direccion: String = "",

    // ── Reputación ───────────────────────────────────────────────────────────
    val rating: Double = 0.0,
    val reviewCount: Int = 0,
    val visitCount: Int = 0,

    // ── Contacto ─────────────────────────────────────────────────────────────
    val telefono: String = "",
    val sitioWeb: String = "",

    // ── Horarios ─────────────────────────────────────────────────────────────
    /** Horarios estructurados (nuevo). Si null, usar `horarios` legacy. */
    val horariosEstructurados: OpeningHours? = null,
    /** Texto original de Google Places weekdayText — fallback humano. */
    val horariosTextoOriginal: String = "",
    /** Campo legacy — se mantiene para compatibilidad con docs antiguos. */
    val horarios: String = "",
    /** true si Google Places reporta CLOSED_TEMPORARILY o CLOSED_PERMANENTLY */
    val cerradoTemporalmente: Boolean = false,

    // ── Precio ───────────────────────────────────────────────────────────────
    /** 0=Gratis, 1=$, 2=$$, 3=$$$, 4=$$$$ */
    val precioNivel: Int = 0,
    val precioPromedioMxn: Int = 0,
    val entradaGratuita: Boolean = false,
    /** Campo legacy — se mantiene para compatibilidad. */
    val precioEstimado: String = "",

    // ── Duración y experiencia ───────────────────────────────────────────────
    val duracionMinSugeridaMin: Int = 20,
    val duracionMaxSugeridaMin: Int = 45,
    /** Lista de MomentoDia.name (ej: ["MAÑANA", "TARDE"]) */
    val mejorMomentoDelDia: List<String> = emptyList(),
    val mejorTemporada: List<String> = emptyList(),
    val epocasEvitar: List<String> = emptyList(),

    // ── Audiencia ────────────────────────────────────────────────────────────
    /** Lista de AudienciaIdeal.name */
    val audienciaIdeal: List<String> = emptyList(),
    val aptoNiños: Boolean = true,
    val aptoMascotas: Boolean = false,
    /** 1=muy fácil, 5=muy difícil físicamente */
    val nivelDificultadFisica: Int = 1,

    // ── Accesibilidad ────────────────────────────────────────────────────────
    val accesibilidad: Accesibilidad = Accesibilidad(),

    // ── Servicios ────────────────────────────────────────────────────────────
    val servicios: Servicios = Servicios(),

    // ── Restaurante (solo si categoria == "Restaurante") ────────────────────
    val restaurante: RestauranteInfo = RestauranteInfo(),

    // ── Tags / búsqueda ──────────────────────────────────────────────────────
    val tags: List<String> = emptyList(),

    // ── Metadatos enriquecimiento IA ─────────────────────────────────────────
    val enrichment: EnrichmentMeta = EnrichmentMeta(),

    // ── Legacy (no usar en código nuevo) ────────────────────────────────────
    @Deprecated("Usar subcollection lugares/{id}/reviews en su lugar")
    val reviews: List<String> = emptyList()
) {
    /** Conveniencia: tipo de actividad como enum */
    fun tipoActividadEnum(): TipoActividad = try {
        TipoActividad.valueOf(tipoActividad)
    } catch (e: IllegalArgumentException) {
        TipoActividad.MIXTO
    }

    /** Conveniencia: mejor momento como lista de enums */
    fun mejorMomentoEnum(): List<MomentoDia> = mejorMomentoDelDia.mapNotNull {
        try { MomentoDia.valueOf(it) } catch (e: IllegalArgumentException) { null }
    }

    /** Conveniencia: audiencia como lista de enums */
    fun audienciaIdealEnum(): List<AudienciaIdeal> = audienciaIdeal.mapNotNull {
        try { AudienciaIdeal.valueOf(it) } catch (e: IllegalArgumentException) { null }
    }

    /** Descripción corta con fallback a primeros 140 chars de descripcion */
    fun descripcionCortaEfectiva(): String = descripcionCorta.ifBlank {
        descripcion.take(140)
    }
}

// ─── Sub-objetos del modelo ───────────────────────────────────────────────────

data class Accesibilidad(
    val sillaRuedas: Boolean = false,
    val banoAccesible: Boolean = false,
    val estacionamiento: Boolean = false,
    val estacionamientoAccesible: Boolean = false,
    val señalizacionBraille: Boolean = false,
    val notas: String = ""
)

data class Servicios(
    val wifi: Boolean = false,
    val aireAcondicionado: Boolean = false,
    val tomasCorriente: Boolean = false,
    val aceptaTarjeta: Boolean = false,
    val reservacionRequerida: Boolean = false,
    val idiomas: List<String> = listOf("es"),
    val tourGuiado: Boolean = false,
    val audioguia: Boolean = false
)

data class RestauranteInfo(
    val tipoCocina: List<String> = emptyList(),
    /** Lista de OpcionDietetica.name */
    val opcionesDieteticas: List<String> = emptyList(),
    val tieneTerraza: Boolean = false,
    val platosRecomendados: List<String> = emptyList()
) {
    fun opcionesDieteticasEnum(): List<OpcionDietetica> = opcionesDieteticas.mapNotNull {
        try { OpcionDietetica.valueOf(it) } catch (e: IllegalArgumentException) { null }
    }
}

data class EnrichmentMeta(
    val version: Int = 0,
    val source: String = "",
    val confidenceScore: Double = 0.0,
    /** Campos que el admin refinó manualmente — el bulk enrichment los respeta */
    val manualOverride: Map<String, Boolean> = emptyMap()
)
