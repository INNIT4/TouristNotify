package com.joseibarra.trazago.routegen

import android.util.Log
import com.joseibarra.trazago.AppConstants
import com.joseibarra.trazago.TouristSpot
import com.joseibarra.trazago.model.GeneratedRoute
import com.joseibarra.trazago.model.ROUTE_RESPONSE_SCHEMA
import com.joseibarra.trazago.model.RouteMetrics
import com.joseibarra.trazago.model.RouteSummary
import com.joseibarra.trazago.model.RouteStop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

private const val TAG = "RouteGeneratorV2"

/**
 * Genera rutas turísticas usando Gemini con JSON structured output.
 *
 * Mejoras sobre RouteGenerator V1:
 * - `responseMimeType = "application/json"` + `responseSchema` → JSON garantizado.
 * - Devuelve `GeneratedRoute` con `placeId`, razones, tiempos, costos por parada.
 * - Sin necesidad de parsear nombres en texto libre → elimina bug de substring matching.
 * - `promptVersion = 2` para control de versión.
 */
object RouteGeneratorV2 {

    private val httpClient = OkHttpClient.Builder()
        .callTimeout(90, TimeUnit.SECONDS)
        .build()

    /**
     * Genera una ruta nueva basada en las preferencias del usuario.
     *
     * @param promptInput  Input construido por PromptBuilderV2.
     * @param apiKey       GEMINI_API_KEY obtenida de ConfigManager.
     */
    suspend fun generate(
        promptInput: PromptBuilderV2.PromptInput,
        apiKey: String
    ): GeneratedRoute {
        val prompt = PromptBuilderV2.build(promptInput)
        Log.d(TAG, "Prompt V2 length: ${prompt.length} chars")

        return executeGeminiRequest(prompt, apiKey)
    }

    /**
     * Regenera una ruta basada en feedback del usuario sobre una ruta previa.
     *
     * @param prevRoute    Ruta anterior que el usuario quiere mejorar.
     * @param feedback     Feedback libre del usuario ("más barata", "más cultural", etc.).
     * @param promptInput  Input original (mismas prefs).
     * @param apiKey       GEMINI_API_KEY.
     */
    suspend fun regenerate(
        prevRoute: GeneratedRoute,
        feedback: String,
        promptInput: PromptBuilderV2.PromptInput,
        apiKey: String
    ): GeneratedRoute {
        val originalPrompt = PromptBuilderV2.build(promptInput)
        val prevPlaceIds = prevRoute.paradas.joinToString(", ") { it.placeId }

        val regenerationPrompt = buildString {
            appendLine(originalPrompt)
            appendLine()
            appendLine("# REGENERACIÓN CON FEEDBACK")
            appendLine("La ruta anterior incluía: $prevPlaceIds")
            appendLine("El usuario la rechazó porque: \"$feedback\"")
            appendLine("Genera una ruta DIFERENTE. Evita usar exactamente las mismas paradas.")
            appendLine("Aplica el feedback para mejorar la ruta. Devuelve el mismo JSON.")
        }

        Log.d(TAG, "Regenerating with feedback: $feedback")
        return executeGeminiRequest(regenerationPrompt, apiKey)
    }

    // ─── Privados ────────────────────────────────────────────────────────────

    private suspend fun executeGeminiRequest(prompt: String, apiKey: String): GeneratedRoute {
        val body = buildRequestBody(prompt)

        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val jsonText = withTimeout(AppConstants.AI_TIMEOUT_V2_MS) {
            withContext(Dispatchers.IO) {
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw Exception("API error ${response.code}")
                    val json = JSONObject(response.body?.string() ?: throw Exception("Respuesta vacía"))
                    extractTextFromResponse(json)
                }
            }
        }

