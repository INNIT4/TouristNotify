package com.joseibarra.trazago

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.FirebaseFirestore
import com.joseibarra.trazago.databinding.ActivityGlobalSearchBinding
import com.joseibarra.trazago.ui.BaseActivity
import com.joseibarra.trazago.ui.EmptyStateHelper
import com.joseibarra.trazago.ui.MotionHelper
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class GlobalSearchActivity : BaseActivity() {

    private lateinit var binding: ActivityGlobalSearchBinding
    private val db = FirebaseFirestore.getInstance()
    private val searchResults = mutableListOf<SearchResult>()
    private lateinit var adapter: GlobalSearchAdapter
    private val searchHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var pendingSearchRunnable: Runnable? = null

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 400L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGlobalSearchBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsets(binding.headerRow, applyTop = true, applyBottom = false)

        setupRecyclerView()
        setupSearch()
        showInitialEmptyState()

        binding.backButton.setOnClickListener { finish() }

        // Auto-focus + abrir teclado al entrar a la pantalla
        binding.searchEditText.requestFocus()
        binding.searchEditText.post {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(binding.searchEditText, InputMethodManager.SHOW_IMPLICIT)
        }
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
                        putExtra(PlaceSummary.EXTRA_KEY, PlaceSummary(id = result.id, nombre = result.title))
                    }
                    startActivity(intent)
                    overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out)
                }
                SearchResultType.EVENT -> {
                    startActivity(Intent(this, EventsActivity::class.java))
                    overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out)
                }
                SearchResultType.BLOG -> {
                    val intent = Intent(this, BlogPostDetailActivity::class.java).apply {
                        putExtra("POST_ID", result.id)
                        putExtra("POST_TITLE", result.title)
                    }
                    startActivity(intent)
                    overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out)
                }
                SearchResultType.ROUTE -> {
                    startActivity(Intent(this, MyRoutesActivity::class.java))
                    overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out)
                }
            }
        }
        binding.searchResultsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.searchResultsRecyclerView.adapter = adapter
        binding.searchResultsRecyclerView.itemAnimator = MotionHelper.fadeSlideItemAnimator()
    }

    private fun setupSearch() {
        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                val query = s.toString().trim()
                binding.clearButton.isVisible = query.isNotEmpty()

                pendingSearchRunnable?.let { searchHandler.removeCallbacks(it) }

                if (query.length >= 2) {
                    pendingSearchRunnable = Runnable { performSearch(query) }
                    searchHandler.postDelayed(pendingSearchRunnable!!, SEARCH_DEBOUNCE_MS)
                } else {
                    searchResults.clear()
                    adapter.notifyDataSetChanged()
                    showInitialEmptyState()
                }
            }
        })

        binding.clearButton.setOnClickListener {
            binding.searchEditText.text?.clear()
        }
    }

    private fun showInitialEmptyState() {
        binding.searchResultsRecyclerView.visibility = View.GONE
        binding.skeletonContainer.visibility = View.GONE
        EmptyStateHelper.show(
            root = binding.emptyState.root,
            icon = R.drawable.ic_empty_state_search,
            title = R.string.search_initial_title,
            subtitle = R.string.search_initial_subtitle
        )
    }

    private fun showNoResultsEmptyState() {
        binding.searchResultsRecyclerView.visibility = View.GONE
        binding.skeletonContainer.visibility = View.GONE
        EmptyStateHelper.show(
            root = binding.emptyState.root,
            icon = R.drawable.ic_empty_state_search,
            title = R.string.search_no_results_title,
            subtitle = R.string.search_no_results_subtitle
        )
    }

    private fun showSkeleton() {
        EmptyStateHelper.hide(binding.emptyState.root)
        binding.searchResultsRecyclerView.visibility = View.GONE
        binding.skeletonContainer.visibility = View.VISIBLE
    }

    private fun showResults() {
        EmptyStateHelper.hide(binding.emptyState.root)
        binding.skeletonContainer.visibility = View.GONE
        binding.searchResultsRecyclerView.visibility = View.VISIBLE
    }

    private fun performSearch(query: String) {
        showSkeleton()
        searchResults.clear()

        lifecycleScope.launch {
            val placesDeferred = async {
                runCatching {
                    db.collection(FirestoreCollections.PLACES).limit(100).get().await()
                }.getOrNull()
            }
            placesDeferred.await()?.forEach { doc ->
                val nombre = doc.getString("nombre") ?: ""
                val categoria = doc.getString("categoria") ?: ""
                val descripcion = doc.getString("descripcion") ?: ""
                if (nombre.contains(query, true) || descripcion.contains(query, true) || categoria.contains(query, true)) {
                    searchResults.add(SearchResult(
                        id = doc.id,
                        title = nombre,
                        subtitle = "📍 $categoria - $descripcion",
                        type = SearchResultType.PLACE
                    ))
                }
            }

            val blogDeferred = async {
                runCatching {
                    db.collection(FirestoreCollections.BLOG_POSTS).limit(50).get().await()
                }.getOrNull()
            }
            val eventsDeferred = async {
                runCatching {
                    db.collection(FirestoreCollections.EVENTS).limit(50).get().await()
                }.getOrNull()
            }

            blogDeferred.await()?.forEach { doc ->
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

            eventsDeferred.await()?.forEach { doc ->
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

            if (searchResults.isEmpty()) {
                showNoResultsEmptyState()
            } else {
                showResults()
                searchResults.sortWith(compareBy({ it.type }, { it.title }))
                adapter.notifyDataSetChanged()
            }
        }
    }

    override fun onDestroy() {
        pendingSearchRunnable?.let { searchHandler.removeCallbacks(it) }
        super.onDestroy()
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
