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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEventsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = FirebaseFirestore.getInstance()

        setupRecyclerView()
        loadEvents()
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.fade_in, R.anim.slide_out_right)
    }

    private fun setupRecyclerView() {
        adapter = EventsAdapter { event ->
            openEventDetails(event)
        }
        binding.eventsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.eventsRecyclerView.adapter = adapter
    }

    private fun loadEvents() {
        binding.progressBar.visibility = View.VISIBLE
        binding.emptyStateContainer.visibility = View.GONE

        // PERF-006: orderBy en Firestore (requiere índice compuesto isFeatured DESC + startDate ASC)
        db.collection(FirestoreCollections.EVENTS)
            .orderBy("isFeatured", Query.Direction.DESCENDING)
            .orderBy("startDate", Query.Direction.ASCENDING)
            .limit(50)
            .get()
            .addOnSuccessListener { documents ->
                binding.progressBar.visibility = View.GONE

                if (documents.isEmpty) {
                    loadSampleEvents()
                    return@addOnSuccessListener
                }

                val currentDate = Date()
                val events = mutableListOf<Event>()
                for (document in documents) {
                    try {
                        val event = document.toObject(Event::class.java).copy(id = document.id)
                        if (event.endDate == null || event.endDate >= currentDate) {
                            events.add(event)
                        }
                    } catch (e: Exception) {
                        if (BuildConfig.DEBUG) android.util.Log.e("EventsActivity", "Error al parsear evento: ${e.message}")
                    }
                }

                adapter.submitList(events)

                if (events.isEmpty()) {
                    showEmptyState()
                }
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                // En caso de error, mostrar eventos de ejemplo
                loadSampleEvents()
            }
    }

    private fun loadSampleEvents() {
        val sampleEvents = mutableListOf<Event>()
        val calendar = Calendar.getInstance()

        // Evento 1: Festival Alfonso Ortiz Tirado
        calendar.set(2026, Calendar.JANUARY, 20)
        val faotStart = calendar.time
        calendar.set(2026, Calendar.JANUARY, 26)
        val faotEnd = calendar.time

        sampleEvents.add(Event(
            id = "sample1",
            title = "Festival Alfonso Ortiz Tirado (FAOT)",
            description = "El evento cultural más importante de Álamos. Una semana completa de conciertos, exposiciones y actividades culturales en honor al tenor Alfonso Ortiz Tirado.",
            category = "Cultural",
            location = "Plaza de Armas y varios recintos",
            startDate = faotStart,
            endDate = faotEnd,
            isFeatured = true,
            organizerName = "Patronato del FAOT",
            organizerContact = "info@festivalalfonso.com"
        ))

        // Evento 2: Día de los Muertos
        calendar.set(2026, Calendar.OCTOBER, 31)
        val muertosStart = calendar.time
        calendar.set(2026, Calendar.NOVEMBER, 2)
        val muertosEnd = calendar.time

        sampleEvents.add(Event(
            id = "sample2",
            title = "Celebración del Día de Muertos",
            description = "Tradición ancestral con altares, calaveras, pan de muerto y visitas al panteón. Recorridos nocturnos por el pueblo iluminado con velas.",
            category = "Religioso",
            location = "Panteón Municipal y Plaza de Armas",
            startDate = muertosStart,
            endDate = muertosEnd,
            isFeatured = true,
            organizerName = "Gobierno Municipal de Álamos"
        ))

        // Evento 3: Festival del Cine
        calendar.set(2026, Calendar.MARCH, 15)
        val cineStart = calendar.time
        calendar.set(2026, Calendar.MARCH, 20)
        val cineEnd = calendar.time

        sampleEvents.add(Event(
            id = "sample3",
            title = "Festival Internacional de Cine de Álamos",
            description = "Proyecciones de películas internacionales e independientes en locaciones únicas del pueblo. Conversatorios con directores y actores.",
            category = "Cultural",
            location = "Teatro Municipal",
            startDate = cineStart,
            endDate = cineEnd,
            isFeatured = false,
            organizerName = "Asociación de Cineastas"
        ))

        adapter.submitList(sampleEvents)
        binding.emptyStateContainer.visibility = View.GONE
        binding.eventsRecyclerView.visibility = View.VISIBLE
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
            overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out)
        } else {
            // Mostrar detalles del evento en un diálogo o nueva pantalla
            NotificationHelper.info(binding.root, "Evento: ${event.title}")
        }
    }
}
