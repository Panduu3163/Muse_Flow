package com.example

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

internal data class PortableSettings(val stores: Map<String, Map<String, Any>>) {
    fun toJson(): JSONObject = JSONObject().put("version", 1).put("stores", JSONObject().apply {
        stores.forEach { (store, values) -> put(store, JSONObject().apply { values.forEach { (key, value) -> put(key, value) } }) }
    })
}

internal interface PortableSettingsAccess {
    suspend fun snapshot(): PortableSettings
    suspend fun apply(store: String, values: Map<String, Any>)
}

private typealias SettingRule = (Any) -> Any

internal object PortableSettingsLocks { val writes = Mutex() }

internal suspend fun DataStore<Preferences>.portableEdit(transform: suspend (MutablePreferences) -> Unit) =
    PortableSettingsLocks.writes.withLock { edit(transform) }

internal object PortableSettingsFormat {
    private val boolean: SettingRule = { value -> require(value is Boolean); value }
    private fun integer(range: IntRange = Int.MIN_VALUE..Int.MAX_VALUE): SettingRule = { value ->
        require(value is Number && value.toDouble().isFinite() && value.toDouble() == value.toInt().toDouble() && value.toInt() in range)
        value.toInt()
    }
    private inline fun <reified T : Enum<T>> choice(): SettingRule = { value ->
        require(value is String && enumValues<T>().any { it.name == value }); value
    }
    private val providerOrder: SettingRule = { value ->
        require(value is String && value.length <= 512)
        val names = value.split(',').filter { it.isNotBlank() }.map { it.trim() }
        require(names.distinct().size == names.size && names.all { name -> LyricsProviderId.entries.any { it.name == name } })
        names.joinToString(",")
    }
    private val equalizer: SettingRule = { value ->
        require(value is String && value.length <= 512)
        val bands = if (value.isEmpty()) emptyList() else value.split(',').map { it.toInt() }
        require(bands.size <= 32 && bands.all { it in -2400..2400 }); bands.joinToString(",")
    }
    private val libraryViews: SettingRule = { value ->
        require(value is String && value.length <= 16_384)
        val json = JSONObject(value.ifBlank { "{}" })
        val views = json.keys().asSequence().associate { name ->
            val section = LibrarySection.valueOf(name)
            val row = json.getJSONObject(name)
            require(row.opt("ascending") is Boolean && row.opt("grid") is Boolean)
            section to LibraryViewPreference(
                trackSort = TrackSortOption.valueOf(row.getString("sort")),
                playlistSort = PlaylistSortOption.valueOf(row.getString("playlistSort")),
                ascending = row.getBoolean("ascending"), grid = row.getBoolean("grid"), query = "",
            )
        }
        // Search text is transient browsing data, not a portable setting.
        encodeLibraryViews(views)
    }

    val rules: Map<String, Map<String, SettingRule>> = linkedMapOf(
        "app" to buildMap {
            listOf("hide_player_thumbnail", "crop_album_art", "swipe_to_change_song", "show_animated_canvas",
                "skip_silence", "persistent_queue", "preload_next_track", "audio_normalization_enabled", "crossfade_enabled",
                "bass_boost_enabled", "crossfeed_enabled", "rotating_thumbnail_animation", "show_comment_button", "show_codec_info",
                "glowing_lyrics_effect", "blur_inactive_lines", "change_lyrics_on_click", "auto_scroll_lyrics",
                "swipe_song_in_fullscreen_lyrics", "show_play_pause_overlay_on_thumbnail", "hide_status_bar_in_fullscreen_lyrics",
                "swipe_song_to_queue", "enable_haptics", "swipe_song_to_remove_from_playlist", "show_liked_playlist",
                "show_downloaded_playlist", "show_exported_playlist", "show_top_playlist", "show_cached_playlist",
                "show_recently_played_shortcut").forEach { put(it, boolean) }
            put("thumbnail_corner_radius", integer(0..48)); put("mini_player_swipe_sensitivity", integer(0..100))
            put("crossfade_duration_ms", integer(1000..12000)); put("bass_boost_intensity", integer(0..100))
            put("crossfeed_intensity", integer(0..100)); put("lyrics_text_size", integer(14..34))
            put("lyrics_line_spacing") { value ->
                require(value is Number && value.toDouble().isFinite() && value.toDouble() in 1.0..3.0); value.toFloat()
            }
            put("mini_player_background_style", choice<BackgroundStyle>()); put("player_background_style", choice<BackgroundStyle>())
            put("player_button_color", choice<PlayerButtonColorOption>()); put("player_slider_style", choice<PlayerSliderStyle>())
            put("player_transport_style", choice<PlayerTransportStyle>()); put("audio_quality_mode", choice<AudioQualityMode>())
            put("lyrics_text_position", choice<LyricsTextPosition>()); put("word_animation_style", choice<WordAnimationStyle>())
            put("lyrics_provider_order", providerOrder); put("default_open_tab", choice<DefaultTab>())
            put("default_library_chip", choice<DefaultLibraryChip>()); put("grid_cell_size", choice<GridCellSize>())
            put("display_density", choice<DisplayDensity>()); put("font_style", choice<AppFontStyle>())
        },
        "theme" to mapOf("seed_color" to integer(), "pure_black" to boolean, "dark_theme" to boolean,
            "dynamic_album_color" to boolean, "theme_mode" to choice<ThemeMode>()),
        "equalizer" to mapOf("eq_enabled" to boolean, "eq_band_levels" to equalizer),
        "library" to mapOf("sections" to libraryViews),
    )

    fun parse(root: JSONObject): PortableSettings {
        require(root.opt("version") == 1) { "Unsupported settings format" }
        val stores = root.getJSONObject("stores")
        require(stores.keys().asSequence().toSet() == rules.keys) { "Incomplete or unsupported settings groups" }
        return PortableSettings(rules.mapValues { (store, fields) ->
            val values = stores.getJSONObject(store)
            values.keys().asSequence().associateWith { key ->
                val rule = fields[key] ?: throw IllegalArgumentException("Unsupported portable setting")
                rule(values.get(key))
            }
        })
    }
}

internal class DataStorePortableSettings(context: Context) : PortableSettingsAccess {
    private val stores: Map<String, DataStore<Preferences>> = linkedMapOf(
        "app" to context.applicationContext.appSettingsDataStore,
        "theme" to context.applicationContext.themeDataStore,
        "equalizer" to context.applicationContext.equalizerDataStore,
        "library" to context.applicationContext.libraryViewsDataStore,
    )

    override suspend fun snapshot(): PortableSettings = PortableSettingsLocks.writes.withLock { PortableSettings(stores.mapValues { (store, data) ->
        val rules = PortableSettingsFormat.rules.getValue(store)
        data.data.first().asMap().entries.filter { it.key.name in rules }.associate { (key, value) ->
            key.name to rules.getValue(key.name)(value)
        }
    }) }

    override suspend fun apply(store: String, values: Map<String, Any>) {
        val rules = PortableSettingsFormat.rules.getValue(store)
        require(values.keys.all { it in rules })
        stores.getValue(store).edit { prefs ->
            prefs.asMap().keys.filter { it.name in rules }.forEach { prefs.remove(it) }
            values.forEach { (name, raw) ->
                when (val value = rules.getValue(name)(raw)) {
                    is Boolean -> prefs[booleanPreferencesKey(name)] = value
                    is Int -> prefs[intPreferencesKey(name)] = value
                    is Float -> prefs[floatPreferencesKey(name)] = value
                    is String -> prefs[stringPreferencesKey(name)] = value
                    else -> error("Unsupported portable setting value")
                }
            }
        }
    }
}
