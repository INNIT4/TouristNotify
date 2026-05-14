package com.joseibarra.trazago

/**
 * Fuente única de verdad para los nombres de colecciones de Firestore.
 *
 * Antes de esta clase, distintos archivos usaban nombres inconsistentes
 * ("events" vs "eventos", "lugares_turisticos" vs "lugares") que causaban
 * features silenciosamente rotas. Cualquier nuevo acceso a Firestore
 * debe usar estas constantes.
 */
object FirestoreCollections {

    /** Lugares turísticos (catálogo). */
    const val PLACES = "lugares"

    /** Eventos del calendario. */
    const val EVENTS = "eventos"

    /** Posts del blog. */
    const val BLOG_POSTS = "blog_posts"

    /** Usuarios y sus subcollecciones. */
    const val USERS = "users"

    /** Subcollecciones bajo `users/{uid}/`. */
    const val USER_FAVORITES = "favorites"
    const val USER_ROUTES = "routes"
    const val USER_STATS = "stats"
    const val USER_USAGE = "usage"

    /** Check-ins (raíz, no subcollection). */
    const val CHECK_INS = "checkIns"

    /** Reseñas (subcollection bajo `lugares/{placeId}/reviews`). */
    const val REVIEWS = "reviews"

    /** Fotos de lugares. */
    const val PLACE_PHOTOS = "place_photos"

    /** Rutas predeterminadas / temáticas. */
    const val THEMED_ROUTES = "themed_routes"

    /** Servicios y contactos de emergencia. */
    const val SERVICES = "services"
    const val EMERGENCY_CONTACTS = "emergency_contacts"

    /** Geofences configuradas. */
    const val GEOFENCES = "geofences"
}
