package com.joseibarra.trazago.base

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.AfterClass
import org.junit.BeforeClass

abstract class FirestoreEmulatorTest {

    companion object {
        const val EMULATOR_HOST = "10.0.2.2"
        const val FIRESTORE_PORT = 8080
        const val AUTH_PORT = 9099

        @JvmStatic
        protected lateinit var db: FirebaseFirestore

        @JvmStatic
        protected lateinit var auth: FirebaseAuth

        @BeforeClass
        @JvmStatic
        fun setupEmulator() {
            db = FirebaseFirestore.getInstance()
            db.useEmulator(EMULATOR_HOST, FIRESTORE_PORT)
            db.firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(false)
                .build()

            auth = FirebaseAuth.getInstance()
            auth.useEmulator(EMULATOR_HOST, AUTH_PORT)
        }

        @AfterClass
        @JvmStatic
        fun teardownEmulator() {
            runBlocking {
                try { db.clearPersistence().await() } catch (_: Exception) {}
            }
        }
    }

    protected suspend fun createTestUser(
        email: String = "test@example.com",
        password: String = "test123456"
    ): String {
        val result = auth.createUserWithEmailAndPassword(email, password).await()
        return result.user!!.uid
    }
}
