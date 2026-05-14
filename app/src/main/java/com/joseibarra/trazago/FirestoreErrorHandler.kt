package com.joseibarra.trazago

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code

/**
 * Manejador centralizado de errores de Firestore
 *
 * Proporciona mensajes amigables para el usuario cuando las Security Rules
 * bloquean operaciones.
 */
object FirestoreErrorHandler {

    private const val TAG = "FirestoreError"

    /**
     * Convierte excepciones de Firestore en mensajes amigables para el usuario
     *
     * @param exception La excepción de Firestore
     * @param operation Descripción de la operación (ej: "guardar favorito")
     * @return Mensaje amigable para mostrar al usuario
     */
    fun getErrorMessage(exception: Exception, operation: String = "realizar esta acción"): String {
        return when (exception) {
            is FirebaseFirestoreException -> {
                when (exception.code) {
                    Code.PERMISSION_DENIED -> {
                        Log.w(TAG, "Permission denied for: $operation", exception)
                        "⚠️ No tienes permiso para $operation. " +
                        "Por favor, inicia sesión o verifica tus permisos."
                    }

                    Code.UNAUTHENTICATED -> {
                        Log.w(TAG, "Unauthenticated for: $operation", exception)
                        "🔒 Debes iniciar sesión para $operation"
                    }

                    Code.NOT_FOUND -> {
                        Log.w(TAG, "Document not found for: $operation", exception)
                        "❌ No se encontró el elemento solicitado"
                    }

                    Code.ALREADY_EXISTS -> {
                        Log.w(TAG, "Document already exists for: $operation", exception)
                        "⚠️ Este elemento ya existe"
                    }

                    Code.FAILED_PRECONDITION -> {
                        Log.w(TAG, "Failed precondition for: $operation", exception)
                        "⚠️ No se cumplieron las condiciones necesarias para $operation"
                    }

                    Code.INVALID_ARGUMENT -> {
                        Log.w(TAG, "Invalid argument for: $operation", exception)
                        "⚠️ Datos inválidos. Verifica la información e intenta nuevamente."
                    }

                    Code.UNAVAILABLE -> {
                        Log.w(TAG, "Service unavailable for: $operation", exception)
                        "📡 Servicio temporalmente no disponible. Intenta más tarde."
                    }

                    Code.DEADLINE_EXCEEDED -> {
                        Log.w(TAG, "Timeout for: $operation", exception)
                        "⏱️ La operación tardó demasiado. Verifica tu conexión."
                    }

                    Code.RESOURCE_EXHAUSTED -> {
                        Log.w(TAG, "Quota exceeded for: $operation", exception)
                        "⚠️ Se ha excedido el límite de operaciones. Intenta más tarde."
                    }

                    Code.CANCELLED -> {
                        Log.i(TAG, "Operation cancelled: $operation")
                        "❌ Operación cancelada"
                    }

                    Code.ABORTED -> {
                        Log.w(TAG, "Transaction aborted for: $operation", exception)
                        "⚠️ Operación interrumpida. Intenta nuevamente."
                    }

                    else -> {
                        Log.e(TAG, "Unknown Firestore error for: $operation", exception)
                        "❌ Error: ${exception.message ?: "Error desconocido"}"
                    }
                }
            }

            else -> {
                Log.e(TAG, "Non-Firestore error for: $operation", exception)
                "❌ Error inesperado: ${exception.message ?: "Error desconocido"}"
            }
        }
    }

    /**
     * Verifica si un error es de tipo PERMISSION_DENIED
     */
    fun isPermissionDenied(exception: Exception): Boolean {
        return exception is FirebaseFirestoreException &&
               exception.code == Code.PERMISSION_DENIED
    }

    /**
     * Verifica si un error es de tipo UNAUTHENTICATED
     */
    fun isUnauthenticated(exception: Exception): Boolean {
        return exception is FirebaseFirestoreException &&
               exception.code == Code.UNAUTHENTICATED
    }

