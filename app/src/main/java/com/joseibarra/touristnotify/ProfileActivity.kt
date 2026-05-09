package com.joseibarra.touristnotify

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.joseibarra.touristnotify.databinding.ActivityProfileBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Actividad para gestionar el perfil del usuario
 * Permite ver información, editar nickname, cambiar contraseña, eliminar cuenta y cerrar sesión
 */
class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val profileRepository by lazy { UserProfileRepository(auth, db, storage) }
    private val accountManager by lazy { AccountManager(auth, db, storage) }

    // P1-3: Photo Picker API — no requiere READ_MEDIA_IMAGES (API 33+).
    // En dispositivos más antiguos el sistema hace fallback a GetContent automáticamente.
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { uploadProfilePhoto(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        loadUserData()
        loadStatistics()
        setupListeners()
    }

    private fun setupUI() {
        binding.backButton.setOnClickListener { finish() }
        binding.avatarCard.setOnClickListener {
            imagePickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }

    private fun uploadProfilePhoto(uri: Uri) {
        val uid = auth.currentUser?.uid ?: return
        NotificationHelper.info(binding.root, "Subiendo foto…")
        lifecycleScope.launch {
            profileRepository.uploadPhoto(uid, uri)
                .onSuccess { downloadUrl ->
                    loadProfilePhoto(downloadUrl)
                    NotificationHelper.success(binding.root, "Foto de perfil actualizada")
                }
                .onFailure { e ->
                    NotificationHelper.error(binding.root, "Error al subir foto: ${e.message}")
                }
        }
    }

    private fun loadProfilePhoto(url: String?) {
        // PEN-005: Validar URL contra whitelist antes de Glide
        val safeUrl = SafeImageUrl.sanitize(url) ?: return
        binding.avatarText.visibility = android.view.View.GONE
        binding.avatarImage.visibility = android.view.View.VISIBLE
        Glide.with(this)
            .load(safeUrl)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .circleCrop()
            .into(binding.avatarImage)
    }

    private fun loadUserData() {
        if (!NetworkUtils.isNetworkAvailable(this)) {
            NotificationHelper.warning(binding.root, getString(R.string.profile_no_internet))
        }

        val user = auth.currentUser ?: run {
            // No hay usuario autenticado, volver al login
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        // Mostrar email
        binding.userEmailText.text = user.email ?: getString(R.string.no_email)

        // Mostrar inicial en el avatar
        val initial = user.email?.firstOrNull()?.uppercaseChar()?.toString() ?: "U"
        binding.avatarText.text = initial

        lifecycleScope.launch {
            profileRepository.loadProfile(user.uid)
                .onSuccess { data ->
                    if (!data.nickname.isNullOrBlank()) {
                        binding.nicknameEditText.setText(data.nickname)
                    }
                    val displayName = data.nickname?.takeIf { it.isNotBlank() }
                        ?: auth.currentUser?.email ?: getString(R.string.profile_title)
                    binding.avatarImage.contentDescription = getString(R.string.a11y_profile_photo, displayName)
                    loadProfilePhoto(data.photoUrl)
                }
            // Fail silently — field stays empty
        }
    }

    private fun loadStatistics() {
        val userId = auth.currentUser?.uid ?: return
        lifecycleScope.launch {
            profileRepository.loadStats(userId)
                .onSuccess { stats ->
                    binding.routesCountText.text = stats.routesCount.toString()
                    binding.favoritesCountText.text = stats.favoritesCount.toString()
                    binding.checkinsCountText.text = stats.checkInsCount.toString()
                }
            // Fail silently — defaults to 0

            val usageStats = UsageManager.getUsageStats(this@ProfileActivity)
            binding.aiUsageText.text = "Rutas IA hoy: ${usageStats.routesUsedToday}/${usageStats.routesLimitToday}"
            binding.aiUsageProgress.progress = usageStats.usagePercentage
        }
    }

    private fun setupListeners() {
        // Guardar cambios de perfil
        binding.saveProfileButton.setOnClickListener {
            saveProfileChanges()
        }

        // Política de Privacidad
        binding.privacyPolicyButton.setOnClickListener {
            startActivity(Intent(this, PrivacyPolicyActivity::class.java))
        }

        // Cambiar contraseña
        binding.changePasswordButton.setOnClickListener {
            showChangePasswordDialog()
        }

        // Exportar datos personales (GDPR Art. 20)
        binding.exportDataButton.setOnClickListener {
            showExportDataDialog()
        }

        // Eliminar cuenta
        binding.deleteAccountButton.setOnClickListener {
            showDeleteAccountDialog()
        }

        // Cerrar sesión
        binding.logoutButton.setOnClickListener {
            performLogout()
        }
    }

    private fun saveProfileChanges() {
        val userId = auth.currentUser?.uid ?: return
        val nickname = binding.nicknameEditText.text.toString().trim()
        if (nickname.isBlank()) {
            NotificationHelper.warning(binding.root, getString(R.string.profile_username_empty))
            return
        }
        if (nickname.length < 3) {
            NotificationHelper.warning(binding.root, getString(R.string.nickname_min_length))
            return
        }
        lifecycleScope.launch {
            profileRepository.saveNickname(userId, nickname)
                .onSuccess {
                    NotificationHelper.success(binding.root, getString(R.string.profile_updated))
                }
                .onFailure { e ->
                    if (e.message == "nickname_taken") {
                        NotificationHelper.warning(binding.root, getString(R.string.profile_username_taken))
                    } else {
                        NotificationHelper.error(binding.root, getString(R.string.profile_save_error, e.message))
                    }
                }
        }
    }

    private fun showChangePasswordDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_change_password, null)
        val currentPasswordInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.current_password_input)
        val newPasswordInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.new_password_input)
        val confirmPasswordInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.confirm_password_input)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.change_password_title))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.change)) { _, _ ->
                val currentPassword = currentPasswordInput.text.toString()
                val newPassword = newPasswordInput.text.toString()
                val confirmPassword = confirmPasswordInput.text.toString()

                when {
                    currentPassword.isBlank() -> {
                        NotificationHelper.warning(binding.root, getString(R.string.enter_current_password))
                    }
                    newPassword.length < 6 -> {
                        NotificationHelper.warning(binding.root, getString(R.string.new_password_min_length))
                    }
                    newPassword != confirmPassword -> {
                        NotificationHelper.warning(binding.root, getString(R.string.passwords_dont_match))
                    }
                    else -> {
                        changePassword(currentPassword, newPassword)
                    }
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun changePassword(currentPassword: String, newPassword: String) {
        val email = auth.currentUser?.email ?: return
        lifecycleScope.launch {
            accountManager.changePassword(email, currentPassword, newPassword)
                .onSuccess {
                    NotificationHelper.success(binding.root, getString(R.string.password_changed))
                }
                .onFailure { e ->
                    NotificationHelper.error(binding.root, getString(R.string.password_change_error, e.message))
                }
        }
    }

    private fun showDeleteAccountDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_account_title))
            .setMessage(getString(R.string.delete_account_message))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                showDeleteAccountPasswordDialog()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showDeleteAccountPasswordDialog() {
        val input = com.google.android.material.textfield.TextInputEditText(this)
        input.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        input.hint = getString(R.string.password_hint)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.confirm_delete_title))
            .setMessage(getString(R.string.confirm_delete_message))
            .setView(input)
            .setPositiveButton(getString(R.string.confirm)) { _, _ ->
                val password = input.text.toString()
                if (password.isNotBlank()) {
                    deleteAccount(password)
                } else {
                    NotificationHelper.warning(binding.root, getString(R.string.enter_password))
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun deleteAccount(password: String) {
        val email = auth.currentUser?.email ?: return
        lifecycleScope.launch {
            accountManager.deleteAccount(email, password, applicationContext)
                .onSuccess {
                    val intent = Intent(this@ProfileActivity, LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
                .onFailure { e ->
                    NotificationHelper.error(binding.root, getString(R.string.password_change_error, e.message))
                }
        }
    }

    private fun showExportDataDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.export_data_title))
            .setMessage(getString(R.string.export_data_message))
            .setPositiveButton(getString(R.string.export_data_button)) { _, _ ->
                exportUserData()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun exportUserData() {
        val uid = auth.currentUser?.uid ?: return
        NotificationHelper.info(binding.root, getString(R.string.exporting_data))

        lifecycleScope.launch {
            try {
                val json = withContext(Dispatchers.IO) {
                    coroutineScope {
                        val profileDeferred = async {
                            db.collection("users").document(uid).get().await()
                        }
                        val favoritesDeferred = async {
                            db.collection(FirestoreCollections.USERS).document(uid)
                                .collection(FirestoreCollections.USER_FAVORITES).get().await()
                        }
                        val checkInsDeferred = async {
                            db.collection(FirestoreCollections.CHECK_INS)
                                .whereEqualTo("userId", uid).get().await()
                        }
                        val routesDeferred = async {
                            db.collection("rutas").whereEqualTo("id_usuario", uid).get().await()
                        }
                        val reviewsDeferred = async {
                            db.collectionGroup("reviews").whereEqualTo("userId", uid).get().await()
                        }

                        val root = JSONObject()
                        root.put("exportDate", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date()))
                        root.put("userId", uid)

                        val profileDoc = profileDeferred.await()
                        root.put("profile", JSONObject(profileDoc.data ?: emptyMap<String, Any>()))

                        root.put("favorites", JSONArray(favoritesDeferred.await().documents.map {
                            JSONObject(it.data ?: emptyMap<String, Any>())
                        }))
                        root.put("checkIns", JSONArray(checkInsDeferred.await().documents.map {
                            JSONObject(it.data ?: emptyMap<String, Any>())
                        }))
                        root.put("routes", JSONArray(routesDeferred.await().documents.map {
                            JSONObject(it.data ?: emptyMap<String, Any>())
                        }))
                        root.put("reviews", JSONArray(reviewsDeferred.await().documents.map {
                            JSONObject(it.data ?: emptyMap<String, Any>())
                        }))

                        root.toString(2)
                    }
                }

                val fileName = "lupita_datos_${SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())}.json"
                val file = File(cacheDir, fileName)
                file.writeText(json)

                val uri = FileProvider.getUriForFile(
                    this@ProfileActivity,
                    "${packageName}.fileprovider",
                    file
                )

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(shareIntent, getString(R.string.export_data_title)))
                NotificationHelper.success(binding.root, getString(R.string.export_data_success))

            } catch (e: Exception) {
                NotificationHelper.error(binding.root, getString(R.string.export_data_error, e.message))
            }
        }
    }

    private fun performLogout() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.logout_title))
            .setMessage(getString(R.string.logout_confirm_simple))
            .setPositiveButton(getString(R.string.close_session)) { _, _ ->
                // Cerrar sesión de Firebase
                auth.signOut()

                // Desactivar modo invitado
                AuthManager.disableGuestMode(this)

                // Volver al login
                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
}
