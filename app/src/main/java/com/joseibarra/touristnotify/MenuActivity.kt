package com.joseibarra.touristnotify

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.joseibarra.touristnotify.databinding.ActivityMenuBinding
import kotlinx.coroutines.launch

class MenuActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMenuBinding

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

        setupMenuGrid()

        loadWeather()

        // Búsqueda global (click en el texto de bienvenida)
        binding.welcomeText.setOnClickListener {
            val intent = Intent(this, GlobalSearchActivity::class.java)
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

    }

    private fun setupMenuGrid() {
        val menuItems = listOf(
            MenuItemData(
                id = MenuItemId.GENERATE_ROUTE,
                titleRes = R.string.menu_card_generate_route,
                iconEmoji = "🤖",
                colorScheme = MenuColorScheme.PRIMARY,
                isLarge = true,
                requiresAuth = AuthManager.AuthRequired.GENERATE_ROUTES,
                a11yDescRes = R.string.a11y_card_generate_route
            ),
            MenuItemData(
                id = MenuItemId.THEMED_ROUTES,
                titleRes = R.string.menu_card_themed_routes,
                iconEmoji = "🗺️",
                colorScheme = MenuColorScheme.SECONDARY,
                isLarge = true,
                requiresAuth = AuthManager.AuthRequired.THEMED_ROUTES,
                a11yDescRes = R.string.a11y_card_themed_routes
            ),
            MenuItemData(
                id = MenuItemId.MY_ROUTES,
                titleRes = R.string.menu_card_my_routes,
                iconEmoji = "📍",
                colorScheme = MenuColorScheme.TERTIARY,
                isLarge = true,
                requiresAuth = AuthManager.AuthRequired.MY_ROUTES,
                a11yDescRes = R.string.a11y_card_my_routes
            ),
            MenuItemData(
                id = MenuItemId.VIEW_MAP,
                titleRes = R.string.menu_card_view_map,
                iconEmoji = "🗾",
                colorScheme = MenuColorScheme.PRIMARY,
                isLarge = true,
                a11yDescRes = R.string.a11y_card_view_map
            ),
            MenuItemData(
                id = MenuItemId.TOP_PLACES,
                titleRes = R.string.menu_card_top_places,
                subtitleRes = R.string.menu_card_top_places_subtitle,
                iconEmoji = "🏆",
                colorScheme = MenuColorScheme.PRIMARY,
                spanFull = true,
                showArrow = true,
                a11yDescRes = R.string.a11y_card_top_places
            ),
            MenuItemData(
                id = MenuItemId.CONTACTS,
                titleRes = R.string.menu_card_contacts,
                iconEmoji = "🚨",
                colorScheme = MenuColorScheme.TERTIARY,
                showArrow = true,
                a11yDescRes = R.string.a11y_card_contacts
            ),
            MenuItemData(
                id = MenuItemId.FAVORITES,
                titleRes = R.string.menu_card_favorites,
                iconEmoji = "❤️",
                colorScheme = MenuColorScheme.SECONDARY,
                showArrow = true,
                requiresAuth = AuthManager.AuthRequired.MY_FAVORITES,
                a11yDescRes = R.string.a11y_card_favorites
            ),
            MenuItemData(
                id = MenuItemId.EVENTS,
                titleRes = R.string.menu_card_events,
                iconEmoji = "🎭",
                colorScheme = MenuColorScheme.SECONDARY,
                showArrow = true,
                a11yDescRes = R.string.a11y_card_events
            ),
            MenuItemData(
                id = MenuItemId.BLOG,
                titleRes = R.string.menu_card_blog,
                iconEmoji = "✍️",
                colorScheme = MenuColorScheme.TERTIARY,
                showArrow = true,
                a11yDescRes = R.string.a11y_card_blog
            ),
            MenuItemData(
                id = MenuItemId.PROXIMITY,
                titleRes = R.string.menu_card_proximity,
                iconEmoji = "📡",
                colorScheme = MenuColorScheme.PRIMARY,
                showArrow = true,
                requiresAuth = AuthManager.AuthRequired.PROXIMITY_NOTIFICATIONS,
                a11yDescRes = R.string.a11y_card_proximity
            )
        ).toMutableList()

        val adapter = MenuAdapter(menuItems, AuthManager.isAuthenticated()) { id ->
            handleMenuClick(id)
        }

        val gridLayoutManager = GridLayoutManager(this, 2)
        gridLayoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int) = adapter.getSpanSize(position)
        }

        binding.menuRecycler.layoutManager = gridLayoutManager
        binding.menuRecycler.adapter = adapter
    }

    private fun handleMenuClick(id: MenuItemId) {
        when (id) {
            MenuItemId.GENERATE_ROUTE -> AuthManager.requireAuth(this, AuthManager.AuthRequired.GENERATE_ROUTES) {
                startActivity(com.joseibarra.touristnotify.wizard.RouteWizardActivity.newIntent(this))
                overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out)
            }
            MenuItemId.THEMED_ROUTES -> AuthManager.requireAuth(this, AuthManager.AuthRequired.THEMED_ROUTES) {
                startActivity(Intent(this, ThemedRoutesActivity::class.java))
                overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out)
            }
            MenuItemId.MY_ROUTES -> AuthManager.requireAuth(this, AuthManager.AuthRequired.MY_ROUTES) {
                startActivity(Intent(this, MyRoutesActivity::class.java))
                overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out)
            }
            MenuItemId.VIEW_MAP -> {
                startActivity(Intent(this, MapsActivity::class.java))
                overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
            }
            MenuItemId.CONTACTS -> {
                startActivity(Intent(this, ContactsActivity::class.java))
                overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out)
            }
            MenuItemId.TOP_PLACES -> {
                startActivity(Intent(this, TopPlacesActivity::class.java))
                overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out)
            }
            MenuItemId.FAVORITES -> AuthManager.requireAuth(this, AuthManager.AuthRequired.MY_FAVORITES) {
                startActivity(Intent(this, FavoritesActivity::class.java))
                overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out)
            }
            MenuItemId.EVENTS -> {
                startActivity(Intent(this, EventsActivity::class.java))
                overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out)
            }
            MenuItemId.BLOG -> {
                startActivity(Intent(this, BlogActivity::class.java))
                overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out)
            }
            MenuItemId.PROXIMITY -> AuthManager.requireAuth(this, AuthManager.AuthRequired.PROXIMITY_NOTIFICATIONS) {
                startActivity(Intent(this, ProximityNotificationsActivity::class.java))
                overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out)
            }
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

                binding.weatherEmojiTextView.text = WeatherManager.getWeatherEmoji(weather.icon)
                binding.weatherTempTextView.text = "${weather.temperature.toInt()}°C"
                binding.weatherDescriptionTextView.text = if (weather.isMock) {
                    "${weather.description} (estimado)"
                } else {
                    weather.description
                }

                binding.weatherHumidityTextView.text = "💧 ${weather.humidity}%"
                binding.weatherWindTextView.text = "💨 ${weather.windSpeed.toInt()} km/h"
                binding.weatherFeelsLikeTextView.text = "🌡️ ${weather.feelsLike.toInt()}°C"

                val recommendations = WeatherManager.getWeatherRecommendations(weather)
                binding.weatherRecommendationsTextView.text = recommendations
            }.onFailure { e ->
                binding.weatherProgressBar.visibility = View.GONE
                binding.weatherTempTextView.text = "--°C"
                binding.weatherDescriptionTextView.text = getString(R.string.weather_unavailable)
                binding.weatherEmojiTextView.text = "🌤️"
            }
        }
    }

    private fun showQuickSettingsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_quick_settings, null)
        val darkModeSwitch = dialogView.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.dark_mode_switch)
        val authStatusText = dialogView.findViewById<android.widget.TextView>(R.id.auth_status_text)
        val profileButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.profile_button)
        val logoutButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.logout_button)

        darkModeSwitch.isChecked = TouristNotifyApplication.isDarkModeEnabled(this)

        val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        val isGuest = AuthManager.isGuestMode(this)

        authStatusText.text = when {
            currentUser != null -> getString(R.string.session_active, currentUser.email ?: "")
            isGuest -> getString(R.string.guest_mode)
            else -> getString(R.string.no_session)
        }

        if (currentUser != null) {
            profileButton.visibility = View.VISIBLE
            profileButton.setOnClickListener {
                val intent = Intent(this, ProfileActivity::class.java)
                startActivity(intent)
                overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out)
            }
        } else {
            profileButton.visibility = View.GONE
        }

        if (currentUser != null || isGuest) {
            logoutButton.visibility = View.VISIBLE
            logoutButton.text = if (currentUser != null) getString(R.string.close_session) else getString(R.string.exit_guest_mode)
            logoutButton.setOnClickListener {
                performLogout()
            }
        } else {
            logoutButton.visibility = View.GONE
        }

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.quick_settings_title))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.close), null)
            .create()

        darkModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            TouristNotifyApplication.setDarkMode(this, isChecked)
            recreate()
        }

        dialog.show()
    }

    private fun performLogout() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.logout_title))
            .setMessage(getString(R.string.logout_message))
            .setPositiveButton(getString(R.string.close_session)) { _, _ ->
                com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
                AuthManager.disableGuestMode(this)
                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

}
