package com.joseibarra.touristnotify

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

class MarkerRenderer(
    private val context: Context,
    private val map: GoogleMap,
    private val scope: CoroutineScope
) {
    private val _markers = mutableListOf<Marker>()
    val markers: List<Marker> get() = _markers

    private val generation = AtomicInteger(0)
    private var alive = true

    fun addMarker(spot: TouristSpot, routeIndex: Int = -1) {
        spot.ubicacion ?: return
        val position = LatLng(spot.ubicacion.latitude, spot.ubicacion.longitude)
        val categoryColor = getCategoryColor(spot.categoria)

        val safeImageUrl = SafeImageUrl.sanitize(spot.imagenUrl)
        if (safeImageUrl != null) {
            val capturedGen = generation.get()
            Glide.with(context)
                .asBitmap()
                .load(safeImageUrl)
                .apply(
                    RequestOptions()
                        .circleCrop()
                        .override(120, 120)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                )
                .into(object : CustomTarget<Bitmap>() {
                    private var loadedResource: Bitmap? = null

                    override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                        loadedResource = resource
                        if (capturedGen != generation.get() || !alive) return
                        if (resource.width == 0 || resource.height == 0) {
                            addFallbackMarker(spot, position, routeIndex, categoryColor)
                            return
                        }
                        scope.launch(Dispatchers.Default) {
                            val markerBitmap = if (routeIndex >= 1) {
                                createCircularBitmapWithNumber(resource, routeIndex, categoryColor)
                            } else {
                                createCircularBitmapWithBorder(resource, categoryColor)
                            }
                            if (capturedGen != generation.get() || !alive) return@launch
                            withContext(Dispatchers.Main) {
                                addMarkerToMap(spot, position, markerBitmap)
                            }
                        }
                    }

                    override fun onLoadFailed(errorDrawable: Drawable?) {
                        loadedResource = null
                        if (capturedGen != generation.get() || !alive) return
                        addFallbackMarker(spot, position, routeIndex, categoryColor)
                    }

                    override fun onLoadCleared(placeholder: Drawable?) {
                        loadedResource = null
                    }
                })
        } else {
            addFallbackMarker(spot, position, routeIndex, categoryColor)
        }
    }

    fun clearMarkers() {
        generation.incrementAndGet()
        _markers.clear()
    }

    fun destroy() {
        alive = false
        clearMarkers()
    }

    private fun addFallbackMarker(spot: TouristSpot, position: LatLng, routeIndex: Int, categoryColor: Int) {
        val bitmap = if (routeIndex >= 1) createNumberedCircleMarker(routeIndex, categoryColor)
                     else createColoredCircleMarker(categoryColor)
        addMarkerToMap(spot, position, bitmap)
    }

    private fun addMarkerToMap(spot: TouristSpot, position: LatLng, bitmap: Bitmap) {
        val marker = map.addMarker(
            MarkerOptions()
                .position(position)
                .title(spot.nombre)
                .snippet("${spot.categoria} • ${String.format("%.1f", spot.rating)}⭐")
                .icon(BitmapDescriptorFactory.fromBitmap(bitmap))
                .anchor(0.5f, 0.5f)
        )
        marker?.tag = spot
        marker?.let { _markers.add(it) }
    }

    private fun createColoredCircleMarker(backgroundColor: Int): Bitmap {
        val size = 80
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val cx = size / 2f
        val cy = size / 2f
        val radius = size / 2f - 5f
        paint.color = Color.argb(60, 0, 0, 0)
        canvas.drawCircle(cx + 2f, cy + 2f, radius, paint)
        paint.color = backgroundColor
        canvas.drawCircle(cx, cy, radius, paint)
        paint.color = Color.WHITE
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3.5f
        canvas.drawCircle(cx, cy, radius, paint)
        return bitmap
    }

    private fun createCircularBitmapWithBorder(source: Bitmap, borderColor: Int): Bitmap {
        val borderPx = dpToPx(3)
        val size = source.width + borderPx * 2
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawCircle(size / 2f, size / 2f, size / 2f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = borderColor })
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - dpToPx(1),
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
        canvas.drawBitmap(source, borderPx.toFloat(), borderPx.toFloat(), null)
        return output
    }

    private fun createCircularBitmapWithNumber(source: Bitmap, number: Int, borderColor: Int): Bitmap {
        val borderPx = dpToPx(3)
        val size = source.width + borderPx * 2
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawCircle(size / 2f, size / 2f, size / 2f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = borderColor })
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - dpToPx(1),
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
        canvas.drawBitmap(source, borderPx.toFloat(), borderPx.toFloat(), null)
        val badgeRadius = dpToPx(10).toFloat()
        val badgeX = size - badgeRadius
        val badgeY = badgeRadius
        canvas.drawCircle(badgeX, badgeY, badgeRadius + dpToPx(1),
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
        canvas.drawCircle(badgeX, badgeY, badgeRadius,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ContextCompat.getColor(context, R.color.marker_badge_blue)
            })
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = dpToPx(10).toFloat()
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(
            number.toString(), badgeX,
            badgeY - (textPaint.descent() + textPaint.ascent()) / 2f,
            textPaint
        )
        return output
    }

    private fun createNumberedCircleMarker(number: Int, backgroundColor: Int): Bitmap {
        val sizePx = dpToPx(44)
        val output = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
        canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f - dpToPx(2),
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = backgroundColor })
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = dpToPx(16).toFloat()
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(
            number.toString(), sizePx / 2f,
            sizePx / 2f - (textPaint.descent() + textPaint.ascent()) / 2f,
            textPaint
        )
        return output
    }

    fun getCategoryColor(category: String): Int = when (category.lowercase()) {
        "museo" -> ContextCompat.getColor(context, R.color.marker_museum)
        "restaurante", "gastronomía" -> ContextCompat.getColor(context, R.color.marker_restaurant)
        "hotel", "hospedaje" -> ContextCompat.getColor(context, R.color.marker_hotel)
        "iglesia", "templo" -> ContextCompat.getColor(context, R.color.marker_church)
        "parque", "naturaleza" -> ContextCompat.getColor(context, R.color.marker_park)
        "tienda", "comercio" -> ContextCompat.getColor(context, R.color.marker_shop)
        else -> ContextCompat.getColor(context, R.color.marker_default)
    }

    private fun dpToPx(dp: Int): Int = (dp * context.resources.displayMetrics.density).toInt()
}
