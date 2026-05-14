package com.joseibarra.trazago

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.android.gms.location.LocationServices
import com.joseibarra.trazago.databinding.ActivityProximityNotificationsBinding
import com.joseibarra.trazago.ui.BaseActivity

/**
 * Activity para configurar notificaciones de proximidad
 */
class ProximityNotificationsActivity : BaseActivity() {

    private lateinit var binding: ActivityProximityNotificationsBinding
    private lateinit var proximityManager: ProximityNotificationManager

    private fun getEncryptedPrefs() = encryptedPrefs(this)

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
            NotificationHelper.error(binding.root, getString(R.string.location_permission_required))
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
                getString(R.string.notifications_background_only)
            )
            setupNotifications()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            NotificationHelper.success(binding.root, getString(R.string.notifications_enabled))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Verificar autenticación antes de configurar notificaciones
        if (!AuthManager.requireAuth(this, AuthManager.AuthRequired.PROXIMITY_NOTIFICATIONS) {
                initializeActivity()
            }) {
            finish()
            return
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-verificar permisos al volver desde Configuración del sistema
        if (::binding.isInitialized) {
            checkPermissions()
        }
    }

    private fun initializeActivity() {
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
                NotificationHelper.info(binding.root, getString(R.string.notifications_disabled))
            }
        }

        // Radius slider — actualiza label en vivo y reconfigura geofences al soltar
        binding.radiusSlider.addOnChangeListener { _, value, _ ->
            binding.radiusValueTextView.text = getString(R.string.radius_meters_format, value.toInt())
        }
        binding.radiusSlider.addOnSliderTouchListener(object : com.google.android.material.slider.Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: com.google.android.material.slider.Slider) {}
            override fun onStopTrackingTouch(slider: com.google.android.material.slider.Slider) {
                val radius = slider.value.toInt()
                saveProximityRadius(radius)
                if (binding.notificationsSwitch.isChecked) {
                    proximityManager.clearCooldowns() // permite re-notificar tras cambio
                    setupNotifications()
                }
            }
        })

        // Request permissions button — pide lo que falte (ubicación y/o notificaciones)
        binding.requestPermissionsButton.setOnClickListener {
            when {
                !hasLocationPermissions() -> requestLocationPermissions()
                !hasNotificationPermission() -> requestNotificationPermission()
            }
        }
    }

    private fun loadSettings() {
        val prefs = getEncryptedPrefs()

        // Load enabled state
        val enabled = prefs.getBoolean("proximity_notifications_enabled", false)
        binding.notificationsSwitch.isChecked = enabled

        // Load radius (default 200m, clamp al rango del slider 50-1000)
        val radius = prefs.getInt("proximity_radius", DEFAULT_RADIUS_METERS)
            .coerceIn(MIN_RADIUS_METERS, MAX_RADIUS_METERS)
        binding.radiusSlider.value = radius.toFloat()
        binding.radiusValueTextView.text = getString(R.string.radius_meters_format, radius)
    }

    private fun saveNotificationsEnabled(enabled: Boolean) {
        getEncryptedPrefs()
            .edit()
            .putBoolean("proximity_notifications_enabled", enabled)
            .apply()
    }

    private fun saveProximityRadius(radius: Int) {
        getEncryptedPrefs()
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
            binding.permissionsStatusTextView.text = getString(R.string.permissions_granted)
            binding.requestPermissionsButton.visibility = View.GONE
        } else {
            val missing = mutableListOf<String>()
            if (!hasLocation) missing.add(getString(R.string.location_permission))
            if (!hasNotifications) missing.add(getString(R.string.notification_permission))

            binding.permissionsStatusTextView.text =
                getString(R.string.permissions_missing, missing.joinToString(", "))
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
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return

        // P0-3 (compliance-auditor) — Google Play Location Policy exige
        // un disclosure prominente con texto literal antes de pedir
        // ACCESS_BACKGROUND_LOCATION. Sin esto, la app es rechazada en
        // revisión y puede generar suspensión de cuenta de desarrollador.
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.bg_location_title)
            .setMessage(R.string.bg_location_disclosure)
            .setCancelable(false)
            .setPositiveButton(R.string.bg_location_continue) { _, _ ->
                backgroundLocationLauncher.launch(
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                )
            }
            .setNegativeButton(R.string.bg_location_decline) { _, _ ->
                NotificationHelper.show(
                    binding.root,
                    getString(R.string.bg_location_skipped),
                    NotificationHelper.NotificationType.INFO
                )
                binding.notificationsSwitch.isChecked = false
            }
            .show()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    @SuppressLint("MissingPermission") // ya validado en hasLocationPermissions()
    private fun setupNotifications() {
        val prefs = getEncryptedPrefs()
        val radius = prefs.getInt("proximity_radius", DEFAULT_RADIUS_METERS)
            .coerceIn(MIN_RADIUS_METERS, MAX_RADIUS_METERS)
            .toFloat()

        binding.setupProgressBar.visibility = View.VISIBLE
        binding.setupStatusTextView.text = getString(R.string.configuring_notifications)
        binding.setupStatusTextView.visibility = View.VISIBLE

        // Intentar obtener ubicación actual para priorizar los 100 lugares más cercanos.
        // Si falla o no hay ubicación, el manager hace fallback a los primeros 100 de Firestore.
        val configureWith: (Location?) -> Unit = { userLocation ->
            proximityManager.setupGeofencesForAllPlaces(radius, userLocation) { success, count ->
                binding.setupProgressBar.visibility = View.GONE

                if (success) {
                    binding.setupStatusTextView.text = getString(R.string.places_monitored, count)
                    NotificationHelper.success(
                        binding.root,
                        getString(R.string.notification_configured, count)
                    )
                } else {
                    binding.setupStatusTextView.text = getString(R.string.notification_config_error)
                    NotificationHelper.error(binding.root, getString(R.string.geofence_error))
                    binding.notificationsSwitch.isChecked = false
                }
            }
        }

        if (hasLocationPermissions()) {
            LocationServices.getFusedLocationProviderClient(this).lastLocation
                .addOnSuccessListener { location -> configureWith(location) }
                .addOnFailureListener { configureWith(null) }
        } else {
            configureWith(null)
        }

        // Solicitar permiso de notificaciones si no lo tiene
        if (!hasNotificationPermission()) {
            requestNotificationPermission()
        }

        checkPermissions()
    }

    companion object {
        private const val PREFS_NAME = "TrazaGoPrefs"
        const val MIN_RADIUS_METERS = 50
        const val MAX_RADIUS_METERS = 1000
        const val DEFAULT_RADIUS_METERS = 200

        private fun encryptedPrefs(context: Context) =
            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )

        fun isEnabled(context: Context): Boolean {
            return encryptedPrefs(context)
                .getBoolean("proximity_notifications_enabled", false)
        }

        fun getProximityRadius(context: Context): Int {
            return encryptedPrefs(context)
                .getInt("proximity_radius", DEFAULT_RADIUS_METERS)
                .coerceIn(MIN_RADIUS_METERS, MAX_RADIUS_METERS)
        }
    }
}
