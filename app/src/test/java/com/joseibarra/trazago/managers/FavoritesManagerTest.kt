package com.joseibarra.trazago.managers

import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.joseibarra.trazago.FavoritesManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FavoritesManagerTest {

    private lateinit var mockFirestore: FirebaseFirestore
    private lateinit var mockAuth: FirebaseAuth
    private lateinit var mockUser: FirebaseUser
    private lateinit var mockCollection: CollectionReference
    private lateinit var mockUserDoc: DocumentReference
    private lateinit var mockFavoritesCollection: CollectionReference
    private lateinit var mockPlaceDoc: DocumentReference
    private lateinit var favoritesManager: FavoritesManager

    @Before
    fun setup() {
        mockFirestore = mockk(relaxed = true)
        mockAuth = mockk()
        mockUser = mockk()
        mockCollection = mockk(relaxed = true)
        mockUserDoc = mockk(relaxed = true)
        mockFavoritesCollection = mockk(relaxed = true)
        mockPlaceDoc = mockk(relaxed = true)

        favoritesManager = FavoritesManager(db = mockFirestore, auth = mockAuth)
    }

    @Test
    fun `addFavorite writes to users collection not usuarios`() = runTest {
        val uid = "test-uid-123"
        every { mockAuth.currentUser } returns mockUser
        every { mockUser.uid } returns uid
        every { mockFirestore.collection("users") } returns mockCollection
        every { mockCollection.document(uid) } returns mockUserDoc
        every { mockUserDoc.collection("favorites") } returns mockFavoritesCollection
        every { mockFavoritesCollection.document("place-abc") } returns mockPlaceDoc
        every { mockPlaceDoc.set(any()) } returns Tasks.forResult(null)

        val result = favoritesManager.addFavorite("place-abc", "Catedral de Álamos", "Historia")

        assertTrue("Expected success but got: $result", result.isSuccess)
        verify(exactly = 1) { mockFirestore.collection("users") }
        verify(exactly = 0) { mockFirestore.collection("usuarios") }
    }

    @Test
    fun `addFavorite writes to favorites subcollection not favoritos`() = runTest {
        val uid = "test-uid-456"
        every { mockAuth.currentUser } returns mockUser
        every { mockUser.uid } returns uid
        every { mockFirestore.collection("users") } returns mockCollection
        every { mockCollection.document(uid) } returns mockUserDoc
        every { mockUserDoc.collection("favorites") } returns mockFavoritesCollection
        every { mockFavoritesCollection.document(any()) } returns mockPlaceDoc
        every { mockPlaceDoc.set(any()) } returns Tasks.forResult(null)

        favoritesManager.addFavorite("place-xyz", "Museo Kosterlitzky", "Cultura")

        verify(exactly = 1) { mockUserDoc.collection("favorites") }
        verify(exactly = 0) { mockUserDoc.collection("favoritos") }
    }

    @Test
    fun `addFavorite returns failure when user is not authenticated`() = runTest {
        every { mockAuth.currentUser } returns null

        val result = favoritesManager.addFavorite("place-abc", "Catedral", "Historia")

        assertTrue("Expected failure", result.isFailure)
        assertEquals("Usuario no autenticado", result.exceptionOrNull()?.message)
    }

    @Test
    fun `removeFavorite returns failure when user is not authenticated`() = runTest {
        every { mockAuth.currentUser } returns null

        val result = favoritesManager.removeFavorite("place-abc")

        assertTrue("Expected failure", result.isFailure)
    }

    @Test
    fun `getFavorites returns failure when user is not authenticated`() = runTest {
        every { mockAuth.currentUser } returns null

        val result = favoritesManager.getFavorites()

        assertTrue("Expected failure", result.isFailure)
        assertEquals("Usuario no autenticado", result.exceptionOrNull()?.message)
    }

    @Test
    fun `isFavorite returns false when user is not authenticated`() = runTest {
        every { mockAuth.currentUser } returns null

        val result = favoritesManager.isFavorite("place-abc")

        assertFalse(result)
    }
}
