package com.joseibarra.touristnotify

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.joseibarra.touristnotify.databinding.ListItemContactBinding

class ContactsAdapter(private val contacts: List<Contact>) : RecyclerView.Adapter<ContactsAdapter.ContactViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val binding = ListItemContactBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ContactViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        val contact = contacts[position]
        holder.bind(contact)
    }

    override fun getItemCount() = contacts.size

    inner class ContactViewHolder(private val binding: ListItemContactBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(contact: Contact) {
            binding.textViewContactName.text = contact.name
            binding.textViewContactPhone.text = contact.phoneNumber
            binding.textViewContactCategory.text = contact.category
            binding.textViewContactDescription.text = contact.description

            // Ambos el botón y el card completo inician la llamada
            binding.buttonCall.setOnClickListener { callContact(contact.phoneNumber) }
            binding.root.setOnClickListener { callContact(contact.phoneNumber) }
        }

        private fun callContact(phoneNumber: String) {
            val context = binding.root.context
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$phoneNumber")
            }
            context.startActivity(intent)
        }
    }
}
