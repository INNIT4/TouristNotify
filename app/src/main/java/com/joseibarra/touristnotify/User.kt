package com.joseibarra.touristnotify

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Modelo de datos para un usuario de la aplicación
 *
 * Representa el perfil de usuario almacenado en Firestore.
 * Incluye preferencias de viaje y metadatos de actividad.
 *
 * Almacenado en Firestore: collection "users", document ID = Firebase Auth UID
 *
 * @property id_usuario ID único del usuario (mismo que Firebase Auth UID)
 * @property nombre_usuario Nombre de perfil elegido por el usuario
 * @property email Email del usuario (sincronizado con Firebase Auth)
 * @property preferencias_viaje Lista de categorías de interés del usuario
 * @property fecha_registro Timestamp de cuando se registró (auto-generado)
 * @property ultimo_acceso Fecha del último acceso a la app
 * @property idioma_preferido Código de idioma ISO (default: "es")
 */
data class User(
    val id_usuario: String = "",
    var nombre_usuario: String = "",
    val email: String = "",
    val preferencias_viaje: List<String> = emptyList(),
    @ServerTimestamp
    val fecha_registro: Date? = null,
    var ultimo_acceso: Date? = null,
    val idioma_preferido: String = "es" // Por defecto en español
)
