package com.joseibarra.touristnotify

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.joseibarra.touristnotify.databinding.ActivityMenuBinding

class MenuActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMenuBinding

    private var adminClickCount = 0
    private var lastAdminClickTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMenuBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.buttonGenerateRoute.setOnClickListener {
            val intent = Intent(this, PreferencesActivity::class.java)
            startActivity(intent)
        }

        binding.buttonMyRoutes.setOnClickListener {
            val intent = Intent(this, MyRoutesActivity::class.java)
            startActivity(intent)
        }

        binding.buttonViewMap.setOnClickListener {
            val intent = Intent(this, MapsActivity::class.java)
            startActivity(intent)
        }

        binding.buttonContacts.setOnClickListener {
            val intent = Intent(this, ContactsActivity::class.java)
            startActivity(intent)
        }

        binding.buttonTopPlaces.setOnClickListener {
            val intent = Intent(this, TopPlacesActivity::class.java)
            startActivity(intent)
        }

        // Acceso secreto al panel administrativo (mantener presionado el footer)
        binding.footerText.setOnLongClickListener {
            showAdminAccessDialog()
            true
        }

        // También mediante 5 clicks rápidos en el footer
        binding.footerText.setOnClickListener {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastAdminClickTime < 500) {
                adminClickCount++
                if (adminClickCount >= 5) {
                    showAdminAccessDialog()
                    adminClickCount = 0
                }
            } else {
                adminClickCount = 1
            }
            lastAdminClickTime = currentTime
        }
    }

    private fun showAdminAccessDialog() {
        val dialogBuilder = androidx.appcompat.app.AlertDialog.Builder(this)
        dialogBuilder.setTitle("⚙️ Acceso Administrativo")
        dialogBuilder.setMessage("¿Deseas acceder al panel administrativo para importar lugares desde Google Places?")
        dialogBuilder.setPositiveButton("Acceder") { _, _ ->
            val intent = Intent(this, AdminPlacesActivity::class.java)
            startActivity(intent)
        }
        dialogBuilder.setNegativeButton("Cancelar", null)
        dialogBuilder.show()
    }
}
