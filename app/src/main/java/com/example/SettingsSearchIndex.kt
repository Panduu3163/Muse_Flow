package com.example

/** A visible settings control and the screen that owns it. Keep labels in sync with the rows. */
data class SettingsSearchEntry(
    val id: String,
    val title: String,
    val category: String,
    val route: String,
    val keywords: String = "",
)

/** Search only controls that currently exist; results open the control's actual settings screen. */
object SettingsSearchIndex {
    private val aliases = mapOf(
        (Routes.SETTINGS_APPEARANCE to "Accent colour") to "theme color palette custom",
        (Routes.SETTINGS_APPEARANCE to "Colour from album art") to "dynamic color artwork",
        (Routes.SETTINGS_PLAYER to "Progress bar style") to "slider seek bar wavy squiggly slim",
        (Routes.SETTINGS_PLAYER to "Background style") to "glow animated apple music live mesh album",
        (Routes.SETTINGS_LYRICS to "Lyrics provider order") to "sources fallback priority",
        (Routes.SETTINGS_AUDIO to "Normalize volume") to "normalization loudness",
        (Routes.SETTINGS_PLAYBACK to "Remember queue") to "persistent restore restart",
        (Routes.BACKUP to "Auto backup") to "automatic daily",
        (Routes.STORAGE to "Clear cache") to "free space",
    )

    private fun entries(route: String, category: String, vararg controls: Pair<String, String>) =
        controls.map { (id, title) -> SettingsSearchEntry(id, title, category, route, aliases[route to title].orEmpty()) }

    val entries: List<SettingsSearchEntry> = buildList {
        addAll(entries(Routes.SETTINGS_APPEARANCE, "Appearance",
            "appearance.system" to "Follow system", "appearance.light" to "Light",
            "appearance.dark" to "Dark", "appearance.amoled" to "AMOLED",
            "appearance.accent" to "Accent colour", "appearance.density" to "Display density",
            "appearance.font" to "Font style", "appearance.card_size" to "Card size",
            "appearance.album_color" to "Colour from album art", "appearance.reset" to "Reset appearance"))
        addAll(entries(Routes.SETTINGS_MINI_PLAYER, "Mini player",
            "mini.background" to "Background style", "mini.reset" to "Reset mini player"))
        addAll(entries(Routes.SETTINGS_PLAYER, "Player",
            "player.hide_art" to "Hide artwork", "player.crop_art" to "Crop artwork to square",
            "player.background" to "Background style", "player.button_color" to "Button colour",
            "player.progress_style" to "Progress bar style", "player.transport" to "Transport button style",
            "player.swipe" to "Swipe to change song", "player.art_radius" to "Artwork corner radius",
            "player.rotate_art" to "Rotating artwork",
            "player.codec" to "Show codec info",
            "player.reset" to "Reset player appearance"))
        addAll(entries(Routes.SETTINGS_LYRICS, "Lyrics",
            "lyrics.provider_order" to "Lyrics provider order", "lyrics.text_size" to "Text size",
            "lyrics.alignment" to "Alignment", "lyrics.auto_scroll" to "Auto-scroll",
            "lyrics.tap_seek" to "Tap a line to seek", "lyrics.blur" to "Blur inactive lines",
            "lyrics.word_animation" to "Word animation", "lyrics.glow" to "Glowing effect",
            "lyrics.line_spacing" to "Line spacing"))
        addAll(entries(Routes.SETTINGS_AUDIO, "Audio",
            "audio.equalizer" to "Equalizer", "audio.skip_silence" to "Skip silence",
            "audio.normalize" to "Normalize volume", "audio.crossfade" to "Crossfade",
            "audio.crossfade_length" to "Crossfade length", "audio.bass" to "Bass boost",
            "audio.bass_intensity" to "Bass boost intensity", "audio.crossfeed" to "Headphone crossfeed",
            "audio.crossfeed_intensity" to "Crossfeed intensity"))
        addAll(entries(Routes.SETTINGS_PLAYBACK, "Playback",
            "playback.preload" to "Preload next track", "playback.remember_queue" to "Remember queue"))
        addAll(entries(Routes.SETTINGS_GENERAL, "General", "general.default_tab" to "Default tab"))
        addAll(entries(Routes.SETTINGS_LIBRARY_SECTIONS, "Library sections",
            "library.liked" to "Liked", "library.downloads" to "Downloads",
            "library.top" to "Top 50", "library.recent_shortcut" to "Recently played shortcut"))
        addAll(entries(Routes.SETTINGS_PRIVACY, "Privacy",
            "privacy.screenshots" to "Disable screenshots", "privacy.clear_listening" to "Clear listening history",
            "privacy.clear_search" to "Clear search history"))
        addAll(entries(Routes.BACKUP, "Backup & restore",
            "backup.export" to "Export backup", "backup.restore" to "Restore from backup",
            "backup.import_csv" to "Import playlist from a file", "backup.import_m3u" to "Import M3U playlist",
            "backup.auto" to "Auto backup"))
        addAll(entries(Routes.STORAGE, "Storage",
            "storage.clear_downloads" to "Clear downloads", "storage.clear_cache" to "Clear cache"))
    }

    private val byLocation = entries.associateBy { it.route to it.title }
    fun idFor(route: String, title: String): String? = byLocation[route to title]?.id

    fun search(query: String): List<SettingsSearchEntry> {
        val normalizedQuery = normalize(query.trim())
        val terms = normalizedQuery.split(Regex("\\s+")).filter(String::isNotBlank)
        if (terms.isEmpty()) return emptyList()
        return entries.filter { entry ->
            val searchable = normalize("${entry.title} ${entry.category} ${entry.keywords}")
            terms.all(searchable::contains)
        }.sortedWith(compareBy<SettingsSearchEntry> {
            !normalize(it.title).startsWith(normalizedQuery)
        }.thenBy { it.category }.thenBy { it.title })
    }

    private fun normalize(value: String) = value.lowercase().replace("colour", "color")
}
