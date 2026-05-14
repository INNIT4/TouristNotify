package com.joseibarra.trazago

import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await

data class UserProfileData(val nickname: String?, val photoUrl: String?, val email: String?)
data class UserStatsData(val routesCount: Int, val checkInsCount: Int, val favoritesCount: Int)

class UserProfileRepository(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
    private val storage: FirebaseStorage
) {
    suspend fun loadProfile(uid: String): Result<UserProfileData> = runCatching {
        val doc = db.collection("users").document(uid).get().await()
        UserProfileData(
            nickname = doc.getString("nickname"),
            photoUrl = doc.getString("photoUrl"),
            email = auth.currentUser?.email
        )
    }

    suspend fun loadStats(uid: String): Result<UserStatsData> = runCatching {
        coroutineScope {
            val routesDeferred = async {
                db.collection("rutas").whereEqualTo("id_usuario", uid).get().await()
            }
            val favoritesDeferred = async {
                db.collection(FirestoreCollections.USERS).document(uid)
                    .collection(FirestoreCollections.USER_FAVORITES).get().await()
            }
            val checkInsDeferred = async {
                db.collection(FirestoreCollections.CHECK_INS)
                    .whereEqualTo("userId", uid).limit(1000).get().await()
            }
            UserStatsData(
                routesCount = routesDeferred.await().size(),
                favoritesCount = favoritesDeferred.await().size(),
                checkInsCount = checkInsDeferred.await().size()
            )
        }
    }

    suspend fun saveNickname(uid: String, nickname: String): Result<Unit> = runCatching {
        val taken = db.collection("users").whereEqualTo("nickname", nickname).get().await()
            .documents.any { it.id != uid }
        if (taken) throw Exception("nickname_taken")
        db.collection("users").document(uid).update("nickname", nickname).await()
    }

    suspend fun uploadPhoto(uid: String, uri: Uri): Result<String> = runCatching {
        val ref = storage.reference.child("users/$uid/profile_photo.jpg")
        ref.putFile(uri).await()
        val downloadUrl = ref.downloadUrl.await().toString()
        db.collection("users").document(uid).update("photoUrl", downloadUrl).await()
        downloadUrl
    }
}
