package com.joseibarra.touristnotify

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.joseibarra.touristnotify.databinding.ListItemGroupBinding
import java.text.SimpleDateFormat
import java.util.*

/**
 * Adapter para mostrar grupos de viaje
 */
class GroupsAdapter(
    private val onGroupClick: (TravelGroup) -> Unit
) : ListAdapter<TravelGroup, GroupsAdapter.GroupViewHolder>(GroupDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupViewHolder {
        val binding = ListItemGroupBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return GroupViewHolder(binding)
    }

    override fun onBindViewHolder(holder: GroupViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class GroupViewHolder(
        private val binding: ListItemGroupBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(group: TravelGroup) {
            binding.groupNameTextView.text = group.name
            binding.groupDescriptionTextView.text = group.description.ifBlank { "Sin descripción" }

            // Mostrar número de miembros
            binding.memberCountTextView.text = "${group.memberIds.size} miembro${if (group.memberIds.size != 1) "s" else ""}"

            // Mostrar código del grupo
            binding.groupCodeTextView.text = "Código: ${group.groupCode}"

            // Mostrar punto de encuentro si existe
            if (group.meetingPoint.isNotBlank()) {
                binding.meetingPointTextView.text = "📍 ${group.meetingPoint}"
            } else {
                binding.meetingPointTextView.text = "📍 Sin punto de encuentro"
            }

            // Fecha de creación
            group.createdAt?.let { date ->
                val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale("es", "MX"))
                binding.createdDateTextView.text = "Creado: ${dateFormat.format(date)}"
            }

            // Indicador de activo
            binding.activeIndicatorTextView.text = if (group.isActive) "✓ Activo" else "○ Inactivo"
            binding.activeIndicatorTextView.setTextColor(
                if (group.isActive) 0xFF4CAF50.toInt() else 0xFF9E9E9E.toInt()
            )

            binding.root.setOnClickListener {
                onGroupClick(group)
            }
        }
    }

    class GroupDiffCallback : DiffUtil.ItemCallback<TravelGroup>() {
        override fun areItemsTheSame(oldItem: TravelGroup, newItem: TravelGroup): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: TravelGroup, newItem: TravelGroup): Boolean {
            return oldItem == newItem
        }
    }
}
