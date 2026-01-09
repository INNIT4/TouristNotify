package com.joseibarra.touristnotify

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.joseibarra.touristnotify.databinding.ActivityTopPlacesBinding

class TopPlacesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTopPlacesBinding
    private lateinit var db: FirebaseFirestore
    private lateinit var adapter: TopPlacesAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTopPlacesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = FirebaseFirestore.getInstance()

        setupRecyclerView()
        loadTopPlaces()
    }

    private fun setupRecyclerView() {
        adapter = TopPlacesAdapter(emptyList()) { place ->
            val intent = Intent(this, PlaceDetailsActivity::class.java).apply {
                putExtra("PLACE_ID", place.id)
                putExtra("PLACE_NAME", place.nombre)
                putExtra("PLACE_CATEGORY", place.categoria)
                putExtra("PLACE_DESCRIPTION", place.descripcion)
                putExtra("GOOGLE_PLACE_ID", place.googlePlaceId)
                place.ubicacion?.let {
                    putExtra("PLACE_LATITUDE", it.latitude)
                    putExtra("PLACE_LONGITUDE", it.longitude)
                }
            }
            startActivity(intent)
        }

        binding.topPlacesRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.topPlacesRecyclerView.adapter = adapter
    }

    private fun loadTopPlaces() {
        binding.progressBar.visibility = View.VISIBLE
        binding.topPlacesRecyclerView.visibility = View.GONE
        binding.emptyTextView.visibility = View.GONE

        db.collection("lugares")
            .orderBy("visitCount", Query.Direction.DESCENDING)
            .limit(10)
            .get()
            .addOnSuccessListener { documents ->
                binding.progressBar.visibility = View.GONE

                if (documents.isEmpty) {
                    binding.emptyTextView.visibility = View.VISIBLE
                    binding.topPlacesRecyclerView.visibility = View.GONE
                } else {
                    binding.topPlacesRecyclerView.visibility = View.VISIBLE
                    binding.emptyTextView.visibility = View.GONE

                    val places = documents.mapNotNull { doc ->
                        doc.toObject(TouristSpot::class.java).copy(id = doc.id)
                    }
                    adapter.updatePlaces(places)
                }
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                binding.emptyTextView.visibility = View.VISIBLE
                binding.topPlacesRecyclerView.visibility = View.GONE
                binding.emptyTextView.text = "Error al cargar los lugares populares"
                NotificationHelper.error(binding.root, "Error: ${e.message}")
            }
    }
}
