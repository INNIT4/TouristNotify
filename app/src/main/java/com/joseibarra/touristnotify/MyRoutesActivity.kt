package com.joseibarra.touristnotify

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.joseibarra.touristnotify.databinding.ActivityMyRoutesBinding

class MyRoutesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMyRoutesBinding
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var routeAdapter: RouteAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMyRoutesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        setupRecyclerView()
        loadSavedRoutes()
    }

    private fun setupRecyclerView() {
        routeAdapter = RouteAdapter(
            routes = emptyList(),
            onItemClicked = { route ->
                val intent = Intent(this, MapsActivity::class.java).apply {
                    putStringArrayListExtra("ROUTE_PLACES_IDS", ArrayList(route.pdis_incluidos))
                }
                startActivity(intent)
            },
            onDeleteClicked = { route, position ->
                showDeleteConfirmation(route, position)
            }
        )
        binding.routesRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.routesRecyclerView.adapter = routeAdapter
    }

    private fun showDeleteConfirmation(route: Route, position: Int) {
        AlertDialog.Builder(this)
            .setTitle("Eliminar ruta")
            .setMessage("¿Estás seguro de que deseas eliminar '${route.nombre_ruta}'?")
            .setPositiveButton("Eliminar") { _, _ ->
                deleteRoute(route, position)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun deleteRoute(route: Route, position: Int) {
        // SEGURIDAD: Validar que el usuario actual sea el dueño de la ruta
        val currentUserId = auth.currentUser?.uid
        if (currentUserId == null) {
            NotificationHelper.error(binding.root, "Debes iniciar sesión para eliminar rutas")
            return
        }

        if (route.id_usuario != currentUserId) {
            NotificationHelper.error(binding.root, "No tienes permiso para eliminar esta ruta")
            return
        }

        db.collection("rutas").document(route.id_ruta)
            .delete()
            .addOnSuccessListener {
                routeAdapter.removeRoute(position)
                NotificationHelper.success(binding.root, "Ruta eliminada exitosamente")

                // Verificar si no quedan más rutas
                if (routeAdapter.itemCount == 0) {
                    binding.routesRecyclerView.visibility = View.GONE
                    binding.noRoutesTextView.visibility = View.VISIBLE
                }
            }
            .addOnFailureListener { e ->
                NotificationHelper.error(binding.root, "Error al eliminar la ruta")
            }
    }

    private fun loadSavedRoutes() {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            NotificationHelper.error(binding.root, "No se pudo obtener el usuario")
            binding.noRoutesTextView.visibility = View.VISIBLE
            binding.routesRecyclerView.visibility = View.GONE
            return
        }

        db.collection("rutas")
            .whereEqualTo("id_usuario", userId)
            .orderBy("fecha_creacion", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    binding.routesRecyclerView.visibility = View.GONE
                    binding.noRoutesTextView.visibility = View.VISIBLE
                } else {
                    binding.routesRecyclerView.visibility = View.VISIBLE
                    binding.noRoutesTextView.visibility = View.GONE
                    val routes = documents.toObjects(Route::class.java)
                    routeAdapter.updateRoutes(routes)
                }
            }
            .addOnFailureListener { e ->
                binding.routesRecyclerView.visibility = View.GONE
                binding.noRoutesTextView.visibility = View.VISIBLE
                binding.noRoutesTextView.text = "Error al cargar las rutas"
                NotificationHelper.error(binding.root, "Error: ${e.message}")
            }
    }
}