package com.joseibarra.touristnotify

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

/**
 * Manager para obtener información del clima de Álamos, Sonora
 *
 * Usa OpenWeatherMap API (gratuita)
 * Para configurar:
 * 1. Obtener API key en: https://openweathermap.org/api
 * 2. Agregar en local.properties: WEATHER_API_KEY=tu_api_key_aqui
 */
object WeatherManager {

    private const val TAG = "WeatherManager"
    private const val ALAMOS_LAT = 27.0275
    private const val ALAMOS_LON = -108.94

    /**
     * Obtiene el clima actual de Álamos, Sonora desde OpenWeatherMap
     */
    suspend fun getCurrentWeather(): Result<WeatherInfo> = withContext(Dispatchers.IO) {
        try {
            val apiKey = BuildConfig.WEATHER_API_KEY

            // Si no hay API key, usar datos mock
            if (apiKey.isBlank()) {
                Log.w(TAG, "WEATHER_API_KEY no configurada, usando datos simulados")
                return@withContext Result.success(getMockWeather())
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
                windSpeed = wind.getDouble("speed") * 3.6, // m/s a km/h
                timestamp = System.currentTimeMillis()
            )

            Result.success(weatherInfo)

        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo clima, usando datos mock", e)
            // Fallback a datos mock si falla la API
            Result.success(getMockWeather())
        }
    }

    /**
     * Obtiene el pronóstico de los próximos 5 días
     */
    suspend fun getForecast(): Result<List<ForecastDay>> = withContext(Dispatchers.IO) {
        try {
            val apiKey = BuildConfig.WEATHER_API_KEY

            if (apiKey.isBlank()) {
                Log.w(TAG, "WEATHER_API_KEY no configurada, usando pronóstico simulado")
                return@withContext Result.success(getMockForecast())
            }

            val url = "https://api.openweathermap.org/data/2.5/forecast?" +
                    "lat=$ALAMOS_LAT&lon=$ALAMOS_LON&appid=$apiKey&units=metric&lang=es"

            val response = URL(url).readText()
            val json = JSONObject(response)
            val list = json.getJSONArray("list")

            // Agrupar por día (toma el pronóstico de mediodía de cada día)
            val forecastMap = mutableMapOf<String, ForecastDay>()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

            for (i in 0 until list.length()) {
                val item = list.getJSONObject(i)
                val dt = item.getLong("dt") * 1000
                val date = Date(dt)
                val dayKey = dateFormat.format(date)

                // Solo tomar el pronóstico del mediodía (12:00)
                val dtTxt = item.getString("dt_txt")
                if (dtTxt.contains("12:00:00") && forecastMap.size < 5) {
                    val main = item.getJSONObject("main")
                    val weather = item.getJSONArray("weather").getJSONObject(0)

                    forecastMap[dayKey] = ForecastDay(
                        date = date,
                        tempMax = main.getDouble("temp_max"),
                        tempMin = main.getDouble("temp_min"),
                        description = weather.getString("description").replaceFirstChar { it.uppercase() },
                        icon = weather.getString("icon")
                    )
                }
            }

            val forecast = forecastMap.values.toList()
            Result.success(forecast)

        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo pronóstico, usando datos mock", e)
            Result.success(getMockForecast())
        }
    }

    /**
     * Datos mock/simulados para cuando no hay API key o falla la conexión
     */
    private fun getMockWeather(): WeatherInfo {
        return WeatherInfo(
            temperature = 28.5,
            feelsLike = 30.0,
            description = "Despejado",
            icon = "01d",
            humidity = 45,
            windSpeed = 12.5,
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * Pronóstico mock de 5 días
     */
    private fun getMockForecast(): List<ForecastDay> {
        val calendar = Calendar.getInstance()
        return (1..5).map { day ->
            calendar.add(Calendar.DAY_OF_MONTH, 1)
            ForecastDay(
                date = calendar.time,
                tempMax = 30.0 + (Math.random() * 5 - 2.5),
                tempMin = 18.0 + (Math.random() * 3 - 1.5),
                description = listOf("Despejado", "Parcialmente nublado", "Soleado").random(),
                icon = listOf("01d", "02d", "03d").random()
            )
        }
    }

    /**
     * Genera recomendaciones basadas en el clima
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
