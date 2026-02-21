package com.joseibarra.touristnotify

import android.content.Context
import android.util.Log
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import kotlinx.coroutines.tasks.await

/**
 * Manager centralizado para gestionar configuración de la app
 * Soporta múltiples fuentes con fallback automático:
 * 1. Firebase Remote Config (producción)
 * 2. BuildConfig (desarrollo/fallback)
 * 3. Valores por defecto
 */
object ConfigManager {

    private const val TAG = "ConfigManager"
    private var remoteConfig: FirebaseRemoteConfig? = null
    private var isInitialized = false

    // Claves de configuración
    private const val KEY_GEMINI_API_KEY = "gemini_api_key"
    private const val KEY_MAPS_API_KEY = "maps_api_key"
    private const val KEY_WEATHER_API_KEY = "weather_api_key"
    private const val KEY_MAX_DAILY_ROUTES = "max_daily_routes"
    private const val KEY_MAX_DAILY_ROUTES_PREMIUM = "max_daily_routes_premium"
    private const val KEY_AUTHORIZED_ADMIN_EMAILS = "authorized_admin_emails"

    /**
     * Inicializa Firebase Remote Config
     * Debe llamarse al inicio de la app (Application.onCreate)
     */
    suspend fun initialize(context: Context) {
        if (isInitialized) return

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
                        // Admin emails como CSV (fallback en BuildConfig si Remote Config falla)
                        KEY_AUTHORIZED_ADMIN_EMAILS to "turismo@alamos.gob.mx,admin@turismoalamos.gob.mx"
                    )
                )
            }

            // Fetch y activar configuración remota
            remoteConfig?.fetchAndActivate()?.await()
            isInitialized = true
            if (BuildConfig.DEBUG) Log.i(TAG, "✅ Remote Config inicializado correctamente")

        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "⚠️ Error inicializando Remote Config, usando BuildConfig", e)
            isInitialized = true // Marcar como inicializado para usar BuildConfig
        }
    }

    /**
     * Helper privado para obtener API keys con fallback
     * Prioridad: Remote Config > BuildConfig > empty
     */
    private fun getApiKey(
        remoteConfigKey: String,
        buildConfigKey: String?,
        keyName: String
    ): String {
        // 1. Intentar Remote Config
        remoteConfig?.getString(remoteConfigKey)?.let { remoteKey ->
            if (remoteKey.isNotBlank() && remoteKey != "your_key_here") {
                if (BuildConfig.DEBUG) Log.d(TAG, "🔑 $keyName API key desde Remote Config")
                return remoteKey
            }
        }

        // 2. Fallback a BuildConfig (si existe)
        buildConfigKey?.let { buildKey ->
            if (buildKey.isNotBlank() && buildKey != "your_key_here") {
                if (BuildConfig.DEBUG) Log.d(TAG, "🔑 $keyName API key desde BuildConfig")
                return buildKey
            }
        }

        // 3. No hay key disponible
        if (BuildConfig.DEBUG) Log.w(TAG, "⚠️ $keyName API key no configurada")
        return ""
    }

    /**
     * Obtiene la API key de Gemini
     * Prioridad: Remote Config > BuildConfig > empty
     */
    fun getGeminiApiKey(): String {
        return getApiKey(KEY_GEMINI_API_KEY, BuildConfig.GEMINI_API_KEY, "Gemini")
    }

    /**
     * Obtiene la API key de Maps
     */
    fun getMapsApiKey(): String {
        return getApiKey(KEY_MAPS_API_KEY, BuildConfig.MAPS_API_KEY, "Maps")
    }

    /**
     * Obtiene la API key del clima
     */
    fun getWeatherApiKey(): String {
        // BuildConfig no tiene WEATHER_API_KEY, usar null
        return getApiKey(KEY_WEATHER_API_KEY, null, "Weather")
    }

    /**
     * Obtiene el límite máximo de rutas diarias para usuarios estándar
     */
    fun getMaxDailyRoutes(): Int {
        return try {
            val value = remoteConfig?.getLong(KEY_MAX_DAILY_ROUTES)?.toInt() ?: 5
            if (BuildConfig.DEBUG) Log.d(TAG, "⚙️ Max daily routes: $value")
            value
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "Error obteniendo max_daily_routes, usando default: 5", e)
            5
        }
    }

    /**
     * Obtiene el límite máximo de rutas diarias para usuarios premium
     */
    fun getMaxDailyRoutesPremium(): Int {
        return try {
            val value = remoteConfig?.getLong(KEY_MAX_DAILY_ROUTES_PREMIUM)?.toInt() ?: 20
            if (BuildConfig.DEBUG) Log.d(TAG, "⚙️ Max daily routes premium: $value")
            value
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "Error obteniendo max_daily_routes_premium, usando default: 20", e)
            20
        }
    }

    /**
     * Obtiene la lista de emails administrativos autorizados
     *
     * Formato en Remote Config: CSV separado por comas
     * Ejemplo: "turismo@alamos.gob.mx,admin@turismoalamos.gob.mx,info@turismoalamos.gob.mx"
     *
     * @return Set de emails en minúsculas (normalizados)
     */
    fun getAuthorizedAdminEmails(): Set<String> {
        return try {
            // Intentar Remote Config primero
            val csvEmails = remoteConfig?.getString(KEY_AUTHORIZED_ADMIN_EMAILS)

            if (!csvEmails.isNullOrBlank()) {
                val emails = csvEmails.split(",")
                    .map { it.trim().lowercase() }
                    .filter { it.isNotBlank() }
                    .toSet()

                if (emails.isNotEmpty()) {
                    if (BuildConfig.DEBUG) Log.d(TAG, "🔒 Admin emails desde Remote Config: ${emails.size} emails")
                    return emails
                }
            }

            // Fallback a valores hardcoded (seguridad)
            val fallbackEmails = setOf(
                "turismo@alamos.gob.mx",
                "admin@turismoalamos.gob.mx",
                "info@turismoalamos.gob.mx",
                "comunicacion@alamos.gob.mx",
                "director.turismo@alamos.gob.mx"
            )

            if (BuildConfig.DEBUG) Log.w(TAG, "⚠️ Usando emails admin hardcoded como fallback")
            fallbackEmails

        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Error obteniendo admin emails, usando fallback", e)
            // Fallback final a emails hardcoded
            setOf(
                "turismo@alamos.gob.mx",
                "admin@turismoalamos.gob.mx",
                "info@turismoalamos.gob.mx",
                "comunicacion@alamos.gob.mx",
                "director.turismo@alamos.gob.mx"
            )
        }
    }

    /**
     * Fuerza un refresh de la configuración remota
     * Útil para actualizaciones inmediatas en producción
     */
    suspend fun forceRefresh() {
        try {
            remoteConfig?.fetchAndActivate()?.await()
            if (BuildConfig.DEBUG) Log.i(TAG, "🔄 Remote Config actualizado")
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Error al actualizar Remote Config", e)
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
