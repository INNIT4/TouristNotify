package com.joseibarra.touristnotify.admin

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.firestore.FirebaseFirestore
import com.joseibarra.touristnotify.FirestoreCollections
import com.joseibarra.touristnotify.R
import com.joseibarra.touristnotify.TouristSpot
import com.joseibarra.touristnotify.databinding.ActivityAdminPlaceEditorBinding
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Editor completo para un TouristSpot.
 * TabLayout + ViewPager2 con 9 pestañas:
 *   0 Básico | 1 Multimedia | 2 Horarios | 3 Precio/Duración |
 *   4 Audiencia | 5 Accesibilidad | 6 Servicios | 7 Restaurante | 8 Tags/IA
 */
class AdminPlaceEditorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminPlaceEditorBinding

    var currentSpot: TouristSpot = TouristSpot()
        private set

    private val tabLabels = arrayOf(
        "Básico", "Multimedia", "Horarios", "Precio/Duración",
        "Audiencia", "Accesibilidad", "Servicios", "Restaurante", "Tags/IA"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminPlaceEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val placeId = intent.getStringExtra(EXTRA_PLACE_ID) ?: run { finish(); return }
        loadSpot(placeId)
    }

    private fun loadSpot(placeId: String) {
        lifecycleScope.launch {
            try {
                val doc = FirebaseFirestore.getInstance()
                    .collection(FirestoreCollections.PLACES)
                    .document(placeId)
                    .get()
                    .await()
                currentSpot = doc.toObject(TouristSpot::class.java)?.copy(id = doc.id) ?: TouristSpot()
                supportActionBar?.title = currentSpot.nombre.ifBlank { "Nuevo lugar" }
                setupViewPager()
                binding.fabSave.setOnClickListener { collectAndSave() }
            } catch (e: Exception) {
                Snackbar.make(binding.root, "Error al cargar: ${e.message}", Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun setupViewPager() {
        binding.viewPager.adapter = EditorPagerAdapter(this)
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, pos ->
            tab.text = tabLabels[pos]
        }.attach()
    }

    /** Recoger datos de todos los fragments y guardar en Firestore. */
    private fun collectAndSave() {
        val fm = supportFragmentManager
        val updates = mutableMapOf<String, Any?>()
        (fm.findFragmentByTag("f0") as? EditorTabBasicFragment)?.collectInto(updates)
        (fm.findFragmentByTag("f1") as? EditorTabMultimediaFragment)?.collectInto(updates)
        (fm.findFragmentByTag("f2") as? EditorTabHorariosFragment)?.collectInto(updates)
        (fm.findFragmentByTag("f3") as? EditorTabPrecioFragment)?.collectInto(updates)
        (fm.findFragmentByTag("f4") as? EditorTabAudienciaFragment)?.collectInto(updates)
        (fm.findFragmentByTag("f5") as? EditorTabAccesibilidadFragment)?.collectInto(updates)
        (fm.findFragmentByTag("f6") as? EditorTabServiciosFragment)?.collectInto(updates)
        (fm.findFragmentByTag("f7") as? EditorTabRestauranteFragment)?.collectInto(updates)
        (fm.findFragmentByTag("f8") as? EditorTabTagsAiFragment)?.collectInto(updates)

        if (updates.isEmpty()) {
            Snackbar.make(binding.root, "Nada que guardar", Snackbar.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                FirebaseFirestore.getInstance()
                    .collection(FirestoreCollections.PLACES)
                    .document(currentSpot.id)
                    .update(updates.filterValues { it != null } as Map<String, Any>)
                    .await()
                Snackbar.make(binding.root, "Guardado", Snackbar.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Snackbar.make(binding.root, "Error: ${e.message}", Snackbar.LENGTH_LONG).show()
            }
        }
    }

    fun onSpotEnriched(updated: TouristSpot) {
        currentSpot = updated
        Snackbar.make(binding.root, "Enriquecimiento completado", Snackbar.LENGTH_SHORT).show()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { onBackPressedDispatcher.onBackPressed(); return true }
        return super.onOptionsItemSelected(item)
    }

    private inner class EditorPagerAdapter(activity: AppCompatActivity) :
        FragmentStateAdapter(activity) {

        override fun getItemCount() = tabLabels.size

        override fun createFragment(position: Int): Fragment = when (position) {
            0 -> EditorTabBasicFragment()
            1 -> EditorTabMultimediaFragment()
            2 -> EditorTabHorariosFragment()
            3 -> EditorTabPrecioFragment()
            4 -> EditorTabAudienciaFragment()
            5 -> EditorTabAccesibilidadFragment()
            6 -> EditorTabServiciosFragment()
            7 -> EditorTabRestauranteFragment()
            8 -> EditorTabTagsAiFragment()
            else -> EditorTabPlaceholderFragment.newInstance(tabLabels[position])
        }
    }

    companion object {
        const val EXTRA_PLACE_ID = "extra_place_id"

        fun newIntent(context: Context, placeId: String) =
            Intent(context, AdminPlaceEditorActivity::class.java).apply {
                putExtra(EXTRA_PLACE_ID, placeId)
            }
    }
}
