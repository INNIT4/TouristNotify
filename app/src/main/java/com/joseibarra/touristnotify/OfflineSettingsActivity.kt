package com.joseibarra.touristnotify

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.joseibarra.touristnotify.databinding.ActivityOfflineSettingsBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Actividad para gestionar configuración del modo offline
 */
class OfflineSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOfflineSettingsBinding
    private lateinit var offlineManager: OfflineManager
    private lateinit var connectivityObserver: ConnectivityObserver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOfflineSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.title = "Modo Sin Conexión"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        offlineManager = OfflineManager(this)
        connectivityObserver = ConnectivityObserver(this)

        setupUI()
        loadSettings()
        loadStats()
    }

    private fun setupUI() {
        // Switch de modo offline
        binding.offlineModeSwitch.isChecked = offlineManager.isOfflineModeEnabled()
        binding.offlineModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            offlineManager.setOfflineModeEnabled(isChecked)
            updateUI()
        }

        // Switch de auto-sync
        binding.autoSyncSwitch.isChecked = offlineManager.isAutoSyncEnabled()
        binding.autoSyncSwitch.setOnCheckedChangeListener { _, isChecked ->
            offlineManager.setAutoSyncEnabled(isChecked)
        }

        // Botón de sincronizar ahora
        binding.syncNowButton.setOnClickListener {
            syncData()
        }

        // Botón de limpiar datos
        binding.clearDataButton.setOnClickListener {
            showClearDataDialog()
        }
    }

    private fun loadSettings() {
        // Mostrar estado de conexión
        lifecycleScope.launch {
            connectivityObserver.observe().collect { isConnected ->
                binding.connectionStatusTextView.text = if (isConnected) {
                    "🟢 Conectado"
                } else {
                    "🔴 Sin conexión"
                }
                binding.syncNowButton.isEnabled = isConnected
            }
        }
    }

    private fun loadStats() {
        lifecycleScope.launch {
            val stats = offlineManager.getOfflineStats()

            binding.spotsCountTextView.text = "${stats.spotCount} lugares"
            binding.eventsCountTextView.text = "${stats.eventCount} eventos"
            binding.postsCountTextView.text = "${stats.postCount} posts"
            binding.dataSizeTextView.text = String.format("%.2f MB", stats.dataSizeMB)

            if (stats.lastSyncTime > 0) {
                val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                binding.lastSyncTextView.text = sdf.format(Date(stats.lastSyncTime))
            } else {
                binding.lastSyncTextView.text = "Nunca"
            }
        }
    }

    private fun syncData() {
        binding.progressBar.visibility = View.VISIBLE
        binding.syncNowButton.isEnabled = false

        lifecycleScope.launch {
            val result = offlineManager.syncFromFirebase()

            result.onSuccess {
                binding.progressBar.visibility = View.GONE
                binding.syncNowButton.isEnabled = true
                NotificationHelper.success(binding.root, "✅ Datos sincronizados correctamente")
                loadStats()
            }.onFailure { error ->
                binding.progressBar.visibility = View.GONE
                binding.syncNowButton.isEnabled = true
                NotificationHelper.error(binding.root, "Error al sincronizar: ${error.message}")
            }
        }
    }

    private fun showClearDataDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Limpiar datos offline")
            .setMessage("¿Estás seguro? Se eliminarán todos los datos descargados.")
            .setPositiveButton("Eliminar") { _, _ ->
                clearData()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun clearData() {
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            offlineManager.clearOfflineData()
            binding.progressBar.visibility = View.GONE
            NotificationHelper.success(binding.root, "Datos offline eliminados")
            loadStats()
        }
    }

    private fun updateUI() {
        // Actualizar UI según estado del modo offline
        val enabled = offlineManager.isOfflineModeEnabled()
        binding.statsCard.visibility = if (enabled) View.VISIBLE else View.GONE
        binding.actionsCard.visibility = if (enabled) View.VISIBLE else View.GONE
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
