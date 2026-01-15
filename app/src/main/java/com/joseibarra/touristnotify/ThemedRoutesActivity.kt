package com.joseibarra.touristnotify

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.FirebaseFirestore
import com.joseibarra.touristnotify.databinding.ActivityThemedRoutesBinding

/**
 * Activity que muestra las rutas temáticas disponibles en Álamos
 *
 * Temas: Histórica, Gastronómica, Religiosa, Arquitectónica, Fotográfica, Natural, etc.
 */
class ThemedRoutesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityThemedRoutesBinding
    private lateinit var db: FirebaseFirestore
    private lateinit var adapter: ThemedRoutesAdapter
    private val routes = mutableListOf<ThemedRoute>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityThemedRoutesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = FirebaseFirestore.getInstance()

        setupRecyclerView()
        loadRoutes()
    }

    private fun setupRecyclerView() {
        adapter = ThemedRoutesAdapter(routes) { route ->
            openRoute(route)
        }
        binding.routesRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.routesRecyclerView.adapter = adapter
    }

    private fun loadRoutes() {
        binding.progressBar.visibility = View.VISIBLE
        binding.emptyStateContainer.visibility = View.GONE

        db.collection("themed_routes")
            .get()
            .addOnSuccessListener { documents ->
                binding.progressBar.visibility = View.GONE
                routes.clear()

                if (documents.isEmpty) {
                    // Si no hay rutas en Firebase, mostrar rutas predefinidas
                    loadPredefinedRoutes()
                    return@addOnSuccessListener
                }

                for (document in documents) {
                    try {
                        val route = document.toObject(ThemedRoute::class.java).copy(id = document.id)
                        routes.add(route)
                    } catch (e: Exception) {
                        android.util.Log.e("ThemedRoutesActivity", "Error al parsear ruta: ${e.message}")
                    }
                }

                // Ordenar: destacadas primero, luego por nombre
                routes.sortWith(
                    compareByDescending<ThemedRoute> { it.isFeatured }
                        .thenBy { it.name }
                )

                adapter.notifyDataSetChanged()

                if (routes.isEmpty()) {
                    showEmptyState()
                }
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                // En caso de error, mostrar rutas predefinidas
                loadPredefinedRoutes()
            }
    }

    /**
     * Carga rutas predefinidas cuando no hay datos en Firebase
     * Esto permite que la app funcione sin configuración inicial
     */
    private fun loadPredefinedRoutes() {
        routes.clear()
        routes.addAll(
            listOf(
                ThemedRoute(
                    id = "historica",
                    name = "Ruta Histórica",
                    theme = "Histórica",
                    description = "Descubre los edificios coloniales y sitios históricos que hacen de Álamos un Pueblo Mágico. Visita la Plaza de Armas, el Museo Costumbrista y las casonas del siglo XVIII.",
                    placeIds = emptyList(),
                    estimatedDuration = "2-3 horas",
                    difficulty = "Fácil",
                    color = "#8B4513",
                    icon = "🏛️",
                    isFeatured = true
                ),
                ThemedRoute(
                    id = "gastronomica",
                    name = "Ruta Gastronómica",
                    theme = "Gastronómica",
                    description = "Prueba los sabores tradicionales de Sonora. Recorre los mejores restaurantes y cocinas locales. No te pierdas las gorditas, carne asada y dulces regionales.",
                    placeIds = emptyList(),
                    estimatedDuration = "3-4 horas",
                    difficulty = "Fácil",
                    color = "#FF6347",
                    icon = "🍴",
                    isFeatured = true
                ),
                ThemedRoute(
                    id = "religiosa",
                    name = "Ruta Religiosa",
                    theme = "Religiosa",
                    description = "Visita las iglesias y templos que narran la historia de la evangelización en la región. Incluye la Parroquia de la Purísima Concepción y capillas coloniales.",
                    placeIds = emptyList(),
                    estimatedDuration = "2 horas",
                    difficulty = "Fácil",
                    color = "#4169E1",
                    icon = "⛪",
                    isFeatured = false
                ),
                ThemedRoute(
                    id = "arquitectonica",
                    name = "Ruta Arquitectónica",
                    theme = "Arquitectónica",
                    description = "Admira la arquitectura colonial española preservada. Casas con portones de mezquite, patios internos y detalles únicos del siglo XVIII.",
                    placeIds = emptyList(),
                    estimatedDuration = "2-3 horas",
                    difficulty = "Fácil",
                    color = "#DAA520",
                    icon = "🏘️",
                    isFeatured = false
                ),
                ThemedRoute(
                    id = "fotografica",
                    name = "Ruta Fotográfica",
                    theme = "Fotográfica",
                    description = "Los mejores spots para capturar la belleza de Álamos. Calles empedradas, atardeceres en la plaza y rincones pintorescos.",
                    placeIds = emptyList(),
                    estimatedDuration = "2-3 horas",
                    difficulty = "Moderado",
                    color = "#FF1493",
                    icon = "📸",
                    isFeatured = false
                ),
                ThemedRoute(
                    id = "natural",
                    name = "Ruta Natural",
                    theme = "Natural",
                    description = "Explora la naturaleza que rodea Álamos. Senderos, miradores y observación de aves en la Sierra de Álamos.",
                    placeIds = emptyList(),
                    estimatedDuration = "3-5 horas",
                    difficulty = "Moderado",
                    color = "#228B22",
                    icon = "🌿",
                    isFeatured = false
                )
            )
        )
        adapter.notifyDataSetChanged()
    }

    private fun showEmptyState() {
        binding.emptyStateContainer.visibility = View.VISIBLE
        binding.routesRecyclerView.visibility = View.GONE
    }

    private fun openRoute(route: ThemedRoute) {
        if (route.placeIds.isNotEmpty()) {
            // Si la ruta tiene lugares definidos, abrir el mapa con esos lugares
            val intent = Intent(this, MapsActivity::class.java).apply {
                putStringArrayListExtra("ROUTE_PLACES_IDS", ArrayList(route.placeIds))
            }
            startActivity(intent)
        } else {
            // Mostrar diálogo con información detallada de la ruta
            showRouteDetailsDialog(route)
        }
    }

    private fun showRouteDetailsDialog(route: ThemedRoute) {
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("${route.icon} ${route.name}")
            .setMessage(buildString {
                append("📝 Descripción:\n${route.description}\n\n")
                append("⏱️ Duración: ${route.estimatedDuration}\n")
                append("🎯 Dificultad: ${route.difficulty}\n")
                append("🎨 Tema: ${route.theme}")
            })
            .setPositiveButton("Ver en mapa") { _, _ ->
                // Abrir el mapa general
                val intent = Intent(this, MapsActivity::class.java)
                startActivity(intent)
            }
            .setNegativeButton("Cerrar", null)
            .create()
        dialog.show()
    }
}
