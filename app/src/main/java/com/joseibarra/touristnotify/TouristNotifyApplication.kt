package com.joseibarra.touristnotify

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TouristNotifyApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()

        // Aplicar tema guardado
        applyTheme()

        // Habilitar persistencia offline de Firestore
        val settings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true)
            .build()

        FirebaseFirestore.getInstance().firestoreSettings = settings

        // Inicializar ConfigManager (Remote Config)
        applicationScope.launch {
            ConfigManager.initialize(this@TouristNotifyApplication)
        }
    }

    private fun applyTheme() {
        val prefs = getSharedPreferences("TouristNotifyPrefs", MODE_PRIVATE)
        val darkMode = prefs.getBoolean("dark_mode", false)

        AppCompatDelegate.setDefaultNightMode(
            if (darkMode) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )
    }

    companion object {
        fun setDarkMode(context: android.content.Context, enabled: Boolean) {
            val prefs = context.getSharedPreferences("TouristNotifyPrefs", MODE_PRIVATE)
            prefs.edit().putBoolean("dark_mode", enabled).apply()

            AppCompatDelegate.setDefaultNightMode(
                if (enabled) AppCompatDelegate.MODE_NIGHT_YES
                else AppCompatDelegate.MODE_NIGHT_NO
            )
        }

        fun isDarkModeEnabled(context: android.content.Context): Boolean {
            val prefs = context.getSharedPreferences("TouristNotifyPrefs", MODE_PRIVATE)
            return prefs.getBoolean("dark_mode", false)
        }
    }
}
