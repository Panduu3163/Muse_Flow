# Plan — mini-player fix, playlist detail redesign, track sheet redesign

Written 2026-07-26, end of the session that shipped multi-select and the playlist long-press menu.

**Updated same day, later in the session:** §0 (both bugs) and §2.1/§2.2 (interaction model +
select-all) are now built — see the ✅ markers below. §1 (playlist detail redesign) and §2.4/§2.5
(sheet layout redesign + new actions) are still just planned, blocked on the open questions listed
in §3. None of what's built has been device-verified yet.

Companion doc: `full-gap-audit.md` has been updated alongside this one — §2.2/§2.3 now reflect what
shipped today, and a new §2.9 lists the gaps this message surfaced.

---

## 0. Bug fixes (small, do these first)

**Both done 2026-07-26, same session as this doc.** Build green, 103 tests, 0 failures. Not yet
device-verified.

### 0.1 Mini-player doesn't appear on non-tab routes — ✅ done
**Symptom:** play a song from inside a playlist detail screen (or History, Backup, Equalizer) —
the mini-player doesn't show until you navigate back to a tab (Home/Search/Library/Settings).

**Root cause, confirmed by reading the code:** `MainActivity.kt`'s `Scaffold.bottomBar` renders
`MiniPlayer` and `MuseFlowNavBar` **together**, gated by one condition:
```kotlin
if (TopLevelDestination.forRoute(currentRoute) != null) {
    Column { MiniPlayer(...); MuseFlowNavBar(...) }
}
```
`TopLevelDestination.forRoute` returns null for every non-tab route (`playlist/{id}`, `history`,
`settings/backup`, `settings/equalizer`, `now_playing`). So the mini-player is coupled to the
bottom nav bar's visibility, and both vanish together on any screen that isn't one of the four
tabs. This is deliberate for the *nav bar* (comment at `MainActivity.kt:127`: "highlights nothing"
on those screens) but was never meant to take the mini-player down with it.

**Fix direction:** decouple the two conditions.
```kotlin
bottomBar = {
    Column {
        // Shows whenever something is loaded and the full player isn't already open on screen -
        // independent of whether this route is a tab.
        if (nowPlaying.hasMedia && currentRoute != Routes.NOW_PLAYING) {
            MiniPlayer(...)
        }
        if (TopLevelDestination.forRoute(currentRoute) != null) {
            MuseFlowNavBar(...)
        }
    }
}
```
**Things to check while implementing:**
- `Routes.NOW_PLAYING` must stay excluded — showing a mini-player *behind* the full player it
  mirrors is a real regression, not just a cosmetic one.
- `Routes.PLAYER` argument routes if any exist under `playerGraph()` — check
  `MainActivity.kt`'s `playerGraph` for any other full-player routes with different names before
  hardcoding just `Routes.NOW_PLAYING`.
