package com.joseibarra.touristnotify

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.joseibarra.touristnotify.databinding.ListItemGroupMemberBinding
import java.text.SimpleDateFormat
import java.util.*

/**
 * Adapter para mostrar miembros del grupo
 */
class GroupMembersAdapter : ListAdapter<GroupMember, GroupMembersAdapter.MemberViewHolder>(MemberDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemberViewHolder {
        val binding = ListItemGroupMemberBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MemberViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MemberViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class MemberViewHolder(
        private val binding: ListItemGroupMemberBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(member: GroupMember) {
            binding.memberNameTextView.text = member.userName

            // Estado online/offline
            if (member.isOnline) {
                binding.statusIndicatorView.setBackgroundColor(0xFF4CAF50.toInt())
                binding.statusTextView.text = "En línea"
                binding.statusTextView.setTextColor(0xFF4CAF50.toInt())
            } else {
                binding.statusIndicatorView.setBackgroundColor(0xFF9E9E9E.toInt())
                binding.statusTextView.text = "Desconectado"
                binding.statusTextView.setTextColor(0xFF9E9E9E.toInt())
            }

            // Última actualización
            member.lastUpdate?.let { date ->
                val now = Date()
                val diffMs = now.time - date.time
                val diffMins = diffMs / (60 * 1000)

                val timeText = when {
                    diffMins < 1 -> "Ahora"
                    diffMins < 60 -> "Hace ${diffMins}m"
                    diffMins < 1440 -> "Hace ${diffMins / 60}h"
                    else -> "Hace ${diffMins / 1440}d"
                }

                binding.lastUpdateTextView.text = timeText
            } ?: run {
                binding.lastUpdateTextView.text = "Sin ubicación"
            }

            // Mostrar coordenadas si están disponibles
            if (member.latitude != 0.0 && member.longitude != 0.0) {
                binding.locationTextView.text = "📍 Ubicación compartida"
            } else {
                binding.locationTextView.text = "📍 Sin compartir"
            }
        }
    }

    class MemberDiffCallback : DiffUtil.ItemCallback<GroupMember>() {
        override fun areItemsTheSame(oldItem: GroupMember, newItem: GroupMember): Boolean {
            return oldItem.userId == newItem.userId
        }

        override fun areContentsTheSame(oldItem: GroupMember, newItem: GroupMember): Boolean {
            return oldItem == newItem
        }
    }
}
