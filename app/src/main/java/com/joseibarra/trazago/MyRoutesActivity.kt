package com.joseibarra.trazago

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.joseibarra.trazago.databinding.ActivityMyRoutesBinding
import com.joseibarra.trazago.ui.BaseActivity
import com.joseibarra.trazago.ui.EmptyStateHelper
import com.joseibarra.trazago.ui.MotionHelper

class MyRoutesActivity : BaseActivity() {

    private lateinit var binding: ActivityMyRoutesBinding
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var routeAdapter: RouteAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Verificar autenticación antes de acceder a rutas guardadas
        if (!AuthManager.requireAuth(this, AuthManager.AuthRequired.MY_ROUTES) {
                initializeActivity()
            }) {
            finish()
            return
        }
    }

    private fun initializeActivity() {
        binding = ActivityMyRoutesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsets(binding.routesHeader, applyTop = true, applyBottom = false)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        setupRecyclerView()
        loadSavedRoutes()
    }

    private fun showEmptyState() {
        binding.routesRecyclerView.visibility = View.GONE
        EmptyStateHelper.show(
            root = binding.emptyState.root,
            icon = R.drawable.ic_empty_state_routes,
            title = R.string.empty_routes_title,
            subtitle = R.string.empty_routes_subtitle,
            actionLabel = R.string.empty_routes_action,
            onAction = {
                startActivity(com.joseibarra.trazago.wizard.RouteWizardActivity.newIntent(this))
                overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out)
            }
        )
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.fade_in, R.anim.slide_out_right)
    }

    private fun setupRecyclerView() {
        routeAdapter = RouteAdapter(
            onItemClicked = { route ->
                val intent = Intent(this, MapsActivity::class.java).apply {
                    putStringArrayListExtra("ROUTE_PLACES_IDS", ArrayList(route.pdis_incluidos))
                }
                startActivity(intent)
                overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
            },
            onDeleteClicked = { route, position ->
                showDeleteConfirmation(route, position)
            }
        )
        binding.routesRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.routesRecyclerView.adapter = routeAdapter
        binding.routesRecyclerView.itemAnimator = MotionHelper.fadeSlideItemAnimator()
    }

    private fun showDeleteConfirmation(route: Route, position: Int) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_route_title))
            .setMessage(getString(R.string.delete_route_message, route.nombre_ruta))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                deleteRoute(route, position)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun deleteRoute(route: Route, position: Int) {
        // SEGURIDAD: Validar que el usuario actual sea el dueño de la ruta
        val currentUserId = auth.currentUser?.uid
        if (currentUserId == null) {
            NotificationHelper.error(binding.root, getString(R.string.login_required_delete))
            return
        }

        if (route.id_usuario != currentUserId) {
            NotificationHelper.error(binding.root, getString(R.string.no_permission_delete))
            return
        }

        db.collection("rutas").document(route.id_ruta)
            .delete()
            .addOnSuccessListener {
                routeAdapter.removeRoute(position)
                NotificationHelper.success(binding.root, getString(R.string.route_deleted))

                // Verificar si no quedan más rutas
                if (routeAdapter.itemCount == 0) {
                    showEmptyState()
                }
            }
            .addOnFailureListener { e ->
                NotificationHelper.error(binding.root, getString(R.string.route_delete_error))
            }
    }

    private fun loadSavedRoutes() {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            NotificationHelper.error(binding.root, getString(R.string.user_not_found))
            showEmptyState()
            return
        }

        db.collection("rutas")
            .whereEqualTo("id_usuario", userId)
            .orderBy("fecha_creacion", Query.Direction.DESCENDING)
            .limit(100)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    showEmptyState()
                } else {
                    EmptyStateHelper.hide(binding.emptyState.root)
                    binding.routesRecyclerView.visibility = View.VISIBLE
                    val routes = documents.toObjects(Route::class.java)
                    routeAdapter.updateRoutes(routes)
                }
            }
            .addOnFailureListener { e ->
                showEmptyState()
                NotificationHelper.error(binding.root, "Error: ${e.message}")
            }
    }
}