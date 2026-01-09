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
        binding = ActivityMenuBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadWeather()

        binding.buttonGenerateRoute.setOnClickListener {
            val intent = Intent(this, PreferencesActivity::class.java)
            startActivity(intent)
        }

        binding.buttonMyRoutes.setOnClickListener {
            val intent = Intent(this, MyRoutesActivity::class.java)
            startActivity(intent)
        }

        binding.buttonViewMap.setOnClickListener {
            val intent = Intent(this, MapsActivity::class.java)
            startActivity(intent)
        }

        binding.buttonContacts.setOnClickListener {
            val intent = Intent(this, ContactsActivity::class.java)
            startActivity(intent)
        }

        binding.buttonTopPlaces.setOnClickListener {
            val intent = Intent(this, TopPlacesActivity::class.java)
            startActivity(intent)
        }

        binding.buttonFavorites.setOnClickListener {
            val intent = Intent(this, FavoritesActivity::class.java)
            startActivity(intent)
        }

        binding.buttonStats.setOnClickListener {
            val intent = Intent(this, StatsActivity::class.java)
            startActivity(intent)
        }

        binding.buttonEvents.setOnClickListener {
            val intent = Intent(this, EventsActivity::class.java)
            startActivity(intent)
        }

        binding.buttonThemedRoutes.setOnClickListener {
            val intent = Intent(this, ThemedRoutesActivity::class.java)
            startActivity(intent)
        }

        binding.buttonComparator.setOnClickListener {
            val intent = Intent(this, ComparatorActivity::class.java)
            startActivity(intent)
        }

        binding.buttonBusinessTraveler.setOnClickListener {
            val intent = Intent(this, BusinessTravelerActivity::class.java)
            startActivity(intent)
        }

        binding.buttonBlog.setOnClickListener {
            val intent = Intent(this, BlogActivity::class.java)
            startActivity(intent)
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

    private fun showAdminAccessDialog() {
        val dialogBuilder = androidx.appcompat.app.AlertDialog.Builder(this)
        dialogBuilder.setTitle("⚙️ Acceso Administrativo")
        dialogBuilder.setMessage("¿Deseas acceder al panel administrativo para importar lugares desde Google Places?")
        dialogBuilder.setPositiveButton("Acceder") { _, _ ->
            val intent = Intent(this, AdminPlacesActivity::class.java)
            startActivity(intent)
        }
        dialogBuilder.setNegativeButton("Cancelar", null)
        dialogBuilder.show()
    }
}
