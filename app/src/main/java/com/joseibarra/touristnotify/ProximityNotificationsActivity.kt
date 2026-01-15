package com.joseibarra.touristnotify

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.joseibarra.touristnotify.databinding.ActivityProximityNotificationsBinding

/**
 * Activity para configurar notificaciones de proximidad
 */
class ProximityNotificationsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProximityNotificationsBinding
    private lateinit var proximityManager: ProximityNotificationManager

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocation = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocation = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        if (fineLocation || coarseLocation) {
            // Permisos básicos concedidos, ahora pedir background si es necesario
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                requestBackgroundLocationPermission()
            } else {
                setupNotifications()
            }
        } else {
            NotificationHelper.error(binding.root, "Se requieren permisos de ubicación")
        }
    }

    private val backgroundLocationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            setupNotifications()
        } else {
            NotificationHelper.warning(
                binding.root,
                "Las notificaciones funcionarán solo cuando la app esté abierta"
            )
            setupNotifications()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            NotificationHelper.success(binding.root, "Notificaciones habilitadas")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProximityNotificationsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        proximityManager = ProximityNotificationManager(this)

        setupUI()
        loadSettings()
        checkPermissions()
    }

    private fun setupUI() {
        // Enable/Disable notifications
        binding.notificationsSwitch.setOnCheckedChangeListener { _, isChecked ->
            saveNotificationsEnabled(isChecked)

            if (isChecked) {
                if (hasLocationPermissions()) {
                    setupNotifications()
                } else {
                    binding.notificationsSwitch.isChecked = false
                    requestLocationPermissions()
                }
            } else {
                proximityManager.removeAllGeofences()
                NotificationHelper.info(binding.root, "Notificaciones desactivadas")
            }
        }

        // Radius selection
        binding.radiusChipGroup.setOnCheckedChangeListener { _, checkedId ->
            val radius = when (checkedId) {
                R.id.radius_100m_chip -> 10
                R.id.radius_250m_chip -> 250
                R.id.radius_500m_chip -> 500
                R.id.radius_1km_chip -> 1000
                else -> 10
            }
            saveProximityRadius(radius)

            if (binding.notificationsSwitch.isChecked) {
                // Recrear geofences con nuevo radio
                setupNotifications()
            }
        }

        // Request permissions button
        binding.requestPermissionsButton.setOnClickListener {
            requestLocationPermissions()
        }
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences("TouristNotifyPrefs", Context.MODE_PRIVATE)

        // Load enabled state
        val enabled = prefs.getBoolean("proximity_notifications_enabled", false)
        binding.notificationsSwitch.isChecked = enabled

        // Load radius
        val radius = prefs.getInt("proximity_radius", 10)
        when (radius) {
            10 -> binding.radius100mChip.isChecked = true
            250 -> binding.radius250mChip.isChecked = true
            500 -> binding.radius500mChip.isChecked = true
            1000 -> binding.radius1kmChip.isChecked = true
        }
    }

    private fun saveNotificationsEnabled(enabled: Boolean) {
        getSharedPreferences("TouristNotifyPrefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("proximity_notifications_enabled", enabled)
            .apply()
    }

    private fun saveProximityRadius(radius: Int) {
        getSharedPreferences("TouristNotifyPrefs", Context.MODE_PRIVATE)
            .edit()
            .putInt("proximity_radius", radius)
            .apply()
    }

    private fun checkPermissions() {
        val hasLocation = hasLocationPermissions()
        val hasNotifications = hasNotificationPermission()

        // Update UI
        binding.permissionsStatusContainer.visibility = View.VISIBLE

        if (hasLocation && hasNotifications) {
            binding.permissionsStatusTextView.text = "✅ Todos los permisos concedidos"
            binding.requestPermissionsButton.visibility = View.GONE
        } else {
            val missing = mutableListOf<String>()
            if (!hasLocation) missing.add("ubicación")
            if (!hasNotifications) missing.add("notificaciones")

            binding.permissionsStatusTextView.text =
                "⚠️ Faltan permisos: ${missing.joinToString(", ")}"
            binding.requestPermissionsButton.visibility = View.VISIBLE
        }
    }

    private fun hasLocationPermissions(): Boolean {
        val fineLocation = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseLocation = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        return fineLocation || coarseLocation
    }

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // No se requiere en versiones anteriores
        }
    }

    private fun requestLocationPermissions() {
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun requestBackgroundLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun setupNotifications() {
        val prefs = getSharedPreferences("TouristNotifyPrefs", Context.MODE_PRIVATE)
        val radius = prefs.getInt("proximity_radius", 10).toFloat()

        binding.setupProgressBar.visibility = View.VISIBLE
        binding.setupStatusTextView.text = "Configurando notificaciones..."
        binding.setupStatusTextView.visibility = View.VISIBLE

        proximityManager.setupGeofencesForAllPlaces(radius) { success, count ->
            binding.setupProgressBar.visibility = View.GONE

            if (success) {
                binding.setupStatusTextView.text =
                    "✅ $count lugares monitoreados\nRecibirás notificaciones al acercarte"
                NotificationHelper.success(
                    binding.root,
                    "Notificaciones configuradas para $count lugares"
                )
            } else {
                binding.setupStatusTextView.text = "❌ Error al configurar notificaciones"
                NotificationHelper.error(binding.root, "Error al configurar geofences")
                binding.notificationsSwitch.isChecked = false
            }
        }

        // Solicitar permiso de notificaciones si no lo tiene
        if (!hasNotificationPermission()) {
            requestNotificationPermission()
        }

        checkPermissions()
    }

    companion object {
        fun isEnabled(context: Context): Boolean {
            return context.getSharedPreferences("TouristNotifyPrefs", Context.MODE_PRIVATE)
                .getBoolean("proximity_notifications_enabled", false)
        }

        fun getProximityRadius(context: Context): Int {
            return context.getSharedPreferences("TouristNotifyPrefs", Context.MODE_PRIVATE)
                .getInt("proximity_radius", 10)
        }
    }
}
