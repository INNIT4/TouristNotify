package com.joseibarra.trazago

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.joseibarra.trazago.databinding.ActivityThemedRoutesBinding
import com.joseibarra.trazago.ui.BaseActivity
import com.joseibarra.trazago.ui.MotionHelper
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ThemedRoutesActivity : BaseActivity() {

    private lateinit var binding: ActivityThemedRoutesBinding
    private lateinit var db: FirebaseFirestore
    private lateinit var adapter: ThemedRoutesAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityThemedRoutesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsets(binding.headerBackground, applyTop = true, applyBottom = false)

        db = FirebaseFirestore.getInstance()

        setupRecyclerView()
        loadRoutes()
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.fade_in, R.anim.slide_out_right)
    }

    private fun setupRecyclerView() {
        adapter = ThemedRoutesAdapter { route -> openRoute(route) }
        binding.themedRoutesRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.themedRoutesRecyclerView.adapter = adapter
        binding.themedRoutesRecyclerView.itemAnimator = MotionHelper.fadeSlideItemAnimator()
    }

    private fun loadRoutes() {
        binding.progressBar.visibility = View.VISIBLE
        binding.emptyStateContainer.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val snap = db.collection(FirestoreCollections.THEMED_ROUTES)
                    .orderBy("isFeatured", Query.Direction.DESCENDING)
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .get()
                    .await()

                val routes = snap.documents.mapNotNull { doc ->
                    try {
                        doc.toObject(ThemedRoute::class.java)?.copy(id = doc.id)
                    } catch (_: Exception) { null }
                }

                binding.progressBar.visibility = View.GONE

                if (routes.isEmpty()) {
                    showEmptyState(false)
                } else {
                    binding.themedRoutesRecyclerView.visibility = View.VISIBLE
                    adapter.submitList(routes)
                }
            } catch (_: Exception) {
                binding.progressBar.visibility = View.GONE
                showEmptyState(true)
            }
        }
    }

    private fun showEmptyState(offline: Boolean) {
        binding.emptyStateContainer.visibility = View.VISIBLE
        binding.themedRoutesRecyclerView.visibility = View.GONE
        binding.emptyStateTitle.text = getString(
            if (offline) R.string.themed_routes_offline_title
            else R.string.themed_routes_empty_title
        )
        if (offline) {
            binding.emptyStateSubtitle.visibility = View.GONE
        } else {
            binding.emptyStateSubtitle.visibility = View.VISIBLE
            binding.emptyStateSubtitle.text = getString(R.string.themed_routes_empty_subtitle)
        }
        binding.emptyStateRetry.visibility = if (offline) View.VISIBLE else View.GONE
        binding.emptyStateRetry.setOnClickListener { loadRoutes() }
    }

    private fun openRoute(route: ThemedRoute) {
        if (route.placeIds.isNotEmpty()) {
            val intent = Intent(this, MapsActivity::class.java).apply {
                putStringArrayListExtra("PLACE_IDS", ArrayList(route.placeIds))
                putExtra("ROUTE_NAME", route.name)
            }
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out)
        } else {
            AlertDialog.Builder(this)
                .setTitle(route.name)
                .setMessage(route.description)
                .setPositiveButton(getString(R.string.close)) { dialog, _ -> dialog.dismiss() }
                .show()
        }
    }
}
