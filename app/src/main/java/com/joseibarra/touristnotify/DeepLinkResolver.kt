package com.joseibarra.touristnotify

import android.net.Uri

object DeepLinkResolver {
    fun resolvePlaceId(uri: Uri): String? = runCatching {
        when (uri.scheme) {
            "touristnotify" -> if (uri.host == "place") uri.lastPathSegment else null
            "https", "http" -> {
                if (uri.host != "touristnotify.app") return null
                val segs = uri.pathSegments
                if (segs.size >= 2 && segs[0] == "place") segs[1] else null
            }
            else -> null
        }
    }.getOrNull()
}
