package com.joseibarra.touristnotify

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
        }
    }
}
