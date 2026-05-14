package com.joseibarra.trazago.managers

import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Transaction
import com.joseibarra.trazago.CheckInManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CheckInManagerTest {

    private lateinit var mockFirestore: FirebaseFirestore
    private lateinit var mockAuth: FirebaseAuth
    private lateinit var mockUser: FirebaseUser
    private lateinit var mockCollection: CollectionReference
    private lateinit var mockQuery: Query
    private lateinit var mockSnapshot: QuerySnapshot

    @Before
    fun setup() {
        mockFirestore = mockk(relaxed = true)
        mockAuth = mockk()
        mockUser = mockk()
        mockCollection = mockk(relaxed = true)
        mockQuery = mockk(relaxed = true)
        mockSnapshot = mockk()

        every { mockAuth.currentUser } returns mockUser
        every { mockUser.uid } returns "user-111"
        every { mockUser.displayName } returns "Test User"

        every { mockFirestore.collection("checkIns") } returns mockCollection
        every { mockCollection.whereEqualTo("userId", any<String>()) } returns mockQuery
        every { mockQuery.whereEqualTo("placeId", any<String>()) } returns mockQuery
        every { mockQuery.whereGreaterThan(any<String>(), any()) } returns mockQuery
        every { mockQuery.get() } returns Tasks.forResult(mockSnapshot)
    }

    @Test
    fun `hasCheckedInToday returns true when check-in exists within 24 hours`() = runTest {
        val now = System.currentTimeMillis()
        val fixedClock: () -> Long = { now }
        every { mockSnapshot.isEmpty } returns false

        val manager = CheckInManager(db = mockFirestore, auth = mockAuth, clock = fixedClock)
        val result = manager.hasCheckedInToday("place-001")

        assertTrue("Expected true when snapshot is not empty", result)
    }

    @Test
    fun `hasCheckedInToday returns false when no check-in exists within 24 hours`() = runTest {
        val now = System.currentTimeMillis()
        val fixedClock: () -> Long = { now }
        every { mockSnapshot.isEmpty } returns true

        val manager = CheckInManager(db = mockFirestore, auth = mockAuth, clock = fixedClock)
        val result = manager.hasCheckedInToday("place-001")

        assertFalse("Expected false when snapshot is empty", result)
    }

    @Test
    fun `hasCheckedInToday returns false when user is not authenticated`() = runTest {
        every { mockAuth.currentUser } returns null

        val manager = CheckInManager(db = mockFirestore, auth = mockAuth)
        val result = manager.hasCheckedInToday("place-001")

        assertFalse(result)
    }
}
