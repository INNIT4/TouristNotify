package com.joseibarra.touristnotify

import android.content.Context
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await

class AccountManager(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
    private val storage: FirebaseStorage
) {
    suspend fun changePassword(
        email: String,
        currentPassword: String,
        newPassword: String
    ): Result<Unit> = runCatching {
        val user = auth.currentUser!!
        user.reauthenticate(EmailAuthProvider.getCredential(email, currentPassword)).await()
        user.updatePassword(newPassword).await()
    }

    suspend fun deleteAccount(email: String, password: String, context: Context): Result<Unit> = runCatching {
        val user = auth.currentUser!!
        user.reauthenticate(EmailAuthProvider.getCredential(email, password)).await()

        val uid = user.uid
        val userRef = db.collection("users").document(uid)

        for (sub in listOf("favorites", "stats", "usage")) {
            batchDelete(userRef.collection(sub).get().await().documents.map { it.reference })
        }
        batchDelete(db.collection("rutas").whereEqualTo("id_usuario", uid).get().await().documents.map { it.reference })
        batchDelete(db.collection(FirestoreCollections.CHECK_INS).whereEqualTo("userId", uid).get().await().documents.map { it.reference })
        batchDelete(db.collectionGroup("reviews").whereEqualTo("userId", uid).get().await().documents.map { it.reference })
        batchDelete(db.collection("notifications").whereEqualTo("userId", uid).get().await().documents.map { it.reference })

        try {
            storage.reference.child("users/$uid").delete().await()
        } catch (_: Exception) {}

        userRef.delete().await()
        user.delete().await()
    }

    private suspend fun batchDelete(refs: List<DocumentReference>) {
        refs.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { batch.delete(it) }
            batch.commit().await()
        }
    }
}
