package com.joseibarra.touristnotify

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.joseibarra.touristnotify.databinding.ActivityContactsBinding

data class Contact(val name: String, val phoneNumber: String)

class ContactsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityContactsBinding
    private val emergencyContacts = listOf(
        Contact("Policía", "911"),
        Contact("Ambulancia", "065"),
        Contact("Bomberos", "068")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityContactsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.recyclerViewContacts.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewContacts.adapter = ContactsAdapter(emergencyContacts)
    }
}