- Screens with `bottom = 140.dp` list content padding (`PlaylistDetailScreen`, `HistoryScreen`,
  Library's lists) were already sized assuming a mini-player-height space at the bottom even
  though it wasn't rendering there — so no padding change should be needed, but verify on device
  that the last row isn't now hidden *or* that there isn't a big dead gap.
- Backup/Equalizer routes will start showing a mini-player too (they didn't before) — confirm
  that reads fine rather than cramped, since those screens weren't designed expecting one.

### 0.2 Playlist actions sheet's pin heart doesn't reflect the toggle live — ✅ done
**Symptom:** long-press a playlist → tap the header heart (or "Pin playlist") → it visually stays
in the same state. Only after closing the sheet and reopening it does the pin show correctly.

**Root cause, confirmed by reading the code:** `LibraryScreen.kt` holds the selected playlist as a
one-time snapshot:
```kotlin
var selectedPlaylist by remember { mutableStateOf<PlaylistEntity?>(null) }
```
set once on long-press (`onLongPress = { selectedPlaylist = it }`) and never refreshed. Toggling
pin updates the `playlists` `StateFlow` (via Room), but `PlaylistActionsSheet` is reading
`isPinned` off the **stale captured object**, which the flow update never touches.

**Fix direction:** stop holding the entity, hold the id, and look the live entity up from the
already-collected `playlists` list every recomposition:
```kotlin
var selectedPlaylistId by remember { mutableStateOf<Long?>(null) }
...
val selectedPlaylist = selectedPlaylistId?.let { id -> playlists.find { it.id == id } }
```
`selectedPlaylist` becomes null automatically if the playlist is deleted while the sheet is open
(e.g. from a second window/process) — dismiss cleanly in that case rather than crashing on a null
`.name`.

---

## 1. Playlist detail screen — redesign — ✅ done 2026-07-26 (open questions resolved with this doc's own recommended defaults)

Built: centered 140dp cover, centered title, three circular buttons (Shuffle / prominent filled
Play / ⋮ opening the same `PlaylistActionsSheet` Library's long-press uses), a metadata line
("N songs · duration" via new `List<Track>.totalDurationLabel()`, tested), an always-shown About
card (§1.2 Option A: created date - the one fact not already visible elsewhere on screen), a
per-screen sort/ascending state (**not** shared with Library's, per §1.3's own warning) reusing
`LibrarySortHeader`, and a top-right search icon that toggles a local text field filtering this
playlist's own tracks by title/artist (§1.1 resolved as "filters this playlist", not global
search). Selection, the track list, and the single-track sheet are otherwise unchanged from
before this redesign - same `TrackSelectionHost`/`TrackActionsHost` wiring, just reading from the
new sorted+filtered list instead of the raw one.

Deleting the playlist from its own `⋮` menu now also calls `onBack()` - the one call site where
`PlaylistActionsSheet`'s delete needs to navigate away rather than just refresh a list underneath.

Current `PlaylistDetailScreen.kt`: a plain top bar (back button, name, song count), a
Play/Shuffle pill row, then the track list straight below. No context menu on the header itself,
no "About" card, no sort controls.

**Target layout** (from the message):
```
[<- Back]                                         [Search]

                  +---------------+
                  |  Cover Art    |
                  |  Thumbnail    |
                  +---------------+
                       Title

 (Shuffle Button)    (Play Button)    (Context Menu)

             Metadata Text Line Vector
-----------------------------------------------------------
About Collection Card Component Block
-----------------------------------------------------------
[Sorting Dropdown] [Direction Toggle]
-----------------------------------------------------------
Track List View Stack...
```

### 1.1 Header
- Centered large cover art (currently there's no cover shown on this screen at all — only the
  small 52dp icon box in Library's row). `PlaylistEntity.coverImageUrl` already exists as a field;
  online-imported playlists populate it, locally-created ones don't (`null` → placeholder, same
  icon fallback `TrackRow`'s `Artwork` already uses).
- Title centered under the art (currently top-bar-left-aligned next to the back button).
- Three actions in a row under the title: **Shuffle**, **Play**, **Context menu** (⋮). Reuses the
  existing `PlayButton` composable for the first two (already in this file). The context menu
  button is new — it should open **the same `PlaylistActionsSheet`** already built for the
  Library long-press (per the message: "inside the context menu it is as same as the things when
  i long tap a playlist without opening or getting into it"). No new sheet needed here, just a
  second entry point into the existing one.
- **Search icon, top right** — new. Scope question: does this filter *this playlist's* tracks by
  title/artist (a local filter, no backend call), or does it open the app's global Search screen?
  A local filter is the more useful reading given it's inside a specific playlist's screen — flag
  as the default assumption, confirm before building.
- "Metadata Text Line Vector" under the action row — read as a small metadata line, likely
  song count + total duration (e.g. "42 songs · 2 hr 14 min"). Total duration isn't computed
  anywhere today; `Track.duration` is a formatted `"m:ss"` string per track, so summing it needs a
  parse step (`PlaybackHistoryEntity`/`Track` has no raw millis field for playlist tracks). Small,
  contained addition — a `List<Track>.totalDuration(): String` helper next to `LibrarySorting.kt`.

### 1.2 "About Collection Card Component Block"
Unclear from the ASCII alone what this card should contain for a **local** playlist that has no
description, creator, or source metadata beyond what MuseFlow itself tracks (name, created date,
cover, track count). Echo's "About" card on a real Echo playlist likely shows source-specific
metadata (creator name, YouTube description, etc.) that doesn't exist for a MuseFlow-native
playlist. Needs a decision before building:
- **Option A:** Show what MuseFlow actually has — created date, track count, total duration,
  source (if imported from CSV/online, note that). Always present, same shape for every playlist.
- **Option B:** Only show the card for playlists that have real source metadata (imported ones);
  omit it for locally-created playlists rather than showing a mostly-empty card. Matches this
  session's "no mockups — a control ships only when it changes behaviour" rule more closely.

Recommend **Option A** scoped down: created date + track count + total duration, always shown,
since that's real information MuseFlow already has for every playlist and needs no new schema.

### 1.3 Sort row
`[Sorting Dropdown] [Direction Toggle]` — this already exists as a component
(`ui/component/LibrarySortHeader.kt`, used throughout Library) but **isn't wired into
`PlaylistDetailScreen` at all** today; the screen has no sort/direction state. Needs:
- `TrackSortOption`/ascending state added to whichever ViewModel backs this screen
  (`LibraryViewModel` already owns `tracksForPlaylist`, but its `trackSort`/`ascending` state is
  currently screen-global for *Library's* sections — reusing it here would mean sorting the
  playlist screen also silently resorts Library's Liked/Downloads/etc. the next time you visit
  them, which is wrong). This needs its **own** sort/ascending `StateFlow` pair, scoped to
  `PlaylistDetailScreen`, not shared with Library's.
- Interacts with multi-select exactly like Library does: re-sorting mid-selection must clear the
  selection (see how `LibraryScreen.kt`'s `LaunchedEffect(section, trackSort, ascending) {
  selection.clear() }` does it — same pattern needed here keyed on this screen's own sort state).

### 1.4 Track rows
Covered in §2 below — same row redesign as Library/Search/History gets applied here too, since
they all render through the same `TrackRow`.

---

## 2. Track row + track long-press menu — redesign

### 2.1 Row interaction model changes — ✅ done 2026-07-26
**Today:** tap a row → plays it (or toggles selection if already in selection mode). Long-press a
row → opens `TrackActionsSheet`, which itself has a "Select" row to *enter* multi-select.

**Requested:** decouple the sheet from long-press entirely.
- **Long-press → enters multi-select directly**, ticking that row. No sheet in between.
- **A persistent trailing `⋮` icon-button on every row → opens the actions sheet.** Tap-only, no
  long-press needed to reach the sheet anymore.
- Plain tap still plays (outside selection mode) / toggles the row (inside selection mode) —
  unchanged.

This matches the standard YouTube Music / Echo interaction model and is a bigger change than it
first looks, because it touches **every screen already wired to `TrackRow`** — Library (list +
grid), History, PlaylistDetail, Search → Songs — the same four surfaces the multi-select work
touched today. Each needs:
- Its long-press handler changed from "open sheet" to "enter selection" (`selection.start(index)`
  directly, dropping the sheet's own now-removed "Select" row — `TrackActionsSheet`'s `onSelect`
  parameter becomes dead code and should be removed once every caller stops needing it).
- A new per-row `onOpenMenu: (Int, Track) -> Unit` (or similar) wired to the trailing icon, doing
  what long-press used to do (`selectedIndex = index; selectedTrack = track.asTrackResult()`).
- `TrackRow.kt` gains a trailing `IconButton` (⋮) — likely replacing the existing bare
  `trailing: @Composable (() -> Unit)?` slot's only current user (`HistoryScreen`'s remove-from-
  history ✕ button) or sitting alongside it. **Open question:** History currently uses `trailing`
  for a dedicated "remove from history" ✕ button per row — does the new ⋮ menu replace that (i.e.
  "remove from history" becomes an action inside the sheet instead of its own icon), or do both
  icons need to coexist on that one screen? Recommend folding it into the sheet as a new
  "Remove from history" action (only present when `TrackActionsHost` is instantiated from
  `HistoryScreen`, same nullable-lambda pattern `onRemoveFromPlaylist` already uses) — cleaner
  than two icons crowding one row.

**Built exactly as recommended above.** `TrackRow.onOpenMenu` replaced the `trailing` slot;
`TrackActionsSheet`'s "Select" row and `TrackActionsHost.onEnterSelection` were removed entirely;
`TrackActionsSheet` gained `onRemoveFromHistory`, wired only from `HistoryScreen` (History's old
per-row ✕ is gone, replaced by a "Remove from history" row inside the sheet). Grid cells in
Library have no room for a menu button, so they keep long-press-to-select only, same as before —
reaching the sheet from grid view means switching to list view first. `CollectionSheet`'s own
long-press (search → album/artist/playlist tracklists) is unchanged: that surface has no selection
state, so long-press there still opens the sheet directly, same as always.

### 2.2 Select-all — ✅ done 2026-07-26
The overflow-menu "Select all / Select none" toggle already exists inside `TrackSelectionBar`
(added today). The message asks to make it more prominent — "also add a button for select all
together" alongside making long-press the entry to selection. Recommend **promoting it out of the
overflow into a first-class icon** on the selection bar itself (next to Close), rather than
requiring an extra tap through the `⋮` overflow to reach it — it's a common enough action once
entering selection is a single long-press. `TrackSelectionBar.kt` already has `onToggleSelectAll`
wired through; this is just moving one `DropdownMenuItem` to a `BarAction` icon (`SelectAll`
already imported).

### 2.3 Songs section row shows only `⋮`, no heart/download glyphs
The message's "Songs" mockup shows only thumb / title / artist·duration / `⋮` — no liked-heart or
download-status glyph on the row itself, which `TrackRow` currently always shows when
`isLiked`/`isDownloaded`/`downloadProgress` are set. **Open question, flag before building:** is
this
- (a) a simplification in the ASCII art only (glyphs stay, `⋮` is additive), or
- (b) an intentional removal — status glyphs move *off* the row and become something you only see
  once you open the sheet (matching Echo more closely, where row real estate is tighter)?

Recommend (a) — keep the glyphs, they're useful at-a-glance information this session's own commit
history calls out as deliberate ("so a liked/downloaded row is identifiable at a glance without
opening its actions sheet") — and just add `⋮` after them. But this is a real design call, not an
implementation detail, so confirm before touching `TrackRow.kt`.

### 2.4 Actions sheet redesign — ✅ layout + Details done 2026-07-26; Edit/Ringtone/Refetch/Add-to-library/View-artist/View-album still not built
Header thumbnail added, second header icon dropped (per this doc's own recommendation), header
heart wired as a duplicate entry point to the same like toggle the list row already had (matching
the playlist sheet's pin heart pattern). Pills: Start radio / Add ("Add to queue") / Share, each
omitted individually when unavailable rather than shown disabled. Play next demoted into the list
as a two-line row. Download's list row gained supporting text. New: **Details** - a read-only
dialog (`TrackDetailsDialog.kt`) showing title/artist/duration/source/liked/downloaded/id, no
actions of its own. Still open, listed again in §2.5 below with their own blockers.
Current `TrackActionsSheet.kt`: header (title/artist, no icons), then a flat list — Play next, Add
to queue, [Start radio], divider, Like toggle, Download/Delete-download, Add to playlist,
[Remove from playlist], [Share]. Single-line rows, no supporting text.

**Target layout:**
```
[Drag Handle]                                    <- already free: ModalBottomSheet's default
                                                      dragHandle renders this already, no change

  Thumb   Husn                              [Heart]
          Nishant Das Choudhury · 4:00     [Icon ]
------------------------------------------------------
   ( Start radio )      ( Add )      ( Share )
------------------------------------------------------
  [Pencil]     Edit / Edit song
  [Play Next]  Play next / Add to the top of your queue
  [Queue]      Add to queue / Add to the bottom of your queue
  [+]          Pin to Speed dial
  [Library+]   Add to library / Save to your library
  [Download]   Download / Make available for offline playback
  [Bell]       Set as Ringtone
  [User/Music] View artist / <artist name>
  [Disc]       View album / <album name>
  [Refresh]    Refetch / Refetching the stream from YouTube...
  [Info]       Details / View the song's information
```

Header adds a thumbnail (currently text-only) and two trailing icons. **Open question, same shape
as the playlist sheet's header:** what does `[Icon ]` (the second, unlabeled one) do? Candidates:
a compact download-status glyph (mirrors the row's own glyph, redundant but consistent), or a
close/dismiss button (redundant with tapping outside the sheet / swiping down). Recommend treating
the header Heart as the existing Like toggle promoted to the header (same "duplicate entry point,
one flag" pattern as the playlist sheet's pin heart — confirmed to work, see §0.2's fix) and
**dropping the second icon** unless there's a specific thing it should do — a header with one
purposeful icon beats one with a decorative second slot.

Three pills replace the current flat "Play next / Add to queue / Start radio" block for the three
**most common** actions — but the pill labels in the mockup are **Start radio / Add / Share**,
which drops **Play next** from the pill row (it reappears further down as a full list row) and
turns **Add to queue** into a bare **"Add"** pill. Confirm before building: is "Add" here short
for "Add to queue" (pill) with "Play next" demoted to a normal list row, or does the pill row need
to keep all of play-next/add-to-queue and *add* Share as a third, dropping something else? The
ASCII only shows three pill slots.

### 2.5 New actions — feasibility notes (each is real, uncosted work)
| Action | What it needs | Notes |
|---|---|---|
| **Edit / "Edit song"** | A metadata-edit dialog + a place to persist it per source. | **Investigated 2026-07-26, found a hard blocker beyond the propagation-across-repos question below.** `AudioTagger.embedIfSupported` needs the download's original HTTP `Content-Type` to pick a tag writer (`audioTagFileExtensionFor`) — and that's **never persisted** (`DownloadedTrackEntity` has no content-type column). There is no reliable way to know a downloaded file's real container format after the fact. Worse, `audioTagFileExtensionFor` only maps `audio/mp4`/`audio/aac`/`audio/mpeg` at all — **WebM/Opus, YouTube's actual common format per `YouTubeStreamResolver.pickBestAudioFormat`'s own doc comment, returns null and is left untagged even at original download time.** So file-tag editing is infeasible for the majority of real downloads today, not just an inconvenience. A DB-only rename (skip the file tag entirely) is still possible but doesn't resolve the propagation-across-repos question already noted below - still needs a decision, now with the added caveat that "edit" can never mean "rewrite the actual audio file" for most tracks regardless of that decision. |
| **Pin to Speed dial** | A Speed Dial surface on Home. | Doesn't exist — same call made and deferred for the playlist sheet today. Deferring here too unless you want Speed Dial built as its own feature first. |
| **Add to library** | ✅ **resolved, no build needed** - per your call, this is the same action as "Add to playlist" (Library is the section that holds playlists in this app). No separate control added; the existing "Add to playlist" row stands in for it. |
| **Download** | Already exists (`onDownload`/`onCancelDownload`/`isDownloaded` on `TrackActionsHost` today) — carries over as-is, just re-skinned into the two-line row style. | ✅ done - no new work, just layout (see §2.4 note above). |
| **Set as Ringtone** | `RingtoneManager` + `WRITE_SETTINGS` permission (a special system-settings-redirect grant on Android, not a runtime permission dialog) + a real local file — grep confirms no `RingtoneManager`/`WRITE_SETTINGS` usage anywhere in the codebase today. | **Investigated 2026-07-26, found a hard blocker beyond the permission dance already noted.** `DownloadRepository.downloadsDir()` is `context.filesDir/downloads` - **app-private internal storage**. `RingtoneManager` and the system ringtone picker cannot see files there at all, permission or not. Making this work means copying the file out to public/MediaStore storage first (`MediaStore.Audio.Media.EXTERNAL_CONTENT_URI` insert, version-fragmented across API levels down to minSdk 24), *then* the `WRITE_SETTINGS` dance to actually set it. Meaningfully bigger and more fragile than it first looked - not attempted this session. |
| **View artist / View album** | Destination screens + browseIds on `TrackResult`. | ✅ **built 2026-07-26** - see §3's new entry below. Both blockers this row originally named are closed: `TrackResult`/`Track` now carry `albumId`/`artistId` (already present in the InnerTube parsing, just not surfaced before), and `ArtistScreen.kt`/`AlbumScreen.kt` exist as real destinations. |
| **Refetch** | Force a fresh stream resolve, bypassing whatever's cached. | **Investigated 2026-07-26, found this doesn't have a safe implementation as a generic per-row action.** Confirmed `StreamResolverRouter.invalidate()` really does only touch the legacy resolver's cache - the active InnerTube backend re-resolves fresh on every HTTP-open already (`ResolvingDataSource` in `PlaybackService.kt`), so there is nothing to invalidate on the path that matters. The only way to force a *real* re-resolve is to make ExoPlayer reopen the data source, and the obvious way to do that - `playerViewModel.play(track)` - **replaces the entire queue with a 1-item queue**, silently destroying whatever else was queued. A non-destructive version needs either a new `PlayerViewModel` method that reopens the current item in place without touching queue position/contents (untested whether `replaceMediaItem` with an identical Uri actually forces a fresh load rather than being treated as a no-op by ExoPlayer's diffing - unverified), or scoping this action to "only when this track is the one currently playing." Not attempted this session - the risk of shipping either a no-op or a queue-clobbering footgun outweighed the value of forcing it in. |
| **Details** | A read-only info sheet/dialog: title, artist, album, duration, source type, id, file path (if downloaded), added-at date. | ✅ done 2026-07-26 (`TrackDetailsDialog.kt`) - title, artist, duration, source, liked/downloaded state, track id. No album/file-path fields (not on `TrackResult`), no blockers hit. |

---

## 2.6 View artist / View album — ✅ built 2026-07-26 (the DB-upgrade work)

A full vertical slice, not just the sheet actions: schema, providers, two new screens, and
navigation. Broken into layers so the next session can find any one piece fast.

**Data model.** `TrackResult`/`Track` gained `albumId`/`artistId` (both nullable). These were
**already sitting in the InnerTube parsing** - `SongItem.album?.id` and
`SongItem.artists.firstOrNull()?.id` - just never surfaced into `TrackResult` before. No new
network calls needed for fresh search/tracklist results.

**DB migration `MIGRATION_11_12`** (v11 → v12): adds `albumId`/`artistId` columns to all four
tables that store a track outside a fresh search result - `playlist_tracks`, `liked_songs`,
`downloaded_tracks`, `playback_history`. Without this, "View artist"/"View album" would only work
on a track you'd just searched for, and lose it the moment it was liked/downloaded/playlisted/
played - each of those tables denormalizes its own copy of the track and had nowhere to put the
id. Every read/write path for all four (`toTrack()` conversions, `LikedSongsRepository.like()`,
`DownloadRepository`'s download-complete write, `PlaybackHistoryRepository.recordPlayed()`,
`PlaylistRepository.addTracks()`) now carries the two fields through.

**Provider layer.** `InnerTubeMusicProvider.getArtistTracklist()` now also returns the artist's own
name/imageUrl (`ArtistTracklist` gained those fields) - it already had them in the raw page
response, just wasn't surfacing them. New `getAlbumDetails(albumId): AlbumDetails` (title, artist,
imageUrl, tracks) - `getAlbumTracks()` (tracks only, used by Search's existing `CollectionSheet`
flow) was left untouched rather than changed, so nothing about Search's current behaviour risked
breaking. The **legacy** `YouTubeMusicProvider` got matching implementations (`MusicSearchRouter`
routes to whichever backend is active, so both need to satisfy the same interface) - its own
`getAlbumDetails` and enriched `getArtistTracklist` are new raw-JSON-parsing code, done carefully
with the same null-safe `optJSONObject`/`optString` chains as everything else in that file, but
**unverified against a real response** the way the InnerTube path implicitly is by already being
in daily use. If artist/album screens ever look broken specifically with Legacy selected in
Settings, start there.

**Screens.** `ArtistScreen.kt`/`AlbumScreen.kt` - both read-only, fetched live via
`MusicSearchRouter.getArtistTracklist()`/`getAlbumDetails()` on `LaunchedEffect(id)`, no local
ViewModel class (a one-shot fetch didn't need one). Circular cover for artist, square for album.
Play/Shuffle buttons, a plain track list with `TrackActionsHost` per row (no multi-select, no
sort/search - there's nothing to manage on a single fetched shelf the way there is on a playlist
you own). **Deliberately no local caching of the fetched page** - every visit re-fetches. That's
the gap the original Stage C framing (~20 DB entities, "must precede artist/album screens or they
refetch everything") was warning about; this session's migration only persists the *id* to
navigate with, not a cached copy of the artist/album page itself. Revisiting the same artist twice
in a session means two network calls. Acceptable for a first version; worth remembering if
performance ever becomes the complaint rather than "this screen doesn't exist."

**Navigation.** `Routes.ARTIST`/`Routes.ALBUM` (`"artist/{artistId}"`/`"album/{albumId}"`), ids
URL-encoded going in and decoded coming out (a browseId can contain characters that would
otherwise be read as extra path segments). `onGoToArtist`/`onGoToAlbum: (String) -> Unit` threaded
from `MainActivity` through every screen that can open `TrackActionsHost` - Library, PlaylistDetail,
History, Search - the same shape `onOpenPlaylist` already used.

**Sheet.** `TrackActionsSheet` gained "View artist" (supporting text: the artist name) and "View
album" (supporting text: the album name, from `TrackResult.source` - confirmed to always be the
real album name whenever `albumId` is non-null, since both come from the same source field). Both
nullable, shown only when the track actually has the corresponding id - a local file or an
albumless single simply doesn't offer them, same pattern as every other conditional action here.

**What this does NOT include**, so it isn't mistaken for done: Echo's actual artist page is 4
screens (overview/albums/songs/related) with a real discography, not one flat top-songs shelf;
there's still no Explore/Browse/Mood or New-releases screen: those remain the rest of Stage C in
`full-gap-audit.md` §2.1, unaddressed by this session's narrower slice.

---

## 3. Suggested build order for next session

Cheapest, highest-value first:
1. **§0.1 mini-player fix** ✅ done — one file, no ambiguity, fixes a real daily annoyance.
2. **§0.2 playlist pin reactivity fix** ✅ done — one file, no ambiguity, same shape as #1.
3. **§2.1/§2.2 interaction model change** ✅ done (long-press → select, ⋮ → sheet, select-all
   promoted to the bar) — touched Library list+grid, History, PlaylistDetail, Search → Songs. §2.3's
   glyph question was resolved by proceeding with this doc's own recommendation (a): row glyphs
   (liked heart, download status) stay exactly as they were, the ⋮ button was simply added after
   them — nothing about `TrackRow`'s existing glyph rendering changed.
4. **§2.4/§2.5 sheet redesign** ✅ layout + already-existing actions + Details done. §2.4's two open
   questions were resolved with this doc's own recommendations: header's second icon dropped, pill
   row's "Add" = Add to queue with Play next demoted to a list row.
5. **§1 playlist detail redesign** ✅ done, same session. §1.1/§1.2 resolved with this doc's own
   recommendations: search filters the playlist locally, About card is Option A (always shown).

**Still open - each investigated 2026-07-26 and found to have a real technical blocker, not just
an unanswered design question. See the §2.5 table for the full finding on each:**
- **Add to library** — genuinely just an unanswered design question (new concept vs. rename of an
  existing action). The one item here that's actually unblocked once you decide.
- **Edit song** — infeasible to rewrite the actual audio file for most downloads (WebM/Opus has no
  supported tag writer at all; the container format isn't even persisted for the rest). A DB-only
  rename is still possible but still needs the propagation-across-repos decision first.
- **Set as Ringtone** — downloaded files live in app-private storage `RingtoneManager` can't reach;
  needs a copy-to-public-storage step before the permission dance even matters.
- **Refetch** — the active backend has nothing to invalidate (it re-resolves every play already);
  the only way to force a real re-resolve risks silently wiping the user's queue.
- **View artist / View album** — unchanged, still blocked on Stage C (no destination screens, no
  browseId on `TrackResult`), see `full-gap-audit.md` §2.1.
