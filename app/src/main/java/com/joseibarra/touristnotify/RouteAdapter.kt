package com.joseibarra.touristnotify

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.joseibarra.touristnotify.databinding.ListItemRouteBinding

class RouteAdapter(
    private var routes: List<Route>,
    private val onItemClicked: (Route) -> Unit
) : RecyclerView.Adapter<RouteAdapter.RouteViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RouteViewHolder {
        val binding = ListItemRouteBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RouteViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RouteViewHolder, position: Int) {
        val route = routes[position]
        holder.bind(route)
        holder.itemView.setOnClickListener { onItemClicked(route) }
    }

    override fun getItemCount() = routes.size

    fun updateRoutes(newRoutes: List<Route>) {
        routes = newRoutes
        notifyDataSetChanged()
    }

    inner class RouteViewHolder(private val binding: ListItemRouteBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(route: Route) {
            binding.routeNameTextView.text = route.nombre_ruta
            binding.routeDescriptionTextView.text = route.descripcion
            binding.routeInfoTextView.text = "${route.pdis_incluidos.size} paradas"
        }
    }
}
