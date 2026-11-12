package com.example.ui.component

import coil3.request.allowHardware

import android.content.Context
import android.content.Intent
import androidx.core.graphics.withClip
import androidx.core.graphics.withTranslation
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import coil3.toBitmap
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.widget.Toast
import android.widget.ImageView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.example.AlbumPalette
import com.example.LyricsResult
import com.example.R
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class CardLook(val label: String, val first: Color, val last: Color) {
    Midnight("Midnight", Color(0xFF151A3F), Color(0xFF584077)),
    Rose("Rose", Color(0xFF3C183D), Color(0xFFAE567B)),
    Ocean("Ocean", Color(0xFF10394C), Color(0xFF397A90)),
    Sunset("Sunset", Color(0xFF4F203D), Color(0xFFC7795C)),
    Forest("Forest", Color(0xFF123C35), Color(0xFF6C8470)),
    AlbumGlow("Album glow", Color(0xFF24334D), Color(0xFF5D416D)),
    LiquidGlass("Liquid glass", Color(0xFF1C2943), Color(0xFF475A73)),
}

private fun CardLook.colors(palette: AlbumPalette?): Pair<Color, Color> =
    if (this == CardLook.AlbumGlow && palette != null) {
        androidx.compose.ui.graphics.lerp(palette.dominant, Color.Black, 0.48f) to
            androidx.compose.ui.graphics.lerp(palette.vibrant, Color.Black, 0.31f)
    } else first to last

