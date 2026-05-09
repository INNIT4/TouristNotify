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
        adapter = TopPlacesAdapter { place ->
            val intent = Intent(this, PlaceDetailsActivity::class.java).apply {
                putExtra(PlaceSummary.EXTRA_KEY, PlaceSummary.fromTouristSpot(place))
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
                binding.emptyTextView.text = getString(R.string.top_places_load_error)
                NotificationHelper.error(binding.root, "Error: ${e.message}")
            }
    }
}
