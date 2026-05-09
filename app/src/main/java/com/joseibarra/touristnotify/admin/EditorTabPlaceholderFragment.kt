package com.joseibarra.touristnotify.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment

/** Placeholder para pestañas pendientes de implementación completa. */
class EditorTabPlaceholderFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return TextView(requireContext()).apply {
            text = "Pestaña «${arguments?.getString(ARG_LABEL)}» — próximamente"
            textSize = 16f
            gravity = android.view.Gravity.CENTER
            setPadding(32, 32, 32, 32)
        }
    }

    companion object {
        private const val ARG_LABEL = "label"
        fun newInstance(label: String) = EditorTabPlaceholderFragment().apply {
            arguments = Bundle().apply { putString(ARG_LABEL, label) }
        }
    }
}
