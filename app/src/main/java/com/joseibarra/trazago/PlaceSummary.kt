package com.joseibarra.trazago

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Resumen de un lugar turístico para pasar entre Activities vía Intent.
 *
 * Reemplaza los 7 extras sueltos (PLACE_ID, PLACE_NAME, PLACE_CATEGORY,
 * PLACE_DESCRIPTION, GOOGLE_PLACE_ID, PLACE_LATITUDE, PLACE_LONGITUDE) por
 * un único Parcelable, reduciendo el riesgo de omitir un campo al navegar.
 *
 * PlaceDetailsActivity sigue aceptando el extra legacy PLACE_ID para deep links
 * y otros callers externos. Los callers internos deben usar EXTRA_KEY.
 */
@Parcelize
data class PlaceSummary(
    val id: String,
    val nombre: String = "",
    val categoria: String = "",
    val descripcion: String = "",
    val googlePlaceId: String? = null,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0
) : Parcelable {

    companion object {
        const val EXTRA_KEY = "PLACE_SUMMARY"

        fun fromTouristSpot(spot: TouristSpot) = PlaceSummary(
            id = spot.id ?: "",
            nombre = spot.nombre ?: "",
            categoria = spot.categoria ?: "",
            descripcion = spot.descripcion ?: "",
            googlePlaceId = spot.googlePlaceId,
            latitude = spot.ubicacion?.latitude ?: 0.0,
            longitude = spot.ubicacion?.longitude ?: 0.0
        )
    }
}
