package com.joseibarra.trazago

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.appcompat.widget.SearchView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.joseibarra.trazago.databinding.ActivityEventsBinding
import com.joseibarra.trazago.ui.BaseActivity
import com.joseibarra.trazago.ui.MotionHelper
import java.util.Calendar
import java.util.Date

class EventsActivity : BaseActivity() {

    private lateinit var binding: ActivityEventsBinding
    private lateinit var db: FirebaseFirestore
    private lateinit var adapter: EventsAdapter
    private val allEvents = mutableListOf<Event>()
    private val handler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null
    private var selectedTab = 3 // All
    private val selectedCategories = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEventsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsets(binding.headerBackground, applyTop = true, applyBottom = false)

        db = FirebaseFirestore.getInstance()

        setupRecyclerView()
        setupSearch()
        setupTabs()
        loadEvents()
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.fade_in, R.anim.slide_out_right)
    }

    private fun setupRecyclerView() {
        adapter = EventsAdapter { event -> openEventDetails(event) }
        binding.eventsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.eventsRecyclerView.adapter = adapter
        binding.eventsRecyclerView.itemAnimator = MotionHelper.fadeSlideItemAnimator()
    }

    private fun setupSearch() {
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = false
            override fun onQueryTextChange(newText: String?): Boolean {
                searchRunnable?.let { handler.removeCallbacks(it) }
                searchRunnable = Runnable { applyFilters() }
                handler.postDelayed(searchRunnable!!, 400)
                return true
            }
        })
    }

    private fun setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                selectedTab = tab?.position ?: 3
                applyFilters()
            }
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
        })
    }

    private fun setupCategoryChips(categories: Set<String>) {
        binding.categoryChipGroup.removeAllViews()
        categories.sorted().forEach { cat ->
            val chip = Chip(this).apply {
                text = "${CategoryUtils.getCategoryEmoji(cat)} $cat"
                isCheckable = true
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) selectedCategories.add(cat) else selectedCategories.remove(cat)
                    applyFilters()
                }
            }
            binding.categoryChipGroup.addView(chip)
        }
    }

    private fun loadEvents() {
        binding.progressBar.isVisible = true
        binding.emptyStateContainer.isVisible = false

        db.collection(FirestoreCollections.EVENTS)
            .orderBy("isFeatured", Query.Direction.DESCENDING)
            .orderBy("startDate", Query.Direction.ASCENDING)
            .limit(200)
            .get()
            .addOnSuccessListener { documents ->
                binding.progressBar.isVisible = false

                val currentDate = Date()
                allEvents.clear()

                if (documents.isEmpty) {
                    loadSampleEvents()
                    return@addOnSuccessListener
                }

                for (doc in documents) {
                    try {
                        val event = doc.toObject(Event::class.java).copy(id = doc.id)
                        if (event.endDate == null || event.endDate >= currentDate) {
                            allEvents.add(event)
                        }
                    } catch (_: Exception) {}
                }

                setupCategoryChipsFromEvents()
                applyFilters()

                if (allEvents.isEmpty()) showEmptyState(false)
            }
            .addOnFailureListener {
                binding.progressBar.isVisible = false
                loadSampleEvents()
            }
    }

    private fun loadSampleEvents() {
        allEvents.clear()
        val calendar = Calendar.getInstance()

        calendar.set(2026, Calendar.JANUARY, 20)
        val faotStart = calendar.time
        calendar.set(2026, Calendar.JANUARY, 26)
        val faotEnd = calendar.time

        allEvents.add(Event(
            id = "sample1",
            title = "Festival Alfonso Ortiz Tirado (FAOT)",
            description = "El evento cultural más importante de Álamos. Una semana completa de conciertos, exposiciones y actividades culturales en honor al tenor Alfonso Ortiz Tirado.",
            category = "Cultural",
            location = "Plaza de Armas y varios recintos",
            startDate = faotStart,
            endDate = faotEnd,
            isFeatured = true,
            organizerName = "Patronato del FAOT",
            organizerContact = "info@festivalalfonso.com",
            imageUrl = "https://firebasestorage.googleapis.com/v0/b/touristnotify-db.firebasestorage.app/o/events%2Ffaot.jpg?alt=media",
            latitude = 27.0247,
            longitude = -108.9337,
            priceInfo = "\$200"
        ))

        calendar.set(2026, Calendar.OCTOBER, 31)
        allEvents.add(Event(
            id = "sample2",
            title = "Celebración del Día de Muertos",
            description = "Tradición ancestral con altares, calaveras, pan de muerto y visitas al panteón.",
            category = "Religioso",
            location = "Panteón Municipal y Plaza de Armas",
            startDate = calendar.time,
            endDate = calendar.apply { set(2026, Calendar.NOVEMBER, 2) }.time,
            isFeatured = true,
            organizerName = "Gobierno Municipal de Álamos",
            latitude = 27.0235,
            longitude = -108.9380,
            priceInfo = "",
            tags = listOf("tradición", "cultura")
        ))

        calendar.set(2026, Calendar.MARCH, 15)
        allEvents.add(Event(
            id = "sample3",
            title = "Festival Internacional de Cine de Álamos",
            description = "Proyecciones de películas internacionales en locaciones únicas del pueblo.",
            category = "Cultural",
            location = "Teatro Municipal",
            startDate = calendar.time,
            endDate = calendar.apply { set(2026, Calendar.MARCH, 20) }.time,
            isFeatured = false,
            organizerName = "Asociación de Cineastas",
            tags = listOf("cine", "internacional"),
            priceInfo = "\$150"
        ))

        setupCategoryChipsFromEvents()
        applyFilters()
        binding.emptyStateContainer.isVisible = false
        binding.eventsRecyclerView.isVisible = true
    }

    private fun setupCategoryChipsFromEvents() {
        val categories = allEvents.map { it.category }.filter { it.isNotBlank() }.toSet()
        setupCategoryChips(categories)
    }

    private fun applyFilters() {
        val query = binding.searchView.query?.toString()?.trim()?.lowercase() ?: ""
        val now = Date()
        val weekEnd = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 7) }.time

        val filtered = allEvents.filter { event ->
            // Time tab filter
            val matchesTab = when (selectedTab) {
                0 -> event.startDate?.let { isSameDay(it, now) } == true
                1 -> event.startDate?.let { it.after(now) && it.before(weekEnd) } == true
                2 -> event.endDate?.let { it >= now } ?: (event.startDate?.let { it >= now } ?: true)
                else -> true
            }

            // Category filter
            val matchesCategory = selectedCategories.isEmpty() || selectedCategories.contains(event.category)

            // Search filter
            val matchesSearch = query.isBlank() ||
                event.title.lowercase().contains(query) ||
                event.description.lowercase().contains(query) ||
                event.category.lowercase().contains(query) ||
                event.location.lowercase().contains(query) ||
                event.tags.any { it.lowercase().contains(query) }

            matchesTab && matchesCategory && matchesSearch
        }

        adapter.submitList(filtered)

        if (filtered.isEmpty() && allEvents.isNotEmpty()) {
            showEmptyState(true)
        } else if (filtered.isEmpty()) {
            showEmptyState(false)
        } else {
            showContent()
        }
    }

    private fun isSameDay(d1: Date, d2: Date): Boolean {
        val c1 = Calendar.getInstance().apply { time = d1 }
        val c2 = Calendar.getInstance().apply { time = d2 }
        return c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR) &&
            c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR)
    }

    private fun showEmptyState(isSearchResult: Boolean) {
        binding.emptyStateContainer.isVisible = true
        binding.eventsRecyclerView.isVisible = false
        if (isSearchResult) {
            binding.emptyStateTitle.text = getString(R.string.search_no_results_title)
            binding.emptyStateSubtitle.text = getString(R.string.event_no_search_results)
        } else {
            binding.emptyStateTitle.text = getString(R.string.empty_events_title)
            binding.emptyStateSubtitle.text = getString(R.string.empty_events_subtitle)
        }
    }

    private fun showContent() {
        binding.emptyStateContainer.isVisible = false
        binding.eventsRecyclerView.isVisible = true
    }

    private fun openEventDetails(event: Event) {
        val intent = Intent(this, EventDetailsActivity::class.java).apply {
            putExtra(EventDetailsActivity.EXTRA_EVENT, event)
        }
        startActivity(intent)
        overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out)
    }
}
