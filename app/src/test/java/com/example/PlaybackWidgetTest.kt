package com.example

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.view.drawToBitmap
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import com.github.takahirom.roborazzi.captureRoboImage

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xxhdpi")
class PlaybackWidgetTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    @Before fun reset() { PlaybackControls.detach(context) }
    @After fun resetScale() { RuntimeEnvironment.setFontScale(1f); PlaybackControls.detach(context) }

    private fun widget(width: Int = 320, height: Int = 160): View {
        val manager = AppWidgetManager.getInstance(context)
        val shadow = shadowOf(manager)
        shadow.bindAppWidgetId(77, ComponentName(context, PlaybackWidget::class.java))
        manager.updateAppWidgetOptions(77, android.os.Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, width)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, width)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, height)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, height)
        })
        PlaybackWidget.updateAll(context)
        return shadow.getViewFor(77)
    }

    @Test fun resizingKeepsTextAndTouchTargetsInsideBothCardsAtLargeText() {
        RuntimeEnvironment.setFontScale(2f)
        for (width in listOf(180, 240, 320, 400)) {
            val view = widget(width)
            val density = context.resources.displayMetrics.density
            val pixels = (width * density).toInt()
            val height = (160 * density).toInt()
            view.measure(View.MeasureSpec.makeMeasureSpec(pixels, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, pixels, height)
            listOf(R.id.widget_title, R.id.widget_artist, R.id.widget_previous, R.id.widget_play, R.id.widget_next).forEach { id ->
                val child = view.findViewById<View>(id)
                val bounds = android.graphics.Rect()
                assertTrue("Visible control $id at width $width", child.getGlobalVisibleRect(bounds))
                assertEquals("No horizontal clipping at $width", child.width, bounds.width())
                assertEquals("No vertical clipping at $width", child.height, bounds.height())
                if (child is ImageButton) {
                    assertTrue(child.width >= 48 * density)
                    assertTrue(child.height >= 48 * density)
                }
            }
            val artist = view.findViewById<View>(R.id.widget_artist)
            val controls = view.findViewById<View>(R.id.widget_controls)
            assertTrue("Metadata cannot overlap playback controls", artist.bottom <= controls.top)
            view.drawToBitmap().captureRoboImage("build/outputs/roborazzi/18-widget-${width}dp-large-text.png")
        }
    }

    @Test fun emptyQueueOpensAppAndButtonsHaveAccessibleLabels() {
        val view = widget()
        assertEquals("Open MuseFlow", view.findViewById<TextView>(R.id.widget_title).text.toString())
        listOf(R.id.widget_play, R.id.widget_previous, R.id.widget_next).forEach {
            assertTrue(view.findViewById<ImageButton>(it).contentDescription.isNotBlank())
        }
        assertNull(PlaybackControls.token)
        assertFalse(PlaybackControls.state.playing)
    }

    @Test
    @Config(sdk = [24])
    @GraphicsMode(GraphicsMode.Mode.LEGACY)
    fun olderAndroidUsesASquareThatFitsTheHost() {
        val view = widget(230, 300)
        val density = context.resources.displayMetrics.density
        val width = (230 * density).toInt(); val height = (300 * density).toInt()
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, width, height)
        val card = view.findViewById<View>(R.id.widget_metadata)
        assertEquals(card.width, card.height)
        assertTrue(card.width <= width && card.height <= height)
        assertTrue(card.width >= 160 * density)
    }

    @Test fun coverStaysSquareWithTextAndControlsInsideIt() {
        val density = context.resources.displayMetrics.density
        for (fontScale in listOf(1f, 2f)) {
            RuntimeEnvironment.setFontScale(fontScale)
            for ((width, height) in listOf(180 to 180, 224 to 224, 288 to 288, 320 to 260, 360 to 480, 180 to 320)) {
                val view = widget(width, height)
                val w = (width * density).toInt(); val h = (height * density).toInt()
                view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
                view.layout(0, 0, w, h)
                val artwork = view.findViewById<View>(R.id.widget_artwork)
                val title = view.findViewById<View>(R.id.widget_title)
                val artist = view.findViewById<View>(R.id.widget_artist)
                val controls = view.findViewById<View>(R.id.widget_controls)
                val card = view.findViewById<View>(R.id.widget_metadata)
                assertEquals("Card must match the square cover", card.width, card.height)
                assertEquals((minOf(width, height) * density).toInt(), card.width)
                assertEquals(card.width, artwork.width)
                assertEquals(card.height, artwork.height)
                assertTrue("Title is over the cover", title.top >= 0 && title.bottom <= artist.top)
                assertTrue("No large gap above playback controls", controls.top - artist.bottom <= 13 * density)
                assertTrue(controls.bottom <= card.height)
                listOf(artwork, title, artist, controls).forEach { child ->
                    val bounds = android.graphics.Rect()
                    assertTrue(child.getGlobalVisibleRect(bounds))
                    assertEquals(child.width, bounds.width())
                    assertEquals(child.height, bounds.height())
                }
                view.drawToBitmap().captureRoboImage("build/outputs/roborazzi/20-square-widget-${width}x${height}-font${fontScale.toInt()}.png")
            }
        }
        assertNull(PlaybackControls.token)
        assertFalse(PlaybackControls.state.playing)
    }

    @Test fun unavailableSourceIsVisibleWithLargeTextAndNoAutomaticPlayback() {
        RuntimeEnvironment.setFontScale(2f)
        widget()
        PlaybackControls.showError(context, "This local song is unavailable. Open MuseFlow and choose it again.")
        val view = shadowOf(AppWidgetManager.getInstance(context)).getViewFor(77)
        assertTrue(view.findViewById<TextView>(R.id.widget_artist).text.contains("unavailable"))
        view.measure(View.MeasureSpec.makeMeasureSpec(1_032, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, 1_032, 480)
        // RemoteViews are hosted by a launcher, not an app Activity. Capture the native view
        // canvas directly; Roborazzi's View overload requires an Activity-backed context.
        view.drawToBitmap().captureRoboImage("build/outputs/roborazzi/18-widget-large-text.png")
        assertNull(PlaybackControls.token)
    }

    @Test fun controlWithoutLiveSessionFinishesAndCannotStartPlayback() {
        var completions = 0
        PlaybackControls.command(context, "next") { completions++ }
        assertEquals(1, completions)
        assertFalse(PlaybackControls.state.playing)
    }

    @Test fun idleRememberedQueueDoesNotClaimToBePlayingAndReleaseClearsMetadata() {
        val player = androidx.media3.exoplayer.ExoPlayer.Builder(context).build()
        try {
            player.setMediaItem(androidx.media3.common.MediaItem.Builder().setMediaId("saved").setUri("file:///music/saved.opus")
                .setMediaMetadata(androidx.media3.common.MediaMetadata.Builder().setTitle("Remembered song").build()).build())
            PlaybackControls.publish(context, player)
            assertTrue(PlaybackControls.state.hasQueue)
            assertEquals("Remembered song", PlaybackControls.state.title)
            assertFalse(PlaybackControls.state.playing)
            PlaybackControls.detach(context)
            assertEquals("", PlaybackControls.state.title)
            assertFalse(PlaybackControls.state.hasQueue)
        } finally { player.release() }
    }

    @Test fun currentArtworkIsDecodedToASmallThumbnailAndClearedWithTheSession() {
        widget()
        val player = androidx.media3.exoplayer.ExoPlayer.Builder(context).build()
        val session = androidx.media3.session.MediaSession.Builder(context, player).build()
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main)
        val bitmap = android.graphics.Bitmap.createBitmap(24, 24, android.graphics.Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.rgb(180, 30, 80))
        val bytes = java.io.ByteArrayOutputStream().also { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        try {
            player.setMediaItem(androidx.media3.common.MediaItem.Builder().setMediaId("with-art").setUri("file:///music/track.opus")
                .setMediaMetadata(androidx.media3.common.MediaMetadata.Builder().setTitle("Artwork song").setArtist("MuseFlow")
                    .setArtworkData(bytes, androidx.media3.common.MediaMetadata.PICTURE_TYPE_FRONT_COVER).build()).build())
            PlaybackControls.attach(context, session.token, player, scope)
            val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5)
            while (PlaybackControls.state.artwork == null && System.nanoTime() < deadline) {
                shadowOf(android.os.Looper.getMainLooper()).idle()
                Thread.sleep(10)
            }
            val thumbnail = PlaybackControls.state.artwork
            assertNotNull("Embedded cover should load through the existing Coil decoder", thumbnail)
            assertEquals(512, thumbnail!!.width); assertEquals(512, thumbnail.height)
            assertEquals(android.graphics.Color.rgb(180, 30, 80), thumbnail.getPixel(64, 64))
            val rounded = WidgetArtwork.card(context, thumbnail, 224, 224)
            assertEquals("Cover corners stay transparent on the software canvas", 0, android.graphics.Color.alpha(rounded.getPixel(0, 0)))
            assertEquals(thumbnail.getPixel(64, 64), rounded.getPixel(64, 64))
            val card = WidgetArtwork.card(context, thumbnail, 180, 160)
            assertTrue(card.width <= 512 && card.height <= 512)
            assertEquals(0, android.graphics.Color.alpha(card.getPixel(0, 0)))
            for ((width, height) in listOf(180 to 180, 360 to 180, 360 to 280, 360 to 380, 360 to 480)) {
                val view = widget(width, height)
                val density = context.resources.displayMetrics.density
                val w = (width * density).toInt(); val h = (height * density).toInt()
                view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
                view.layout(0, 0, w, h)
                view.drawToBitmap().captureRoboImage("build/outputs/roborazzi/20-square-widget-${width}x${height}-artwork.png")
            }
            PlaybackControls.detach(context)
            shadowOf(android.os.Looper.getMainLooper()).idle()
            assertNull(PlaybackControls.state.artwork)
        } finally {
            PlaybackControls.detach(context)
            scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
            session.release(); player.release()
        }
    }
}
