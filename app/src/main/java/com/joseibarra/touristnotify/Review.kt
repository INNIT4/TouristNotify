package com.joseibarra.touristnotify

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class Review(
    val userId: String = "",
    val userName: String = "Anónimo",
    val rating: Float = 0f,
    val comment: String = "",
    val imageUrl: String = "",
    @ServerTimestamp
    val timestamp: Date? = null
)
