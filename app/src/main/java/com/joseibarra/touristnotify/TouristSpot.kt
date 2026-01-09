package com.joseibarra.touristnotify

import com.google.firebase.firestore.GeoPoint

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
