package com.joseibarra.trazago.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityOptionsCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.joseibarra.trazago.LocaleHelper

/**
 * Base común para todas las Activities de UI principal.
 *
 * Provee:
 *  - Edge-to-edge correctamente configurado (Android 15-ready)
 *  - Helper para aplicar window insets sin hardcodear paddings
 *  - Transiciones de actividad estandarizadas
 *  - Shared element helpers seguros (con fallback si la imagen falla en Glide)
 *  - Atajos de feedback háptico
 *
 * Heredar SOLO desde Activities que renderizan UI propia. Activities con tema
 * Theme.Black.NoTitleBar (FullScreenPhotoActivity) no deben heredar.
 */
abstract class BaseActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Edge-to-edge antes de setContentView para que el sistema lo respete.
        // windowActivityTransitions=true ya se declara en el theme base.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
    }

    /**
     * Aplica los insets de las system bars como padding del root.
     * Llamar una vez después de setContentView/inflate del binding.
     *
     * @param applyTop si true, aplica padding del status bar
     * @param applyBottom si true, aplica padding de la navigation bar
     */
    protected fun applyWindowInsets(
        root: View,
        applyTop: Boolean = true,
        applyBottom: Boolean = true,
        applyHorizontal: Boolean = false
    ) {
        val baseLeft = root.paddingLeft
        val baseTop = root.paddingTop
        val baseRight = root.paddingRight
        val baseBottom = root.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(
                if (applyHorizontal) baseLeft + bars.left else baseLeft,
                if (applyTop) baseTop + bars.top else baseTop,
                if (applyHorizontal) baseRight + bars.right else baseRight,
                if (applyBottom) baseBottom + bars.bottom else baseBottom
            )
            insets
        }
    }

    /**
     * Crea un Bundle de opciones para shared element transition.
     * Devuelve null si la API o el dispositivo no soportan transiciones.
     */
    protected fun makeSharedElementOptions(view: View, transitionName: String): Bundle? {
        ViewCompat.setTransitionName(view, transitionName)
        return ActivityOptionsCompat
            .makeSceneTransitionAnimation(this, view, transitionName)
            .toBundle()
    }

    /** Atajos hápticos delegados al [HapticHelper]. */
    protected fun View.hapticTick() = HapticHelper.tick(this)
    protected fun View.hapticConfirm() = HapticHelper.confirm(this)
    protected fun View.hapticReject() = HapticHelper.reject(this)
    protected fun View.hapticLongPress() = HapticHelper.longPress(this)

    /**
     * Navega con shared element. Si view es null, navega normal.
     */
    protected fun navigateWithSharedElement(intent: Intent, view: View?, transitionName: String) {
        if (view != null) {
            val opts = makeSharedElementOptions(view, transitionName)
            startActivity(intent, opts)
        } else {
            startActivity(intent)
        }
    }
}
