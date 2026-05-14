package com.joseibarra.trazago

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.joseibarra.trazago.databinding.ActivitySettingsBinding
import com.joseibarra.trazago.ui.BaseActivity
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

class SettingsActivity : BaseActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val accountManager by lazy { AccountManager(auth, db, storage) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsets(binding.headerRow, applyTop = true, applyBottom = false)

        binding.backButton.setOnClickListener {
            finish()
            overridePendingTransition(0, 0)
        }

        setupDarkMode()
        setupListeners()
    }

    private fun setupDarkMode() {
        binding.darkModeSwitch.isChecked = TrazaGoApplication.isDarkModeEnabled(this)
        binding.darkModeRow.setOnClickListener {
            binding.darkModeSwitch.toggle()
        }
        binding.darkModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            TrazaGoApplication.setDarkMode(this, isChecked)
            recreate()
        }
    }

    private fun setupListeners() {
        setupLanguageRow()
        binding.changePasswordRow.setOnClickListener { showChangePasswordDialog() }
        binding.exportDataRow.setOnClickListener { showExportDataDialog() }
        binding.deleteAccountRow.setOnClickListener { showDeleteAccountDialog() }
    }

    private fun setupLanguageRow() {
        binding.languageValueText.text = LocaleHelper.getCurrentLanguageName(this)
        binding.languageRow.setOnClickListener {
            val currentCode = LocaleHelper.getCurrentLanguageCode(this)
            val languages = LocaleHelper.supportedLocales
            val items = languages.map { it.second }.toTypedArray()
            val checkedIndex = languages.indexOfFirst { it.first.language == currentCode }

            AlertDialog.Builder(this)
                .setTitle(getString(R.string.select_language))
                .setSingleChoiceItems(items, checkedIndex) { dialog, which ->
                    val selected = languages[which]
                    if (selected.first.language != currentCode) {
                        LocaleHelper.setLocale(this, selected.first.language)
                        dialog.dismiss()
                        val intent = Intent(this, MenuActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finishAffinity()
                    }
                }
                .setNegativeButton(getString(R.string.cancel), null)
                .show()
        }
    }

    private fun showChangePasswordDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_change_password, null)
        val currentInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.current_password_input)
        val newInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.new_password_input)
        val confirmInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.confirm_password_input)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.change_password_title))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.change)) { _, _ ->
                val current = currentInput.text.toString()
                val new = newInput.text.toString()
                val confirm = confirmInput.text.toString()
                when {
                    current.isBlank() -> NotificationHelper.warning(binding.root, getString(R.string.enter_current_password))
                    new.length < 6 -> NotificationHelper.warning(binding.root, getString(R.string.new_password_min_length))
                    new != confirm -> NotificationHelper.warning(binding.root, getString(R.string.passwords_dont_match))
                    else -> changePassword(current, new)
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun changePassword(currentPassword: String, newPassword: String) {
        val email = auth.currentUser?.email ?: return
        lifecycleScope.launch {
            accountManager.changePassword(email, currentPassword, newPassword)
                .onSuccess { NotificationHelper.success(binding.root, getString(R.string.password_changed)) }
                .onFailure { e -> NotificationHelper.error(binding.root, getString(R.string.password_change_error, e.message)) }
        }
    }

    private fun showExportDataDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.export_data_title))
            .setMessage(getString(R.string.export_data_message))
            .setPositiveButton(getString(R.string.export_data_button)) { _, _ -> exportUserData() }
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
                        val profileDeferred = async { db.collection("users").document(uid).get().await() }
                        val favoritesDeferred = async {
                            db.collection(FirestoreCollections.USERS).document(uid)
                                .collection(FirestoreCollections.USER_FAVORITES).get().await()
                        }
                        val checkInsDeferred = async {
                            db.collection(FirestoreCollections.CHECK_INS).whereEqualTo("userId", uid).get().await()
                        }
                        val routesDeferred = async {
                            db.collection("rutas").whereEqualTo("id_usuario", uid).get().await()
                        }

                        val root = JSONObject()
                        root.put("exportDate", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date()))
                        root.put("userId", uid)
                        root.put("profile", JSONObject(profileDeferred.await().data ?: emptyMap<String, Any>()))
                        root.put("favorites", JSONArray(favoritesDeferred.await().documents.map { JSONObject(it.data ?: emptyMap<String, Any>()) }))
                        root.put("checkIns", JSONArray(checkInsDeferred.await().documents.map { JSONObject(it.data ?: emptyMap<String, Any>()) }))
                        root.put("routes", JSONArray(routesDeferred.await().documents.map { JSONObject(it.data ?: emptyMap<String, Any>()) }))
                        root.toString(2)
                    }
                }

                val fileName = "trazago_datos_${SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())}.json"
                val file = File(cacheDir, fileName)
                file.writeText(json)

                val uri = FileProvider.getUriForFile(this@SettingsActivity, "${packageName}.fileprovider", file)
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

    private fun showDeleteAccountDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_account_title))
            .setMessage(getString(R.string.delete_account_message))
            .setPositiveButton(getString(R.string.delete)) { _, _ -> showDeleteAccountPasswordDialog() }
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
                if (password.isNotBlank()) deleteAccount(password)
                else NotificationHelper.warning(binding.root, getString(R.string.enter_password))
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun deleteAccount(password: String) {
        val email = auth.currentUser?.email ?: return
        lifecycleScope.launch {
            accountManager.deleteAccount(email, password, applicationContext)
                .onSuccess {
                    val intent = Intent(this@SettingsActivity, LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
                .onFailure { e ->
                    NotificationHelper.error(binding.root, getString(R.string.password_change_error, e.message))
                }
        }
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, 0)
    }
}
