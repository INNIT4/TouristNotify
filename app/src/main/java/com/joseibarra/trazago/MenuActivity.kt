package com.joseibarra.trazago

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.joseibarra.trazago.databinding.ActivityMenuBinding
import com.joseibarra.trazago.ui.AnimationHelper
import com.joseibarra.trazago.ui.BaseActivity
import com.joseibarra.trazago.ui.MotionHelper
import kotlinx.coroutines.launch

class MenuActivity : BaseActivity() {

    private lateinit var binding: ActivityMenuBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check if onboarding needs to be shown
        val prefs = getSharedPreferences("TrazaGoPrefs", MODE_PRIVATE)
        if (!prefs.getBoolean("onboarding_completed", false)) {
            val intent = Intent(this, OnboardingActivity::class.java)
            startActivity(intent)
            finish()
            return
        }

        binding = ActivityMenuBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsets(binding.headerRow, applyTop = true, applyBottom = false)

        setupMenuGrid()

        loadWeather()

        // Búsqueda global: tanto el texto de bienvenida como la propia search bar abren GlobalSearch
        val openGlobalSearch = View.OnClickListener {
            val intent = Intent(this, GlobalSearchActivity::class.java)
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out)
        }
        binding.welcomeText.setOnClickListener(openGlobalSearch)
        binding.searchBarContainer.setOnClickListener(openGlobalSearch)

        // Animación de entrada del weather card (slide-fade desde -20dp)
        AnimationHelper.fadeIn(binding.weatherCard)

        // Bottom Navigation
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> true
                R.id.nav_map -> {
                    startActivity(Intent(this, MapsActivity::class.java))
                    overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out)
                    true
                }
                R.id.nav_favorites -> {
                    startActivity(Intent(this, FavoritesActivity::class.java))
                    overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out)
                    true
                }
                R.id.nav_profile -> {
                    startActivity(Intent(this, ProfileActivity::class.java))
                    overridePendingTransition(0, 0)
                    true
                }
                else -> false
            }
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
                id = MenuItemId.CONTACTS,
                titleRes = R.string.menu_card_contacts,
                iconEmoji = "🆘",
                colorScheme = MenuColorScheme.PRIMARY,
                isLarge = true,
                a11yDescRes = R.string.a11y_card_contacts
            ),
            MenuItemData(
                id = MenuItemId.SERVICES,
                titleRes = R.string.menu_card_services,
                iconEmoji = "🛎️",
                colorScheme = MenuColorScheme.SECONDARY,
                showArrow = true,
                a11yDescRes = R.string.a11y_card_services
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
        binding.menuRecycler.itemAnimator = MotionHelper.fadeSlideItemAnimator()
    }

    private fun handleMenuClick(id: MenuItemId) {
        when (id) {
            MenuItemId.GENERATE_ROUTE -> AuthManager.requireAuth(this, AuthManager.AuthRequired.GENERATE_ROUTES) {
                startActivity(com.joseibarra.trazago.wizard.RouteWizardActivity.newIntent(this))
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
            MenuItemId.CONTACTS -> {
                startActivity(Intent(this, ContactsActivity::class.java))
                overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out)
            }
            MenuItemId.SERVICES -> {
                startActivity(Intent(this, ServicesActivity::class.java))
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
            else -> { /* VIEW_MAP y TOP_PLACES eliminados del menú */ }
        }
    }

    private fun loadWeather() {
        binding.weatherProgressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            val result = WeatherManager.getCurrentWeather()

            result.onSuccess { weather ->
                binding.weatherProgressBar.visibility = View.GONE

                binding.weatherEmojiTextView.text = WeatherManager.getWeatherEmoji(weather.icon)
                binding.weatherTempTextView.text = "${weather.temperature.toInt()}°C"
                binding.weatherDescriptionTextView.text = if (weather.isMock) {
                    "${weather.description} (estimado)"
                } else {
                    weather.description
                }
            }.onFailure { e ->
                binding.weatherProgressBar.visibility = View.GONE
                binding.weatherTempTextView.text = "--°C"
                binding.weatherDescriptionTextView.text = getString(R.string.weather_unavailable)
                binding.weatherEmojiTextView.text = "🌤️"
            }
        }
    }

}
