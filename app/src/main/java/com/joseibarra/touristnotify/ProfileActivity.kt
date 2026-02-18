package com.joseibarra.touristnotify

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.joseibarra.touristnotify.databinding.ActivityProfileBinding
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Actividad para gestionar el perfil del usuario
 * Permite ver información, editar nickname, cambiar contraseña, eliminar cuenta y cerrar sesión
 */
class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

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
        // Configurar botón de volver
        binding.backButton.setOnClickListener {
            finish()
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = cm.activeNetwork ?: return false
            cm.getNetworkCapabilities(network)
                ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        } else {
            @Suppress("DEPRECATION")
            cm.activeNetworkInfo?.isConnected == true
        }
    }

    private fun loadUserData() {
        if (!isNetworkAvailable()) {
            Toast.makeText(this, "Sin conexión a internet. Algunos datos pueden no cargarse.", Toast.LENGTH_LONG).show()
        }

        val user = auth.currentUser ?: run {
            // No hay usuario autenticado, volver al login
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        // Mostrar email
        binding.userEmailText.text = user.email ?: "Sin correo"

        // Mostrar inicial en el avatar
        val initial = user.email?.firstOrNull()?.uppercaseChar()?.toString() ?: "U"
        binding.avatarText.text = initial

        // Cargar nickname desde Firestore
        lifecycleScope.launch {
            try {
                val doc = db.collection("users")
                    .document(user.uid)
                    .get()
                    .await()

                val nickname = doc.getString("nickname")
                if (!nickname.isNullOrBlank()) {
                    binding.nicknameEditText.setText(nickname)
                }
            } catch (e: Exception) {
                // Fallar silenciosamente, el campo quedará vacío
            }
        }
    }

    private fun loadStatistics() {
        val userId = auth.currentUser?.uid ?: return

        lifecycleScope.launch {
            try {
                // Lanzar las 3 queries a Firestore en paralelo
                coroutineScope {
                    val userDocDeferred = async {
                        db.collection("users").document(userId).get().await()
                    }
                    val routesDeferred = async {
                        db.collection("users").document(userId).collection("routes").get().await()
                    }
                    val checkInsDeferred = async {
                        // checkIns se guarda en la colección raíz con userId como campo
                        db.collection("checkIns").whereEqualTo("userId", userId).get().await()
                    }

                    val userDoc = userDocDeferred.await()
                    val routesCount = routesDeferred.await().size()
                    val checkInsCount = checkInsDeferred.await().size()

                    binding.routesCountText.text = routesCount.toString()
                    binding.checkinsCountText.text = checkInsCount.toString()

                    val favorites = userDoc.get("favorites") as? List<*>
                    binding.favoritesCountText.text = (favorites?.size ?: 0).toString()
                }

                // Uso de rutas IA (local, sin red)
                val usageStats = UsageManager.getUsageStats(this@ProfileActivity)
                binding.aiUsageText.text = "Rutas IA hoy: ${usageStats.routesUsedToday}/${usageStats.routesLimitToday}"
                binding.aiUsageProgress.progress = usageStats.usagePercentage

            } catch (e: Exception) {
                // Fallar silenciosamente, mostrar valores por defecto (0)
            }
        }
    }

    private fun setupListeners() {
        // Guardar cambios de perfil
        binding.saveProfileButton.setOnClickListener {
            saveProfileChanges()
        }

        // Cambiar contraseña
        binding.changePasswordButton.setOnClickListener {
            showChangePasswordDialog()
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
            Toast.makeText(this, "El nombre de usuario no puede estar vacío", Toast.LENGTH_SHORT).show()
            return
        }

        // Validar formato mínimo: al menos 3 caracteres, sin espacios
        if (nickname.length < 3) {
            Toast.makeText(this, "El nombre de usuario debe tener al menos 3 caracteres", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                // Verificar que ningún otro usuario tenga ese nickname
                val existing = db.collection("users")
                    .whereEqualTo("nickname", nickname)
                    .get()
                    .await()

                val takenByOther = existing.documents.any { it.id != userId }
                if (takenByOther) {
                    Toast.makeText(this@ProfileActivity, "❌ Ese nombre de usuario ya está en uso", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                db.collection("users")
                    .document(userId)
                    .update("nickname", nickname)
                    .await()

                Toast.makeText(this@ProfileActivity, "✅ Perfil actualizado", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@ProfileActivity, "❌ Error al guardar: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showChangePasswordDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_change_password, null)
        val currentPasswordInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.current_password_input)
        val newPasswordInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.new_password_input)
        val confirmPasswordInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.confirm_password_input)

        AlertDialog.Builder(this)
            .setTitle("Cambiar Contraseña")
            .setView(dialogView)
            .setPositiveButton("Cambiar") { _, _ ->
                val currentPassword = currentPasswordInput.text.toString()
                val newPassword = newPasswordInput.text.toString()
                val confirmPassword = confirmPasswordInput.text.toString()

                when {
                    currentPassword.isBlank() -> {
                        Toast.makeText(this, "Ingresa tu contraseña actual", Toast.LENGTH_SHORT).show()
                    }
                    newPassword.length < 6 -> {
                        Toast.makeText(this, "La nueva contraseña debe tener al menos 6 caracteres", Toast.LENGTH_SHORT).show()
                    }
                    newPassword != confirmPassword -> {
                        Toast.makeText(this, "Las contraseñas no coinciden", Toast.LENGTH_SHORT).show()
                    }
                    else -> {
                        changePassword(currentPassword, newPassword)
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun changePassword(currentPassword: String, newPassword: String) {
        val user = auth.currentUser ?: return
        val email = user.email ?: return

        lifecycleScope.launch {
            try {
                // Re-autenticar usuario
                val credential = EmailAuthProvider.getCredential(email, currentPassword)
                user.reauthenticate(credential).await()

                // Cambiar contraseña
                user.updatePassword(newPassword).await()

                Toast.makeText(this@ProfileActivity, "✅ Contraseña cambiada exitosamente", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@ProfileActivity, "❌ Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showDeleteAccountDialog() {
        AlertDialog.Builder(this)
            .setTitle("⚠️ Eliminar Cuenta")
            .setMessage("Esta acción es irreversible. Se eliminarán todos tus datos:\n\n" +
                    "• Rutas guardadas\n" +
                    "• Favoritos\n" +
                    "• Check-ins\n" +
                    "• Reseñas\n" +
                    "• Estadísticas\n\n" +
                    "¿Estás seguro de que deseas continuar?")
            .setPositiveButton("Eliminar") { _, _ ->
                showDeleteAccountPasswordDialog()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showDeleteAccountPasswordDialog() {
        val input = com.google.android.material.textfield.TextInputEditText(this)
        input.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        input.hint = "Contraseña"

        AlertDialog.Builder(this)
            .setTitle("Confirmar Eliminación")
            .setMessage("Por seguridad, ingresa tu contraseña para confirmar:")
            .setView(input)
            .setPositiveButton("Confirmar") { _, _ ->
                val password = input.text.toString()
                if (password.isNotBlank()) {
                    deleteAccount(password)
                } else {
                    Toast.makeText(this, "Ingresa tu contraseña", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun deleteAccount(password: String) {
        val user = auth.currentUser ?: return
        val email = user.email ?: return

        lifecycleScope.launch {
            try {
                // Re-autenticar usuario
                val credential = EmailAuthProvider.getCredential(email, password)
                user.reauthenticate(credential).await()

                // Eliminar subcollecciones del usuario
                val uid = user.uid
                val userRef = db.collection("users").document(uid)

                for (sub in listOf("routes", "favorites", "stats", "usage")) {
                    val docs = userRef.collection(sub).get().await()
                    for (doc in docs.documents) doc.reference.delete().await()
                }

                // Eliminar check-ins del usuario (colección raíz)
                val checkIns = db.collection("checkIns").whereEqualTo("userId", uid).get().await()
                for (doc in checkIns.documents) doc.reference.delete().await()

                // Eliminar documento del usuario
                userRef.delete().await()

                // Eliminar cuenta de Firebase Auth
                user.delete().await()

                Toast.makeText(this@ProfileActivity, "Cuenta eliminada", Toast.LENGTH_SHORT).show()

                // Volver al login
                val intent = Intent(this@ProfileActivity, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()

            } catch (e: Exception) {
                Toast.makeText(this@ProfileActivity, "❌ Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun performLogout() {
        AlertDialog.Builder(this)
            .setTitle("Cerrar Sesión")
            .setMessage("¿Estás seguro de que deseas cerrar sesión?")
            .setPositiveButton("Cerrar Sesión") { _, _ ->
                // Cerrar sesión de Firebase
                auth.signOut()

                // Desactivar modo invitado
                AuthManager.disableGuestMode(this)

                Toast.makeText(this, "Sesión cerrada", Toast.LENGTH_SHORT).show()

                // Volver al login
                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
