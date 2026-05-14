package com.joseibarra.trazago.ui

import android.view.View
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import com.google.android.material.button.MaterialButton
import com.joseibarra.trazago.R

/**
 * Configura un empty state inflado desde include_empty_state.xml.
 *
 * Uso típico:
 * ```
 * EmptyStateHelper.show(
 *     root = binding.emptyStateRoot,
 *     icon = R.drawable.ic_empty_state_favorites,
 *     title = R.string.empty_favorites_title,
 *     subtitle = R.string.empty_favorites_subtitle,
 *     actionLabel = R.string.empty_favorites_action,
 *     onAction = { startActivity(Intent(this, MapsActivity::class.java)) }
 * )
 * ```
 */
object EmptyStateHelper {

    fun show(
        root: View,
        @DrawableRes icon: Int,
        @StringRes title: Int,
        @StringRes subtitle: Int,
        @StringRes actionLabel: Int? = null,
        onAction: (() -> Unit)? = null
    ) {
        root.isVisible = true
        root.findViewById<android.widget.ImageView>(R.id.empty_state_image)
            ?.setImageResource(icon)
        root.findViewById<android.widget.TextView>(R.id.empty_state_title)
            ?.setText(title)
        root.findViewById<android.widget.TextView>(R.id.empty_state_subtitle)
            ?.setText(subtitle)

        val button = root.findViewById<MaterialButton>(R.id.empty_state_action)
        if (actionLabel != null && onAction != null) {
            button?.apply {
                setText(actionLabel)
                isVisible = true
                setOnClickListener { onAction() }
            }
        } else {
            button?.isVisible = false
        }
    }

    fun hide(root: View) {
        root.isVisible = false
    }
}
