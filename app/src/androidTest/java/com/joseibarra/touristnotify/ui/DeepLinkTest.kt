package com.joseibarra.touristnotify.ui

import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import com.joseibarra.touristnotify.PlaceDetailsActivity
import com.joseibarra.touristnotify.R
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@MediumTest
class DeepLinkTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun deepLink_validPlaceId_opensPlaceDetailsActivity() {
        val validId = "test-place-001"
        val deepLinkUri = Uri.parse("touristnotify://place/$validId")
        val intent = Intent(Intent.ACTION_VIEW, deepLinkUri).apply {
            setPackage(context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        ActivityScenario.launch<PlaceDetailsActivity>(intent).use {
            // Activity should open and show its root content; Firestore may not
            // return data in test environment — we just verify routing works.
            onView(withId(R.id.place_name_text_view))
                .check(matches(isDisplayed()))
        }
    }

    @Test
    fun deepLink_invalidPlaceId_finishesActivity() {
        val maliciousId = "../../etc/passwd"
        val deepLinkUri = Uri.parse("touristnotify://place/$maliciousId")
        val intent = Intent(Intent.ACTION_VIEW, deepLinkUri).apply {
            setPackage(context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        ActivityScenario.launch<PlaceDetailsActivity>(intent).use { scenario ->
            // Activity with invalid ID calls finish() immediately
            scenario.onActivity { activity ->
                assert(activity.isFinishing) {
                    "PlaceDetailsActivity should finish when placeId fails PLACE_ID_PATTERN"
                }
            }
        }
    }

    @Test
    fun deepLink_emptyId_finishesActivity() {
        val deepLinkUri = Uri.parse("touristnotify://place/")
        val intent = Intent(Intent.ACTION_VIEW, deepLinkUri).apply {
            setPackage(context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        ActivityScenario.launch<PlaceDetailsActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                assert(activity.isFinishing) {
                    "PlaceDetailsActivity should finish when placeId is empty"
                }
            }
        }
    }
}
