package com.joseibarra.touristnotify

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.joseibarra.touristnotify.databinding.ActivityMenuBinding
import kotlinx.coroutines.launch

class MenuActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMenuBinding

    private var adminClickCount = 0
    private var lastAdminClickTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check if onboarding needs to be shown
        val prefs = getSharedPreferences("TouristNotifyPrefs", MODE_PRIVATE)
        if (!prefs.getBoolean("onboarding_completed", false)) {
            val intent = Intent(this, OnboardingActivity::class.java)
            startActivity(intent)
            finish()
            return
        }

        binding = ActivityMenuBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // DEBUG: Mostrar estado de autenticación
        showAuthenticationStatus()

        // Aplicar estilos bloqueados si es usuario invitado
        setupLockedFeaturesUI()

        loadWeather()

        // Búsqueda global (click en el texto de bienvenida)
        binding.welcomeText.setOnClickListener {
            val intent = Intent(this, GlobalSearchActivity::class.java)
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out)
        }

        binding.buttonGenerateRoute.setOnClickListener {
            val intent = Intent(this, PreferencesActivity::class.java)
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out)
        }

        binding.buttonMyRoutes.setOnClickListener {
            val intent = Intent(this, MyRoutesActivity::class.java)
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out)
        }

        binding.buttonViewMap.setOnClickListener {
            val intent = Intent(this, MapsActivity::class.java)
            startActivity(intent)
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        }

        binding.buttonContacts.setOnClickListener {
            val intent = Intent(this, ContactsActivity::class.java)
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out)
        }

        binding.buttonTopPlaces.setOnClickListener {
            val intent = Intent(this, TopPlacesActivity::class.java)
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out)
        }

        binding.buttonFavorites.setOnClickListener {
            val intent = Intent(this, FavoritesActivity::class.java)
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out)
        }

        binding.buttonStats.setOnClickListener {
            val intent = Intent(this, StatsActivity::class.java)
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out)
        }

        binding.buttonEvents.setOnClickListener {
            val intent = Intent(this, EventsActivity::class.java)
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out)
        }

        binding.buttonThemedRoutes.setOnClickListener {
            val intent = Intent(this, ThemedRoutesActivity::class.java)
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out)
        }

        binding.buttonBlog.setOnClickListener {
            val intent = Intent(this, BlogActivity::class.java)
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out)
        }

        binding.buttonProximityNotifications.setOnClickListener {
            val intent = Intent(this, ProximityNotificationsActivity::class.java)
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out)
        }

        binding.buttonOffline.setOnClickListener {
            val intent = Intent(this, OfflineSettingsActivity::class.java)
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out)
        }

        // Botón de configuración rápida (click en el icono de la app)
        binding.appIcon.setOnClickListener {
            showQuickSettingsDialog()
        }

        // Directorio de servicios (long click en weather card)
        binding.weatherCard.setOnLongClickListener {
            val intent = Intent(this, ServicesDirectoryActivity::class.java)
            startActivity(intent)
            overridePendingTransition(R.anim.slide_up, R.anim.fade_out)
            true
        }

        // Acceso secreto al panel administrativo (mantener presionado el footer)
        binding.footerText.setOnLongClickListener {
            showAdminAccessDialog()
            true
        }

        // También mediante 5 clicks rápidos en el footer
        binding.footerText.setOnClickListener {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastAdminClickTime < 500) {
                adminClickCount++
                if (adminClickCount >= 5) {
                    showAdminAccessDialog()
                    adminClickCount = 0
                }
            } else {
                adminClickCount = 1
            }
            lastAdminClickTime = currentTime
        }
    }

    /**
     * Muestra el estado de autenticación en un Toast (solo en modo debug)
     */
    private fun showAuthenticationStatus() {
        if (BuildConfig.DEBUG) {
            val isAuth = AuthManager.isAuthenticated()
            val isGuest = AuthManager.isGuestMode(this)
            val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser

            val status = buildString {
                appendLine("🔐 Estado de Autenticación:")
                appendLine("Autenticado: $isAuth")
                appendLine("Modo Invitado: $isGuest")
                appendLine("Usuario Firebase: ${currentUser?.email ?: "null"}")
            }

            android.widget.Toast.makeText(this, status, android.widget.Toast.LENGTH_LONG).show()
            android.util.Log.d("MenuActivity", status)
        }
    }

    private fun setupLockedFeaturesUI() {
        // Aplicar estilo bloqueado a elementos premium si es usuario invitado
        AuthManager.applyLockedStyleIfGuest(binding.buttonGenerateRoute, binding.lockIconGenerateRoute)
        AuthManager.applyLockedStyleIfGuest(binding.buttonMyRoutes, binding.lockIconMyRoutes)
        AuthManager.applyLockedStyleIfGuest(binding.buttonContacts, binding.lockIconContacts)
        AuthManager.applyLockedStyleIfGuest(binding.buttonFavorites, binding.lockIconFavorites)
        AuthManager.applyLockedStyleIfGuest(binding.buttonStats, binding.lockIconStats)
        AuthManager.applyLockedStyleIfGuest(binding.buttonProximityNotifications, binding.lockIconProximity)
    }

    private fun loadWeather() {
        binding.weatherProgressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            val result = WeatherManager.getCurrentWeather()

            result.onSuccess { weather ->
                binding.weatherProgressBar.visibility = View.GONE
                binding.weatherDetailsContainer.visibility = View.VISIBLE
                binding.weatherRecommendationsTextView.visibility = View.VISIBLE

                // Actualizar UI con datos del clima
                binding.weatherEmojiTextView.text = WeatherManager.getWeatherEmoji(weather.icon)
                binding.weatherTempTextView.text = "${weather.temperature.toInt()}°C"
                binding.weatherDescriptionTextView.text = weather.description

                binding.weatherHumidityTextView.text = "💧 ${weather.humidity}%"
                binding.weatherWindTextView.text = "💨 ${weather.windSpeed.toInt()} km/h"
                binding.weatherFeelsLikeTextView.text = "🌡️ ${weather.feelsLike.toInt()}°C"

                // Generar recomendaciones
                val recommendations = WeatherManager.getWeatherRecommendations(weather)
                binding.weatherRecommendationsTextView.text = recommendations
            }.onFailure { e ->
                binding.weatherProgressBar.visibility = View.GONE
                binding.weatherTempTextView.text = "--°C"
                binding.weatherDescriptionTextView.text = "No disponible"
                binding.weatherEmojiTextView.text = "🌤️"
            }
        }
    }

    private fun showQuickSettingsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_quick_settings, null)
        val darkModeSwitch = dialogView.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.dark_mode_switch)
        val authStatusText = dialogView.findViewById<android.widget.TextView>(R.id.auth_status_text)
        val logoutButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.logout_button)

        // Cargar estado actual
        darkModeSwitch.isChecked = TouristNotifyApplication.isDarkModeEnabled(this)

        // Mostrar estado de autenticación
        val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        val isGuest = AuthManager.isGuestMode(this)

        authStatusText.text = when {
            currentUser != null -> "✅ Sesión activa: ${currentUser.email}"
            isGuest -> "👤 Modo Invitado"
            else -> "❌ Sin sesión"
        }

        // Configurar botón de logout
        if (currentUser != null || isGuest) {
            logoutButton.visibility = View.VISIBLE
            logoutButton.text = if (currentUser != null) "Cerrar Sesión" else "Salir del Modo Invitado"
            logoutButton.setOnClickListener {
                performLogout()
            }
        } else {
            logoutButton.visibility = View.GONE
        }

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("⚙️ Configuración Rápida")
            .setView(dialogView)
            .setPositiveButton("Cerrar", null)
            .create()

        darkModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            TouristNotifyApplication.setDarkMode(this, isChecked)
            // El tema se aplicará automáticamente al recrear la actividad
            recreate()
        }

        dialog.show()
    }

    private fun performLogout() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("⚠️ Cerrar Sesión")
            .setMessage("¿Estás seguro que deseas cerrar sesión? Perderás acceso a tus favoritos, rutas guardadas y estadísticas.")
            .setPositiveButton("Cerrar Sesión") { _, _ ->
                // Cerrar sesión de Firebase
                com.google.firebase.auth.FirebaseAuth.getInstance().signOut()

                // Desactivar modo invitado
                AuthManager.disableGuestMode(this)

                // Volver a LoginActivity
                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showAdminAccessDialog() {
        val dialogBuilder = androidx.appcompat.app.AlertDialog.Builder(this)
        dialogBuilder.setTitle("⚙️ Acceso Administrativo")
        dialogBuilder.setMessage("¿Deseas acceder al panel administrativo para importar lugares desde Google Places?")
        dialogBuilder.setPositiveButton("Acceder") { _, _ ->
            val intent = Intent(this, AdminPlacesActivity::class.java)
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out)
        }
        dialogBuilder.setNegativeButton("Cancelar", null)
        dialogBuilder.show()
    }
}
