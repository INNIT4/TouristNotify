package com.joseibarra.trazago

import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import com.joseibarra.trazago.databinding.ActivityContactsBinding
import com.joseibarra.trazago.ui.BaseActivity
import com.joseibarra.trazago.ui.MotionHelper

data class Contact(
    val name: String,
    val phoneNumber: String,
    val category: String = "General",
    val description: String = ""
)

class ContactsActivity : BaseActivity() {

    private lateinit var binding: ActivityContactsBinding
    private val touristContacts = listOf(
        // Emergencias
        Contact("Policía", "911", "Emergencia", "Emergencias policiales"),
        Contact("Ambulancia / Cruz Roja", "065", "Emergencia", "Atención médica de emergencia"),
        Contact("Bomberos", "068", "Emergencia", "Combate de incendios y rescate"),
        Contact("Protección Civil", "642-428-0033", "Emergencia", "Protección y auxilio en desastres")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityContactsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsets(binding.root, applyTop = true, applyBottom = true)

        binding.recyclerViewContacts.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewContacts.adapter = ContactsAdapter(touristContacts)
        binding.recyclerViewContacts.itemAnimator = MotionHelper.fadeSlideItemAnimator()
    }
}
