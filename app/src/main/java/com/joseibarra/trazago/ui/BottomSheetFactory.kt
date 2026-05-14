package com.joseibarra.trazago.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import androidx.annotation.LayoutRes
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog

/**
 * Crea BottomSheetDialogs con configuración consistente:
 * - Drag handle implícito (vía estilo)
 * - Peek height 40% pantalla
 * - Skip collapsed = false (permite peek)
 *
 * Uso:
 * ```
 * val sheet = BottomSheetFactory.create(this, R.layout.bottom_sheet_share_route)
 * sheet.show()
 * ```
 */
object BottomSheetFactory {

    fun create(context: Context, @LayoutRes layout: Int, configure: (View, BottomSheetDialog) -> Unit = { _, _ -> }): BottomSheetDialog {
        val dialog = BottomSheetDialog(context)
        val view = LayoutInflater.from(context).inflate(layout, null, false)
        dialog.setContentView(view)
        dialog.behavior.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = false
            isDraggable = true
        }
        configure(view, dialog)
        return dialog
    }
}
