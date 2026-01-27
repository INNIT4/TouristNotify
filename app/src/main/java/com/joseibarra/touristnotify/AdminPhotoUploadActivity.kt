package com.joseibarra.touristnotify

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.joseibarra.touristnotify.databinding.ActivityAdminPhotoUploadBinding
import java.io.ByteArrayOutputStream
import java.util.UUID

/**
 * Actividad para que la Oficina de Turismo suba fotos de lugares
 */
class AdminPhotoUploadActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminPhotoUploadBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    private var selectedImageUri: Uri? = null
    private var placeId: String? = null
    private var placeName: String? = null

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedImageUri = uri
                displaySelectedImage(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()

        // Verificar autenticación antes de verificar permisos de admin
        if (!AuthManager.requireAuth(this, AuthManager.AuthRequired.UPLOAD_PHOTOS) {
                checkAdminAndInitialize()
            }) {
            finish()
            return
        }
    }

    private fun checkAdminAndInitialize() {
        // Verificar autorización - Solo Oficina de Turismo
        if (!AdminConfig.canCreateBlogPosts(auth.currentUser?.email)) {
            NotificationHelper.error(
                window.decorView.rootView,
                "Acceso denegado. Solo personal autorizado de la Oficina de Turismo puede subir fotos."
            )
            finish()
            return
        }

        binding = ActivityAdminPhotoUploadBinding.inflate(layoutInflater)
        setContentView(binding.root)

        placeId = intent.getStringExtra("PLACE_ID")
        placeName = intent.getStringExtra("PLACE_NAME")

        if (placeId == null || placeName == null) {
            NotificationHelper.error(binding.root, "Error: datos del lugar no encontrados")
            finish()
            return
        }

        supportActionBar?.title = "Subir Foto - $placeName"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        setupUI()
    }

    private fun setupUI() {
        binding.selectImageButton.setOnClickListener {
            selectImage()
        }

        binding.uploadButton.setOnClickListener {
            uploadPhoto()
        }
    }

    private fun selectImage() {
        val intent = Intent(Intent.ACTION_PICK).apply {
            type = "image/*"
        }
        imagePickerLauncher.launch(intent)
    }

    private fun displaySelectedImage(uri: Uri) {
        Glide.with(this)
            .load(uri)
            .centerCrop()
            .into(binding.selectedImageView)

        binding.selectedImageView.visibility = View.VISIBLE
        binding.uploadButton.isEnabled = true
    }

    private fun uploadPhoto() {
        val uri = selectedImageUri
        if (uri == null) {
            NotificationHelper.error(binding.root, "Selecciona una imagen primero")
            return
        }

        val caption = binding.captionEditText.text.toString().trim()

        binding.progressBar.visibility = View.VISIBLE
        binding.uploadButton.isEnabled = false
        binding.selectImageButton.isEnabled = false

        // Comprimir imagen antes de subir
        val compressedImageData = compressImage(uri)

        if (compressedImageData == null) {
            binding.progressBar.visibility = View.GONE
            binding.uploadButton.isEnabled = true
            binding.selectImageButton.isEnabled = true
            NotificationHelper.error(binding.root, "Error al comprimir la imagen")
            return
        }

        // Subir a Firebase Storage
        val photoId = UUID.randomUUID().toString()
        val storageRef = storage.reference
            .child("place_photos")
            .child(placeId!!)
            .child("$photoId.jpg")

        val uploadTask = storageRef.putBytes(compressedImageData)

        uploadTask.continueWithTask { task ->
            if (!task.isSuccessful) {
                task.exception?.let { throw it }
            }
            storageRef.downloadUrl
        }.addOnSuccessListener { downloadUri ->
            // Guardar metadata en Firestore
            savePhotoMetadata(photoId, downloadUri.toString(), caption)
        }.addOnFailureListener { e ->
            binding.progressBar.visibility = View.GONE
            binding.uploadButton.isEnabled = true
            binding.selectImageButton.isEnabled = true
            NotificationHelper.error(binding.root, "Error al subir foto: ${e.message}")
        }
    }

    private fun compressImage(uri: Uri): ByteArray? {
        return try {
            val inputStream = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            // Calcular nuevo tamaño manteniendo aspect ratio
            val maxSize = 1920f
            val width = bitmap.width.toFloat()
            val height = bitmap.height.toFloat()

            val scaleFactor = if (width > height) {
                maxSize / width
            } else {
                maxSize / height
            }

            val newWidth = (width * scaleFactor).toInt()
            val newHeight = (height * scaleFactor).toInt()

            // Redimensionar
            val scaledBitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)

            // Comprimir a JPEG con calidad 85%
            val outputStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)

            bitmap.recycle()
            scaledBitmap.recycle()

            outputStream.toByteArray()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun savePhotoMetadata(photoId: String, imageUrl: String, caption: String) {
        val currentUser = auth.currentUser ?: return

        val photo = PlacePhoto(
            id = photoId,
            placeId = placeId!!,
            placeName = placeName!!,
            imageUrl = imageUrl,
            thumbnailUrl = imageUrl, // Mismo URL para ambos por ahora
            uploadedBy = currentUser.uid,
            uploaderName = "Oficina de Turismo de Álamos",
            caption = caption,
            uploadedAt = null, // ServerTimestamp se asignará automáticamente
            likes = 0,
            width = 0,
            height = 0
        )

        db.collection("place_photos")
            .document(photoId)
            .set(photo)
            .addOnSuccessListener {
                binding.progressBar.visibility = View.GONE
                NotificationHelper.success(binding.root, "✓ Foto subida exitosamente")

                // Esperar un momento y cerrar
                binding.root.postDelayed({
                    finish()
                }, 1500)
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                binding.uploadButton.isEnabled = true
                binding.selectImageButton.isEnabled = true
                NotificationHelper.error(binding.root, "Error al guardar metadata: ${e.message}")
            }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
