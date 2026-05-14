package com.joseibarra.trazago.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.view.View
import android.view.animation.AnimationUtils
import androidx.core.view.isVisible
import com.joseibarra.trazago.R

/**
 * Animaciones reutilizables sobre Views: fade in/out, slide-from-bottom, scale pop.
 * Todas son idempotentes y seguras para llamar varias veces.
 */
object AnimationHelper {

    private const val FADE_DURATION_MS = 220L
    private const val ENTER_TRANSLATION_Y = 24f

    /** Fade-in con leve translación vertical. Útil al cargar contenido async. */
    fun fadeIn(view: View, durationMs: Long = FADE_DURATION_MS) {
        view.alpha = 0f
        view.translationY = ENTER_TRANSLATION_Y
        view.isVisible = true
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(durationMs)
            .setInterpolator(android.view.animation.DecelerateInterpolator(1.6f))
            .start()
    }

    /** Fade-out que oculta la view al terminar (visibility GONE). */
    fun fadeOut(view: View, durationMs: Long = FADE_DURATION_MS, onEnd: (() -> Unit)? = null) {
        if (!view.isVisible) {
            onEnd?.invoke()
            return
        }
        view.animate()
            .alpha(0f)
            .setDuration(durationMs)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    view.isVisible = false
                    view.alpha = 1f
                    view.setListener(null)
                    onEnd?.invoke()
                }
            })
            .start()
    }

    /** Slide desde abajo (300ms) — para FABs, snackbars custom. */
    fun slideInBottom(view: View) {
        view.startAnimation(AnimationUtils.loadAnimation(view.context, R.anim.slide_in_bottom))
        view.isVisible = true
    }

    /** Pop animado para confirmaciones (check-in exitoso, badge ganado). */
    fun popIn(view: View) {
        view.startAnimation(AnimationUtils.loadAnimation(view.context, R.anim.scale_up_center))
        view.isVisible = true
    }

    /** Quita el listener al terminar para evitar fugas. */
    private fun View.setListener(listener: Animator.AnimatorListener?) {
        this.animate().setListener(listener)
    }
}
