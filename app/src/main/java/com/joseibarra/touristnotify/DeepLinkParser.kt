package com.joseibarra.touristnotify

import android.net.Uri
import android.util.Log

/**
 * Parser centralizado para deep links de la aplicación
 *
 * Soporta los siguientes formatos:
 * - Custom scheme: touristnotify://place/{placeId}
 * - HTTPS App Links: https://touristnotify.app/place/{placeId}
 *
 * Uso:
 * ```kotlin
 * val placeId = DeepLinkParser.parsePlaceId(uri)
 * ```
 */
object DeepLinkParser {

    private const val TAG = "DeepLinkParser"
    private const val SCHEME_CUSTOM = "touristnotify"
    private const val SCHEME_HTTPS = "https"
    private const val SCHEME_HTTP = "http"
    private const val HOST_CUSTOM = "place"
    private const val HOST_HTTPS = "touristnotify.app"
    private const val PATH_PLACE = "place"

    /**
     * Resultado del parseo de deep link
     */
    sealed class DeepLinkResult {
        data class Place(val placeId: String) : DeepLinkResult()
        object Invalid : DeepLinkResult()
        data class Error(val exception: Exception) : DeepLinkResult()
    }

    /**
     * Parsea un URI y extrae el placeId si es válido
     *
     * @param uri URI del deep link
     * @return placeId si se pudo parsear, null si el URI es inválido
     */
    fun parsePlaceId(uri: Uri?): String? {
        return when (val result = parse(uri)) {
            is DeepLinkResult.Place -> result.placeId
            is DeepLinkResult.Invalid -> null
            is DeepLinkResult.Error -> {
                if (BuildConfig.DEBUG) Log.e(TAG, "Error parsing deep link", result.exception)
                null
            }
        }
    }

    /**
     * Parsea un URI y devuelve un resultado detallado
     *
     * @param uri URI del deep link
     * @return DeepLinkResult indicando el resultado del parseo
     */
    fun parse(uri: Uri?): DeepLinkResult {
        if (uri == null) {
            return DeepLinkResult.Invalid
        }

        return try {
            when (uri.scheme) {
                // touristnotify://place/abc123
                SCHEME_CUSTOM -> parseCustomScheme(uri)

                // https://touristnotify.app/place/abc123
                SCHEME_HTTPS, SCHEME_HTTP -> parseHttpsScheme(uri)

                else -> {
                    if (BuildConfig.DEBUG) Log.w(TAG, "Unsupported scheme: ${uri.scheme}")
                    DeepLinkResult.Invalid
                }
            }
        } catch (e: Exception) {
            DeepLinkResult.Error(e)
        }
    }

    /**
     * Parsea URIs con custom scheme: touristnotify://place/{placeId}
     */
    private fun parseCustomScheme(uri: Uri): DeepLinkResult {
        // Validar host
        if (uri.host != HOST_CUSTOM) {
            if (BuildConfig.DEBUG) Log.w(TAG, "Invalid host for custom scheme: ${uri.host}")
            return DeepLinkResult.Invalid
        }

        // Extraer placeId del último segmento
        val placeId = uri.lastPathSegment
        if (placeId.isNullOrBlank()) {
            if (BuildConfig.DEBUG) Log.w(TAG, "Missing placeId in custom scheme URI")
            return DeepLinkResult.Invalid
        }

        if (BuildConfig.DEBUG) Log.d(TAG, "✓ Parsed custom scheme deep link: placeId = $placeId")
        return DeepLinkResult.Place(placeId)
    }

    /**
     * Parsea URIs con HTTPS: https://touristnotify.app/place/{placeId}
     */
    private fun parseHttpsScheme(uri: Uri): DeepLinkResult {
        // Validar dominio
        if (uri.host != HOST_HTTPS) {
            if (BuildConfig.DEBUG) Log.w(TAG, "Invalid host for HTTPS scheme: ${uri.host}")
            return DeepLinkResult.Invalid
        }

        // Validar estructura del path: /place/{placeId}
        val pathSegments = uri.pathSegments
        if (pathSegments.size < 2) {
            if (BuildConfig.DEBUG) Log.w(TAG, "Invalid path structure: expected /place/{placeId}")
            return DeepLinkResult.Invalid
        }

        if (pathSegments[0] != PATH_PLACE) {
            if (BuildConfig.DEBUG) Log.w(TAG, "Invalid path: expected 'place', got '${pathSegments[0]}'")
            return DeepLinkResult.Invalid
        }

        val placeId = pathSegments[1]
        if (placeId.isBlank()) {
            if (BuildConfig.DEBUG) Log.w(TAG, "Missing placeId in HTTPS URI")
            return DeepLinkResult.Invalid
        }

        if (BuildConfig.DEBUG) Log.d(TAG, "✓ Parsed HTTPS deep link: placeId = $placeId")
        return DeepLinkResult.Place(placeId)
    }

    /**
     * Valida si un URI es un deep link válido de la app
     */
    fun isValidDeepLink(uri: Uri?): Boolean {
        return parsePlaceId(uri) != null
    }

    /**
     * Construye un deep link personalizado para un lugar
     */
    fun buildCustomDeepLink(placeId: String): Uri {
        return Uri.parse("$SCHEME_CUSTOM://$HOST_CUSTOM/$placeId")
    }

    /**
     * Construye un App Link HTTPS para un lugar
     */
    fun buildHttpsDeepLink(placeId: String): Uri {
        return Uri.parse("$SCHEME_HTTPS://$HOST_HTTPS/$PATH_PLACE/$placeId")
    }
}
