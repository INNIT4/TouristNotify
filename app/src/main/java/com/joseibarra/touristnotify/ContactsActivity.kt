package com.joseibarra.touristnotify

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.joseibarra.touristnotify.databinding.ActivityContactsBinding

data class Contact(
    val name: String,
    val phoneNumber: String,
    val category: String = "General",
    val description: String = ""
)

class ContactsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityContactsBinding
    private val touristContacts = listOf(
        // Emergencias
        Contact("Policía", "911", "Emergencia", "Emergencias policiales"),
        Contact("Ambulancia / Cruz Roja", "065", "Emergencia", "Atención médica de emergencia"),
        Contact("Bomberos", "068", "Emergencia", "Combate de incendios y rescate"),
        Contact("Protección Civil", "642-428-0033", "Emergencia", "Protección y auxilio en desastres"),

        // Servicios Turísticos
        Contact("Oficina de Turismo Álamos", "647-428-0450", "Turismo", "Información turística local"),
        Contact("Museo Costumbrista", "647-428-0053", "Turismo", "Historia y cultura de Álamos"),
        Contact("Casa de la Cultura", "647-428-0780", "Turismo", "Actividades culturales y eventos"),

        // Transporte
        Contact("Terminal de Autobuses", "647-428-0145", "Transporte", "Autobuses interurbanos"),
        Contact("Taxis Álamos", "647-428-0900", "Transporte", "Servicio de taxis local"),

        // Salud
        Contact("Hospital General Álamos", "647-428-0234", "Salud", "Atención médica general"),
        Contact("Farmacia Guadalajara", "647-428-0567", "Salud", "Farmacia 24 horas"),

        // Otros servicios
        Contact("Hotel Hacienda de los Santos", "647-428-0222", "Hospedaje", "Hotel boutique"),
        Contact("Restaurante El Cactus", "647-428-0345", "Gastronomía", "Cocina regional")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityContactsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.recyclerViewContacts.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewContacts.adapter = ContactsAdapter(touristContacts)
    }
}
