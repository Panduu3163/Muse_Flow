package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import kotlin.math.max
import kotlin.math.roundToInt

/** Bounded software artwork masks also work in launchers on Android 7–11. */
internal object WidgetArtwork {
    fun card(context: Context, artwork: Bitmap?, widthDp: Int, heightDp: Int): Bitmap {
        val width = widthDp.coerceIn(160, 1_200)
        val height = heightDp.coerceIn(160, 1_200)
        val scale = 512f / max(width, height)
        return render(context, artwork, (width * scale).roundToInt(), (height * scale).roundToInt(), 28 * scale, height.toFloat())
    }

    private fun render(context: Context, artwork: Bitmap?, width: Int, height: Int, radius: Float, logicalHeightDp: Float): Bitmap {
        val output = createBitmap(width, height)
        val canvas = Canvas(output)
        canvas.clipPath(Path().apply { addRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), radius, radius, Path.Direction.CW) })
        canvas.drawColor(PlaybackWidget.surfaceColor(artwork))
        if (artwork != null) {
            val scale = max(width.toFloat() / artwork.width, height.toFloat() / artwork.height)
            val left = (width - artwork.width * scale) / 2
            val top = (height - artwork.height * scale) / 2
            canvas.drawBitmap(artwork, null, RectF(left, top, left + artwork.width * scale, top + artwork.height * scale), Paint(Paint.FILTER_BITMAP_FLAG))
        } else {
            ContextCompat.getDrawable(context, R.drawable.ic_widget_artwork)?.let { placeholder ->
                val side = (minOf(width, height) * .55f).roundToInt()
                val left = (width - side) / 2; val top = 0
                placeholder.setBounds(left, top, left + side, top + side)
                placeholder.draw(canvas)
            }
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), Paint().apply {
            // Grow the legibility zone with accessibility text without dimming all of the cover.
            val overlayDp = 68f + 38f * context.resources.configuration.fontScale + 36f
            val start = (height - overlayDp / logicalHeightDp * height).coerceAtLeast(0f)
            shader = LinearGradient(0f, start, 0f, height.toFloat(),
                intArrayOf(0x00000000, 0xB8000000.toInt(), 0xEC000000.toInt()), floatArrayOf(0f, .24f, 1f), Shader.TileMode.CLAMP)
        })
        return output
    }

}
