package com.joseibarra.trazago

/**
 * Utilidades centralizadas para manejo de categorías
 * Evita código duplicado en múltiples Activities y Adapters
 */
object CategoryUtils {

    /**
     * Devuelve el emoji correspondiente a una categoría turística
     */
    fun getCategoryEmoji(category: String): String {
        return when (category.lowercase()) {
            "museo" -> "🏛️"
            "restaurante", "gastronomía", "comida" -> "🍽️"
            "hotel", "hospedaje" -> "🏨"
            "iglesia", "templo" -> "⛪"
            "parque" -> "🌳"
            "tienda", "shopping" -> "🛍️"
            "atracción turística", "atracción" -> "🎭"
            "mirador" -> "🏞️"
            "plaza" -> "🏛️"
            "cultura" -> "🎨"
            "consejos", "tips" -> "💡"
            "historia" -> "📜"
            "eventos", "festival" -> "🎉"
            else -> "📍"
        }
    }

    /**
     * Adivina la categoría basándose en los tipos de Google Places
     */
    fun guessCategory(types: List<String>): String {
        return when {
            types.any { it.contains("museum", ignoreCase = true) } -> "Museo"
            types.any { it.contains("restaurant", ignoreCase = true) || it.contains("food", ignoreCase = true) } -> "Restaurante"
            types.any { it.contains("lodging", ignoreCase = true) || it.contains("hotel", ignoreCase = true) } -> "Hotel"
            types.any { it.contains("church", ignoreCase = true) || it.contains("place_of_worship", ignoreCase = true) } -> "Iglesia"
            types.any { it.contains("park", ignoreCase = true) } -> "Parque"
            types.any { it.contains("store", ignoreCase = true) || it.contains("shopping_mall", ignoreCase = true) } -> "Tienda"
            types.any { it.contains("tourist_attraction", ignoreCase = true) } -> "Atracción Turística"
            else -> "Otro"
        }
    }

    /**
     * Formatea el precio estimado con símbolos de dólar
     */
    fun formatPriceLevel(priceLevel: Int): String {
        return when (priceLevel) {
            1 -> "$"
            2 -> "$$"
            3 -> "$$$"
            4 -> "$$$$"
            else -> ""
        }
    }
}
