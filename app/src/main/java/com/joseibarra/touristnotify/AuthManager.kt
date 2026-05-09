package com.joseibarra.touristnotify

import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.firebase.auth.FirebaseAuth

/**
 * Manager para gestionar autenticación y modo invitado
 */
object AuthManager {

    // CR P2: usar AppConstants.PREFS_NAME para evitar duplicación de la constante
    private const val KEY_GUEST_MODE = "guest_mode_enabled"

    /**
     * Crea o abre el EncryptedSharedPreferences de la app.
     * Las claves y valores quedan cifrados en el almacenamiento del dispositivo.
     */
    private fun getEncryptedPrefs(context: Context) =
        EncryptedSharedPreferences.create(
            context,
            AppConstants.PREFS_NAME,
            MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

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
        return getEncryptedPrefs(context)
            .getBoolean(KEY_GUEST_MODE, false)
    }

    /**
     * Activa el modo invitado
     */
    fun enableGuestMode(context: Context) {
        getEncryptedPrefs(context)
            .edit()
            .putBoolean(KEY_GUEST_MODE, true)
            .apply()
    }

    /**
     * Desactiva el modo invitado (cuando el usuario inicia sesión)
     */
    fun disableGuestMode(context: Context) {
        getEncryptedPrefs(context)
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

        // Mostrar diálogo pidiendo login (strings localizados desde resources)
        val ctx = context
        AlertDialog.Builder(ctx)
            .setTitle(ctx.getString(R.string.login_button))
            .setMessage(ctx.getString(R.string.auth_required_for_action, actionName))
            .setPositiveButton(ctx.getString(R.string.login_button)) { _, _ ->
                val intent = Intent(ctx, LoginActivity::class.java)
                intent.putExtra("RETURN_AFTER_LOGIN", true)
                ctx.startActivity(intent)
            }
            .setNegativeButton(ctx.getString(R.string.later), null)
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
        const val VIEW_TOP_PLACES = true
        const val VIEW_PHOTOS = true
        const val VIEW_SERVICES = true
        const val EMERGENCY_CONTACTS = true
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
        const val MY_ROUTES = "ver tus rutas guardadas"
        const val MY_FAVORITES = "ver tus favoritos"
        const val THEMED_ROUTES = "acceder a rutas predeterminadas"
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

    /**
     * Aplica estilo visual de "bloqueado" a una vista cuando el usuario es invitado
     * Reduce opacidad para indicar que está deshabilitada
     *
     * @param view Vista a la que aplicar el estilo
     * @param lockIconView Vista opcional del icono de candado a mostrar
     */
    fun applyLockedStyle(view: View, lockIconView: ImageView? = null) {
        // Mantener el botón visualmente activo (alpha completo) para que el usuario
        // sepa que puede tocarlo. El icono de candado ya comunica que requiere login.
        // Alpha=0.5 enviaba la señal incorrecta de "botón roto/deshabilitado".
        view.alpha = 1.0f

        // Mostrar icono de candado si se proporciona
        lockIconView?.visibility = View.VISIBLE
    }

    /**
     * Remueve el estilo visual de "bloqueado" de una vista
     *
     * @param view Vista de la que remover el estilo
     * @param lockIconView Vista opcional del icono de candado a ocultar
     */
    fun removeLockedStyle(view: View, lockIconView: ImageView? = null) {
        // Restaurar opacidad completa
        view.alpha = 1.0f

        // Ocultar icono de candado si se proporciona
        lockIconView?.visibility = View.GONE
    }

    /**
     * Aplica estilo bloqueado condicionalmente basado en si el usuario está autenticado
     *
     * @param view Vista a la que aplicar el estilo
     * @param lockIconView Vista opcional del icono de candado
     */
    fun applyLockedStyleIfGuest(view: View, lockIconView: ImageView? = null) {
        if (!isAuthenticated()) {
            applyLockedStyle(view, lockIconView)
        } else {
            removeLockedStyle(view, lockIconView)
        }
    }
}
