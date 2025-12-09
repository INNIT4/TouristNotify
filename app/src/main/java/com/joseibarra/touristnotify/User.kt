package com.joseibarra.touristnotify

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

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
