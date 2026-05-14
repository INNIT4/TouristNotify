package com.joseibarra.trazago

import android.net.Uri

/**
 * Whitelist de hosts permitidos para cargar imágenes vía Glide.
 *
 * Glide acepta por defecto URLs `file://`, `content://`, `data://`. Eso
 * significa que si un atacante con privilegios de admin (o mediante una
 * inyección a la base de datos de lugares) define `imagenUrl =
 * "file:///data/data/.../shared_prefs/TrazaGoPrefs.xml"`, Glide
 * intentará cargarlo y podría filtrar contenido. Esta clase normaliza
 * y valida URLs antes de pasarlas a `Glide.load(...)`.
 *
 * Hosts permitidos:
 * - Firebase Storage (donde subimos fotos de lugares y avatares)
 * - Google User Content (avatares de Google Sign-In)
 * - Wikipedia/Wikimedia (referencias en blog y eventos)
 */
object SafeImageUrl {

    private val ALLOWED_HOSTS = setOf(
        "firebasestorage.googleapis.com",
        "lh3.googleusercontent.com",
        "lh4.googleusercontent.com",
        "lh5.googleusercontent.com",
        "lh6.googleusercontent.com",
        "upload.wikimedia.org",
        "commons.wikimedia.org"
    )

    /**
     * Devuelve la URL si es segura para cargar con Glide; null si:
     * - es null/blank
     * - tiene un scheme distinto a https
     * - tiene un host fuera del whitelist
     *
     * Caller debe usar el resultado o un placeholder en caso de null.
     */
    fun sanitize(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val uri = try {
            Uri.parse(url)
        } catch (_: Exception) {
            return null
        }
        if (uri.scheme?.lowercase() != "https") return null
        val host = uri.host?.lowercase() ?: return null
        if (host !in ALLOWED_HOSTS) return null
        return url
    }
}
