package com.joseibarra.trazago.result

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.joseibarra.trazago.PlaceDetailsActivity
import com.joseibarra.trazago.TouristSpot
import com.joseibarra.trazago.databinding.ItemRouteStopBinding
import com.joseibarra.trazago.model.RouteStop

/**
 * Adapter para la lista cronológica de paradas en [RouteItineraryFragment].
 *
 * Cada item muestra: foto, nombre, categoría, horario, duración, costo estimado,
 * razón de selección (IA), tips y alternativa si cerrado.
 */
class RouteItineraryAdapter :
    ListAdapter<Pair<RouteStop, TouristSpot?>, RouteItineraryAdapter.StopVH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StopVH =
        StopVH(ItemRouteStopBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: StopVH, position: Int) =
        holder.bind(getItem(position).first, getItem(position).second)

    inner class StopVH(private val b: ItemRouteStopBinding) :
        RecyclerView.ViewHolder(b.root) {

        fun bind(stop: RouteStop, spot: TouristSpot?) {
            // Número de parada
            b.tvOrden.text = stop.ordenSugerido.toString()

            // Foto
            val imageUrl = spot?.imagenUrl ?: spot?.imagenesGaleria?.firstOrNull()
            if (!imageUrl.isNullOrBlank()) {
                Glide.with(b.ivPlaceFoto.context)
                    .load(imageUrl)
                    .centerCrop()
                    .into(b.ivPlaceFoto)
            } else {
                b.ivPlaceFoto.setImageDrawable(null)
            }

            // Nombre y categoría
            b.tvNombreLugar.text = spot?.nombre ?: stop.placeId
            b.tvCategoria.text = spot?.categoria ?: ""

            // Horario
            b.tvHorarioChip.text = if (stop.horaSugeridaInicio.isNotBlank() && stop.horaSugeridaFin.isNotBlank())
                "${stop.horaSugeridaInicio}–${stop.horaSugeridaFin}"
            else ""

            // Duración y costo
            val durText = when {
                stop.duracionEstimadaMin >= 60 -> {
                    val h = stop.duracionEstimadaMin / 60
                    val m = stop.duracionEstimadaMin % 60
                    if (m == 0) "⏱ ${h}h" else "⏱ ${h}h ${m}min"
                }
                else -> "⏱ ${stop.duracionEstimadaMin} min"
            }
            b.tvDuracion.text = durText
            b.tvCosto.text = if (stop.costoEstimadoMxn > 0) "$${stop.costoEstimadoMxn} MXN" else "Sin costo"

            // Razón de selección IA
            b.tvRazonSeleccion.text = stop.razonSeleccion
            b.tvRazonSeleccion.visibility = if (stop.razonSeleccion.isNotBlank()) View.VISIBLE else View.GONE

            // Tips
            if (stop.tipsParaEstaParada.isNotEmpty()) {
                b.tvTips.text = stop.tipsParaEstaParada.joinToString("\n") { "• $it" }
                b.tvTips.visibility = View.VISIBLE
            } else {
                b.tvTips.visibility = View.GONE
            }

            // Alternativa si cerrado
            if (stop.alternativaSiCerrado.isNotBlank()) {
                b.tvAlternativa.text = "Si está cerrado: ${stop.alternativaSiCerrado}"
                b.tvAlternativa.visibility = View.VISIBLE
            } else {
                b.tvAlternativa.visibility = View.GONE
            }

            // Botón Ver detalles
            b.btnVerDetalles.setOnClickListener {
                if (spot != null) {
                    val intent = Intent(b.root.context, PlaceDetailsActivity::class.java).apply {
                        putExtra("PLACE_ID", spot.id)
                        putExtra("PLACE_NAME", spot.nombre)
                        putExtra("PLACE_CATEGORY", spot.categoria)
                        putExtra("PLACE_DESCRIPTION", spot.descripcion)
                        spot.ubicacion?.let { loc ->
                            putExtra("PLACE_LATITUDE", loc.latitude)
                            putExtra("PLACE_LONGITUDE", loc.longitude)
                        }
                    }
                    b.root.context.startActivity(intent)
                }
            }

            // Botón Cómo llegar
            b.btnComoLlegar.setOnClickListener {
                spot?.ubicacion?.let { loc ->
                    val uri = Uri.parse("google.navigation:q=${loc.latitude},${loc.longitude}&mode=w")
                    val mapsIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                        setPackage("com.google.android.apps.maps")
                    }
                    if (mapsIntent.resolveActivity(b.root.context.packageManager) != null) {
                        b.root.context.startActivity(mapsIntent)
                    } else {
                        val webUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${loc.latitude},${loc.longitude}&travelmode=walking")
                        b.root.context.startActivity(Intent(Intent.ACTION_VIEW, webUri))
                    }
                }
            }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Pair<RouteStop, TouristSpot?>>() {
            override fun areItemsTheSame(
                old: Pair<RouteStop, TouristSpot?>,
                new: Pair<RouteStop, TouristSpot?>
            ) = old.first.placeId == new.first.placeId

            override fun areContentsTheSame(
                old: Pair<RouteStop, TouristSpot?>,
                new: Pair<RouteStop, TouristSpot?>
            ) = old == new
        }
    }
}
