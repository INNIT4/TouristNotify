package com.joseibarra.trazago

import android.net.Uri
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await

class AlreadyReportedException : Exception("Ya reportaste esta publicación")

data class NewPostDraft(
    val authorId: String,
    val authorName: String,
    val authorPhotoUrl: String,
    val title: String,
    val content: String,
    val placeId: String = "",
    val placeName: String = ""
)

object CommunityRepository {

    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val posts get() = db.collection(FirestoreCollections.COMMUNITY_POSTS)

    // ── Crear ─────────────────────────────────────────────────────────────────

    suspend fun createPost(draft: NewPostDraft, photoUris: List<Uri>): Result<String> =
        runCatching {
            val postRef = posts.document()
            val postId = postRef.id

            // Paso 1: doc con photoUrls vacío (recuperable si falla la subida)
            postRef.set(
                CommunityPost(
                    id = postId,
                    authorId = draft.authorId,
                    authorName = draft.authorName,
                    authorPhotoUrl = draft.authorPhotoUrl,
                    title = draft.title,
                    content = draft.content,
                    photoUrls = emptyList(),
                    taggedPlaceId = draft.placeId,
                    taggedPlaceName = draft.placeName
                )
            ).await()

            // Paso 2: subir fotos y actualizar URLs
            if (photoUris.isNotEmpty()) {
                val urls = photoUris.mapIndexed { i, uri ->
                    val ref = storage.reference.child("community/$postId/photo_$i.jpg")
                    ref.putFile(uri).await()
                    ref.downloadUrl.await().toString()
                }
                postRef.update("photoUrls", urls).await()
            }
            postId
        }

    // ── Leer ──────────────────────────────────────────────────────────────────