    /**
     * Verifica si un error requiere que el usuario inicie sesión
     */
    fun requiresAuthentication(exception: Exception): Boolean {
        return isPermissionDenied(exception) || isUnauthenticated(exception)
    }

    /**
     * Maneja errores de Firestore y muestra notificación apropiada
     *
     * @param context Contexto para mostrar notificación
     * @param view Vista raíz para el Snackbar
     * @param exception La excepción
     * @param operation Descripción de la operación
     * @param onAuthRequired Callback si se requiere autenticación
     */
    fun handleError(
        context: Context,
        view: android.view.View,
        exception: Exception,
        operation: String = "realizar esta acción",
        onAuthRequired: (() -> Unit)? = null
    ) {
        val message = getErrorMessage(exception, operation)

        // Si requiere autenticación, mostrar acción para iniciar sesión
        if (requiresAuthentication(exception) && onAuthRequired != null) {
            NotificationHelper.show(
                view,
                message,
                NotificationHelper.NotificationType.WARNING,
                com.google.android.material.snackbar.Snackbar.LENGTH_LONG,
                "Iniciar sesión"
            ) {
                onAuthRequired()
            }
        } else {
            // Error general
            NotificationHelper.error(view, message)
        }
    }

    /**
     * Log detallado de error para debugging
     */
    fun logDetailedError(exception: Exception, operation: String, context: Map<String, Any?> = emptyMap()) {
        Log.e(TAG, """
            ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            Firestore Error Details
            ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            Operation: $operation
            Exception: ${exception.javaClass.simpleName}
            Message: ${exception.message}
            ${if (exception is FirebaseFirestoreException) "Code: ${exception.code}" else ""}
            Context: ${context.entries.joinToString("\n            ") { "${it.key}: ${it.value}" }}
            ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        """.trimIndent(), exception)
    }
}

/**
 * Extension function para Task de Firestore
 * Ejemplo de uso:
 *
 * db.collection("lugares").get()
 *   .addOnSuccessListener { ... }
 *   .onFirestoreError(binding.root, "cargar lugares") { exception ->
 *       // Manejo adicional si es necesario
 *   }
 */
fun <T> com.google.android.gms.tasks.Task<T>.onFirestoreError(
    view: android.view.View,
    operation: String,
    onAuthRequired: (() -> Unit)? = null,
    additionalHandler: ((Exception) -> Unit)? = null
): com.google.android.gms.tasks.Task<T> {
    return this.addOnFailureListener { exception ->
        FirestoreErrorHandler.handleError(
            view.context,
            view,
            exception,
            operation,
            onAuthRequired
        )
        additionalHandler?.invoke(exception)
    }
}

/**
 * Extension function para operaciones con coroutines
 * Ejemplo de uso:
 *
 * lifecycleScope.launch {
 *     try {
 *         val result = db.collection("lugares").get().await()
 *         // Procesar resultado
 *     } catch (e: Exception) {
 *         e.handleFirestoreError(
 *             view = binding.root,
 *             operation = "cargar lugares",
 *             onAuthRequired = {
 *                 // Redirigir a login
 *             }
 *         )
 *     }
 * }
 */
fun Exception.handleFirestoreError(
    context: Context,
    view: android.view.View,
    operation: String,
    onAuthRequired: (() -> Unit)? = null
) {
    FirestoreErrorHandler.handleError(
        context,
        view,
        this,
        operation,
        onAuthRequired
    )
}

/**
 * Extension function para Throwable (usado con Result.onFailure)
 * Ejemplo de uso:
 *
 * result.onFailure { e ->
 *     e.handleFirestoreError(
 *         context = this@MyActivity,
 *         view = binding.root,
 *         operation = "cargar datos"
 *     )
 * }
 */
fun Throwable.handleFirestoreError(
    context: Context,
    view: android.view.View,
    operation: String,
    onAuthRequired: (() -> Unit)? = null
) {
    // Convertir Throwable a Exception si es posible
    val exception = when (this) {
        is Exception -> this
        else -> Exception(this.message, this)
    }

    FirestoreErrorHandler.handleError(
        context,
        view,
        exception,
        operation,
        onAuthRequired
    )
}
