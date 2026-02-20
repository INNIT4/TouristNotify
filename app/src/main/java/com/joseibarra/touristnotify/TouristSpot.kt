package com.joseibarra.touristnotify

import com.google.firebase.firestore.GeoPoint

/**
 * Modelo de datos para un lugar turístico en Álamos, Sonora
 *
 * Representa un sitio de interés turístico con su información completa:
 * - Datos básicos (nombre, descripción, categoría)
 * - Ubicación geográfica (GeoPoint de Firebase)
 * - Reseñas y calificaciones
 * - Información de contacto (teléfono, sitio web, horarios)
 * - Servicios y accesibilidad
 *
 * Esta clase es la representación en Firestore del documento en la colección "lugares".
 * Se sincroniza automáticamente con Firestore y soporta todos los tipos de datos nativos.
 *
 * @property id ID único del lugar (generado por Firestore)
 * @property nombre Nombre del lugar turístico
 * @property descripcion Descripción detallada del sitio
 * @property categoria Categoría del lugar (Histórico, Gastronómico, Natural, etc)
 * @property ubicacion Coordenadas geográficas del lugar (GeoPoint de Firebase)
 * @property googlePlaceId ID del lugar en Google Places (si existe)
 * @property rating Calificación promedio (0.0 - 5.0)
 * @property reviewCount Número total de reseñas
 */
data class TouristSpot(
    val id: String = "",
    val nombre: String = "",
    val descripcion: String = "",
    val categoria: String = "General",
    val reviews: List<String> = emptyList(),
    val ubicacion: GeoPoint? = null,
    val imagenUrl: String = "",
    val rating: Double = 0.0,
    val reviewCount: Int = 0,
    val googlePlaceId: String? = null,
    // Nuevos campos mejorados
    val horarios: String = "",
    val sitioWeb: String = "",
    val telefono: String = "",
    val visitCount: Int = 0,
    val direccion: String = "",
    val precioEstimado: String = "",
    val accesibilidad: String = "",
    val servicios: List<String> = emptyList()
)
