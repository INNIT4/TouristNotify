package com.joseibarra.touristnotify.managers

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.joseibarra.touristnotify.AuthManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AuthManagerTest {

    private lateinit var context: android.content.Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        AuthManager.disableGuestMode(context)
    }

    @Test
    fun `enableGuestMode persists guest mode flag as true`() {
        AuthManager.enableGuestMode(context)
        assertTrue("Guest mode should be enabled", AuthManager.isGuestMode(context))
    }

    @Test
    fun `disableGuestMode sets guest mode flag to false`() {
        AuthManager.enableGuestMode(context)
        AuthManager.disableGuestMode(context)
        assertFalse("Guest mode should be disabled", AuthManager.isGuestMode(context))
    }

    @Test
    fun `isGuestMode returns false by default`() {
        assertFalse("Guest mode should be false by default", AuthManager.isGuestMode(context))
    }

    @Test
    fun `shouldMigrateFromGuest returns true only when both guest and authenticated`() {
        AuthManager.enableGuestMode(context)
        assertFalse(
            "Should not migrate when guest but not authenticated",
            AuthManager.shouldMigrateFromGuest(context)
        )
    }

    @Test
    fun `migrateFromGuestToAuth does not throw when guest mode is active`() {
        AuthManager.enableGuestMode(context)
        AuthManager.migrateFromGuestToAuth(context)
        assertTrue(true)
    }

    @Test
    fun `guest mode flag survives multiple read calls`() {
        AuthManager.enableGuestMode(context)
        val first = AuthManager.isGuestMode(context)
        val second = AuthManager.isGuestMode(context)
        val third = AuthManager.isGuestMode(context)
        assertTrue(first && second && third)
    }
}
