package com.joseibarra.touristnotify

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.joseibarra.touristnotify.databinding.ActivityEventsBinding
import java.util.*

/**
 * Activity que muestra los eventos actuales y próximos en Álamos, Sonora
 */
class EventsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEventsBinding
    private lateinit var db: FirebaseFirestore
    private lateinit var adapter: EventsAdapter
    private val events = mutableListOf<Event>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEventsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = FirebaseFirestore.getInstance()

        setupRecyclerView()
        loadEvents()
    }

    private fun setupRecyclerView() {
        adapter = EventsAdapter(events) { event ->
            openEventDetails(event)
        }
        binding.eventsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.eventsRecyclerView.adapter = adapter
    }

    private fun loadEvents() {
        binding.progressBar.visibility = View.VISIBLE
        binding.emptyStateContainer.visibility = View.GONE

        // Obtener eventos que aún no han terminado
        val currentDate = Date()

        db.collection("eventos")
            .whereGreaterThanOrEqualTo("endDate", currentDate)
            .orderBy("endDate", Query.Direction.ASCENDING)
            .orderBy("startDate", Query.Direction.ASCENDING)
            .get()
            .addOnSuccessListener { documents ->
                binding.progressBar.visibility = View.GONE
                events.clear()

                if (documents.isEmpty) {
                    showEmptyState()
                    return@addOnSuccessListener
                }

                for (document in documents) {
                    try {
                        val event = document.toObject(Event::class.java).copy(id = document.id)
                        events.add(event)
                    } catch (e: Exception) {
                        android.util.Log.e("EventsActivity", "Error al parsear evento: ${e.message}")
                    }
                }

                // Ordenar: destacados primero, luego por fecha
                events.sortWith(
                    compareByDescending<Event> { it.isFeatured }
                        .thenBy { it.startDate }
                )

                adapter.notifyDataSetChanged()

                if (events.isEmpty()) {
                    showEmptyState()
                }
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                showEmptyState()
                NotificationHelper.error(binding.root, "Error al cargar eventos: ${e.message}")
            }
    }

    private fun showEmptyState() {
        binding.emptyStateContainer.visibility = View.VISIBLE
        binding.eventsRecyclerView.visibility = View.GONE
    }

    private fun openEventDetails(event: Event) {
        // Si el evento tiene un placeId asociado, abrir los detalles del lugar
        if (event.placeId.isNotBlank()) {
            val intent = Intent(this, PlaceDetailsActivity::class.java).apply {
                putExtra("PLACE_ID", event.placeId)
                putExtra("PLACE_NAME", event.location)
                putExtra("PLACE_CATEGORY", "Evento")
                putExtra("PLACE_DESCRIPTION", event.description)
            }
            startActivity(intent)
        } else {
            // Mostrar detalles del evento en un diálogo o nueva pantalla
            NotificationHelper.info(binding.root, "Evento: ${event.title}")
        }
    }
}
