package com.joseibarra.touristnotify.admin

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.joseibarra.touristnotify.AdminPlacesActivity
import com.joseibarra.touristnotify.databinding.ActivityAdminMigrationBinding
import kotlinx.coroutines.launch

/**
 * UI para la migración masiva de datos de lugares.
 * Flujo: Backup → (confirmación doble) Wipe → Repoblar desde AdminPlacesActivity.
 */
class AdminMigrationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminMigrationBinding
    private var lastBackupCollection: String? = null
    private val log = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminMigrationBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.btnBackup.setOnClickListener { startBackup() }
        binding.btnWipe.setOnClickListener { confirmWipe() }
        binding.btnGoToImport.setOnClickListener {
            startActivity(Intent(this, AdminPlacesActivity::class.java))
        }
    }

    private fun startBackup() {
        binding.btnBackup.isEnabled = false
        binding.progressMigration.visibility = View.VISIBLE
        binding.tvBackupStatus.text = "Iniciando backup…"

        lifecycleScope.launch {
            try {
                val backupCol = PlaceMigrationCoordinator.backupPlaces { event ->
                    runOnUiThread { handleEvent(event) }
                }
                lastBackupCollection = backupCol
                binding.tvBackupStatus.text = "✓ Backup completo → $backupCol"
                binding.btnWipe.isEnabled = true
                appendLog("Backup OK: $backupCol")
            } catch (e: Exception) {
                binding.tvBackupStatus.text = "✗ Error: ${e.message}"
                binding.btnBackup.isEnabled = true
                appendLog("ERROR backup: ${e.message}")
            } finally {
                binding.progressMigration.visibility = View.GONE
            }
        }
    }

    private fun confirmWipe() {
        AlertDialog.Builder(this)
            .setTitle("¿Borrar catálogo?")
            .setMessage(
                "PRIMERA CONFIRMACIÓN\n\n" +
                "Se eliminarán TODOS los lugares de Firestore.\n" +
                "Backup guardado en: ${lastBackupCollection ?: "—"}\n\n" +
                "Esta acción es irreversible. ¿Continuar?"
            )
            .setPositiveButton("Sí, continuar") { _, _ -> confirmWipe2() }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun confirmWipe2() {
        AlertDialog.Builder(this)
            .setTitle("SEGUNDA CONFIRMACIÓN")
            .setMessage("¿ESTÁS SEGURO? Se borrarán todos los lugares permanentemente.")
            .setPositiveButton("SÍ, BORRAR TODO") { _, _ -> startWipe() }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun startWipe() {
        binding.btnWipe.isEnabled = false
        binding.progressMigration.visibility = View.VISIBLE
        binding.tvWipeStatus.text = "Borrando…"

        lifecycleScope.launch {
            try {
                PlaceMigrationCoordinator.wipePlaces { event ->
                    runOnUiThread { handleEvent(event) }
                }
                binding.tvWipeStatus.text = "✓ Catálogo borrado"
                binding.btnGoToImport.isEnabled = true
                appendLog("Wipe completo")
                Snackbar.make(binding.root, "Catálogo borrado. Ahora importa los lugares nuevos.", Snackbar.LENGTH_LONG).show()
            } catch (e: Exception) {
                binding.tvWipeStatus.text = "✗ Error: ${e.message}"
                appendLog("ERROR wipe: ${e.message}")
                Snackbar.make(binding.root, "Error en wipe. El backup sigue disponible.", Snackbar.LENGTH_LONG).show()
            } finally {
                binding.progressMigration.visibility = View.GONE
            }
        }
    }

    private fun handleEvent(event: PlaceMigrationCoordinator.MigrationEvent) {
        when (event) {
            is PlaceMigrationCoordinator.MigrationEvent.Progress -> {
                binding.progressMigration.max   = event.total.coerceAtLeast(1)
                binding.progressMigration.progress = event.done
                appendLog(event.message)
            }
            is PlaceMigrationCoordinator.MigrationEvent.BackupComplete ->
                appendLog("Backup OK: ${event.backupCollection} (${event.count} docs)")
            is PlaceMigrationCoordinator.MigrationEvent.WipeComplete ->
                appendLog("Wipe OK: ${event.deletedCount} docs eliminados")
            is PlaceMigrationCoordinator.MigrationEvent.Error ->
                appendLog("ERROR: ${event.message}")
        }
    }

    private fun appendLog(line: String) {
        log.appendLine(line)
        binding.tvLog.text = log.toString().takeLast(2000)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { onBackPressedDispatcher.onBackPressed(); return true }
        return super.onOptionsItemSelected(item)
    }

    companion object {
        fun newIntent(context: Context) = Intent(context, AdminMigrationActivity::class.java)
    }
}
