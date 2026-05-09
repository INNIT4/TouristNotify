package com.joseibarra.touristnotify

/**
 * Constantes globales de la aplicación
 */
object AppConstants {

    // Coordenadas del centro de Álamos, Sonora, México (Plaza de Armas)
    const val ALAMOS_LAT = 27.0275
    const val ALAMOS_LNG = -108.94

    // SharedPreferences
    const val PREFS_NAME = "TouristNotifyPrefs"
    const val PREF_ONBOARDING_COMPLETED = "onboarding_completed"
    const val PREF_DARK_MODE = "dark_mode"

    // Timeouts
    const val AI_TIMEOUT_MS = 60_000L
    const val SEARCH_DEBOUNCE_MS = 400L

    // Geofence
    const val GEOFENCE_EXPIRATION_MS = 24 * 60 * 60 * 1000L // 24 horas
    const val GEOFENCE_MAX_COUNT = 100

    // Remote Config keys — generación de rutas V2
    const val RC_USE_V2_ROUTE_GENERATOR = "use_v2_route_generator"
    const val RC_MAX_PROMPT_LENGTH = "max_prompt_length"

    // Pipeline V2 — defaults
    const val DEFAULT_MAX_PROMPT_LENGTH = 8000
    const val ROUTE_MIN_STOPS = 2
    const val ROUTE_MAX_STOPS = 8
    const val ROUTE_OPTIMIZER_BRUTE_FORCE_MAX = 6  // N <= 6: enumerar permutaciones
    const val AI_TIMEOUT_V2_MS = 90_000L           // JSON output puede tardar más
}
