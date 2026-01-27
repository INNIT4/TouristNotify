package com.joseibarra.touristnotify

import android.content.Context
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

    // Límites configurables
    private const val MAX_DAILY_ROUTES = 5  // Máximo de rutas IA por día
    private const val MAX_DAILY_ROUTES_PREMIUM = 20  // Para usuarios premium (futuro)

    /**
     * Verifica si el usuario puede generar una ruta con IA
     * @return Pair<Boolean, String> - (puede generar?, mensaje explicativo)
     */
    suspend fun canGenerateRoute(context: Context): Pair<Boolean, String> {
        // Resetear contador si es un nuevo día
        resetIfNewDay(context)

        val currentCount = getCurrentDailyRoutesCount(context)
        val limit = getMaxDailyRoutes(context)

        return if (currentCount < limit) {
            val remaining = limit - currentCount
            Pair(true, "Tienes $remaining rutas IA disponibles hoy")
        } else {
            Pair(false, "Has alcanzado el límite diario de $limit rutas con IA. Vuelve mañana para generar más.")
        }
    }

    /**
     * Registra una ruta generada con IA
     */
    suspend fun recordRouteGeneration(context: Context): Boolean {
        resetIfNewDay(context)

        val currentCount = getCurrentDailyRoutesCount(context)
        val limit = getMaxDailyRoutes(context)

        if (currentCount >= limit) {
            return false
        }

        // Incrementar contador local
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
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
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_DAILY_ROUTES_COUNT, 0)
    }

    /**
     * Obtiene el límite máximo de rutas por día para el usuario actual
     */
    private fun getMaxDailyRoutes(context: Context): Int {
        // TODO: Implementar lógica de usuarios premium cuando exista
        // Por ahora todos tienen el mismo límite
        return MAX_DAILY_ROUTES
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
     * Resetea el contador si es un nuevo día
     */
    private fun resetIfNewDay(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
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
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
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

        return UsageStats(
            routesUsedToday = current,
            routesLimitToday = limit,
            routesRemainingToday = remaining,
            usagePercentage = percentage
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
    val usagePercentage: Int
)