/** Select one to four lines and share a square card. The same palette and layout feed preview and PNG. */
@Composable
fun LyricCardDialog(
    result: LyricsResult?,
    activeIndex: Int,
    title: String,
    artist: String,
    artworkUrl: String?,
    palette: AlbumPalette?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val candidates = remember(result) {
        when (result) {
            is LyricsResult.Synced -> result.lines.map { it.text }.filter { it.isNotBlank() }
            is LyricsResult.PlainOnly -> result.text.lines().map { it.trim() }.filter { it.isNotBlank() }
            else -> emptyList()
        }
    }
    val initialIndex = if (result is LyricsResult.Synced) {
        result.lines.take(activeIndex + 1).count { it.text.isNotBlank() } - 1
    } else 0
    var selected by remember(result, title) {
        mutableStateOf(if (candidates.isEmpty()) emptySet() else setOf(initialIndex.coerceIn(0, candidates.lastIndex)))
    }
    var look by remember { mutableStateOf(CardLook.Midnight) }
    var exporting by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf(false) }
    val lines = selected.sorted().mapNotNull(candidates::getOrNull)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                .padding(top = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(if (preview) "Preview lyric card" else "Select lyrics",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
                if (!preview) {
                    Text("Choose up to 4 full lines · ${selected.size}/4 selected",
                        modifier = Modifier.padding(horizontal = 20.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LazyColumn(Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (candidates.isEmpty()) item {
                            Text("Lyrics are not available yet. Find lyrics again from the player menu.")
                        }
                        itemsIndexed(candidates) { index, line ->
                            val checked = index in selected
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                                    .background(if (checked) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceContainer)
                                    .clickable {
                                        selected = when {
                                            checked -> selected - index
                                            selected.size < 4 -> selected + index
                                            else -> selected
                                        }
                                    }.padding(horizontal = 10.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked = checked, onCheckedChange = {
                                    selected = when {
                                        checked -> selected - index
                                        selected.size < 4 -> selected + index
                                        else -> selected
                                    }
                                })
                                Text(line, modifier = Modifier.weight(1f).padding(start = 4.dp),
                                    style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                    Button(onClick = { preview = true }, enabled = lines.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) { Text("Preview selected lyrics") }
                } else {
                    LazyColumn(Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        item { LyricCardPreview(lines, title, artist, artworkUrl, palette, look) }
                        item {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(CardLook.entries.size) { index ->
                                    val option = CardLook.entries[index]
                                    val colors = option.colors(palette)
                                    Surface(onClick = { look = option }, shape = RoundedCornerShape(50),
                                        color = colors.first,
                                        border = BorderStroke(if (look == option) 2.dp else 1.dp,
                                            if (look == option) Color.White else Color.White.copy(alpha = .25f))) {
                                        Text(option.label, Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            color = Color.White, style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TextButton(onClick = { preview = false }) { Text("Back") }
                        Button(enabled = !exporting && lines.isNotEmpty(),
                            modifier = Modifier.weight(1f), onClick = {
                exporting = true
                val snapshot = lines.toList()
                scope.launch {
                    val uri = withContext(Dispatchers.IO) {
                        runCatching {
                            renderAndSaveLyricCard(context, snapshot, title, artist, artworkUrl, palette, look)
                        }.getOrNull()
                    }
                    exporting = false
                    if (uri == null) {
                        Toast.makeText(context, "Couldn't create lyric card", Toast.LENGTH_SHORT).show()
                    } else {
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = "image/png"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }, "Share lyric card"))
                        onDismiss()
                    }
                }
                            }) { Text(if (exporting) "Creating…" else "Share image") }
                    }
                }
            }
        }
    }
}

@Composable
private fun LyricCardPreview(
    lines: List<String>, title: String, artist: String, artworkUrl: String?,
    palette: AlbumPalette?, look: CardLook,
) {
    val (first, last) = look.colors(palette)
    val background = if (look == CardLook.AlbumGlow) {
        Brush.radialGradient(listOf(last, first, Color(0xFF101522)))
    } else Brush.linearGradient(listOf(first, last))
    Box(
        Modifier.fillMaxWidth().heightIn(min = (380 + lines.sumOf { (it.length / 28 + 1) * 22 }).coerceAtMost(780).dp)
            .clip(RoundedCornerShape(20.dp))
            .background(background).padding(18.dp),
    ) {
        if (look == CardLook.LiquidGlass) {
            Surface(
                modifier = Modifier.matchParentSize(),
                shape = RoundedCornerShape(18.dp),
                color = Color.White.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.32f)),
            ) {}
        }
        Column(Modifier.matchParentSize().padding(if (look == CardLook.LiquidGlass) 12.dp else 0.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                AndroidView(
                    factory = { ctx -> ImageView(ctx).apply {
                        setImageResource(R.mipmap.ic_launcher_round)
                        scaleType = ImageView.ScaleType.CENTER_CROP
                    } },
                    modifier = Modifier.size(24.dp).clip(RoundedCornerShape(7.dp)),
                )
                Text("MUSEFLOW", color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.weight(1f))
            lines.forEach { line ->
                Text(line, color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 5.dp))
            }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                AsyncImage(model = artworkUrl, contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(38.dp).clip(RoundedCornerShape(9.dp))
                        .background(Color.White.copy(alpha = 0.15f)))
                Column(Modifier.weight(1f)) {
                    Text(title, color = Color.White, fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(artist, color = Color.White.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

private suspend fun renderAndSaveLyricCard(
    context: Context, lines: List<String>, title: String, artist: String,
    artworkUrl: String?, palette: AlbumPalette?, look: CardLook,
): android.net.Uri {
    val artwork = artworkUrl?.let { url ->
        runCatching {
            (context.imageLoader.execute(ImageRequest.Builder(context).data(url).allowHardware(false)
                .size(240).build()) as? SuccessResult)?.image?.toBitmap(240, 240)
        }.getOrNull()
    }
    val lyricPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    fun layoutsAt(size: Float): List<StaticLayout> {
        lyricPaint.textSize = size
        return lines.take(4).map { line ->
            StaticLayout.Builder.obtain(line, 0, line.length, lyricPaint, 888)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).build()
        }
    }
    val lyricLayouts = sequenceOf(66f, 60f, 54f, 48f, 42f, 36f)
        .map(::layoutsAt).firstOrNull { layouts ->
            layouts.sumOf { it.height } + 26 * (layouts.size - 1).coerceAtLeast(0) <= 1000
        } ?: layoutsAt(36f)
    val spacing = 26f
    val contentHeight = lyricLayouts.sumOf { it.height }.toFloat() + spacing * (lyricLayouts.size - 1).coerceAtLeast(0)
    val cardHeight = maxOf(1600, (contentHeight + 620).toInt())
    val bitmap = androidx.core.graphics.createBitmap(1080, cardHeight, Bitmap.Config.ARGB_8888)
    try {
        val canvas = Canvas(bitmap)
        val (first, last) = look.colors(palette)
        val background = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = if (look == CardLook.AlbumGlow) {
                RadialGradient(800f, 290f, 1050f,
                    intArrayOf(last.toArgb(), first.toArgb(), Color(0xFF101522).toArgb()),
                    null, Shader.TileMode.CLAMP)
            } else LinearGradient(0f, 0f, 1080f, 1080f,
                first.toArgb(), last.toArgb(), Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, 1080f, cardHeight.toFloat(), background)
        if (look == CardLook.LiquidGlass) {
            canvas.drawRoundRect(RectF(56f, 56f, 1024f, cardHeight - 56f), 48f, 48f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.argb(32, 255, 255, 255) })
            canvas.drawRoundRect(RectF(56f, 56f, 1024f, cardHeight - 56f), 48f, 48f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE; strokeWidth = 2f
                    color = android.graphics.Color.argb(92, 255, 255, 255)
                })
        }
        ContextCompat.getDrawable(context, R.mipmap.ic_launcher_round)?.let { logo ->
            canvas.withClip(Path().apply { addRoundRect(RectF(96f, 90f, 154f, 148f), 16f, 16f, Path.Direction.CW) }) {
                logo.setBounds(96, 90, 154, 148)
                logo.draw(this)
            }
        }
        val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE; alpha = 220
            textSize = 30f; typeface = Typeface.DEFAULT_BOLD
        }
        canvas.drawText("MUSEFLOW", 170f, 130f, labelPaint)

        var y = ((cardHeight - contentHeight) / 2f).coerceAtLeast(210f)
        lyricLayouts.forEach { layout ->
            canvas.withTranslation(96f, y) { layout.draw(this) }
            y += layout.height + spacing
        }
        artwork?.let { drawRoundedBitmap(canvas, it,
            RectF(96f, cardHeight - 190f, 176f, cardHeight - 110f), 18f) }
        val titleX = if (artwork == null) 96f else 198f
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE; textSize = 36f; typeface = Typeface.DEFAULT_BOLD
        }
        val artistPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE; alpha = 190; textSize = 29f
        }
        canvas.drawText(TextUtils.ellipsize(title, titlePaint, 980f - titleX,
            TextUtils.TruncateAt.END).toString(), titleX, cardHeight - 155f, titlePaint)
        canvas.drawText(TextUtils.ellipsize(artist, artistPaint, 980f - titleX,
            TextUtils.TruncateAt.END).toString(), titleX, cardHeight - 113f, artistPaint)
        val folder = File(context.cacheDir, "lyric-cards").apply { mkdirs() }
        val now = System.currentTimeMillis()
        folder.listFiles()?.filter { it.isFile && now - it.lastModified() > 24 * 60 * 60 * 1000L }
            ?.forEach { it.delete() }
        val file = File(folder, "lyric-$now.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    } finally {
        bitmap.recycle()
    }
}

private fun drawRoundedBitmap(canvas: Canvas, bitmap: Bitmap, dest: RectF, radius: Float) {
    canvas.withClip(Path().apply { addRoundRect(dest, radius, radius, Path.Direction.CW) }) {
    val side = minOf(bitmap.width, bitmap.height)
    val src = Rect((bitmap.width - side) / 2, (bitmap.height - side) / 2,
        (bitmap.width + side) / 2, (bitmap.height + side) / 2)
    canvas.drawBitmap(bitmap, src, dest, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
    }
}
