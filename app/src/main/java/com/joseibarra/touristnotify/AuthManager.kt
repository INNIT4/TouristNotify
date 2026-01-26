package com.joseibarra.touristnotify

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AlertDialog
import com.google.firebase.auth.FirebaseAuth

/**
 * Manager para gestionar autenticación y modo invitado
 */
object AuthManager {

    private const val PREFS_NAME = "TouristNotifyPrefs"
    private const val KEY_GUEST_MODE = "guest_mode_enabled"

    /**
     * Verifica si el usuario está autenticado (tiene cuenta)
     */
    fun isAuthenticated(): Boolean {
        return FirebaseAuth.getInstance().currentUser != null
    }

    /**
     * Verifica si está en modo invitado
     */
    fun isGuestMode(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_GUEST_MODE, false)
    }

    /**
     * Activa el modo invitado
     */
    fun enableGuestMode(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_GUEST_MODE, true)
            .apply()
    }

    /**
     * Desactiva el modo invitado (cuando el usuario inicia sesión)
     */
    fun disableGuestMode(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_GUEST_MODE, false)
            .apply()
    }

    /**
     * Verifica si una acción requiere autenticación y muestra prompt si es necesario
     *
     * @return true si puede continuar, false si necesita login
     */
    fun requireAuth(
        context: Context,
        actionName: String,
        onAuthConfirmed: () -> Unit
    ): Boolean {
        if (isAuthenticated()) {
            onAuthConfirmed()
            return true
        }

        // Mostrar diálogo pidiendo login
        AlertDialog.Builder(context)
            .setTitle("Iniciar sesión")
            .setMessage("Para $actionName necesitas crear una cuenta o iniciar sesión.\n\n¿Deseas continuar?")
            .setPositiveButton("Iniciar sesión") { _, _ ->
                // Ir a LoginActivity
                val intent = Intent(context, LoginActivity::class.java)
                intent.putExtra("RETURN_AFTER_LOGIN", true)
                context.startActivity(intent)
            }
            .setNegativeButton("Ahora no", null)
            .show()

        return false
    }

    /**
     * Lista de funciones que NO requieren login (modo invitado)
     */
    object GuestFeatures {
        const val VIEW_PLACES = true
        const val VIEW_MAP = true
        const val VIEW_BLOG = true
        const val VIEW_EVENTS = true
        const val VIEW_WEATHER = true
        const val SCAN_QR = true
        const val VIEW_THEMED_ROUTES = true
        const val VIEW_TOP_PLACES = true
        const val VIEW_PHOTOS = true
        const val VIEW_SERVICES = true
    }

    /**
     * Lista de funciones que SÍ requieren login
     */
    object AuthRequired {
        const val GENERATE_ROUTES = "generar rutas personalizadas con IA"
        const val SAVE_FAVORITES = "guardar favoritos"
        const val SAVE_ROUTES = "guardar rutas"
        const val SHARE_ROUTES = "compartir rutas"
        const val CHECK_IN = "hacer check-ins en lugares"
        const val UPLOAD_PHOTOS = "subir fotos"
        const val LEAVE_REVIEWS = "dejar reseñas"
        const val PROXIMITY_NOTIFICATIONS = "activar notificaciones de proximidad"
        const val VIEW_STATS = "ver estadísticas personales"
        const val EMERGENCY_CONTACTS = "configurar contactos de emergencia"
        const val MY_ROUTES = "ver tus rutas guardadas"
        const val MY_FAVORITES = "ver tus favoritos"
    }

    /**
     * Verifica si se necesita migrar de modo invitado a cuenta
     */
    fun shouldMigrateFromGuest(context: Context): Boolean {
        return isGuestMode(context) && isAuthenticated()
    }

    /**
     * Realiza la migración de modo invitado a cuenta autenticada
     */
    fun migrateFromGuestToAuth(context: Context) {
        if (shouldMigrateFromGuest(context)) {
            disableGuestMode(context)
            // Aquí se podrían migrar datos locales a Firebase si fuera necesario
        }
    }
}
