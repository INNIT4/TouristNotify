package com.joseibarra.touristnotify

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.Toast
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
        routeAdapter = RouteAdapter(emptyList()) { route ->
            val intent = Intent(this, MapsActivity::class.java).apply {
                putStringArrayListExtra("ROUTE_PLACES_IDS", ArrayList(route.pdis_incluidos))
            }
            startActivity(intent)
        }
        binding.routesRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.routesRecyclerView.adapter = routeAdapter
    }

    private fun loadSavedRoutes() {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            Toast.makeText(this, "No se pudo obtener el usuario", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
}