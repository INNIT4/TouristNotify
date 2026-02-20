package com.joseibarra.touristnotify

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

/**
 * Manager para controlar el uso de funciones que consumen API (Gemini)
 * Previene abuso y controla costos de API
 */
object UsageManager {

    private const val PREFS_NAME = "TouristNotifyUsage"
    private const val KEY_LAST_RESET_DATE = "last_reset_date"
    private const val KEY_DAILY_ROUTES_COUNT = "daily_routes_count"

    private fun getEncryptedPrefs(context: Context) =
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

    // Límites configurables (ahora desde ConfigManager/Remote Config)
    // Estos son valores de fallback si Remote Config no está disponible
    private const val DEFAULT_MAX_DAILY_ROUTES = 5
    private const val DEFAULT_MAX_DAILY_ROUTES_PREMIUM = 20

    /**
     * Verifica si el usuario puede generar una ruta con IA
     * @return Pair<Boolean, String> - (puede generar?, mensaje explicativo)
     */
    suspend fun canGenerateRoute(context: Context): Pair<Boolean, String> {
        // Actualizar status premium si es necesario
        refreshPremiumStatus()

        // Resetear contador si es un nuevo día
        resetIfNewDay(context)

        val currentCount = getCurrentDailyRoutesCount(context)
        val limit = getMaxDailyRoutes(context)
        val isPremium = isUserPremiumCached()

        return if (currentCount < limit) {
            val remaining = limit - currentCount
            val tierInfo = if (isPremium) " (Premium)" else ""
            Pair(true, "Tienes $remaining rutas IA disponibles hoy$tierInfo")
        } else {
            val upgradeMsg = if (!isPremium) " Mejora a Premium para más rutas." else ""
            Pair(false, "Has alcanzado el límite diario de $limit rutas con IA.$upgradeMsg Vuelve mañana para generar más.")
        }
    }

    /**
     * Registra una ruta generada con IA
     */
    suspend fun recordRouteGeneration(context: Context): Boolean {
        // Actualizar status premium si es necesario
        refreshPremiumStatus()

        resetIfNewDay(context)

        val currentCount = getCurrentDailyRoutesCount(context)
        val limit = getMaxDailyRoutes(context)

        if (currentCount >= limit) {
            return false
        }

        // Incrementar contador local
        val prefs = getEncryptedPrefs(context)
        prefs.edit()
            .putInt(KEY_DAILY_ROUTES_COUNT, currentCount + 1)
            .apply()

        // Si está autenticado, también guardar en Firestore (para sincronización)
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null) {
            try {
                val db = FirebaseFirestore.getInstance()
                val today = getTodayDateString()

                db.collection("users")
                    .document(userId)
                    .collection("usage")
                    .document(today)
                    .set(
                        mapOf(
                            "routesGenerated" to currentCount + 1,
                            "date" to today,
                            "timestamp" to System.currentTimeMillis()
                        )
                    ).await()
            } catch (e: Exception) {
                // Fallar silenciosamente, el contador local es suficiente
            }
        }