        Log.d(TAG, "Gemini response (${jsonText.length} chars)")
        return parseGeneratedRoute(jsonText)
    }

    private fun buildRequestBody(prompt: String): JSONObject = JSONObject().apply {
        put("contents", JSONArray().put(JSONObject().apply {
            put("parts", JSONArray().put(JSONObject().put("text", prompt)))
        }))
        put("generationConfig", JSONObject().apply {
            put("temperature", 0.3)
            put("topP", 0.8)
            put("maxOutputTokens", 8192)
            put("responseMimeType", "application/json")
            put("responseSchema", JSONObject(ROUTE_RESPONSE_SCHEMA))
            // Deshabilitar thinking para que todos los tokens vayan al JSON de salida
            put("thinkingConfig", JSONObject().put("thinkingBudget", 0))
        })
        put("safetySettings", buildSafetySettings())
    }

    private fun buildSafetySettings(): JSONArray = JSONArray().apply {
        listOf(
            "HARM_CATEGORY_HARASSMENT",
            "HARM_CATEGORY_HATE_SPEECH",
            "HARM_CATEGORY_SEXUALLY_EXPLICIT",
            "HARM_CATEGORY_DANGEROUS_CONTENT"
        ).forEach { category ->
            put(JSONObject().apply {
                put("category", category)
                put("threshold", "BLOCK_MEDIUM_AND_ABOVE")
            })
        }
    }

    private fun extractTextFromResponse(json: JSONObject): String {
        val parts = json.getJSONArray("candidates")
            .getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")

        return buildString {
            for (i in 0 until parts.length()) {
                val part = parts.getJSONObject(i)
                if (!part.optBoolean("thought", false)) {
                    append(part.optString("text", ""))
                }
            }
        }.ifBlank { throw Exception("La respuesta de la IA está vacía.") }
    }

    internal fun parseGeneratedRoute(jsonText: String): GeneratedRoute {
        val json = try {
            JSONObject(jsonText)
        } catch (e: Exception) {
            Log.e(TAG, "JSON truncado (${jsonText.length} chars): ${jsonText.take(200)}")
            throw Exception("La IA devolvió una respuesta incompleta. Intenta de nuevo.")
        }

        val resumenJson = json.optJSONObject("resumen") ?: JSONObject()
        val resumen = RouteSummary(
            titulo = resumenJson.optString("titulo", "Ruta por Álamos"),
            descripcion = resumenJson.optString("descripcion", ""),
            temaPrincipal = resumenJson.optString("temaPrincipal", ""),
            consejosGenerales = resumenJson.optJSONArray("consejosGenerales")
                ?.let { arr -> (0 until arr.length()).map { arr.getString(it) } }
                ?: emptyList()
        )

        val paradasArr = json.optJSONArray("paradas") ?: JSONArray()
        val paradas = (0 until paradasArr.length()).map { i ->
            val p = paradasArr.getJSONObject(i)
            RouteStop(
                placeId = p.optString("placeId", ""),
                ordenSugerido = p.optInt("ordenSugerido", i + 1),
                razonSeleccion = p.optString("razonSeleccion", ""),
                duracionEstimadaMin = p.optInt("duracionEstimadaMin", 20),
                horaSugeridaInicio = p.optString("horaSugeridaInicio", ""),
                horaSugeridaFin = p.optString("horaSugeridaFin", ""),
                costoEstimadoMxn = p.optInt("costoEstimadoMxn", 0),
                tipsParaEstaParada = p.optJSONArray("tipsParaEstaParada")
                    ?.let { arr -> (0 until arr.length()).map { arr.getString(it) } }
                    ?: emptyList(),
                alternativaSiCerrado = p.optString("alternativaSiCerrado", "")
            )
        }.sortedBy { it.ordenSugerido }

        val metricasJson = json.optJSONObject("metricas") ?: JSONObject()
        val metricas = RouteMetrics(
            tiempoTotalMin = metricasJson.optInt("tiempoTotalMin", 0),
            costoTotalEstimadoMxn = metricasJson.optInt("costoTotalEstimadoMxn", 0),
            distanciaCaminadaMetros = metricasJson.optInt("distanciaCaminadaMetros", 0),
            comidaIncluida = metricasJson.optBoolean("comidaIncluida", false),
            advertenciaClima = metricasJson.optString("advertenciaClima", "")
        )

        val route = GeneratedRoute(
            id = UUID.randomUUID().toString(),
            resumen = resumen,
            paradas = paradas,
            metricas = metricas,
            sourceModel = "gemini-2.5-flash"
        )

        if (!route.isValid) {
            throw Exception("La IA generó menos de 2 paradas. Intenta con más tiempo o menos restricciones.")
        }

        return route
    }
}
