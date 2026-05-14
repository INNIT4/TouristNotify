package com.joseibarra.trazago.ui

import android.view.View
import android.view.animation.AnimationUtils
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.RecyclerView
import com.joseibarra.trazago.R

/**
 * Utilidades de movimiento para listas y vistas con entrada escalonada.
 */
object MotionHelper {

    private const val STAGGER_DELAY_MS = 40L
    private const val MAX_STAGGERED_ITEMS = 6

    /**
     * Aplica entrada escalonada (fade + translateY) a items de un RecyclerView
     * en su primer renderizado. Llamar desde Adapter.onBindViewHolder con la posición.
     */
    fun applyStaggeredEnter(itemView: View, position: Int) {
        if (position >= MAX_STAGGERED_ITEMS) return
        itemView.alpha = 0f
        itemView.translationY = 24f
        itemView.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(STAGGER_DELAY_MS * position)
            .setDuration(220L)
            .start()
    }

    /**
     * ItemAnimator con fade-slide para añadir/quitar items en RecyclerView.
     * Asignar con `recyclerView.itemAnimator = MotionHelper.fadeSlideItemAnimator()`.
     */
    fun fadeSlideItemAnimator(): RecyclerView.ItemAnimator = DefaultItemAnimator().apply {
        addDuration = 240L
        removeDuration = 200L
        moveDuration = 200L
        changeDuration = 200L
    }

    /** Aplica una animación de entrada simple desde recurso. */
    fun playEnter(view: View, animResId: Int = R.anim.slide_in_bottom) {
        view.startAnimation(AnimationUtils.loadAnimation(view.context, animResId))
    }
}
