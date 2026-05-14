package com.joseibarra.trazago.firestore

import com.google.firebase.firestore.FirebaseFirestoreException
import com.joseibarra.trazago.base.FirestoreEmulatorTest
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.runTest
import org.junit.Assert.fail
import org.junit.Test

class FirestoreSecurityRulesTest : FirestoreEmulatorTest() {

    @Test
    fun `non-admin_user_cannot_write_to_lugares_turisticos`() = runTest {
        val uid = createTestUser("regular@test.com", "pass123456")

        val placeData = mapOf(
            "nombre" to "Lugar de prueba",
            "categoria" to "Test",
            "descripcion" to "Solo para tests",
            "latitude" to 27.0275,
            "longitude" to -108.94
        )

        try {
            db.collection("lugares_turisticos")
                .document("test-place-001")
                .set(placeData)
                .await()

            fail("Expected PERMISSION_DENIED but write succeeded. Check firestore.rules.")
        } catch (e: FirebaseFirestoreException) {
            val isPermissionDenied = e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
            assert(isPermissionDenied) {
                "Expected PERMISSION_DENIED but got ${e.code}: ${e.message}"
            }
        } finally {
            auth.signOut()
        }
    }

    @Test
    fun `unauthenticated_user_cannot_read_another_users_data`() = runTest {
        auth.signOut()

        try {
            db.collection("usuarios")
                .document("some-other-uid")
                .collection("favoritos")
                .get()
                .await()

            fail("Expected PERMISSION_DENIED but read succeeded.")
        } catch (e: FirebaseFirestoreException) {
            assert(e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                "Expected PERMISSION_DENIED, got ${e.code}"
            }
        }
    }
}
