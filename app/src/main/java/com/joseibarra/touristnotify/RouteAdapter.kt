package com.joseibarra.touristnotify

import android.content.Intent
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.joseibarra.touristnotify.databinding.ListItemRouteBinding

class RouteAdapter(
    private val onItemClicked: (Route) -> Unit,
    private val onDeleteClicked: (Route, Int) -> Unit
) : ListAdapter<Route, RouteAdapter.RouteViewHolder>(RouteDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RouteViewHolder {
        val binding = ListItemRouteBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RouteViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RouteViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    fun updateRoutes(newRoutes: List<Route>) {
        submitList(newRoutes)
    }

    fun removeRoute(position: Int) {
        val current = currentList.toMutableList()
        if (position in current.indices) {
            current.removeAt(position)
            submitList(current)
        }
    }

    inner class RouteViewHolder(private val binding: ListItemRouteBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(route: Route, position: Int) {
            binding.routeNameTextView.text = route.nombre_ruta
            binding.routeDescriptionTextView.text = route.descripcion.ifBlank { "Sin descripción" }
            binding.routeInfoTextView.text = "${route.pdis_incluidos.size} paradas"

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

            binding.root.setOnClickListener {
                onItemClicked(route)
            }

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

            binding.buttonDeleteRoute.setOnClickListener {
                onDeleteClicked(route, position)
            }
        }
    }

    class RouteDiffCallback : DiffUtil.ItemCallback<Route>() {
        override fun areItemsTheSame(oldItem: Route, newItem: Route): Boolean {
            return oldItem.id_ruta == newItem.id_ruta
        }

        override fun areContentsTheSame(oldItem: Route, newItem: Route): Boolean {
            return oldItem == newItem
        }
    }
}
