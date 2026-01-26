package com.joseibarra.touristnotify

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.joseibarra.touristnotify.databinding.ActivityQrscannerBinding
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Activity para escanear códigos QR de puntos de interés
 *
 * FORMATO DEL QR:
 * touristnotify://place/{placeId}
 *
 * Ejemplo: touristnotify://place/abc123xyz
 */
class QRScannerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityQrscannerBinding
    private lateinit var cameraExecutor: ExecutorService
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var isProcessing = false

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startCamera()
        } else {
            NotificationHelper.error(binding.root, "Se necesita permiso de cámara para escanear QR")
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQrscannerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()

        setupUI()
        checkCameraPermission()
    }

    private fun setupUI() {
        binding.closeButton.setOnClickListener {
            finish()
        }

        binding.flashButton.setOnClickListener {
            toggleFlash()
        }
    }

    private fun checkCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                startCamera()
            }
            else -> {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.previewView.surfaceProvider)
                }

            // Image analyzer
            imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, QRAnalyzer { qrCode ->
                        processQRCode(qrCode)
                    })
                }

            // Select back camera
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageAnalyzer
                )

            } catch (e: Exception) {
                Log.e(TAG, "Camera binding failed", e)
                NotificationHelper.error(binding.root, "Error al iniciar cámara")
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun toggleFlash() {
        camera?.let {
            val flashMode = it.cameraInfo.torchState.value == TorchState.ON
            it.cameraControl.enableTorch(!flashMode)
        }
    }

    private fun processQRCode(qrCode: String) {
        if (isProcessing) return
        isProcessing = true

        runOnUiThread {
            binding.scanningIndicator.visibility = View.GONE
            binding.successIndicator.visibility = View.VISIBLE
        }

        // Parsear el QR code
        // Formato esperado: touristnotify://place/{placeId}
        val placeId = extractPlaceId(qrCode)

        if (placeId != null) {
            // Abrir PlaceDetailsActivity
            val intent = Intent(this, PlaceDetailsActivity::class.java).apply {
                putExtra("PLACE_ID", placeId)
                putExtra("FROM_QR", true)
            }
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out)
            finish()
        } else {
            // QR no válido
            runOnUiThread {
                binding.successIndicator.visibility = View.GONE
                binding.scanningIndicator.visibility = View.VISIBLE
                NotificationHelper.error(
                    binding.root,
                    "QR no válido. Debe ser de TouristNotify."
                )
            }
            isProcessing = false
        }
    }

    private fun extractPlaceId(qrCode: String): String? {
        return try {
            // Intentar varios formatos
            when {
                // Formato 1: touristnotify://place/{placeId}
                qrCode.startsWith("touristnotify://place/") -> {
                    qrCode.removePrefix("touristnotify://place/")
                }
                // Formato 2: https://touristnotify.app/place/{placeId}
                qrCode.contains("touristnotify.app/place/") -> {
                    qrCode.substringAfter("place/")
                }
                // Formato 3: Solo el ID directamente
                qrCode.matches(Regex("^[a-zA-Z0-9_-]+$")) -> {
                    qrCode
                }
                else -> null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing QR code", e)
            null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    /**
     * Analizador de imágenes para detectar códigos QR
     */
    private class QRAnalyzer(
        private val onQRDetected: (String) -> Unit
    ) : ImageAnalysis.Analyzer {

        private val scanner = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
        )

        override fun analyze(imageProxy: ImageProxy) {
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image = InputImage.fromMediaImage(
                    mediaImage,
                    imageProxy.imageInfo.rotationDegrees
                )

                scanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        for (barcode in barcodes) {
                            barcode.rawValue?.let { qrCode ->
                                Log.d(TAG, "QR detected: $qrCode")
                                onQRDetected(qrCode)
                            }
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Barcode scanning failed", e)
                    }
                    .addOnCompleteListener {
                        imageProxy.close()
                    }
            } else {
                imageProxy.close()
            }
        }
    }

    companion object {
        private const val TAG = "QRScanner"
    }
}
