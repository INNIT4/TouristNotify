package com.joseibarra.touristnotify

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint

object DatabaseSeeder {
    fun seedIfEmpty(
        db: FirebaseFirestore,
        onComplete: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        val sampleSpots = buildSampleSpots()
        val batch = db.batch()
        sampleSpots.forEach { spot ->
            val docId = spot.nombre
                .lowercase()
                .replace(" ", "_")
                .replace(Regex("[^a-z0-9_]"), "")
            batch.set(db.collection(FirestoreCollections.PLACES).document(docId), spot)
        }
        batch.commit()
            .addOnSuccessListener { onComplete() }
            .addOnFailureListener { onError(it) }
    }

    private fun buildSampleSpots(): List<TouristSpot> = listOf(
        TouristSpot(
            nombre = "Plaza de Armas",
            descripcion = "El corazón histórico y social de Álamos, rodeado de arcos y edificios coloniales.",
            categoria = "Historia",
            ubicacion = GeoPoint(AppConstants.ALAMOS_LAT, AppConstants.ALAMOS_LNG),
            googlePlaceId = "ChIJ-c-fJ8rS1oYREj9T7o-Z1A0"
        ),
        TouristSpot(
            nombre = "Museo Costumbrista de Sonora",
            descripcion = "Un viaje a través de la historia, cultura y tradiciones de la región de Sonora.",
            categoria = "Cultura",
            ubicacion = GeoPoint(27.0267, -108.9395),
            googlePlaceId = "ChIJ-c-fJ8rS1oYRY2q9JdE4j2s"
        ),
        TouristSpot(
            nombre = "Mirador El Perico",
            descripcion = "Ofrece las vistas panorámicas más espectaculares de Álamos y sus alrededores.",
            categoria = "Aire Libre",
            ubicacion = GeoPoint(27.0315, -108.9344),
            googlePlaceId = "ChIJy1Z5zsrS1oYRmY3G8F0B2Fw"
        ),
        TouristSpot(
            nombre = "Templo de la Purísima Concepción",
            descripcion = "Una joya arquitectónica de tres naves y una imponente fachada barroca.",
            categoria = "Historia",
            ubicacion = GeoPoint(27.0279, -108.9393),
            googlePlaceId = "ChIJc-cfJ8rS1oYR4B-ZJdE4j2s"
        ),
        TouristSpot(
            nombre = "Palacio Municipal de Álamos",
            descripcion = "Edificio histórico sede del gobierno local, con un característico reloj en su torre.",
            categoria = "Cultura",
            ubicacion = GeoPoint(27.0272, -108.9404),
            googlePlaceId = "ChIJe-cfJ8rS1oYReJ9T7o-Z1A0"
        ),
        TouristSpot(
            nombre = "Callejón del Beso",
            descripcion = "Un estrecho y romántico callejón lleno de leyendas locales.",
            categoria = "Cultura",
            ubicacion = GeoPoint(27.0258, -108.9398),
            googlePlaceId = null
        )
    )
}
