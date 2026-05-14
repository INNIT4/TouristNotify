package com.joseibarra.trazago

import android.net.Uri

object DeepLinkResolver {
    fun resolvePlaceId(uri: Uri): String? = runCatching {
        when (uri.scheme) {
            "trazago" -> if (uri.host == "place") uri.lastPathSegment else null
            "https", "http" -> {
                if (uri.host != "trazago.app") return null
                val segs = uri.pathSegments
                if (segs.size >= 2 && segs[0] == "place") segs[1] else null
            }
            else -> null
        }
    }.getOrNull()

    fun resolveEventId(uri: Uri): String? = runCatching {
        when (uri.scheme) {
            "trazago" -> if (uri.host == "event") uri.lastPathSegment else null
            "https", "http" -> {
                if (uri.host != "trazago.app") return null
                val segs = uri.pathSegments
                if (segs.size >= 2 && segs[0] == "event") segs[1] else null
            }
            else -> null
        }
    }.getOrNull()
}
