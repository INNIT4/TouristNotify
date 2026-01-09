package com.joseibarra.touristnotify

import android.content.Intent
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.joseibarra.touristnotify.databinding.ListItemRouteBinding

class RouteAdapter(
    private var routes: List<Route>,
    private val onItemClicked: (Route) -> Unit,
    private val onDeleteClicked: (Route, Int) -> Unit
) : RecyclerView.Adapter<RouteAdapter.RouteViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RouteViewHolder {
        val binding = ListItemRouteBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RouteViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RouteViewHolder, position: Int) {
        val route = routes[position]
        holder.bind(route, position)
    }

    override fun getItemCount() = routes.size

    fun updateRoutes(newRoutes: List<Route>) {
        routes = newRoutes
        notifyDataSetChanged()
    }

    fun removeRoute(position: Int) {
        val mutableRoutes = routes.toMutableList()
        if (position in mutableRoutes.indices) {
            mutableRoutes.removeAt(position)
            routes = mutableRoutes
            notifyItemRemoved(position)
        }
    }

    inner class RouteViewHolder(private val binding: ListItemRouteBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(route: Route, position: Int) {
            binding.routeNameTextView.text = route.nombre_ruta
            binding.routeDescriptionTextView.text = route.descripcion.ifBlank { "Sin descripción" }
            binding.routeInfoTextView.text = "${route.pdis_incluidos.size} paradas"

            // Mostrar fecha de creación
            route.fecha_creacion?.let { date ->
                val now = System.currentTimeMillis()
                val timeAgo = DateUtils.getRelativeTimeSpanString(
                    date.time,
                    now,
                    DateUtils.DAY_IN_MILLIS
                )
                binding.routeDateTextView.text = "Creada $timeAgo"
            } ?: run {
                binding.routeDateTextView.text = "Fecha desconocida"
            }

            // Click en la tarjeta completa para ver la ruta
            binding.root.setOnClickListener {
                onItemClicked(route)
            }

            // Botón de compartir
            binding.buttonShareRoute.setOnClickListener {
                val context = binding.root.context
                val shareText = buildString {
                    append("🗺️ ${route.nombre_ruta}\n\n")
                    append("${route.descripcion}\n\n")
                    append("📍 ${route.pdis_incluidos.size} paradas incluidas\n")
                    append("\n¡Descubre Álamos con TouristNotify!")
                }

                val shareIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, shareText)
                    type = "text/plain"
                }
                context.startActivity(Intent.createChooser(shareIntent, "Compartir ruta"))
            }

            // Botón de eliminar
            binding.buttonDeleteRoute.setOnClickListener {
                onDeleteClicked(route, position)
            }
        }
    }
}
