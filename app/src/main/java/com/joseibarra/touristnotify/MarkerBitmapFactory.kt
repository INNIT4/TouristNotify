package com.joseibarra.touristnotify

import android.content.res.Resources
import android.graphics.*

/**
 * Factory para crear bitmaps personalizados de marcadores del mapa
 *
 * Genera diferentes tipos de marcadores para lugares turísticos:
 * - Círculos con imagen del lugar + borde de color de categoría
 * - Círculos numerados para paradas de ruta
 * - Círculos de color sólido como fallback cuando no hay imagen
 *
 * Todos los bitmaps incluyen bordes blancos y sombras suaves para
 * mantener consistencia visual sobre cualquier fondo del mapa.
 */
object MarkerBitmapFactory {

    /**
     * Círculo de color sólido con borde blanco (modo mapa normal, sin imagen)
     *
     * @param backgroundColor Color de la categoría
     * @return Bitmap de 80x80 px
     */
    fun createColoredCircle(backgroundColor: Int): Bitmap {
        val size = 80
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val cx = size / 2f
        val cy = size / 2f
        val radius = size / 2f - 5f

        // Sombra suave
        paint.color = Color.argb(60, 0, 0, 0)
        canvas.drawCircle(cx + 2f, cy + 2f, radius, paint)

        // Relleno de color de categoría
        paint.color = backgroundColor
        canvas.drawCircle(cx, cy, radius, paint)

        // Borde blanco
        paint.color = Color.WHITE
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3.5f
        canvas.drawCircle(cx, cy, radius, paint)

        return bitmap
    }

    /**
     * Círculo con imagen de lugar + borde de color de categoría
     *
     * @param source Bitmap circular del lugar (pre-procesado por Glide circleCrop)
     * @param borderColor Color del borde según categoría
     * @param resources Resources para conversión dp→px
     * @return Bitmap con imagen + borde
     */
    fun createCircularWithBorder(source: Bitmap, borderColor: Int, resources: Resources): Bitmap {
        val borderPx = dpToPx(3, resources)
        val size = source.width + borderPx * 2
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = borderColor })
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - dpToPx(1, resources), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
        canvas.drawBitmap(source, borderPx.toFloat(), borderPx.toFloat(), null)
        return output
    }

    /**
     * Círculo con imagen de lugar + número de parada en badge superior-derecho
     *
     * @param source Bitmap circular del lugar
     * @param number Número de parada en la ruta (1-based)
     * @param borderColor Color del borde según categoría
     * @param resources Resources para conversión dp→px
     * @return Bitmap con imagen + badge numerado
     */
    fun createCircularWithNumber(source: Bitmap, number: Int, borderColor: Int, resources: Resources): Bitmap {
        val borderPx = dpToPx(3, resources)
        val size = source.width + borderPx * 2
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        // Borde de color de categoría
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = borderColor })
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - dpToPx(1, resources), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
        canvas.drawBitmap(source, borderPx.toFloat(), borderPx.toFloat(), null)

        // Badge con número
        val badgeRadius = dpToPx(10, resources).toFloat()
        val badgeX = size - badgeRadius
        val badgeY = badgeRadius
        canvas.drawCircle(badgeX, badgeY, badgeRadius + dpToPx(1, resources), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
        canvas.drawCircle(badgeX, badgeY, badgeRadius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1A73E8") })
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = dpToPx(10, resources).toFloat()
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(number.toString(), badgeX, badgeY - (textPaint.descent() + textPaint.ascent()) / 2f, textPaint)
        return output
    }

    /**
     * Círculo de color con número (fallback cuando no hay imagen)
     *
     * @param number Número de parada en la ruta
     * @param backgroundColor Color de la categoría
     * @param resources Resources para conversión dp→px
     * @return Bitmap de tamaño 44dp
     */
    fun createNumberedCircle(number: Int, backgroundColor: Int, resources: Resources): Bitmap {
        val sizePx = dpToPx(44, resources)
        val output = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
        canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f - dpToPx(2, resources), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = backgroundColor })
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = dpToPx(16, resources).toFloat()
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(number.toString(), sizePx / 2f, sizePx / 2f - (textPaint.descent() + textPaint.ascent()) / 2f, textPaint)
        return output
    }

    private fun dpToPx(dp: Int, resources: Resources): Int =
        (dp * resources.displayMetrics.density).toInt()
}
