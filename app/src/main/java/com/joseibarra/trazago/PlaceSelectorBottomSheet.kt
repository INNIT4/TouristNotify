package com.joseibarra.trazago

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.setFragmentResult
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.firestore.FirebaseFirestore
import com.joseibarra.trazago.databinding.BottomSheetPlaceSelectorBinding
import com.joseibarra.trazago.databinding.ItemPlaceSelectorBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class PlaceSelectorBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetPlaceSelectorBinding? = null
    private val binding get() = _binding!!

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private val db = FirebaseFirestore.getInstance()
    private lateinit var adapter: PlaceAdapter
    private var allPlaces: List<PlaceItem> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetPlaceSelectorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = PlaceAdapter { place ->
            setFragmentResult(RESULT_KEY, bundleOf(
                KEY_PLACE_ID to place.id,
                KEY_PLACE_NAME to place.name
            ))
            dismiss()
        }

        binding.placesRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.placesRecycler.adapter = adapter

        binding.searchInput.doAfterTextChanged { text ->
            val q = text?.toString().orEmpty().trim().lowercase()
            adapter.submitList(
                if (q.isEmpty()) allPlaces
                else allPlaces.filter { it.name.lowercase().contains(q) }
            )
        }

        loadPlaces()
    }

    private fun loadPlaces() {
        scope.launch {
            try {
                val snap = db.collection(FirestoreCollections.PLACES)
                    .orderBy("nombre")
                    .limit(200)
                    .get().await()
                allPlaces = snap.documents.mapNotNull { doc ->
                    val name = doc.getString("nombre") ?: return@mapNotNull null
                    PlaceItem(id = doc.id, name = name, category = doc.getString("categoria").orEmpty())
                }
                adapter.submitList(allPlaces)
            } catch (_: Exception) { /* silent — user can dismiss */ }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        scope.cancel()
        _binding = null
    }

    // ── Inner adapter ─────────────────────────────────────────────────────────

    data class PlaceItem(val id: String, val name: String, val category: String)

    private class PlaceAdapter(
        private val onClick: (PlaceItem) -> Unit,
    ) : ListAdapter<PlaceItem, PlaceAdapter.VH>(object : DiffUtil.ItemCallback<PlaceItem>() {
        override fun areItemsTheSame(old: PlaceItem, new: PlaceItem) = old.id == new.id
        override fun areContentsTheSame(old: PlaceItem, new: PlaceItem) = old == new
    }) {
        inner class VH(private val b: ItemPlaceSelectorBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(item: PlaceItem) {
                b.placeName.text = item.name
                b.placeCategory.text = item.category
                b.root.setOnClickListener { onClick(item) }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(ItemPlaceSelectorBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))
    }

    companion object {
        const val TAG = "PlaceSelectorBottomSheet"
        const val RESULT_KEY = "place_pick"
        const val KEY_PLACE_ID = "placeId"
        const val KEY_PLACE_NAME = "placeName"
    }
}
