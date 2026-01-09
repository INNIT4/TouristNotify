package com.joseibarra.touristnotify

import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar
import android.view.View

object NotificationHelper {

    enum class NotificationType {
        SUCCESS, ERROR, INFO, WARNING
    }

    /**
     * Muestra una notificación estilo Material Design usando Snackbar
     */
    fun show(
        view: View,
        message: String,
        type: NotificationType = NotificationType.INFO,
        duration: Int = Snackbar.LENGTH_SHORT,
        actionText: String? = null,
        actionCallback: (() -> Unit)? = null
    ) {
        val snackbar = Snackbar.make(view, message, duration)

        // Configurar colores según el tipo
        val backgroundColor = when (type) {
            NotificationType.SUCCESS -> R.color.md_theme_light_secondary
            NotificationType.ERROR -> R.color.md_theme_light_error
            NotificationType.WARNING -> R.color.md_theme_light_tertiary
            NotificationType.INFO -> R.color.md_theme_light_primary
        }

        snackbar.view.setBackgroundColor(
            ContextCompat.getColor(view.context, backgroundColor)
        )

        snackbar.setTextColor(
            ContextCompat.getColor(view.context, R.color.white)
        )

        // Agregar acción si se proporciona
        if (actionText != null && actionCallback != null) {
            snackbar.setAction(actionText) {
                actionCallback()
            }
            snackbar.setActionTextColor(
                ContextCompat.getColor(view.context, R.color.white)
            )
        }

        snackbar.show()
    }

    /**
     * Muestra un Toast personalizado (fallback cuando no hay View disponible)
     */
    fun showToast(
        context: Context,
        message: String,
        type: NotificationType = NotificationType.INFO,
        duration: Int = Toast.LENGTH_SHORT
    ) {
        Toast.makeText(context, message, duration).show()
    }

    // Métodos de conveniencia
    fun success(view: View, message: String, duration: Int = Snackbar.LENGTH_SHORT) {
        show(view, message, NotificationType.SUCCESS, duration)
    }

    fun error(view: View, message: String, duration: Int = Snackbar.LENGTH_LONG) {
        show(view, message, NotificationType.ERROR, duration)
    }

    fun info(view: View, message: String, duration: Int = Snackbar.LENGTH_SHORT) {
        show(view, message, NotificationType.INFO, duration)
    }

    fun warning(view: View, message: String, duration: Int = Snackbar.LENGTH_LONG) {
        show(view, message, NotificationType.WARNING, duration)
    }

    // Notificaciones específicas para la app de turismo
    fun routeSaved(view: View, routeName: String) {
        show(
            view,
            "✓ Ruta '$routeName' guardada exitosamente",
            NotificationType.SUCCESS,
            Snackbar.LENGTH_LONG
        )
    }

    fun reviewSubmitted(view: View) {
        show(
            view,
            "✓ Tu reseña ha sido publicada. ¡Gracias por compartir!",
            NotificationType.SUCCESS,
            Snackbar.LENGTH_LONG
        )
    }

    fun placeAddedToRoute(view: View, placeName: String) {
        show(
            view,
            "✓ '$placeName' agregado a tu ruta",
            NotificationType.SUCCESS
        )
    }

    fun locationPermissionNeeded(view: View, onAction: () -> Unit) {
        show(
            view,
            "Se necesita permiso de ubicación para esta función",
            NotificationType.WARNING,
            Snackbar.LENGTH_LONG,
            "Otorgar",
            onAction
        )
    }

    fun loginRequired(view: View, onAction: () -> Unit) {
        show(
            view,
            "Debes iniciar sesión para usar esta función",
            NotificationType.INFO,
            Snackbar.LENGTH_LONG,
            "Iniciar sesión",
            onAction
        )
    }

    fun noInternet(view: View) {
        show(
            view,
            "⚠ Sin conexión a internet. Algunas funciones pueden no estar disponibles.",
            NotificationType.WARNING,
            Snackbar.LENGTH_LONG
        )
    }

    fun placeVisitIncremented(view: View, placeName: String) {
        show(
            view,
            "Gracias por visitar $placeName",
            NotificationType.INFO
        )
    }
}
