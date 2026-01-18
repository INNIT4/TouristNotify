package com.joseibarra.touristnotify

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.FirebaseFirestore
import com.joseibarra.touristnotify.databinding.ActivityGlobalSearchBinding

class GlobalSearchActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGlobalSearchBinding
    private val db = FirebaseFirestore.getInstance()
    private val searchResults = mutableListOf<SearchResult>()
    private lateinit var adapter: GlobalSearchAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGlobalSearchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupSearch()

        // Aplicar animación de entrada
        overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out)
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.fade_in, R.anim.slide_out_right)
    }

    private fun setupRecyclerView() {
        adapter = GlobalSearchAdapter(searchResults) { result ->
            when (result.type) {
                SearchResultType.PLACE -> {
                    val intent = Intent(this, PlaceDetailsActivity::class.java).apply {
                        putExtra("PLACE_ID", result.id)
                        putExtra("PLACE_NAME", result.title)
                    }
                    startActivity(intent)
                    overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out)
                }
                SearchResultType.EVENT -> {
                    val intent = Intent(this, EventsActivity::class.java)
                    startActivity(intent)
                    overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out)
                }
                SearchResultType.BLOG -> {
                    val intent = Intent(this, BlogActivity::class.java)
                    startActivity(intent)
                    overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out)
                }
                SearchResultType.ROUTE -> {
                    val intent = Intent(this, MyRoutesActivity::class.java)
                    startActivity(intent)
                    overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out)
                }
            }
        }
        binding.searchResultsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.searchResultsRecyclerView.adapter = adapter
    }

    private fun setupSearch() {
        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s.toString().trim()
                if (query.length >= 2) {
                    performSearch(query)
                } else {
                    searchResults.clear()
                    adapter.notifyDataSetChanged()
                    binding.emptyState.visibility = View.VISIBLE
                    binding.searchResultsRecyclerView.visibility = View.GONE
                }
            }
        })

        binding.clearButton.setOnClickListener {
            binding.searchEditText.text?.clear()
        }
    }

    private fun performSearch(query: String) {
        binding.progressBar.visibility = View.VISIBLE
        searchResults.clear()

        // Buscar en lugares
        db.collection("lugares")
            .get()
            .addOnSuccessListener { documents ->
                for (doc in documents) {
                    val nombre = doc.getString("nombre") ?: ""
                    val descripcion = doc.getString("descripcion") ?: ""
                    val categoria = doc.getString("categoria") ?: ""

                    if (nombre.contains(query, true) || descripcion.contains(query, true) || categoria.contains(query, true)) {
                        searchResults.add(SearchResult(
                            id = doc.id,
                            title = nombre,
                            subtitle = "📍 $categoria - $descripcion",
                            type = SearchResultType.PLACE
                        ))
                    }
                }
                checkSearchComplete()
            }

        // Buscar en blog
        db.collection("blog_posts")
            .get()
            .addOnSuccessListener { documents ->
                for (doc in documents) {
                    val title = doc.getString("title") ?: ""
                    val content = doc.getString("content") ?: ""
                    val category = doc.getString("category") ?: ""

                    if (title.contains(query, true) || content.contains(query, true)) {
                        searchResults.add(SearchResult(
                            id = doc.id,
                            title = title,
                            subtitle = "📝 $category",
                            type = SearchResultType.BLOG
                        ))
                    }
                }
                checkSearchComplete()
            }

        // Buscar en eventos
        db.collection("eventos")
            .get()
            .addOnSuccessListener { documents ->
                for (doc in documents) {
                    val title = doc.getString("title") ?: ""
                    val description = doc.getString("description") ?: ""

                    if (title.contains(query, true) || description.contains(query, true)) {
                        searchResults.add(SearchResult(
                            id = doc.id,
                            title = title,
                            subtitle = "🎉 Evento",
                            type = SearchResultType.EVENT
                        ))
                    }
                }
                checkSearchComplete()
            }
    }

    private fun checkSearchComplete() {
        binding.progressBar.visibility = View.GONE

        if (searchResults.isEmpty()) {
            binding.emptyState.visibility = View.VISIBLE
            binding.searchResultsRecyclerView.visibility = View.GONE
        } else {
            binding.emptyState.visibility = View.GONE
            binding.searchResultsRecyclerView.visibility = View.VISIBLE
            // Ordenar por tipo y luego alfabéticamente
            searchResults.sortWith(compareBy({ it.type }, { it.title }))
            adapter.notifyDataSetChanged()
        }
    }
}

data class SearchResult(
    val id: String,
    val title: String,
    val subtitle: String,
    val type: SearchResultType
)

enum class SearchResultType {
    PLACE, EVENT, BLOG, ROUTE
}
