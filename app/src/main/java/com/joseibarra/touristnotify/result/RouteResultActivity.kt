package com.joseibarra.touristnotify.result

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.joseibarra.touristnotify.R
import com.joseibarra.touristnotify.TouristSpot
import com.joseibarra.touristnotify.databinding.ActivityRouteResultBinding
import com.joseibarra.touristnotify.model.GeneratedRoute

/**
 * Pantalla post-generación de rutas. Aloja 4 pestañas vía BottomNavigationView:
 *  1. Mapa — markers numerados + polyline animada.
 *  2. Itinerario — lista cronológica enriquecida.
 *  3. Editar — drag-drop, agregar/quitar paradas, re-optimización.
 *  4. Compartir/Regenerar — presets de feedback + Google Maps URL + link interno.
 *
 * El estado de la ruta vive en esta Activity y los fragments lo leen/modifican
 * via [currentRoute] y [updateRoute].
 */
class RouteResultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRouteResultBinding

    /** Ruta actual (puede ser modificada por el editor o regenerador). */
    var currentRoute: GeneratedRoute = GeneratedRoute()
        private set

    /** Candidatos ordenados tal como los devolvió el coordinador. */
    var routeSpots: List<TouristSpot> = emptyList()
        private set

    // Fragments (creados una vez, guardados para show/hide)
    private val mapFragment by lazy { RouteMapFragment() }
    private val itineraryFragment by lazy { RouteItineraryFragment() }
    private val editorFragment by lazy { RouteEditorFragment() }
    private val regenShareFragment by lazy { RouteRegenShareFragment() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRouteResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Leer la ruta del Intent; los spots se pasan via companion (GeoPoint no es Parcelable)
        currentRoute = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_ROUTE, GeneratedRoute::class.java) ?: GeneratedRoute()
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_ROUTE) ?: GeneratedRoute()
        }
        routeSpots = pendingSpots
        pendingSpots = emptyList()

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = currentRoute.resumen.titulo.ifBlank { "Tu ruta" }

        setupBottomNav()
        if (savedInstanceState == null) {
            showFragment(mapFragment, TAG_MAP)
        }
    }

    private fun setupBottomNav() {
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_mapa       -> { showFragment(mapFragment, TAG_MAP); true }
                R.id.nav_itinerario -> { showFragment(itineraryFragment, TAG_ITINERARY); true }
                R.id.nav_editar     -> { showFragment(editorFragment, TAG_EDITOR); true }
                R.id.nav_compartir  -> { showFragment(regenShareFragment, TAG_REGEN_SHARE); true }
                else                -> false
            }
        }
    }

    private fun showFragment(fragment: Fragment, tag: String) {
        val fm = supportFragmentManager
        val tx = fm.beginTransaction()

        // Ocultar todos los fragments visibles
        listOf(TAG_MAP, TAG_ITINERARY, TAG_EDITOR, TAG_REGEN_SHARE).forEach { t ->
            fm.findFragmentByTag(t)?.let { tx.hide(it) }
        }

        val existing = fm.findFragmentByTag(tag)
        if (existing == null) {
            tx.add(binding.fragmentContainer.id, fragment, tag)
        } else {
            tx.show(existing)
        }
        tx.commit()
    }

    /**
     * Actualiza la ruta activa (llamado por editor y regenerador) y notifica
     * al itinerario y mapa para que se refresquen.
     */
    fun updateRoute(newRoute: GeneratedRoute, newSpots: List<TouristSpot> = routeSpots) {
        currentRoute = newRoute
        routeSpots = newSpots
        supportActionBar?.title = currentRoute.resumen.titulo.ifBlank { "Tu ruta" }

        // Notificar fragments que ya existen
        (supportFragmentManager.findFragmentByTag(TAG_MAP) as? RouteMapFragment)?.onRouteUpdated()
        (supportFragmentManager.findFragmentByTag(TAG_ITINERARY) as? RouteItineraryFragment)?.onRouteUpdated()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    companion object {
        const val EXTRA_ROUTE = "extra_route"

        private const val TAG_MAP          = "tag_map"
        private const val TAG_ITINERARY    = "tag_itinerary"
        private const val TAG_EDITOR       = "tag_editor"
        private const val TAG_REGEN_SHARE  = "tag_regen_share"

        /** Spots are not Parcelable (GeoPoint), so we hold them in memory until onCreate. */
        private var pendingSpots: List<TouristSpot> = emptyList()

        fun newIntent(
            context: Context,
            route: GeneratedRoute,
            spots: List<TouristSpot>
        ): Intent {
            pendingSpots = spots
            return Intent(context, RouteResultActivity::class.java).apply {
                putExtra(EXTRA_ROUTE, route)
            }
        }
    }
}
