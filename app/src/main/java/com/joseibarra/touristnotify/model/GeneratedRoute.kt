package com.joseibarra.touristnotify.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Ruta generada por Gemini con JSON structured output.
 * Reemplaza el viejo `RouteResult(orderedPlaceNames, rawResponse)`.
 *
 * El JSON schema que se envía a Gemini exige exactamente esta estructura.
 * Ver PromptBuilderV2.ROUTE_RESPONSE_SCHEMA.
 */
@Parcelize
data class GeneratedRoute(
    val id: String = "",
    val resumen: RouteSummary = RouteSummary(),
    val paradas: List<RouteStop> = emptyList(),
    val metricas: RouteMetrics = RouteMetrics(),
    val sourceModel: String = "",
    val promptVersion: Int = PROMPT_VERSION
) : Parcelable {

    val isValid: Boolean get() = paradas.size >= 2

    companion object {
        const val PROMPT_VERSION = 2
    }
}

@Parcelize
data class RouteSummary(
    val titulo: String = "",
    val descripcion: String = "",
    val temaPrincipal: String = "",
    val consejosGenerales: List<String> = emptyList()
) : Parcelable

@Parcelize
data class RouteStop(
    /** ID del documento en Firestore (colección `lugares`) */
    val placeId: String = "",
    /** Orden de visita 1-based (1 = primera parada) */
    val ordenSugerido: Int = 0,
    /** Por qué la IA eligió este lugar para este viajero específico */
    val razonSeleccion: String = "",
    val duracionEstimadaMin: Int = 30,
    /** Hora sugerida de llegada "HH:mm" */
    val horaSugeridaInicio: String = "",
    /** Hora sugerida de salida "HH:mm" */
    val horaSugeridaFin: String = "",
    val costoEstimadoMxn: Int = 0,
    val tipsParaEstaParada: List<String> = emptyList(),
    /** Nombre de lugar alternativo si esta parada estuviera cerrada */
    val alternativaSiCerrado: String = ""
) : Parcelable

@Parcelize
data class RouteMetrics(
    val tiempoTotalMin: Int = 0,
    val costoTotalEstimadoMxn: Int = 0,
    /** Metros estimados de caminata total */
    val distanciaCaminadaMetros: Int = 0,
    val comidaIncluida: Boolean = false,
    /** Advertencia de clima si aplica, ej: "Lleva sombrilla, probabilidad de lluvia 60%" */
    val advertenciaClima: String = ""
) : Parcelable

// ─── JSON schema para Gemini (responseSchema) ────────────────────────────────

/**
 * Schema JSON que se envía a Gemini con `responseMimeType = "application/json"`.
 * Gemini garantiza que la respuesta cumpla este schema.
 * Los placeIds deben corresponder a documentos reales en Firestore `lugares/`.
 */
const val ROUTE_RESPONSE_SCHEMA = """
{
  "type": "object",
  "required": ["resumen", "paradas", "metricas"],
  "properties": {
    "resumen": {
      "type": "object",
      "required": ["titulo", "descripcion", "temaPrincipal"],
      "properties": {
        "titulo":         { "type": "string" },
        "descripcion":    { "type": "string" },
        "temaPrincipal":  { "type": "string" },
        "consejosGenerales": { "type": "array", "items": { "type": "string" } }
      }
    },
    "paradas": {
      "type": "array",
      "minItems": 2,
      "maxItems": 8,
      "items": {
        "type": "object",
        "required": ["placeId", "ordenSugerido", "razonSeleccion", "duracionEstimadaMin", "horaSugeridaInicio"],
        "properties": {
          "placeId":              { "type": "string" },
          "ordenSugerido":        { "type": "integer", "minimum": 1 },
          "razonSeleccion":       { "type": "string" },
          "duracionEstimadaMin":  { "type": "integer", "minimum": 5, "maximum": 240 },
          "horaSugeridaInicio":   { "type": "string" },
          "horaSugeridaFin":      { "type": "string" },
          "costoEstimadoMxn":     { "type": "integer", "minimum": 0 },
          "tipsParaEstaParada":   { "type": "array", "items": { "type": "string" } },
          "alternativaSiCerrado": { "type": "string" }
        }
      }
    },
    "metricas": {
      "type": "object",
      "properties": {
        "tiempoTotalMin":           { "type": "integer" },
        "costoTotalEstimadoMxn":    { "type": "integer" },
        "distanciaCaminadaMetros":  { "type": "integer" },
        "comidaIncluida":           { "type": "boolean" },
        "advertenciaClima":         { "type": "string" }
      }
    }
  }
}
"""
