package com.joseibarra.touristnotify.admin

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.WriteBatch
import com.joseibarra.touristnotify.FirestoreCollections
import com.joseibarra.touristnotify.TouristSpot
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await

private const val TAG = "MigrationCoordinator"

/**
 * Orquesta la migración masiva de datos de lugares:
 *
 * 1. Backup: copia coleccion "lugares" a "lugares_backup_TIMESTAMP" (batches de 500).
 * 2. Wipe: borra todos los documentos originales (solo tras confirmacion doble).
 * 3. Repoplar: los datos nuevos los importa el admin via AdminPlacesActivity
 *    (Places API + importPlaceToFirebase) + bulk enrichment.
 *
 * Llamar desde [AdminMigrationActivity] en coroutine.
 */
object PlaceMigrationCoordinator {

    private val db = FirebaseFirestore.getInstance()

    sealed class MigrationEvent {
        data class Progress(val message: String, val done: Int, val total: Int) : MigrationEvent()
        data class BackupComplete(val backupCollection: String, val count: Int) : MigrationEvent()
        data class WipeComplete(val deletedCount: Int) : MigrationEvent()
        data class Error(val message: String, val cause: Throwable? = null) : MigrationEvent()
    }

    /**
     * Paso 1: backup de todos los lugares a coleccion "lugares_backup_TIMESTAMP".
     * @return nombre de la coleccion de backup creada.
     */
    suspend fun backupPlaces(
        onEvent: (MigrationEvent) -> Unit
    ): String {
        val timestamp = System.currentTimeMillis()
        val backupCollection = "lugares_backup_$timestamp"

        try {
            val snapshot = db.collection(FirestoreCollections.PLACES).get().await()
            val docs = snapshot.documents
            onEvent(MigrationEvent.Progress("Iniciando backup de ${docs.size} lugares…", 0, docs.size))

            // Firestore WriteBatch permite max 500 ops por batch
            val batches = docs.chunked(499)
            var processed = 0
            batches.forEach { chunk ->
                val batch: WriteBatch = db.batch()
                chunk.forEach { doc ->
                    val data = doc.data ?: return@forEach
                    val ref = db.collection(backupCollection).document(doc.id)
                    batch.set(ref, data)
                }
                batch.commit().await()
                processed += chunk.size
                onEvent(MigrationEvent.Progress("Backup: $processed/${docs.size}", processed, docs.size))
            }

            Log.i(TAG, "Backup completado: $backupCollection (${docs.size} docs)")
            onEvent(MigrationEvent.BackupComplete(backupCollection, docs.size))
            return backupCollection
        } catch (e: Exception) {
            Log.e(TAG, "Error en backup", e)
            onEvent(MigrationEvent.Error("Backup falló: ${e.message}", e))
            throw e
        }
    }

    /**
     * Paso 2 — Wipe de la colección original.
     * Solo llamar tras doble confirmación y backup exitoso.
     */
    suspend fun wipePlaces(
        onEvent: (MigrationEvent) -> Unit
    ) {
        try {
            val snapshot = db.collection(FirestoreCollections.PLACES).get().await()
            val docs = snapshot.documents
            onEvent(MigrationEvent.Progress("Borrando ${docs.size} lugares…", 0, docs.size))

            val batches = docs.chunked(499)
            var deleted = 0
            batches.forEach { chunk ->
                val batch: WriteBatch = db.batch()
                chunk.forEach { doc -> batch.delete(doc.reference) }
                batch.commit().await()
                deleted += chunk.size
                onEvent(MigrationEvent.Progress("Borrado: $deleted/${docs.size}", deleted, docs.size))
            }

            Log.i(TAG, "Wipe completado: $deleted documentos eliminados")
            onEvent(MigrationEvent.WipeComplete(deleted))
        } catch (e: Exception) {
            Log.e(TAG, "Error en wipe", e)
            onEvent(MigrationEvent.Error("Wipe falló: ${e.message}", e))
            throw e
        }
    }

    /**
     * Restaura desde un backup si el wipe fue accidental.
     * @param backupCollection nombre devuelto por [backupPlaces].
     */
    suspend fun restoreFromBackup(
        backupCollection: String,
        onEvent: (MigrationEvent) -> Unit
    ) {
        try {
            val snapshot = db.collection(backupCollection).get().await()
            val docs = snapshot.documents
            onEvent(MigrationEvent.Progress("Restaurando ${docs.size} lugares…", 0, docs.size))

            val batches = docs.chunked(499)
            var restored = 0
            batches.forEach { chunk ->
                val batch: WriteBatch = db.batch()
                chunk.forEach { doc ->
                    val data = doc.data ?: return@forEach
                    val ref = db.collection(FirestoreCollections.PLACES).document(doc.id)
                    batch.set(ref, data)
                }
                batch.commit().await()
                restored += chunk.size
                onEvent(MigrationEvent.Progress("Restaurado: $restored/${docs.size}", restored, docs.size))
            }
            onEvent(MigrationEvent.Progress("Restauración completa: $restored lugares", restored, docs.size))
        } catch (e: Exception) {
            onEvent(MigrationEvent.Error("Restauración falló: ${e.message}", e))
            throw e
        }
    }

    /** Lista los backups disponibles (colecciones cuyo nombre empieza con `lugares_backup_`). */
    suspend fun listBackups(): List<String> {
        // Firestore no tiene API para listar colecciones en el cliente movil.
        // Esta info la gestiona el admin via consola Firebase o Cloud Function.
        // Devolvemos lista vacia como placeholder.
        return emptyList()
    }
}
