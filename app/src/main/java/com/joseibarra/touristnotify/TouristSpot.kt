package com.joseibarra.touristnotify

import com.google.firebase.firestore.GeoPoint

data class TouristSpot(
    val id: String = "",
    val nombre: String = "",
    val descripcion: String = "",
    val categoria: String = "General",
    val ubicacion: GeoPoint? = null,
    val imagenUrl: String = "",
    val rating: Double = 0.0,
    val reviewCount: Int = 0, // Nuevo campo para el contador de reseñas
    val googlePlaceId: String? = null
)
