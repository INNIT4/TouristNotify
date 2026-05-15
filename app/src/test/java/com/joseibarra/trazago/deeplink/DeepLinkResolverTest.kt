package com.joseibarra.trazago.deeplink

import android.app.Application
import android.net.Uri
import com.joseibarra.trazago.DeepLinkResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DeepLinkResolverTest {

    @Test
    fun `custom scheme TrazaGo resolves placeId`() {
        val uri = Uri.parse("trazago://place/plaza_de_armas")
        assertEquals("plaza_de_armas", DeepLinkResolver.resolvePlaceId(uri))
    }

    @Test
    fun `https scheme resolves placeId`() {
        val uri = Uri.parse("https://trazago.app/place/museo_costumbrista")
        assertEquals("museo_costumbrista", DeepLinkResolver.resolvePlaceId(uri))
    }

    @Test
    fun `http scheme resolves placeId`() {
        val uri = Uri.parse("http://trazago.app/place/mirador_el_perico")
        assertEquals("mirador_el_perico", DeepLinkResolver.resolvePlaceId(uri))
    }

    @Test
    fun `custom scheme wrong host returns null`() {
        val uri = Uri.parse("trazago://other/plaza_de_armas")
        assertNull(DeepLinkResolver.resolvePlaceId(uri))
    }

    @Test
    fun `https wrong host returns null`() {
        val uri = Uri.parse("https://example.com/place/plaza_de_armas")
        assertNull(DeepLinkResolver.resolvePlaceId(uri))
    }

    @Test
    fun `https wrong path segment returns null`() {
        val uri = Uri.parse("https://trazago.app/spot/plaza_de_armas")
        assertNull(DeepLinkResolver.resolvePlaceId(uri))
    }

    @Test
    fun `unknown scheme returns null`() {
        val uri = Uri.parse("ftp://trazago.app/place/plaza_de_armas")
        assertNull(DeepLinkResolver.resolvePlaceId(uri))
    }

    @Test
    fun `https path with extra segments still returns placeId`() {
        val uri = Uri.parse("https://trazago.app/place/templo_purisima/extra")
        assertEquals("templo_purisima", DeepLinkResolver.resolvePlaceId(uri))
    }
}
