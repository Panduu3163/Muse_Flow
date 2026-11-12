package com.example

import android.content.Context

/** The complete notes shipped with this APK, available before a GitHub release is published. */
internal object BundledReleaseNotes {
    const val ASSET = "release_notes/current.md"
    @Volatile private var cached: String? = null

    fun load(context: Context): String = cached ?: synchronized(this) {
        cached ?: context.applicationContext.assets.open(ASSET).bufferedReader(Charsets.UTF_8)
            .use { it.readText() }.also { cached = it }
    }
}

internal enum class ReleaseNoteKind { Title, Heading, Paragraph, Bullet }
internal data class ReleaseNoteBlock(val kind: ReleaseNoteKind, val text: String)

/** Render release-note text as text only. Remote HTML or instructions are never executed. */
internal fun parseReleaseNotes(markdown: String): List<ReleaseNoteBlock> = markdown.lineSequence()
    .map(String::trim).filter { it.isNotEmpty() && it != "---" }.map { line ->
        val (kind, content) = when {
            line.startsWith("# ") -> ReleaseNoteKind.Title to line.removePrefix("# ")
            line.startsWith("## ") -> ReleaseNoteKind.Heading to line.removePrefix("## ")
            line.startsWith("- ") -> ReleaseNoteKind.Bullet to line.removePrefix("- ")
            else -> ReleaseNoteKind.Paragraph to line.removePrefix("> ")
        }
        ReleaseNoteBlock(kind, content.replace("**", "").replace("`", ""))
    }.toList()
