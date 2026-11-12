package com.example

import android.content.Context

import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import org.json.JSONObject

internal val Context.libraryViewsDataStore by preferencesDataStore(name = "library_views_v1")

internal data class LibraryViewPreference(
    val trackSort: TrackSortOption = TrackSortOption.DEFAULT,
    val playlistSort: PlaylistSortOption = PlaylistSortOption.DEFAULT,
    val ascending: Boolean = true,
    val grid: Boolean = false,
    val query: String = "",
)

internal class LibraryViewPreferences(context: Context) {
    private val store = context.applicationContext.libraryViewsDataStore
    private val key = stringPreferencesKey("sections")

    suspend fun read(): Map<LibrarySection, LibraryViewPreference> =
        decodeLibraryViews(store.data.first()[key].orEmpty())

    suspend fun write(views: Map<LibrarySection, LibraryViewPreference>) {
        store.portableEdit { it[key] = encodeLibraryViews(views) }
    }
}

internal fun encodeLibraryViews(views: Map<LibrarySection, LibraryViewPreference>): String =
    JSONObject().apply {
        views.forEach { (section, view) ->
            put(section.name, JSONObject().put("sort", view.trackSort.name)
                .put("playlistSort", view.playlistSort.name).put("ascending", view.ascending)
                .put("grid", view.grid).put("query", view.query.take(256)))
        }
    }.toString()

internal fun decodeLibraryViews(raw: String): Map<LibrarySection, LibraryViewPreference> {
    val root = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyMap()
    return LibrarySection.entries.mapNotNull { section ->
        val item = root.optJSONObject(section.name) ?: return@mapNotNull null
        section to LibraryViewPreference(
            trackSort = TrackSortOption.entries.firstOrNull { it.name == item.optString("sort") }
                ?: TrackSortOption.DEFAULT,
            playlistSort = PlaylistSortOption.entries.firstOrNull { it.name == item.optString("playlistSort") }
                ?: PlaylistSortOption.DEFAULT,
            ascending = item.optBoolean("ascending", true),
            grid = item.optBoolean("grid", false),
            query = item.optString("query").take(256),
        )
    }.toMap()
}
