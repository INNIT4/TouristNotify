package com.joseibarra.touristnotify

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.joseibarra.touristnotify.databinding.ListItemGroupChatMessageBinding
import java.text.SimpleDateFormat
import java.util.*

/**
 * Adapter para mensajes de chat grupal
 */
class GroupChatAdapter(
    private val currentUserId: String
) : ListAdapter<GroupChatMessage, GroupChatAdapter.MessageViewHolder>(MessageDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val binding = ListItemGroupChatMessageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MessageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class MessageViewHolder(
        private val binding: ListItemGroupChatMessageBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(message: GroupChatMessage) {
            val isMyMessage = message.senderId == currentUserId

            if (isMyMessage) {
                // Mi mensaje (derecha)
                binding.myMessageCard.visibility = android.view.View.VISIBLE
                binding.otherMessageCard.visibility = android.view.View.GONE

                binding.myMessageTextView.text = message.message
                binding.myTimestampTextView.text = formatTime(message.timestamp)
            } else {
                // Mensaje de otro (izquierda)
                binding.myMessageCard.visibility = android.view.View.GONE
                binding.otherMessageCard.visibility = android.view.View.VISIBLE

                binding.otherSenderNameTextView.text = message.senderName
                binding.otherMessageTextView.text = message.message
                binding.otherTimestampTextView.text = formatTime(message.timestamp)
            }
        }

        private fun formatTime(timestamp: Long): String {
            val now = System.currentTimeMillis()
            val diff = now - timestamp

            return when {
                diff < 60000 -> "Ahora" // Menos de 1 minuto
                diff < 3600000 -> {
                    val minutes = (diff / 60000).toInt()
                    "Hace ${minutes}m"
                }
                diff < 86400000 -> {
                    val hours = (diff / 3600000).toInt()
                    "Hace ${hours}h"
                }
                else -> {
                    val sdf = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
                    sdf.format(Date(timestamp))
                }
            }
        }
    }

    class MessageDiffCallback : DiffUtil.ItemCallback<GroupChatMessage>() {
        override fun areItemsTheSame(oldItem: GroupChatMessage, newItem: GroupChatMessage): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: GroupChatMessage, newItem: GroupChatMessage): Boolean {
            return oldItem == newItem
        }
    }
}
