package com.joseibarra.trazago

import android.content.Intent
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.joseibarra.trazago.databinding.ListItemRouteBinding

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
            binding.routeDescriptionTextView.text = route.descripcion.ifBlank { binding.root.context.getString(R.string.route_no_description) }
            binding.routeInfoTextView.text = binding.root.context.getString(R.string.route_stops_count, route.pdis_incluidos.size)

            route.fecha_creacion?.let { date ->
                val now = System.currentTimeMillis()
                val timeAgo = DateUtils.getRelativeTimeSpanString(
                    date.time,
                    now,
                    DateUtils.DAY_IN_MILLIS
                )
                binding.routeDateTextView.text = binding.root.context.getString(R.string.route_created_format, timeAgo)
            } ?: run {
                binding.routeDateTextView.text = binding.root.context.getString(R.string.route_unknown_date_label)
            }

            binding.root.setOnClickListener {
                onItemClicked(route)
            }

            binding.buttonShareRoute.setOnClickListener {
                val context = binding.root.context
                val shareText = binding.root.context.getString(
                    R.string.route_share_body,
                    route.nombre_ruta,
                    route.descripcion,
                    route.pdis_incluidos.size
                )

                val shareIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, shareText)
                    type = "text/plain"
                }
                context.startActivity(Intent.createChooser(shareIntent, binding.root.context.getString(R.string.route_share_title)))
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