        return true
    }

    /**
     * Obtiene el conteo actual de rutas generadas hoy
     */
    fun getCurrentDailyRoutesCount(context: Context): Int {
        resetIfNewDay(context)
        val prefs = getEncryptedPrefs(context)
        return prefs.getInt(KEY_DAILY_ROUTES_COUNT, 0)
    }

    // Cache para evitar consultas repetidas a Firestore
    private var premiumStatusCache: Pair<String?, Boolean>? = null // (userId, isPremium)
    private var premiumCacheTimestamp: Long = 0
    private const val PREMIUM_CACHE_TTL_MS = 5 * 60 * 1000L // 5 minutos

    /**
     * Obtiene el límite máximo de rutas por día para el usuario actual
     * Usa ConfigManager (Remote Config) con fallback a defaults
     * Considera el tier premium del usuario (usa cache)
     */
    private fun getMaxDailyRoutes(context: Context): Int {
        return try {
            if (isUserPremiumCached()) {
                ConfigManager.getMaxDailyRoutesPremium()
            } else {
                ConfigManager.getMaxDailyRoutes()
            }
        } catch (e: Exception) {
            DEFAULT_MAX_DAILY_ROUTES
        }
    }

    /**
     * Verifica si el usuario es premium usando el cache
     * (versión sync para compatibilidad)
     */
    private fun isUserPremiumCached(): Boolean {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        val userEmail = FirebaseAuth.getInstance().currentUser?.email

        if (userId == null) return false

        // Los admins tienen premium automático
        if (AdminConfig.isTourismOfficeUser(userEmail)) {
            return true
        }

        // Usar cache si está disponible
        val now = System.currentTimeMillis()
        if (premiumStatusCache?.first == userId &&
            (now - premiumCacheTimestamp) < PREMIUM_CACHE_TTL_MS) {
            return premiumStatusCache?.second ?: false
        }

        // Si no hay cache, asumir no premium (se actualizará en próxima llamada suspend)
        return false
    }

    /**
     * Actualiza el cache de status premium consultando Firestore
     * Debe llamarse desde funciones suspend antes de verificar límites
     */
    private suspend fun refreshPremiumStatus() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val userEmail = FirebaseAuth.getInstance().currentUser?.email

        // Los admins siempre son premium
        if (AdminConfig.isTourismOfficeUser(userEmail)) {
            premiumStatusCache = Pair(userId, true)
            premiumCacheTimestamp = System.currentTimeMillis()
            return
        }

        // Consultar Firestore solo si el cache ha expirado
        val now = System.currentTimeMillis()
        if (premiumStatusCache?.first == userId &&
            (now - premiumCacheTimestamp) < PREMIUM_CACHE_TTL_MS) {
            return // Cache válido
        }

        try {
            val db = FirebaseFirestore.getInstance()
            val userDoc = db.collection("users")
                .document(userId)
                .get()
                .await()

            val isPremium = userDoc.getBoolean("isPremium") ?: false

            premiumStatusCache = Pair(userId, isPremium)
            premiumCacheTimestamp = now
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) android.util.Log.w("UsageManager", "Error refreshing premium status", e)
        }
    }

    /**
     * Obtiene cuántas rutas le quedan disponibles al usuario hoy
     */
    fun getRemainingRoutes(context: Context): Int {
        val current = getCurrentDailyRoutesCount(context)
        val limit = getMaxDailyRoutes(context)
        return maxOf(0, limit - current)
    }

    /**
     * Verifica si el usuario actual es premium (versión sync usando cache)
     * Para verificación actualizada, usar isUserPremiumAsync()
     */
    fun isUserPremium(): Boolean {
        return isUserPremiumCached()
    }

    /**
     * Verifica si el usuario es premium con consulta a Firestore
     * Actualiza el cache automáticamente
     */
    suspend fun isUserPremiumAsync(): Boolean {
        refreshPremiumStatus()
        return isUserPremiumCached()
    }

    /**
     * Invalida el cache de premium status
     * Útil después de una compra premium o cambio de tier
     */
    fun invalidatePremiumCache() {
        premiumStatusCache = null
        premiumCacheTimestamp = 0
    }

    /**
     * Resetea el contador si es un nuevo día
     */
    private fun resetIfNewDay(context: Context) {
        val prefs = getEncryptedPrefs(context)
        val lastResetDate = prefs.getString(KEY_LAST_RESET_DATE, "")
        val today = getTodayDateString()

        if (lastResetDate != today) {
            // Es un nuevo día, resetear contador
            prefs.edit()
                .putString(KEY_LAST_RESET_DATE, today)
                .putInt(KEY_DAILY_ROUTES_COUNT, 0)
                .apply()
        }
    }

    /**
     * Obtiene la fecha de hoy en formato YYYY-MM-DD
     */
    private fun getTodayDateString(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return dateFormat.format(Date())
    }

    /**
     * Fuerza un reset manual del contador (solo para admin/testing)
     */
    fun forceResetCounter(context: Context) {
        val prefs = getEncryptedPrefs(context)
        prefs.edit()
            .putInt(KEY_DAILY_ROUTES_COUNT, 0)
            .putString(KEY_LAST_RESET_DATE, getTodayDateString())
            .apply()
    }

    /**
     * Obtiene estadísticas de uso (para mostrar al usuario)
     */
    fun getUsageStats(context: Context): UsageStats {
        val current = getCurrentDailyRoutesCount(context)
        val limit = getMaxDailyRoutes(context)
        val remaining = getRemainingRoutes(context)
        val percentage = if (limit > 0) (current.toFloat() / limit * 100).toInt() else 0
        val isPremium = isUserPremiumCached()

        return UsageStats(
            routesUsedToday = current,
            routesLimitToday = limit,
            routesRemainingToday = remaining,
            usagePercentage = percentage,
            isPremiumUser = isPremium,
            premiumLimit = ConfigManager.getMaxDailyRoutesPremium()
        )
    }
}

/**
 * Data class para estadísticas de uso
 */
data class UsageStats(
    val routesUsedToday: Int,
    val routesLimitToday: Int,
    val routesRemainingToday: Int,
    val usagePercentage: Int,
    val isPremiumUser: Boolean = false,
    val premiumLimit: Int = 20
)
