package com.joseibarra.touristnotify

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import org.json.JSONObject

/**
 * Manager para obtener información del clima de Álamos, Sonora
 *
 * Para usar con OpenWeatherMap API:
 * 1. Obtener API key gratuita en: https://openweathermap.org/api
 * 2. Agregar WEATHER_API_KEY en local.properties
 * 3. Agregar buildConfigField en build.gradle.kts
 * 4. Descomentar la implementación real en getCurrentWeather()
 */
object WeatherManager {

    private const val TAG = "WeatherManager"
    private const val ALAMOS_LAT = 27.0275
    private const val ALAMOS_LON = -108.94

    /**
     * Obtiene el clima actual de Álamos, Sonora
     *
     * NOTA: Esta versión usa datos simulados. Para usar datos reales,
     * descomentar la implementación con OpenWeatherMap API y agregar la API key.
     */
    suspend fun getCurrentWeather(): Result<WeatherInfo> = withContext(Dispatchers.IO) {
        try {
            // === IMPLEMENTACIÓN MOCK (TEMPORAL) ===
            // Simula clima soleado y cálido típico de Álamos
            val mockWeather = WeatherInfo(
                temperature = 28.5,
                feelsLike = 30.0,
                description = "Despejado",
                icon = "01d", // Código de ícono de sol
                humidity = 45,
                windSpeed = 12.5,
                timestamp = System.currentTimeMillis()
            )
            Result.success(mockWeather)

            /* === IMPLEMENTACIÓN REAL CON OPENWEATHERMAP API ===
            val apiKey = BuildConfig.WEATHER_API_KEY
            if (apiKey.isBlank()) {
                return@withContext Result.failure(
                    Exception("API Key no configurada. Ver documentación en WeatherManager.kt")
                )
            }

            val url = "https://api.openweathermap.org/data/2.5/weather?" +
                    "lat=$ALAMOS_LAT&lon=$ALAMOS_LON&appid=$apiKey&units=metric&lang=es"

            val response = URL(url).readText()
            val json = JSONObject(response)

            val main = json.getJSONObject("main")
            val weather = json.getJSONArray("weather").getJSONObject(0)
            val wind = json.getJSONObject("wind")

            val weatherInfo = WeatherInfo(
                temperature = main.getDouble("temp"),
                feelsLike = main.getDouble("feels_like"),
                description = weather.getString("description").replaceFirstChar { it.uppercase() },
                icon = weather.getString("icon"),
                humidity = main.getInt("humidity"),
                windSpeed = wind.getDouble("speed"),
                timestamp = System.currentTimeMillis()
            )

            Result.success(weatherInfo)
            */

        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo clima", e)
            Result.failure(e)
        }
    }

    /**
     * Genera recomendaciones basadas en el clima usando IA
     */
    suspend fun getWeatherRecommendations(weather: WeatherInfo): String {
        return when {
            weather.temperature > 30 -> {
                "☀️ Día caluroso (${weather.temperature.toInt()}°C)\n\n" +
                "• Lleva sombrero y protector solar\n" +
                "• Mantente hidratado\n" +
                "• Visita lugares con sombra o aire acondicionado\n" +
                "• Recomendado: Museos, restaurantes climatizados"
            }
            weather.temperature > 25 -> {
                "🌤️ Clima agradable (${weather.temperature.toInt()}°C)\n\n" +
                "• Perfecto para explorar el centro histórico\n" +
                "• Ideal para caminar por las calles empedradas\n" +
                "• Visita plazas y jardines\n" +
                "• Recomendado: Tour a pie, fotografía"
            }
            weather.temperature > 18 -> {
                "☁️ Clima fresco (${weather.temperature.toInt()}°C)\n\n" +
                "• Lleva una chamarra ligera\n" +
                "• Excelente para actividades al aire libre\n" +
                "• Disfruta de cafés y terrazas\n" +
                "• Recomendado: Parques, miradores"
            }
            else -> {
                "🌙 Clima frío (${weather.temperature.toInt()}°C)\n\n" +
                "• Abrígate bien\n" +
                "• Perfecto para disfrutar bebidas calientes\n" +
                "• Visita lugares cerrados\n" +
                "• Recomendado: Restaurantes, museos"
            }
        }.let { base ->
            if (weather.humidity > 70) {
                "$base\n\n💧 Humedad alta (${weather.humidity}%) - Mantente fresco"
            } else base
        }
    }

    /**
     * Obtiene el emoji del clima según el código de ícono
     */
    fun getWeatherEmoji(iconCode: String): String {
        return when {
            iconCode.startsWith("01") -> "☀️" // Despejado
            iconCode.startsWith("02") -> "⛅" // Pocas nubes
            iconCode.startsWith("03") || iconCode.startsWith("04") -> "☁️" // Nublado
            iconCode.startsWith("09") || iconCode.startsWith("10") -> "🌧️" // Lluvia
            iconCode.startsWith("11") -> "⛈️" // Tormenta
            iconCode.startsWith("13") -> "🌨️" // Nieve
            iconCode.startsWith("50") -> "🌫️" // Niebla
            else -> "🌤️"
        }
    }
}
