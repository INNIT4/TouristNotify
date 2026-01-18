package com.joseibarra.touristnotify

import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.recyclerview.widget.RecyclerView
import com.joseibarra.touristnotify.databinding.ItemServiceCategoryBinding

class ServiceCategoryAdapter(
    private val categories: List<ServiceCategory>,
    private val onClick: (ServiceCategory) -> Unit
) : RecyclerView.Adapter<ServiceCategoryAdapter.ServiceViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ServiceViewHolder {
        val binding = ItemServiceCategoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ServiceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ServiceViewHolder, position: Int) {
        holder.bind(categories[position])

        // Aplicar animación de fade in con delay escalonado
        val animation = AnimationUtils.loadAnimation(holder.itemView.context, R.anim.fade_in)
        animation.startOffset = (position % 8) * 50L // Delay escalonado por fila
        holder.itemView.startAnimation(animation)
    }

    override fun getItemCount() = categories.size

    inner class ServiceViewHolder(
        private val binding: ItemServiceCategoryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(category: ServiceCategory) {
            binding.emojiTextView.text = category.emoji
            binding.nameTextView.text = category.name

            binding.root.setOnClickListener {
                onClick(category)
            }
        }
    }
}
