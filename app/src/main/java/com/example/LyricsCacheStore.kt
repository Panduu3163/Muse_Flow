package com.example

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/** Bounded, private cache for successful lyric lookups. Atomic writes avoid a truncated file if
 * the process dies during a write; malformed entries are ignored so they never block playback. */
internal class LyricsCacheStore(context: Context) {
    private val file = File(context.filesDir, "lyrics_cache_v1.json")
    private val pending = File(context.filesDir, "lyrics_cache_v1.json.tmp")
    private val backup = File(context.filesDir, "lyrics_cache_v1.json.bak")
    private val mutex = Mutex()
    private val entries = LinkedHashMap<String, LyricsLookup>()
    private var loaded = false

    suspend fun get(key: String): LyricsLookup? = withContext(Dispatchers.IO) {
        mutex.withLock {
            loadIfNeeded()
            entries[key]
        }
    }

    suspend fun put(key: String, lookup: LyricsLookup) = withContext(Dispatchers.IO) {
        if (lookup.result is LyricsResult.Error || lookup.result is LyricsResult.NotFound) return@withContext
        mutex.withLock {
            loadIfNeeded()
            entries.remove(key)
            entries[key] = lookup
            while (entries.size > 20) entries.remove(entries.keys.first())
            write()
        }
    }

    suspend fun remove(key: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            loadIfNeeded()
            if (entries.remove(key) != null) write()
        }
    }

    private fun loadIfNeeded() {
        if (loaded) return
        loaded = true
        if (!file.exists() && backup.exists()) backup.renameTo(file)
        if (!file.exists()) return
        runCatching {
            val root = JSONObject(file.readText(Charsets.UTF_8))
            val saved = root.optJSONArray("entries") ?: return@runCatching
            for (index in 0 until saved.length()) {
                val item = saved.optJSONObject(index) ?: continue
                val key = item.optString("key").takeIf { it.isNotBlank() } ?: continue
                decodeLyricsLookup(item)?.let { entries[key] = it }
            }
        }
    }

    private fun write() {
        if (entries.isEmpty()) {
            file.delete()
            pending.delete()
            backup.delete()
            return
        }
        val saved = JSONArray()
        entries.forEach { (key, lookup) ->
            encodeLyricsLookup(lookup)?.let { encoded ->
                encoded.put("key", key)
                saved.put(encoded)
            }
        }
        val bytes = JSONObject().put("entries", saved).toString().toByteArray(Charsets.UTF_8)
        try {
            FileOutputStream(pending).use { output ->
                output.write(bytes)
                output.fd.sync()
            }
            if (backup.exists() && !backup.delete()) return
            if (file.exists() && !file.renameTo(backup)) return
            if (!pending.renameTo(file)) {
                if (!file.exists()) backup.renameTo(file)
                return
            }
            backup.delete()
        } catch (_: Exception) {
            if (!file.exists()) backup.renameTo(file)
        }
    }
}

internal fun encodeLyricsLookup(lookup: LyricsLookup): JSONObject? {
    val root = JSONObject().put("source", lookup.source)
    when (val result = lookup.result) {
        is LyricsResult.Synced -> {
            root.put("kind", "synced")
            root.put("lines", JSONArray().apply {
                result.lines.forEach { line ->
                    put(JSONObject().put("time", line.timeMs).put("text", line.text).apply {
                        line.endMs?.let { put("end", it) }
                        line.sourceKey?.let { put("sourceKey", it) }
                        line.agent?.let { put("agent", it) }
                        line.language?.let { put("language", it) }
                        line.translation?.let { put("translation", it) }
                        line.romanization?.let { put("romanization", it) }
                        line.backgroundText?.let { put("background", it) }
                        line.words?.let { words ->
                            put("words", JSONArray().apply {
                                words.forEach { word ->
                                    put(JSONObject().put("start", word.startMs)
                                        .put("end", word.endMs).put("text", word.text))
                                }
                            })
                        }
                    })
                }
            })
        }
        is LyricsResult.PlainOnly -> root.put("kind", "plain").put("text", result.text)
        is LyricsResult.Instrumental -> root.put("kind", "instrumental")
        else -> return null
    }
    return root
}

internal fun decodeLyricsLookup(root: JSONObject): LyricsLookup? {
    val result = when (root.optString("kind")) {
        "synced" -> {
            val savedLines = root.optJSONArray("lines") ?: return null
            val lines = buildList {
                for (index in 0 until savedLines.length()) {
                    val line = savedLines.optJSONObject(index) ?: continue
                    val time = line.optLong("time", -1L)
                    val text = line.optString("text")
                    if (time < 0 || text.isBlank()) continue
                    val savedWords = line.optJSONArray("words")
                    val words = savedWords?.let { array ->
                        buildList {
                            for (wordIndex in 0 until array.length()) {
                                val word = array.optJSONObject(wordIndex) ?: continue
                                val start = word.optLong("start", -1L)
                                val end = word.optLong("end", -1L)
                                val wordText = word.optString("text")
                                if (start >= 0 && end >= start && wordText.isNotEmpty()) {
                                    add(LyricWord(start, end, wordText))
                                }
                            }
                        }.takeIf { it.isNotEmpty() }
                    }
                    fun optional(key: String) = line.optString(key).takeIf { it.isNotBlank() }
                    add(LyricLine(time, text, words,
                        endMs = line.optLong("end", -1).takeIf { it > time },
                        sourceKey = optional("sourceKey"), agent = optional("agent"),
                        language = optional("language"), translation = optional("translation"),
                        romanization = optional("romanization"), backgroundText = optional("background")))
                }
            }
            if (lines.isEmpty()) return null
            normalizeSyncedLyrics(LyricsResult.Synced(lines))
        }
        "plain" -> root.optString("text").takeIf { it.isNotBlank() }
            ?.let(LyricsResult::PlainOnly) ?: return null
        "instrumental" -> LyricsResult.Instrumental
        else -> return null
    }
    return LyricsLookup(result, root.optString("source").takeIf { it.isNotBlank() })
}
