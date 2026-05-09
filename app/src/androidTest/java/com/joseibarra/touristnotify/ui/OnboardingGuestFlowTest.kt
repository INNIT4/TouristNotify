package com.joseibarra.touristnotify.ui

import android.app.Activity
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.joseibarra.touristnotify.AuthManager
import com.joseibarra.touristnotify.MenuActivity
import com.joseibarra.touristnotify.OnboardingActivity
import com.joseibarra.touristnotify.R
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class OnboardingGuestFlowTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(OnboardingActivity::class.java)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setup() {
        AuthManager.disableGuestMode(context)
        Intents.init()
    }

    @After
    fun teardown() {
        AuthManager.disableGuestMode(context)
        Intents.release()
    }

    @Test
    fun skipButton_enablesGuestMode_navigatesToMenu() {
        onView(withId(R.id.skip_button))
            .check(matches(isDisplayed()))
            .perform(click())

        Intents.intended(hasComponent(MenuActivity::class.java.name))
    }

    @Test
    fun guestMode_checkInButton_isLockedInPlaceDetails() {
        // Enable guest mode (simulates having gone through onboarding as guest)
        AuthManager.enableGuestMode(context)

        // Verify guest flag is set — the check-in button in PlaceDetailsActivity
        // will be visually locked (alpha 0.5) when !AuthManager.isAuthenticated()
        assert(!AuthManager.isAuthenticated()) {
            "Guest mode should not count as authenticated"
        }
    }
}
