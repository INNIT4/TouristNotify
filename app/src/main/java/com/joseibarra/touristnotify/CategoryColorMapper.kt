package com.joseibarra.touristnotify

import android.graphics.Color
import com.google.android.gms.maps.model.BitmapDescriptorFactory

/**
 * Mapper centralizado de categorías turísticas a colores y estilos visuales
 *
 * Proporciona colores Material Design consistentes para cada categoría
 * de lugar turístico. Usado por marcadores del mapa, chips de filtro, etc.
 *
 * Categorías soportadas: museo, restaurante, hotel, iglesia, parque, tienda
 */
object CategoryColorMapper {

    /**
     * Obtiene el color Material Design para una categoría turística
     *
     * @param category Nombre de la categoría (case insensitive)
     * @return Color como Int (ARGB) para usar en Canvas, Paint, etc.
     */
    fun getColor(category: String): Int = when (category.lowercase()) {
        "museo" -> Color.parseColor("#9C27B0")
        "restaurante", "gastronomía" -> Color.parseColor("#FF5722")
        "hotel", "hospedaje" -> Color.parseColor("#2196F3")
        "iglesia", "templo" -> Color.parseColor("#00BCD4")
        "parque", "naturaleza" -> Color.parseColor("#4CAF50")
        "tienda", "comercio" -> Color.parseColor("#FFC107")
        else -> Color.parseColor("#F44336")
    }

    /**
     * Obtiene el hue para BitmapDescriptorFactory (marcadores estándar de Google Maps)
     *
     * @param category Nombre de la categoría (case insensitive)
     * @return Hue flotante para BitmapDescriptorFactory.defaultMarker()
     */
    fun getHue(category: String): Float = when (category.lowercase()) {
        "museo" -> BitmapDescriptorFactory.HUE_VIOLET
        "restaurante", "gastronomía" -> BitmapDescriptorFactory.HUE_ORANGE
        "hotel", "hospedaje" -> BitmapDescriptorFactory.HUE_BLUE
        "iglesia", "templo" -> BitmapDescriptorFactory.HUE_CYAN
        "parque", "naturaleza" -> BitmapDescriptorFactory.HUE_GREEN
        "tienda", "comercio" -> BitmapDescriptorFactory.HUE_YELLOW
        else -> BitmapDescriptorFactory.HUE_RED
    }
}
