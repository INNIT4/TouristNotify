package com.joseibarra.trazago.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Wrapper de feedback háptico tolerante a OEMs y compatible con API 24+.
 * Cada método se traga excepciones: la ausencia de háptica nunca debe romper la UX.
 */
object HapticHelper {

    /** Tap ligero: cards, botones, chips. */
    fun tick(view: View) {
        runCatching { view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) }
    }

    /** Long press confirmado: añadir a favoritos, drag start. */
    fun longPress(view: View) {
        runCatching { view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) }
    }

    /** Confirmación exitosa (check-in OK, ruta generada). API 30+ usa CONFIRM. */
    fun confirm(view: View) {
        val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.VIRTUAL_KEY
        }
        runCatching { view.performHapticFeedback(constant) }
    }

    /** Acción rechazada (modo invitado, validación fallida). API 30+ usa REJECT. */
    fun reject(view: View) {
        val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.REJECT
        } else {
            HapticFeedbackConstants.LONG_PRESS
        }
        runCatching { view.performHapticFeedback(constant) }
    }
}
