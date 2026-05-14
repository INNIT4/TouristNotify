package com.joseibarra.trazago.result

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.joseibarra.trazago.R
import com.joseibarra.trazago.TouristSpot
import com.joseibarra.trazago.databinding.FragmentRouteEditorBinding
import com.joseibarra.trazago.databinding.ItemRouteStopEditorBinding
import com.joseibarra.trazago.model.GeneratedRoute
import com.joseibarra.trazago.model.RouteStop
import com.joseibarra.trazago.routegen.RouteOptimizer
import kotlinx.coroutines.launch

/**
 * Tab 3 — Editor de paradas.
 * Permite reordenar vía drag-drop, quitar paradas (mínimo 2) y agregar lugares.
 * Con Auto-optimizar activo, cada cambio re-optimiza la ruta con TSP/2-opt.
 */
class RouteEditorFragment : Fragment() {

    private var _binding: FragmentRouteEditorBinding? = null
    private val binding get() = _binding!!

    private val activity get() = requireActivity() as RouteResultActivity

    private val stops = mutableListOf<Pair<RouteStop, TouristSpot?>>()
    private val editorAdapter = EditorAdapter()
    private var touchHelper: ItemTouchHelper? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRouteEditorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecycler()
        loadData()
    }

    private fun setupRecycler() {
        binding.recyclerEditor.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = editorAdapter
        }

        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0
        ) {
            override fun onMove(rv: RecyclerView, from: RecyclerView.ViewHolder, to: RecyclerView.ViewHolder): Boolean {
                val fromPos = from.adapterPosition
                val toPos = to.adapterPosition
                stops.add(toPos, stops.removeAt(fromPos))
                editorAdapter.notifyItemMoved(fromPos, toPos)
                return true
            }

            override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {}

            override fun clearView(rv: RecyclerView, vh: RecyclerView.ViewHolder) {
                super.clearView(rv, vh)
                onOrderChanged()
            }
        }
        touchHelper = ItemTouchHelper(callback).also { it.attachToRecyclerView(binding.recyclerEditor) }
    }

    private fun loadData() {
        val route = activity.currentRoute
        val spotsById = activity.routeSpots.associateBy { it.id }
        stops.clear()
        stops.addAll(route.paradas.map { stop -> stop to spotsById[stop.placeId] })
        editorAdapter.notifyDataSetChanged()
    }

    private fun removeStop(position: Int) {
        if (stops.size <= 2) {
            Snackbar.make(binding.root, getString(R.string.editor_min_stops), Snackbar.LENGTH_SHORT).show()
            return
        }
        stops.removeAt(position)
        editorAdapter.notifyItemRemoved(position)
        editorAdapter.notifyItemRangeChanged(position, stops.size)
        onOrderChanged()
    }

    private fun onOrderChanged() {
        if (!binding.switchAutoOptimizar.isChecked) {
            applyManualOrder()
            return
        }
        // Re-optimización en background
        viewLifecycleOwner.lifecycleScope.launch {
            val currentSpots = stops.mapNotNull { it.second }
            if (currentSpots.size < 2) return@launch

            val currentRoute = activity.currentRoute
            val reorderedRoute = currentRoute.copy(
                paradas = stops.mapIndexed { i, (stop, _) ->
                    stop.copy(ordenSugerido = i + 1)
                }
            )

            val result = RouteOptimizer.optimize(
                RouteOptimizer.OptimizationRequest(
                    route = reorderedRoute,
                    spots = currentSpots,
                    isRaining = false
                )
            )

            if (_binding != null) {
                val spotsById = activity.routeSpots.associateBy { it.id }
                stops.clear()
                stops.addAll(result.route.paradas.map { stop -> stop to spotsById[stop.placeId] })
                editorAdapter.notifyDataSetChanged()
                activity.updateRoute(result.route)
            }
        }
    }

    private fun applyManualOrder() {
        val newRoute = activity.currentRoute.copy(
            paradas = stops.mapIndexed { i, (stop, _) ->
                stop.copy(ordenSugerido = i + 1)
            }
        )
        activity.updateRoute(newRoute)
    }

    // ─── Inner adapter ────────────────────────────────────────────────────────

    private inner class EditorAdapter : RecyclerView.Adapter<EditorAdapter.VH>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(ItemRouteStopEditorBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount() = stops.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val (stop, spot) = stops[position]
            holder.bind(stop, spot, position)
        }

        inner class VH(private val b: ItemRouteStopEditorBinding) :
            RecyclerView.ViewHolder(b.root) {

            fun bind(stop: RouteStop, spot: TouristSpot?, position: Int) {
                b.tvEditorOrden.text = (position + 1).toString()
                b.tvEditorNombre.text = spot?.nombre ?: stop.placeId
                b.tvEditorHorario.text = if (stop.horaSugeridaInicio.isNotBlank())
                    "${stop.horaSugeridaInicio}–${stop.horaSugeridaFin}"
                else ""

                b.ivDragHandle.setOnTouchListener { _, _ ->
                    touchHelper?.startDrag(this)
                    false
                }

                b.btnQuitarParada.setOnClickListener {
                    val pos = adapterPosition
                    if (pos != RecyclerView.NO_ID.toInt()) removeStop(pos)
                }
            }
        }
    }

    override fun onDestroyView() {
        touchHelper = null
        super.onDestroyView()
        _binding = null
    }
}
