package com.joseibarra.trazago

import android.content.Context
import android.util.Log
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import kotlinx.coroutines.tasks.await

/**
 * Manager centralizado para gestionar configuración de la app.
 *
 * Soporta múltiples fuentes con fallback automático:
 * 1. Firebase Remote Config (producción)
 * 2. BuildConfig (desarrollo/fallback)
 * 3. Valores por defecto
 *
 * **NOTA DE SEGURIDAD (P1-3)**: Las API keys que se devuelven aquí están en
 * el APK (vía BuildConfig) y son extraíbles. El plan de hardening está en
 * `.claude/docs/api_keys_hardening.md`:
 *   - GEMINI_API_KEY → migrar a Cloud Function `generateRoute` (App Check protected)
 *   - WEATHER_API_KEY → migrar a Cloud Function `getWeather` (con caché)
 *   - DIRECTIONS_API_KEY → migrar igual que Gemini
 *   - MAPS_API_KEY → restringir por package+SHA1 en Cloud Console (no migrable)
 *
 * Mientras tanto: TODO ejecutar las restricciones por package+SHA1 en Google
 * Cloud Console (sección 1 del documento de hardening).
 */
object ConfigManager {

    private const val TAG = "ConfigManager"
    @Volatile
    private var remoteConfig: FirebaseRemoteConfig? = null
    @Volatile
    private var isInitialized = false

    // Claves de configuración
    private const val KEY_GEMINI_API_KEY = "gemini_api_key"
    private const val KEY_MAPS_API_KEY = "maps_api_key"
    private const val KEY_WEATHER_API_KEY = "weather_api_key"
    private const val KEY_DIRECTIONS_API_KEY = "directions_api_key"
    private const val KEY_MAX_DAILY_ROUTES = "max_daily_routes"
    private const val KEY_MAX_DAILY_ROUTES_PREMIUM = "max_daily_routes_premium"
    private const val KEY_USE_V2_ROUTE_GENERATOR = AppConstants.RC_USE_V2_ROUTE_GENERATOR
    private const val KEY_MAX_PROMPT_LENGTH = AppConstants.RC_MAX_PROMPT_LENGTH

    /**
     * Inicializa Firebase Remote Config
     * Debe llamarse al inicio de la app (Application.onCreate)
     */
    suspend fun initialize(context: Context) {
        if (isInitialized) return
        synchronized(this) {
            if (isInitialized) return
        }

        try {
            remoteConfig = FirebaseRemoteConfig.getInstance().apply {
                val configSettings = FirebaseRemoteConfigSettings.Builder()
                    .setMinimumFetchIntervalInSeconds(3600) // 1 hora en producción
                    .build()
                setConfigSettingsAsync(configSettings)

                // Valores por defecto (fallback)
                setDefaultsAsync(
                    mapOf(
                        KEY_MAX_DAILY_ROUTES to 5,
                        KEY_MAX_DAILY_ROUTES_PREMIUM to 20,
                        // Las API keys no tienen defaults por seguridad
                        KEY_GEMINI_API_KEY to "",
                        KEY_MAPS_API_KEY to "",
                        KEY_WEATHER_API_KEY to "",
                        KEY_USE_V2_ROUTE_GENERATOR to true,
                        KEY_MAX_PROMPT_LENGTH to AppConstants.DEFAULT_MAX_PROMPT_LENGTH
                    )
                )
            }

            // Fetch y activar configuración remota
            remoteConfig?.fetchAndActivate()?.await()
            isInitialized = true
            Log.i(TAG, "✅ Remote Config inicializado correctamente")

        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Error inicializando Remote Config, usando BuildConfig", e)
            isInitialized = true // Marcar como inicializado para usar BuildConfig
        }
    }

    /**
     * Obtiene la API key de Gemini
     * Prioridad: Remote Config > BuildConfig > empty
     */
    fun getGeminiApiKey(): String {
        // 1. Intentar Remote Config
        remoteConfig?.getString(KEY_GEMINI_API_KEY)?.let { remoteKey ->
            if (remoteKey.isNotBlank() && remoteKey != "your_key_here") {
                Log.d(TAG, "🔑 Gemini API key desde Remote Config")
                return remoteKey
            }
        }

        // 2. Fallback a BuildConfig (local.properties)
        val buildKey = BuildConfig.GEMINI_API_KEY
        if (buildKey.isNotBlank() && buildKey != "your_key_here") {
            Log.d(TAG, "🔑 Gemini API key desde BuildConfig")
            return buildKey
        }

        // 3. No hay key disponible
        Log.w(TAG, "⚠️ Gemini API key no configurada")
        return ""
    }

    /**
     * Obtiene la API key de Maps
     */
    fun getMapsApiKey(): String {
        remoteConfig?.getString(KEY_MAPS_API_KEY)?.let { remoteKey ->
            if (remoteKey.isNotBlank() && remoteKey != "your_key_here") {
                Log.d(TAG, "🔑 Maps API key desde Remote Config")
                return remoteKey
            }
        }

        val buildKey = BuildConfig.MAPS_API_KEY
        if (buildKey.isNotBlank() && buildKey != "your_key_here") {
            Log.d(TAG, "🔑 Maps API key desde BuildConfig")
            return buildKey
        }

        Log.w(TAG, "⚠️ Maps API key no configurada")
        return ""
    }

