package com.joseibarra.trazago

import android.util.Log
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

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

    // PERF-005: OkHttp con timeouts — reemplaza URL.readText() sin timeout
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private fun fetchUrl(url: String): String {
        val request = Request.Builder().url(url).build()
        return httpClient.newCall(request).execute().use { response ->
            response.body?.string() ?: throw IOException("Respuesta vacía del servidor")
        }
    }

    /**
     * Obtiene el clima actual de Álamos, Sonora desde OpenWeatherMap.
     * Si no hay API key, intenta la Cloud Function `getWeather`; si también falla, usa mock.
     */
    suspend fun getCurrentWeather(): Result<WeatherInfo> = withContext(Dispatchers.IO) {
        try {
            val apiKey = ConfigManager.getWeatherApiKey()

            if (apiKey.isBlank()) {
                val cfResult = getWeatherFromCF("current")
                if (cfResult != null) return@withContext Result.success(cfResult)
                Log.w(TAG, "WEATHER_API_KEY no configurada y CF no disponible, usando datos simulados")
                return@withContext Result.success(getMockWeather())
            }

            val url = "https://api.openweathermap.org/data/2.5/weather?" +
                    "lat=${AppConstants.ALAMOS_LAT}&lon=${AppConstants.ALAMOS_LNG}&appid=$apiKey&units=metric&lang=es"

            val response = fetchUrl(url)
            Result.success(parseCurrentWeatherJson(JSONObject(response)))

        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo clima, usando datos mock", e)
            Result.success(getMockWeather())
        }
    }

    private fun parseCurrentWeatherJson(json: JSONObject): WeatherInfo {
        val main = json.getJSONObject("main")
        val weather = json.getJSONArray("weather").getJSONObject(0)
        val wind = json.getJSONObject("wind")
        return WeatherInfo(
            temperature = main.getDouble("temp"),
            feelsLike = main.getDouble("feels_like"),
            description = weather.getString("description").replaceFirstChar { it.uppercase() },
            icon = weather.getString("icon"),
            humidity = main.getInt("humidity"),
            windSpeed = wind.getDouble("speed") * 3.6,
            timestamp = System.currentTimeMillis()
        )
    }

    private suspend fun getWeatherFromCF(type: String): WeatherInfo? = try {
        val data = mapOf(
            "lat" to AppConstants.ALAMOS_LAT,
            "lon" to AppConstants.ALAMOS_LNG,
            "type" to type
        )
        val result = FirebaseFunctions.getInstance()
            .getHttpsCallable("getWeather")
            .call(data)
            .await()
        @Suppress("UNCHECKED_CAST")
        val jsonStr = (result.data as Map<String, Any>)["json"] as? String ?: return null
        parseCurrentWeatherJson(JSONObject(jsonStr))
    } catch (e: Exception) {
        if (BuildConfig.DEBUG) Log.w(TAG, "CF getWeather falló: ${e.message}")
        null
    }

    /**
     * Obtiene el pronóstico de los próximos 5 días.
     * Si no hay API key, intenta la Cloud Function `getWeather`; si también falla, usa mock.
     */
    suspend fun getForecast(): Result<List<ForecastDay>> = withContext(Dispatchers.IO) {
        try {
            val apiKey = ConfigManager.getWeatherApiKey()

            if (apiKey.isBlank()) {
                val cfResult = getForecastFromCF()
                if (cfResult != null) return@withContext Result.success(cfResult)
                Log.w(TAG, "WEATHER_API_KEY no configurada y CF no disponible, usando pronóstico simulado")
                return@withContext Result.success(getMockForecast())
            }

            val url = "https://api.openweathermap.org/data/2.5/forecast?" +
                    "lat=${AppConstants.ALAMOS_LAT}&lon=${AppConstants.ALAMOS_LNG}&appid=$apiKey&units=metric&lang=es"

            val response = fetchUrl(url)
            Result.success(parseForecastJson(JSONObject(response)))

        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo pronóstico, usando datos mock", e)
            Result.success(getMockForecast())
        }
    }

    private fun parseForecastJson(json: JSONObject): List<ForecastDay> {
        val list = json.getJSONArray("list")
        val forecastMap = mutableMapOf<String, ForecastDay>()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        for (i in 0 until list.length()) {
            val item = list.getJSONObject(i)
            val date = Date(item.getLong("dt") * 1000)
            val dayKey = dateFormat.format(date)
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
        return forecastMap.values.toList()
    }

    private suspend fun getForecastFromCF(): List<ForecastDay>? = try {
        val data = mapOf(
            "lat" to AppConstants.ALAMOS_LAT,
            "lon" to AppConstants.ALAMOS_LNG,
            "type" to "forecast"
        )
        val result = FirebaseFunctions.getInstance()
            .getHttpsCallable("getWeather")
            .call(data)
            .await()
        @Suppress("UNCHECKED_CAST")
        val jsonStr = (result.data as Map<String, Any>)["json"] as? String ?: return null
        parseForecastJson(JSONObject(jsonStr))
    } catch (e: Exception) {
        if (BuildConfig.DEBUG) Log.w(TAG, "CF getForecast falló: ${e.message}")
        null
    }

    /**
     * Datos mock/simulados para cuando no hay API key o falla la conexión
     * Genera datos dinámicos basados en la hora del día para mayor realismo
     */
    private fun getMockWeather(): WeatherInfo {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)

        // Temperatura base según hora del día (patrón típico de Álamos, Sonora)
        val baseTemp = when {
            hour in 0..5 -> 18.0 + (Math.random() * 3)      // Madrugada: 18-21°C
            hour in 6..8 -> 20.0 + (Math.random() * 4)      // Mañana: 20-24°C
            hour in 9..11 -> 25.0 + (Math.random() * 4)     // Media mañana: 25-29°C
            hour in 12..15 -> 30.0 + (Math.random() * 5)    // Mediodía: 30-35°C (más calor)
            hour in 16..18 -> 28.0 + (Math.random() * 4)    // Tarde: 28-32°C
            hour in 19..21 -> 23.0 + (Math.random() * 3)    // Noche: 23-26°C
            else -> 19.0 + (Math.random() * 3)              // Noche tardía: 19-22°C
        }

        val feelsLike = baseTemp + (Math.random() * 3 - 1)

        // Descripción y emoji según hora
        val (description, icon) = when {
            hour in 6..7 -> Pair("Despejado - Amanecer", "01d")
            hour in 8..17 -> {
                when ((Math.random() * 10).toInt()) {
                    in 0..6 -> Pair("Despejado", "01d")
                    in 7..8 -> Pair("Parcialmente nublado", "02d")
                    else -> Pair("Soleado", "01d")
                }
            }
            hour in 18..19 -> Pair("Atardecer despejado", "01d")
            else -> Pair("Despejado - Noche", "01n")
        }

        // Humedad varía con la hora (más alta en la madrugada)
        val humidity = when {
            hour in 0..6 -> 55 + (Math.random() * 15).toInt()   // 55-70%
            hour in 7..11 -> 40 + (Math.random() * 15).toInt()  // 40-55%
            hour in 12..17 -> 30 + (Math.random() * 15).toInt() // 30-45%
            else -> 45 + (Math.random() * 15).toInt()           // 45-60%
        }

        // Viento más fuerte en la tarde
        val windSpeed = when {
            hour in 12..17 -> 10.0 + (Math.random() * 10)  // 10-20 km/h
            else -> 5.0 + (Math.random() * 8)              // 5-13 km/h
        }

        return WeatherInfo(
            temperature = baseTemp,
            feelsLike = feelsLike,
            description = description,
            icon = icon,
            humidity = humidity,
            windSpeed = windSpeed,
            timestamp = System.currentTimeMillis(),
            isMock = true
        )
    }

    /**
     * Pronóstico mock de 5 días
     * Genera pronóstico dinámico con variaciones realistas
     */
    private fun getMockForecast(): List<ForecastDay> {
        val calendar = Calendar.getInstance()
        val forecast = mutableListOf<ForecastDay>()

        // Patrones de clima variados pero coherentes
        val weatherPatterns = listOf(
            Triple("Despejado", "01d", 32.0 to 19.0),
            Triple("Soleado", "01d", 33.0 to 20.0),
            Triple("Parcialmente nublado", "02d", 29.0 to 18.0),
            Triple("Mayormente soleado", "02d", 31.0 to 19.5),
            Triple("Algo nublado", "03d", 27.0 to 17.0)
        )

        repeat(5) { day ->
            calendar.add(Calendar.DAY_OF_MONTH, 1)
            val pattern = weatherPatterns[(day + (Math.random() * weatherPatterns.size).toInt()) % weatherPatterns.size]
            val (description, icon, temps) = pattern

            // Añadir variación aleatoria pequeña a las temperaturas
            val tempVariation = Math.random() * 4 - 2  // ±2°C

            forecast.add(
                ForecastDay(
                    date = calendar.time,
                    tempMax = temps.first + tempVariation,
                    tempMin = temps.second + tempVariation * 0.5,
                    description = description,
                    icon = icon
                )
            )
        }

        return forecast
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
