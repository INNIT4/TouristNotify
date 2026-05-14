package com.joseibarra.trazago.ui

import android.content.Context
import android.util.TypedValue
import android.view.View
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt

/**
 * Extensiones UI ligeras y de uso frecuente.
 */

/** Convierte dp a px usando la densidad del display. */
fun Context.dpToPx(dp: Float): Int =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics).toInt()

/** Resuelve un atributo de color del tema actual (ej. ?attr/colorPrimary). */
@ColorInt
fun Context.resolveThemeColor(@AttrRes attr: Int): Int {
    val typedValue = TypedValue()
    theme.resolveAttribute(attr, typedValue, true)
    return typedValue.data
}

/** Click anti-doble-tap: ignora taps consecutivos a < 500ms. */
private const val DEBOUNCE_MS = 500L

fun View.setSafeClickListener(action: (View) -> Unit) {
    var lastClick = 0L
    setOnClickListener {
        val now = System.currentTimeMillis()
        if (now - lastClick >= DEBOUNCE_MS) {
            lastClick = now
            action(it)
        }
    }
}
