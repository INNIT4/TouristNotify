package com.joseibarra.touristnotify.admin

import com.joseibarra.touristnotify.Accesibilidad
import com.joseibarra.touristnotify.RestauranteInfo
import com.joseibarra.touristnotify.Servicios

/**
 * Respuesta que Gemini devuelve al enriquecer un TouristSpot.
 * Todos los campos son opcionales — solo se sobrescriben los no-nulos
 * (respetando manualOverride del admin).
 */
data class EnrichmentResponse(
    val descripcionCorta: String? = null,
    val descripcionLarga: String? = null,
    val tipsVisita: List<String>? = null,
    val historiaResumen: String? = null,
    val tipoActividad: String? = null,           // "INTERIOR" | "EXTERIOR" | "MIXTO"
    val duracionMinSugeridaMin: Int? = null,
    val duracionMaxSugeridaMin: Int? = null,
    val mejorMomentoDelDia: List<String>? = null, // lista de MomentoDia.name
    val mejorTemporada: List<String>? = null,
    val epocasEvitar: List<String>? = null,
    val audienciaIdeal: List<String>? = null,     // lista de AudienciaIdeal.name
    val aptoNinos: Boolean? = null,
    val aptoMascotas: Boolean? = null,
    val nivelDificultadFisica: Int? = null,       // 1-5
    val precioNivel: Int? = null,                 // 0-4
    val precioPromedioMxn: Int? = null,
    val entradaGratuita: Boolean? = null,
    val tags: List<String>? = null,
    val accesibilidad: Accesibilidad? = null,
    val servicios: Servicios? = null,
    val restaurante: RestauranteInfo? = null
)

/** Schema JSON que se pasa a Gemini como responseSchema. */
object EnrichmentGeminiSchema {
    val schema: Map<String, Any> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "descripcionCorta"        to mapOf("type" to "string"),
            "descripcionLarga"        to mapOf("type" to "string"),
            "tipsVisita"              to mapOf("type" to "array", "items" to mapOf("type" to "string")),
            "historiaResumen"         to mapOf("type" to "string"),
            "tipoActividad"           to mapOf("type" to "string", "enum" to listOf("INTERIOR", "EXTERIOR", "MIXTO")),
            "duracionMinSugeridaMin"  to mapOf("type" to "integer"),
            "duracionMaxSugeridaMin"  to mapOf("type" to "integer"),
            "mejorMomentoDelDia"      to mapOf("type" to "array", "items" to mapOf("type" to "string",
                "enum" to listOf("MANANA", "MEDIODIA", "TARDE", "NOCHE"))),
            "mejorTemporada"          to mapOf("type" to "array", "items" to mapOf("type" to "string")),
            "epocasEvitar"            to mapOf("type" to "array", "items" to mapOf("type" to "string")),
            "audienciaIdeal"          to mapOf("type" to "array", "items" to mapOf("type" to "string",
                "enum" to listOf("SOLO", "PAREJA", "FAMILIA", "AMIGOS", "NINOS", "MAYORES"))),
            "aptoNinos"               to mapOf("type" to "boolean"),
            "aptoMascotas"            to mapOf("type" to "boolean"),
            "nivelDificultadFisica"   to mapOf("type" to "integer"),
            "precioNivel"             to mapOf("type" to "integer"),
            "precioPromedioMxn"       to mapOf("type" to "integer"),
            "entradaGratuita"         to mapOf("type" to "boolean"),
            "tags"                    to mapOf("type" to "array", "items" to mapOf("type" to "string")),
            "accesibilidad" to mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "sillaRuedas"              to mapOf("type" to "boolean"),
                    "banoAccesible"            to mapOf("type" to "boolean"),
                    "estacionamiento"          to mapOf("type" to "boolean"),
                    "estacionamientoAccesible" to mapOf("type" to "boolean"),
                    "señalizacionBraille"      to mapOf("type" to "boolean"),
                    "notas"                    to mapOf("type" to "string")
                )
            ),
            "servicios" to mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "wifi"                  to mapOf("type" to "boolean"),
                    "aireAcondicionado"     to mapOf("type" to "boolean"),
                    "tomasCorriente"        to mapOf("type" to "boolean"),
                    "aceptaTarjeta"         to mapOf("type" to "boolean"),
                    "reservacionRequerida"  to mapOf("type" to "boolean"),
                    "tourGuiado"            to mapOf("type" to "boolean"),
                    "audioguia"             to mapOf("type" to "boolean")
                )
            ),
            "restaurante" to mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "tipoCocina"          to mapOf("type" to "array", "items" to mapOf("type" to "string")),
                    "opcionesDieteticas"  to mapOf("type" to "array", "items" to mapOf("type" to "string")),
                    "tieneTerraza"        to mapOf("type" to "boolean"),
                    "platosRecomendados"  to mapOf("type" to "array", "items" to mapOf("type" to "string"))
                )
            )
        )
    )
}
