package com.joseibarra.trazago

import android.animation.ValueAnimator
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.view.animation.AccelerateDecelerateInterpolator
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.gms.maps.model.RoundCap
import com.google.maps.android.PolyUtil
import com.google.maps.android.SphericalUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.MessageDigest

class RoutePolylineManager(
    private val map: GoogleMap,
    private val lifecycleScope: CoroutineScope,
    private val apiKey: String,
    private val onError: (String) -> Unit,
    private val http: OkHttpClient,
    context: Context
) {
    private var routePolyline: Polyline? = null
    private var navigationPolyline: Polyline? = null
    private var animator: ValueAnimator? = null
    private var alive = true

    private val packageName: String = context.packageName
    private val certSha1: String = computeCertSha1(context)

    fun drawTouristRoute(spots: List<TouristSpot>) {
        if (spots.size < 2) return
        routePolyline?.remove()
        routePolyline = null
        val validSpots = spots.filter { it.ubicacion != null }
        if (validSpots.size < 2) return

        val url = buildDirectionsApiUrl(validSpots)
        val request = androidAuthRequest(url)

        Log.d("RoutePolylineManager", "Directions API URL (ruta): $url")
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = http.newCall(request).execute()
                val rawBody = response.body?.string() ?: ""
                Log.d("RoutePolylineManager", "HTTP ${response.code} — body[0..500]: ${rawBody.take(500)}")
                if (rawBody.isBlank()) throw Exception("Respuesta vacía (HTTP ${response.code})")
                val json = JSONObject(rawBody)
                val status = json.optString("status")
                Log.d("RoutePolylineManager", "Directions status: $status")
                if (status != "OK") throw Exception("Directions API: $status — ${json.optString("error_message")}")
                val encoded = json.getJSONArray("routes")
                    .getJSONObject(0)
                    .getJSONObject("overview_polyline")
                    .getString("points")
                Log.d("RoutePolylineManager", "Encoded polyline length: ${encoded.length}")
                withContext(Dispatchers.Main) {
                    val path = PolyUtil.decode(encoded)
                    Log.d("RoutePolylineManager", "Decoded ${path.size} LatLng points")
                    animatePolylineDraw(path, 0xCC1A73E8.toInt(), 14f) { polyline ->
                        routePolyline = polyline
                    }
                }
            } catch (e: Exception) {
                Log.e("RoutePolylineManager", "Directions API error (ruta): ${e.message}", e)
                withContext(Dispatchers.Main) { drawStraightRoutePolyline(validSpots) }
            }
        }
    }

    fun drawNavigationRoute(origin: LatLng, destination: LatLng) {
        val url = "https://maps.googleapis.com/maps/api/directions/json" +
            "?origin=${origin.latitude},${origin.longitude}" +
            "&destination=${destination.latitude},${destination.longitude}" +
            "&mode=walking" +
            "&key=$apiKey"

        val request = androidAuthRequest(url)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val responseBody = http.newCall(request).execute().body?.string()
                    ?: throw Exception("Respuesta vacía")
                val json = JSONObject(responseBody)
                val status = json.optString("status")
                if (status != "OK") throw Exception("Directions API status: $status — ${json.optString("error_message")}")
                val encoded = json.getJSONArray("routes")
                    .getJSONObject(0)
                    .getJSONObject("overview_polyline")
                    .getString("points")
                withContext(Dispatchers.Main) {
                    navigationPolyline?.remove()
                    val path = PolyUtil.decode(encoded)
                    animatePolylineDraw(path, 0xFFEA4335.toInt(), 12f) { polyline ->
                        navigationPolyline = polyline
                    }
                    val bounds = LatLngBounds.Builder().include(origin).include(destination).build()
                    map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100))
                }
            } catch (e: Exception) {
                Log.e("RoutePolylineManager", "Directions API error (nav): ${e.message}", e)
                if (alive) withContext(Dispatchers.Main) {
                    onError("Error al calcular la ruta: ${e.message}")
                }
            }
        }
    }

    fun clearRoutes() {
        routePolyline = null
        navigationPolyline = null
    }

    fun cancel() {
        alive = false
        animator?.cancel()
        animator = null
    }

    private fun buildDirectionsApiUrl(spots: List<TouristSpot>): String {
        val origin = spots.first().ubicacion!!
        val destination = spots.last().ubicacion!!
        val sb = StringBuilder("https://maps.googleapis.com/maps/api/directions/json")
        sb.append("?origin=${origin.latitude},${origin.longitude}")
        sb.append("&destination=${destination.latitude},${destination.longitude}")
        if (spots.size > 2) {
            val waypoints = spots.subList(1, spots.size - 1)
                .joinToString("|") { "${it.ubicacion!!.latitude},${it.ubicacion!!.longitude}" }
            sb.append("&waypoints=$waypoints")
        }
        sb.append("&mode=walking")
        sb.append("&key=$apiKey")
        return sb.toString()
    }

    private fun drawStraightRoutePolyline(spots: List<TouristSpot>) {
        routePolyline?.remove()
        val polylineOptions = PolylineOptions()
            .color(0xCC1A73E8.toInt())
            .width(14f)
            .startCap(RoundCap())
            .endCap(RoundCap())
            .jointType(JointType.ROUND)
        spots.forEach { spot ->
            spot.ubicacion?.let { polylineOptions.add(LatLng(it.latitude, it.longitude)) }
        }
        routePolyline = map.addPolyline(polylineOptions)
    }

    private fun androidAuthRequest(url: String): Request = Request.Builder()
        .url(url)
        .header("X-Android-Package", packageName)
        .header("X-Android-Cert", certSha1)
        .build()

    private fun animatePolylineDraw(
        fullPath: List<LatLng>,
        color: Int,
        width: Float,
        onCreated: (Polyline) -> Unit
    ) {
        val polyline = map.addPolyline(
            PolylineOptions()
                .color(color)
                .width(width)
                .startCap(RoundCap())
                .endCap(RoundCap())
                .jointType(JointType.ROUND)
                .zIndex(1f)
        )
        onCreated(polyline)

        if (fullPath.size < 2) { polyline.points = fullPath; return }

        val cumDist = mutableListOf(0.0)
        for (i in 0 until fullPath.size - 1) {
            cumDist.add(cumDist.last() + SphericalUtil.computeDistanceBetween(fullPath[i], fullPath[i + 1]))
        }
        val totalDist = cumDist.last()
        if (totalDist == 0.0) { polyline.points = fullPath; return }

        animator?.cancel()
        val partial = ArrayList<LatLng>(fullPath.size)
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1200L
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { anim ->
                val target = totalDist * anim.animatedFraction
                partial.clear()
                partial.add(fullPath[0])
                for (i in 0 until fullPath.size - 1) {
                    if (cumDist[i + 1] <= target) {
                        partial.add(fullPath[i + 1])
                    } else {
                        val segFrac = (target - cumDist[i]) / (cumDist[i + 1] - cumDist[i])
                        partial.add(SphericalUtil.interpolate(fullPath[i], fullPath[i + 1], segFrac))
                        break
                    }
                }
                polyline.points = partial
            }
            start()
        }
    }

    companion object {
        @Suppress("DEPRECATION")
        private fun computeCertSha1(context: Context): String = try {
            val sigs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager
                    .getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                    .signingInfo?.apkContentsSigners
            } else {
                context.packageManager
                    .getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
                    .signatures
            }
            val bytes = sigs?.firstOrNull()?.toByteArray() ?: return ""
            MessageDigest.getInstance("SHA1").digest(bytes)
                .joinToString("") { "%02X".format(it) }
        } catch (e: Exception) {
            Log.w("RoutePolylineManager", "No se pudo obtener SHA1: ${e.message}")
            ""
        }
    }
}