    /**
     * Obtiene la API key del clima
     */
    fun getWeatherApiKey(): String {
        remoteConfig?.getString(KEY_WEATHER_API_KEY)?.let { remoteKey ->
            if (remoteKey.isNotBlank() && remoteKey != "your_key_here") {
                Log.d(TAG, "🔑 Weather API key desde Remote Config")
                return remoteKey
            }
        }

        // BuildConfig no tiene WEATHER_API_KEY, usar string vacío
        // (La app funcionará con datos mock)
        Log.d(TAG, "ℹ️ Weather API key no configurada, usando datos mock")
        return ""
    }

    /** Directions / Routes API key (Routes API v2 + Directions API). */
    fun getDirectionsApiKey(): String {
        remoteConfig?.getString(KEY_DIRECTIONS_API_KEY)?.let { remoteKey ->
            if (remoteKey.isNotBlank() && remoteKey != "your_key_here") return remoteKey
        }
        val buildKey = BuildConfig.DIRECTIONS_API_KEY
        if (buildKey.isNotBlank() && buildKey != "your_key_here") return buildKey
        return ""
    }

    /**
     * Obtiene el límite máximo de rutas diarias para usuarios estándar
     */
    fun getMaxDailyRoutes(): Int {
        return try {
            val value = remoteConfig?.getLong(KEY_MAX_DAILY_ROUTES)?.toInt() ?: 5
            Log.d(TAG, "⚙️ Max daily routes: $value")
            value
        } catch (e: Exception) {
            Log.w(TAG, "Error obteniendo max_daily_routes, usando default: 5", e)
            5
        }
    }

    /**
     * Obtiene el límite máximo de rutas diarias para usuarios premium
     */
    fun getMaxDailyRoutesPremium(): Int {
        return try {
            val value = remoteConfig?.getLong(KEY_MAX_DAILY_ROUTES_PREMIUM)?.toInt() ?: 20
            Log.d(TAG, "⚙️ Max daily routes premium: $value")
            value
        } catch (e: Exception) {
            Log.w(TAG, "Error obteniendo max_daily_routes_premium, usando default: 20", e)
            20
        }
    }

    /**
     * Feature flag: usa el nuevo pipeline de generación de rutas V2 (JSON structured output).
     * Puede desactivarse desde Remote Config para hacer rollback sin publicar.
     */
    fun useV2RouteGenerator(): Boolean {
        return try {
            remoteConfig?.getBoolean(KEY_USE_V2_ROUTE_GENERATOR) ?: true
        } catch (e: Exception) {
            true
        }
    }

    /** Longitud máxima del prompt enviado a Gemini (en caracteres). */
    fun getMaxPromptLength(): Int {
        return try {
            remoteConfig?.getLong(KEY_MAX_PROMPT_LENGTH)?.toInt()
                ?: AppConstants.DEFAULT_MAX_PROMPT_LENGTH
        } catch (e: Exception) {
            AppConstants.DEFAULT_MAX_PROMPT_LENGTH
        }
    }

    /**
     * Fuerza un refresh de la configuración remota
     * Útil para actualizaciones inmediatas en producción
     */
    suspend fun forceRefresh() {
        try {
            remoteConfig?.fetchAndActivate()?.await()
            Log.i(TAG, "🔄 Remote Config actualizado")
        } catch (e: Exception) {
            Log.e(TAG, "Error al actualizar Remote Config", e)
        }
    }

    /**
     * Verifica si todas las API keys críticas están configuradas
     */
    fun areApiKeysConfigured(): Map<String, Boolean> {
        return mapOf(
            "gemini" to getGeminiApiKey().isNotBlank(),
            "maps" to getMapsApiKey().isNotBlank(),
            "weather" to getWeatherApiKey().isNotBlank()
        )
    }

    /**
     * Obtiene información de diagnóstico
     */
    fun getDiagnosticInfo(): String {
        val keys = areApiKeysConfigured()
        return buildString {
            appendLine("🔧 CONFIGURACIÓN")
            appendLine("Remote Config: ${if (isInitialized) "✅" else "❌"}")
            appendLine("")
            appendLine("🔑 API KEYS")
            appendLine("Gemini: ${if (keys["gemini"] == true) "✅" else "❌"}")
            appendLine("Maps: ${if (keys["maps"] == true) "✅" else "❌"}")
            appendLine("Weather: ${if (keys["weather"] == true) "✅" else "⚠️ Mock"}")
            appendLine("")
            appendLine("⚙️ LÍMITES")
            appendLine("Rutas diarias: ${getMaxDailyRoutes()}")
            appendLine("Rutas premium: ${getMaxDailyRoutesPremium()}")
        }
    }
}
