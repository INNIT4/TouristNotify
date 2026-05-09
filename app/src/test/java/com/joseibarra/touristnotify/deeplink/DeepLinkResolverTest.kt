package com.joseibarra.touristnotify.deeplink

import android.net.Uri
import com.joseibarra.touristnotify.DeepLinkResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeepLinkResolverTest {

    @Test
    fun `custom scheme touristnotify resolves placeId`() {
        val uri = Uri.parse("touristnotify://place/plaza_de_armas")
        assertEquals("plaza_de_armas", DeepLinkResolver.resolvePlaceId(uri))
    }

    @Test
    fun `https scheme resolves placeId`() {
        val uri = Uri.parse("https://touristnotify.app/place/museo_costumbrista")
        assertEquals("museo_costumbrista", DeepLinkResolver.resolvePlaceId(uri))
    }

    @Test
    fun `http scheme resolves placeId`() {
        val uri = Uri.parse("http://touristnotify.app/place/mirador_el_perico")
        assertEquals("mirador_el_perico", DeepLinkResolver.resolvePlaceId(uri))
    }

    @Test
    fun `custom scheme wrong host returns null`() {
        val uri = Uri.parse("touristnotify://other/plaza_de_armas")
        assertNull(DeepLinkResolver.resolvePlaceId(uri))
    }

    @Test
    fun `https wrong host returns null`() {
        val uri = Uri.parse("https://example.com/place/plaza_de_armas")
        assertNull(DeepLinkResolver.resolvePlaceId(uri))
    }

    @Test
    fun `https wrong path segment returns null`() {
        val uri = Uri.parse("https://touristnotify.app/spot/plaza_de_armas")
        assertNull(DeepLinkResolver.resolvePlaceId(uri))
    }

    @Test
    fun `unknown scheme returns null`() {
        val uri = Uri.parse("ftp://touristnotify.app/place/plaza_de_armas")
        assertNull(DeepLinkResolver.resolvePlaceId(uri))
    }

    @Test
    fun `https path with extra segments still returns placeId`() {
        val uri = Uri.parse("https://touristnotify.app/place/templo_purisima/extra")
        assertEquals("templo_purisima", DeepLinkResolver.resolvePlaceId(uri))
    }
}
