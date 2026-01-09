package com.joseibarra.touristnotify

import android.app.Application
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings

class TouristNotifyApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Habilitar persistencia offline de Firestore
        val settings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true)
            .build()

        FirebaseFirestore.getInstance().firestoreSettings = settings
    }
}
