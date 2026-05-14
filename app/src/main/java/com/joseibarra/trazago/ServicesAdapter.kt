package com.joseibarra.trazago

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.joseibarra.trazago.databinding.ItemServiceSectionHeaderBinding
import com.joseibarra.trazago.databinding.ListItemContactBinding

class ServicesAdapter(
    private val services: List<Service>
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val rows: List<ServiceRow> = buildRows(services)

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_ITEM = 1
    }

    private fun buildRows(list: List<Service>): List<ServiceRow> {
        val result = mutableListOf<ServiceRow>()
        val grouped = list.sortedWith(compareBy({ it.category.order }, { it.priority }))
            .groupBy { it.category }
        for ((category, items) in grouped) {
            result.add(ServiceRow.Header(category))
            items.forEach { result.add(ServiceRow.Item(it)) }
        }
        return result
    }

    override fun getItemViewType(position: Int) = when (rows[position]) {
        is ServiceRow.Header -> VIEW_TYPE_HEADER
        is ServiceRow.Item -> VIEW_TYPE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder = when (viewType) {
        VIEW_TYPE_HEADER -> HeaderVH(
            ItemServiceSectionHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
        else -> ItemVH(
            ListItemContactBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is ServiceRow.Header -> (holder as HeaderVH).bind(row.category)
            is ServiceRow.Item -> (holder as ItemVH).bind(row.service)
        }
    }

    override fun getItemCount() = rows.size

    class HeaderVH(private val binding: ItemServiceSectionHeaderBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(category: ServiceCat) {
            binding.sectionTitle.text = binding.root.context.getString(category.labelRes)
        }
    }

    class ItemVH(private val binding: ListItemContactBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(service: Service) {
            binding.textViewContactName.text = "${service.iconEmoji} ${service.name}"
            binding.textViewContactDescription.text = service.description
            binding.textViewContactPhone.text = service.phoneNumber
            binding.buttonCall.setOnClickListener {
                val intent = Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:${service.phoneNumber}")
                }
                try { binding.root.context.startActivity(intent) } catch (_: Exception) {}
            }
            binding.buttonCall.contentDescription = "Llamar a ${service.name}"
        }
    }
}

sealed class ServiceRow {
    data class Header(val category: ServiceCat) : ServiceRow()
    data class Item(val service: Service) : ServiceRow()
}
