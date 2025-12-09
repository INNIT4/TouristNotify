package com.joseibarra.touristnotify

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class Route(
    val id_ruta: String = "",
    val id_usuario: String = "",
    val nombre_ruta: String = "",
    val descripcion: String = "",
    val pdis_incluidos: List<String> = emptyList(), // Lista de IDs de TouristSpot
    val duracion_estimada: String = "",
    val distancia_total: String = "",
    @ServerTimestamp
    val fecha_creacion: Date? = null,
    val estado_ruta: String = "guardada"
)