    fun attachFeedListener(
        pageSize: Long,
        onUpdate: (List<CommunityPost>, DocumentSnapshot?) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration = posts
        .whereEqualTo("isHidden", false)
        .orderBy("createdAt", Query.Direction.DESCENDING)
        .limit(pageSize)
        .addSnapshotListener { snap, err ->
            if (err != null) { onError(err); return@addSnapshotListener }
            val docs = snap?.documents ?: emptyList()
            onUpdate(
                docs.map { d -> d.toObject(CommunityPost::class.java)!!.copy(id = d.id) },
                docs.lastOrNull()
            )
        }

    suspend fun loadNextPage(
        cursor: DocumentSnapshot,
        pageSize: Long
    ): Result<Pair<List<CommunityPost>, DocumentSnapshot?>> = runCatching {
        val snap = posts
            .whereEqualTo("isHidden", false)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .startAfter(cursor)
            .limit(pageSize)
            .get().await()
        val items = snap.documents.map { d ->
            d.toObject(CommunityPost::class.java)!!.copy(id = d.id)
        }
        items to snap.documents.lastOrNull()
    }

    suspend fun getPost(postId: String): Result<CommunityPost> = runCatching {
        val doc = posts.document(postId).get().await()
        doc.toObject(CommunityPost::class.java)!!.copy(id = doc.id)
    }

    suspend fun getComments(postId: String): Result<List<PostComment>> = runCatching {
        posts.document(postId)
            .collection(FirestoreCollections.POST_COMMENTS_SUBCOLLECTION)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .get().await()
            .documents.map { d ->
                d.toObject(PostComment::class.java)!!.copy(id = d.id)
            }
    }

    suspend fun isLikedBy(postId: String, userId: String): Result<Boolean> = runCatching {
        posts.document(postId)
            .collection(FirestoreCollections.POST_LIKES_SUBCOLLECTION)
            .document(userId)
            .get().await()
            .exists()
    }

    // ── Interacciones ─────────────────────────────────────────────────────────

    suspend fun togglePostLike(postId: String, userId: String): Result<Boolean> = runCatching {
        val postRef = posts.document(postId)
        val likeRef = postRef
            .collection(FirestoreCollections.POST_LIKES_SUBCOLLECTION)
            .document(userId)
        db.runTransaction { tx ->
            if (!tx.get(postRef).exists()) error("Publicación no encontrada")
            val nowLiked: Boolean
            if (tx.get(likeRef).exists()) {
                tx.delete(likeRef)
                tx.update(postRef, "likeCount", FieldValue.increment(-1))
                nowLiked = false
            } else {
                tx.set(likeRef, mapOf("createdAt" to FieldValue.serverTimestamp()))
                tx.update(postRef, "likeCount", FieldValue.increment(1))
                nowLiked = true
            }
            nowLiked
        }.await()
    }

    suspend fun addComment(postId: String, comment: PostComment): Result<Unit> = runCatching {
        val postRef = posts.document(postId)
        val commentRef = postRef
            .collection(FirestoreCollections.POST_COMMENTS_SUBCOLLECTION)
            .document()
        db.runTransaction { tx ->
            if (!tx.get(postRef).exists()) error("Publicación no encontrada")
            tx.set(commentRef, comment.copy(id = commentRef.id, postId = postId))
            tx.update(postRef, "commentCount", FieldValue.increment(1))
            null
        }.await()
    }

    suspend fun deleteComment(postId: String, commentId: String): Result<Unit> = runCatching {
        val postRef = posts.document(postId)
        val commentRef = postRef
            .collection(FirestoreCollections.POST_COMMENTS_SUBCOLLECTION)
            .document(commentId)
        db.runTransaction { tx ->
            if (tx.get(commentRef).exists()) {
                tx.delete(commentRef)
                tx.update(postRef, "commentCount", FieldValue.increment(-1))
            }
            null
        }.await()
    }

    suspend fun reportPost(
        postId: String,
        reporterId: String,
        reason: String,
        detail: String
    ): Result<Unit> = runCatching {
        val reportId = "${postId}_$reporterId"
        val postRef = posts.document(postId)
        val reportRef = db.collection(FirestoreCollections.POST_REPORTS).document(reportId)
        db.runTransaction { tx ->
            if (tx.get(reportRef).exists()) throw AlreadyReportedException()
            tx.set(
                reportRef,
                PostReport(
                    id = reportId,
                    postId = postId,
                    reporterId = reporterId,
                    reason = reason,
                    detail = detail,
                    status = "pending"
                )
            )
            tx.update(postRef, "reportCount", FieldValue.increment(1))
            null
        }.await()
    }

    // ── Editar ────────────────────────────────────────────────────────────────

    suspend fun updatePost(
        postId: String,
        title: String,
        content: String
    ): Result<Unit> = runCatching {
        posts.document(postId)
            .update(mapOf("title" to title, "content" to content))
            .await()
    }

    // ── Eliminar ──────────────────────────────────────────────────────────────

    suspend fun getLikedPostIds(userId: String, postIds: List<String>): Set<String> {
        if (postIds.isEmpty()) return emptySet()
        val liked = mutableSetOf<String>()
        postIds.forEach { postId ->
            runCatching {
                if (posts.document(postId)
                        .collection(FirestoreCollections.POST_LIKES_SUBCOLLECTION)
                        .document(userId).get().await().exists()
                ) liked.add(postId)
            }
        }
        return liked
    }

    suspend fun deletePost(postId: String, photoUrls: List<String> = emptyList()): Result<Unit> = runCatching {
        // Eliminar fotos de Storage (best-effort)
        runCatching {
            val storageRef = storage.reference.child("community/$postId")
            storageRef.listAll().await().items.forEach { it.delete().await() }
        }
        // Eliminar subcollecciones (comentarios y likes)
        val commentsRef = posts.document(postId)
            .collection(FirestoreCollections.POST_COMMENTS_SUBCOLLECTION)
        commentsRef.get().await().documents.forEach { it.reference.delete().await() }

        val likesRef = posts.document(postId)
            .collection(FirestoreCollections.POST_LIKES_SUBCOLLECTION)
        likesRef.get().await().documents.forEach { it.reference.delete().await() }

        posts.document(postId).delete().await()
    }

    // ── Admin ─────────────────────────────────────────────────────────────────

    suspend fun hidePostAsAdmin(postId: String): Result<Unit> = runCatching {
        posts.document(postId).update("isHidden", true).await()
    }

    suspend fun getPendingReports(): Result<List<PostReport>> = runCatching {
        db.collection(FirestoreCollections.POST_REPORTS)
            .whereEqualTo("status", "pending")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get().await()
            .documents.map { d ->
                d.toObject(PostReport::class.java)!!.copy(id = d.id)
            }
    }

    suspend fun dismissReport(reportId: String): Result<Unit> = runCatching {
        db.collection(FirestoreCollections.POST_REPORTS)
            .document(reportId)
            .update("status", "resolved")
            .await()
    }
}
