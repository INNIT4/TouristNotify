package com.joseibarra.touristnotify.admin

import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import com.joseibarra.touristnotify.ConfigManager
import com.joseibarra.touristnotify.EnrichmentMeta
import com.joseibarra.touristnotify.FirestoreCollections
import com.joseibarra.touristnotify.TouristSpot
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Llama a Gemini para inferir los campos derivados de un TouristSpot
 * y guarda el resultado en Firestore.
 *
 * Throttling: 1 llamada cada [throttleMs] ms para respetar cuotas.
 */
class EnrichmentService {

    private val firestore = FirebaseFirestore.getInstance()
    private val gson = Gson()

    private val http = OkHttpClient.Builder()
        .callTimeout(60, TimeUnit.SECONDS)
        .build()

    private val geminiEndpoint =
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent"

    /**
     * Enriquece un solo lugar. Respeta [manualOverride] — no sobrescribe
     * campos donde el admin indicó override manual.
     *
     * @return el spot actualizado si tuvo éxito, o el spot original si falla
     */
    suspend fun enrichOne(spot: TouristSpot): Result<TouristSpot> {
        val apiKey = ConfigManager.getGeminiApiKey()
        if (apiKey.isBlank()) return Result.failure(IllegalStateException("GEMINI_API_KEY no configurada"))

        val prompt = EnrichmentPromptBuilder.buildPrompt(spot)
        val requestJson = buildGeminiRequest(prompt)

        return try {
            val responseText = callGemini(apiKey, requestJson)
            val enriched = parseResponse(responseText)
            val updated = applyEnrichment(spot, enriched)
            saveToFirestore(updated)
            Result.success(updated)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Enriquece todos los spots en [spots], emitiendo progreso via [onProgress].
     * Se detiene si [isCancelled] devuelve true.
     */
    suspend fun enrichAll(
        spots: List<TouristSpot>,
        throttleMs: Long = 2000L,
        onProgress: (done: Int, total: Int, spotName: String, success: Boolean) -> Unit = { _, _, _, _ -> },
        isCancelled: () -> Boolean = { false }
    ) {
        spots.forEachIndexed { index, spot ->
            if (isCancelled()) return
            val result = enrichOne(spot)
            onProgress(index + 1, spots.size, spot.nombre, result.isSuccess)
            if (index < spots.size - 1) delay(throttleMs)
        }
    }

    // ── private helpers ──────────────────────────────────────────────────────

    private fun buildGeminiRequest(prompt: String): String {
        val schemaJson = gson.toJson(EnrichmentGeminiSchema.schema)
        return """
        {
          "contents": [{"parts": [{"text": ${gson.toJson(prompt)}}]}],
          "generationConfig": {
            "responseMimeType": "application/json",
            "responseSchema": $schemaJson,
            "temperature": 0.3,
            "maxOutputTokens": 2048
          }
        }
        """.trimIndent()
    }

    private fun callGemini(apiKey: String, requestJson: String): String {
        val body = requestJson.toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("$geminiEndpoint?key=$apiKey")
            .post(body)
            .build()

        http.newCall(request).execute().use { response ->
            val responseBody = response.body?.string() ?: throw Exception("Respuesta vacía de Gemini")
            if (!response.isSuccessful) throw Exception("Gemini error ${response.code}: $responseBody")
            val json = JSONObject(responseBody)
            return json
                .getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
        }
    }

    private fun parseResponse(json: String): EnrichmentResponse {
        return try {
            gson.fromJson(json, EnrichmentResponse::class.java) ?: EnrichmentResponse()
        } catch (e: Exception) {
            EnrichmentResponse()
        }
    }

    private fun applyEnrichment(spot: TouristSpot, r: EnrichmentResponse): TouristSpot {
        val overrides = spot.enrichment.manualOverride
        fun <T> field(name: String, newVal: T?, current: T): T =
            if (overrides[name] == true || newVal == null) current else newVal

        return spot.copy(
            descripcionCorta      = field("descripcionCorta", r.descripcionCorta, spot.descripcionCorta),
            descripcionLarga      = field("descripcionLarga", r.descripcionLarga, spot.descripcionLarga),
            tipsVisita            = field("tipsVisita", r.tipsVisita, spot.tipsVisita),
            historiaResumen       = field("historiaResumen", r.historiaResumen, spot.historiaResumen),
            tipoActividad         = field("tipoActividad", r.tipoActividad, spot.tipoActividad),
            duracionMinSugeridaMin = field("duracionMinSugeridaMin", r.duracionMinSugeridaMin, spot.duracionMinSugeridaMin),
            duracionMaxSugeridaMin = field("duracionMaxSugeridaMin", r.duracionMaxSugeridaMin, spot.duracionMaxSugeridaMin),
            mejorMomentoDelDia    = field("mejorMomentoDelDia", r.mejorMomentoDelDia, spot.mejorMomentoDelDia),
            mejorTemporada        = field("mejorTemporada", r.mejorTemporada, spot.mejorTemporada),
            epocasEvitar          = field("epocasEvitar", r.epocasEvitar, spot.epocasEvitar),
            audienciaIdeal        = field("audienciaIdeal", r.audienciaIdeal, spot.audienciaIdeal),
            aptoNiños             = field("aptoNinos", r.aptoNinos, spot.aptoNiños),
            aptoMascotas          = field("aptoMascotas", r.aptoMascotas, spot.aptoMascotas),
            nivelDificultadFisica = field("nivelDificultadFisica", r.nivelDificultadFisica, spot.nivelDificultadFisica),
            precioNivel           = field("precioNivel", r.precioNivel, spot.precioNivel),
            precioPromedioMxn     = field("precioPromedioMxn", r.precioPromedioMxn, spot.precioPromedioMxn),
            entradaGratuita       = field("entradaGratuita", r.entradaGratuita, spot.entradaGratuita),
            tags                  = field("tags", r.tags, spot.tags),
            accesibilidad         = field("accesibilidad", r.accesibilidad, spot.accesibilidad),
            servicios             = field("servicios", r.servicios, spot.servicios),
            restaurante           = field("restaurante", r.restaurante, spot.restaurante),
            enrichment            = spot.enrichment.copy(
                version         = spot.enrichment.version + 1,
                source          = "gemini-1.5-flash",
                confidenceScore = 0.75
            )
        )
    }

    private suspend fun saveToFirestore(spot: TouristSpot) {
        val data = mapOf(
            "descripcionCorta"       to spot.descripcionCorta,
            "descripcionLarga"       to spot.descripcionLarga,
            "tipsVisita"             to spot.tipsVisita,
            "historiaResumen"        to spot.historiaResumen,
            "tipoActividad"          to spot.tipoActividad,
            "duracionMinSugeridaMin" to spot.duracionMinSugeridaMin,
            "duracionMaxSugeridaMin" to spot.duracionMaxSugeridaMin,
            "mejorMomentoDelDia"     to spot.mejorMomentoDelDia,
            "mejorTemporada"         to spot.mejorTemporada,
            "epocasEvitar"           to spot.epocasEvitar,
            "audienciaIdeal"         to spot.audienciaIdeal,
            "aptoNiños"              to spot.aptoNiños,
            "aptoMascotas"           to spot.aptoMascotas,
            "nivelDificultadFisica"  to spot.nivelDificultadFisica,
            "precioNivel"            to spot.precioNivel,
            "precioPromedioMxn"      to spot.precioPromedioMxn,
            "entradaGratuita"        to spot.entradaGratuita,
            "tags"                   to spot.tags,
            "accesibilidad"          to mapOf(
                "sillaRuedas"              to spot.accesibilidad.sillaRuedas,
                "banoAccesible"            to spot.accesibilidad.banoAccesible,
                "estacionamiento"          to spot.accesibilidad.estacionamiento,
                "estacionamientoAccesible" to spot.accesibilidad.estacionamientoAccesible,
                "señalizacionBraille"      to spot.accesibilidad.señalizacionBraille,
                "notas"                    to spot.accesibilidad.notas
            ),
            "servicios" to mapOf(
                "wifi"                 to spot.servicios.wifi,
                "aireAcondicionado"    to spot.servicios.aireAcondicionado,
                "tomasCorriente"       to spot.servicios.tomasCorriente,
                "aceptaTarjeta"        to spot.servicios.aceptaTarjeta,
                "reservacionRequerida" to spot.servicios.reservacionRequerida,
                "tourGuiado"           to spot.servicios.tourGuiado,
                "audioguia"            to spot.servicios.audioguia
            ),
            "restaurante" to mapOf(
                "tipoCocina"         to spot.restaurante.tipoCocina,
                "opcionesDieteticas" to spot.restaurante.opcionesDieteticas,
                "tieneTerraza"       to spot.restaurante.tieneTerraza,
                "platosRecomendados" to spot.restaurante.platosRecomendados
            ),
            "enrichment" to mapOf(
                "version"         to spot.enrichment.version,
                "source"          to spot.enrichment.source,
                "confidenceScore" to spot.enrichment.confidenceScore,
                "manualOverride"  to spot.enrichment.manualOverride
            )
        )
        firestore.collection(FirestoreCollections.PLACES)
            .document(spot.id)
            .update(data)
            .await()
    }
}
