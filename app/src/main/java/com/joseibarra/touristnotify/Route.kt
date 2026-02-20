package com.joseibarra.touristnotify

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Modelo de datos para una ruta turística personalizada
 *
 * Representa una secuencia de lugares turísticos que forman un recorrido.
 * Las rutas pueden ser predefinidas o generadas por IA basadas en preferencias.
 *
 * Almacenado en Firestore: collection "rutas"
 *
 * @property id_ruta ID único de la ruta (generado por Firestore)
 * @property id_usuario ID del usuario que creó/guardó la ruta
 * @property nombre_ruta Nombre descriptivo de la ruta
 * @property descripcion Descripción detallada del recorrido
 * @property pdis_incluidos Lista de IDs de TouristSpot incluidos en orden
 * @property duracion_estimada Tiempo estimado del recorrido (ej: "3 horas")
 * @property distancia_total Distancia total del recorrido (ej: "5.2 km")
 * @property fecha_creacion Timestamp de creación (auto-generado)
 * @property estado_ruta Estado actual: "guardada", "completada", "en_progreso"
 */
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
