# MuseFlow — session handoff (updated 2026-07-26, end of day)

Read this first. It is written so you can start work **without exploring the repo** — the file map
in §4 tells you where everything lives. Only open `full-gap-audit.md` when you need the full
backlog rationale.

**Start-of-next-session priority, found in the last few minutes of this one:** `loadAsUiState`
(`UiState.kt`) swallows every exception silently - `catch (e: Exception) { UiState.Error(...) }`
never logs `e`. New Releases device-confirmed throws (shows its error message), and there is
**zero trace of why** in logcat. This makes every screen built today (Charts, New Releases,
Explore, Browse, Artist, Album) equally blind to its own failures. Add logging there first - it's
a one-line fix that unblocks diagnosing New Releases, and likely Charts too. See
`full-gap-audit.md` §2.9's last two rows.

Companion docs:
- `full-gap-audit.md` — authoritative remaining-work list (~90 items). Supersedes `progress-vs-plan.md`.
- `echo-settings-teardown.md` — Echo's 290 preferences, grouped
- `echo-vs-museflow.md` — architecture comparison
- `progress-vs-plan.md` — **understates the gap; don't use its gap list**

---

## 0. RULE: stop and hand off when context grows

**This is not optional. Follow it.**

Context is re-sent in full every turn, so cost compounds through a session. When any of these is
true, **stop working, tell the user plainly, and offer to update this file for a fresh session**:

- The user reports token usage climbing fast (e.g. "18% → 35% in one prompt")
- You have taken **more than ~8 screenshots** in the session
- You have been through **more than ~3 build-test-fix loops** on device
- The session has covered **more than 2 features**

Do not "just finish one more thing" after noticing. The handoff costs far less than the next turn.

**What actually burns context, worst first:**
1. **Screenshots (~1–1.5k tokens each, re-sent every turn).** A 30-screenshot session carries
   ~40k tokens forever. Use `adb shell dumpsys media_session | grep`, logcat, and `sed` on XML test
   results instead. Screenshot only when a *visual* question can't be answered any other way, and
   say so.
2. **Large command output.** Never `ls` a directory with hundreds of files (`/sdcard/Download` has
   593). Always pipe through `grep`/`head`.
3. **Full file reads.** Prefer `grep -n "fun name" -A10`. Read whole files only for files you are
   about to rewrite.
4. **System file pickers.** Driving MIUI's picker cost ~12 screenshots and produced nothing. Ask
   the user to do picker steps.

---

## 1. What this app is

Android music player. YouTube Music (via a ported InnerTube client) + on-device files. Single
developer. Kotlin/Compose.

**Architecture:** two Gradle modules — `:app` and `:innertube`. **No Hilt** (deliberate). Manual
`Repo.getInstance(context)` singletons. MaterialKolor seed-based theming. Media3 + `MediaController`.
Package is flat: everything is `com.example` in `app/src/main/java/com/example/`.

Package name on device: `com.aistudio.museflow.kqfzyw`. `versionName` is static (`0.0.9`) — compare
`lastUpdateTime` against APK mtime to confirm an install landed.

---

## 2. Non-negotiable constraints (do not undo)

1. **InnerTube is the only extractor.** JioSaavn/NetEase deleted. `YouTubeMusicProvider` (legacy)
   still exists but is not the active backend — **never call it directly, go through
   `MusicSearchRouter`**. A worker did call it directly and was silently checking the wrong
   backend; fixed this session.
2. **NewPipe extraction runs BEFORE `player()`.** `player()` returns URLs whose `n` parameter is
   still obfuscated → HTTP 403. Order matters. Do not "simplify".
3. **Equalizer is DSP inside ExoPlayer, not `AudioEffect`.** This device's audio HAL refuses
   `android.media.audiofx.Equalizer` (`AudioFlinger … status: -38`). **Any future audio DSP
   (normalization, bass boost, virtualizer) goes in the processor chain.**
4. **`REPEAT_MODE_OFF` is deliberate.** `REPEAT_MODE_ALL` made one-track queues loop forever.
5. **Queue persistence stores identity/metadata only, never resolved URLs** (they expire in
   minutes). Restores **paused** on purpose.
6. **No mockups.** A control ships only when it changes behaviour.
7. **A feature ships when it works on device, not when it compiles.** Every bug listed in §6
   compiled cleanly and looked correct in review.

---

## 3. Compose gotcha that has bitten twice

**Never build a Flow inside composition.** `viewModel.someFlow(id).collectAsState()` where
`someFlow()` ends in `.stateIn(...)` creates a *new* flow every recomposition, each starting at its
initial value → visible flicker + a leaked collector per pass. Cache the flow in the ViewModel
(`mutableMapOf<Long, StateFlow<…>>` + `getOrPut`). Fixed in `LibraryViewModel.tracksForPlaylist`.

**A `pointerInput` lambda captures composition-scope values once.** State read through a
`remember { mutableStateOf(...) }` delegate stays live inside the gesture handler, because the
lambda holds the `MutableState` object. But a plain `val` computed during composition is captured
at gesture-setup time and never updates — an `onDragEnd` that reads one is reading a stale value
(in `QueueList`, a still-null target index). Recompute from the state inside the handler.

**Committing a reorder mid-drag cancels the drag.** Rows keyed by queue position get disposed and
recreated when the list mutates, taking the gesture's own composable with them. `QueueList` holds
the move until `onDragEnd` and shows the shuffle as a `graphicsLayer` offset only, so the list is
untouched for the whole gesture.

**Never key a `LazyColumn` by content that can repeat.** Duplicate Compose keys *throw*. Playlists
can hold the same track twice; phones hold duplicate files; YouTube can return the same browseId
twice. Use `itemsIndexed(key = { index, _ -> index })`.

---

## 4. File map — where everything lives

All paths under `app/src/main/java/com/example/` unless stated.

### Data models & providers
| What | File |
|---|---|
| `TrackResult`, `AlbumResult`, `ArtistResult`, `PlaylistResult`, `ArtistTracklist`, `MusicSource` | `Provider.kt` |
| `Track` (UI/db model) | `MusicData.kt` |
| InnerTube adapter: search, album/artist/playlist tracklists, suggestions | `InnerTubeMusicProvider.kt` |
| Legacy provider (**not active** — routed only) | `YouTubeMusicProvider.kt` |
| MediaStore local files + `LocalMediaPermission` | `LocalAudioProvider.kt` |
| **Routes every search/tracklist call to the active backend** | `MusicSearchRouter.kt` |
| Stream resolution routing | `StreamResolverRouter.kt`, `TrackStreamResolver.kt` |

### Database
`DownloadDatabase.kt` holds **all** entities, DAOs and migrations (currently v10+). 6 entities:
downloads, liked songs, playlists, playlist tracks, playback history, followed artists, plus
search-history and home-shelf-cache tables.

### Repositories (all `getInstance(context)` singletons)
`PlaylistRepository` · `LikedSongsRepository` · `PlaybackHistoryRepository` · `DownloadRepository` ·
`BackupRepository` · `FollowedArtistsRepository` · `QueueRepository` · `EqualizerRepository` ·
`SearchHistory.kt` · `HomeShelfCache.kt`

### Pure logic — testable, no Android deps (put new logic here)
| What | File | Tests |
|---|---|---|
| Cross-source title/artist matching (`isLikelyMatch`) | `SearchMatching.kt` | — |
| History day-bucketing | `HistoryGrouping.kt` | `HistoryGroupingTest` (7) |
| CSV playlist parsing (RFC 4180) | `PlaylistCsv.kt` | `PlaylistCsvTest` (11) |
| New-release diff | `ArtistReleaseDiff.kt` | `ArtistReleaseDiffTest` |
| Library sort options | `LibrarySorting.kt` | — |
| Playlist song-count text parsing | `InnerTubeMusicProvider.kt` (`asSongCount`) | `SongCountTextTest` (5) |
| Real-vs-fabricated track id guard | `TrackIdentity.kt` (`hasRealVideoId`) | `TrackIdentityTest` (7) |
| Positional multi-select set ops | `TrackSelection.kt` | `TrackSelectionTest` (8) |
| Backup JSON round-trip | `BackupRepository.kt` | `BackupRepositoryRoundTripTest` (Robolectric) |

### ViewModels
`SearchViewModel` · `LibraryViewModel` · `HomeViewModel` · `PlayerViewModel` · `HistoryViewModel` ·
`BackupViewModel` · `PlaylistImportViewModel` · `TrackActionsViewModel` · `LyricsViewModel` ·
`EqualizerViewModel` · `ThemeViewModel` · `AlbumPaletteViewModel` · `AppSettingsViewModel`

### UI
- Screens: `ui/screens/` — Home, Search, Library, NowPlaying, PlaylistDetail, Settings, Equalizer,
  **History**, **BackupSettings**
- Shared components: `ui/component/` — `TrackRow` (the one song row everywhere), `CollectionRow` +
  `CollectionSheet` (album/artist/playlist), `TrackActionsSheet`, `LibrarySortHeader`,
  `LocalMediaGate`, **`TrackActionsHost`** (the whole track context menu — sheet + dialog + wiring;
  drop it in and pass the selected track), **`TrackSelectionHost`** + `TrackSelectionBar` (the whole
  multi-select surface — same deal, pass a `rememberTrackSelection()` and the list its positions
  index), `AddToPlaylistDialog`, `Preference.kt` (`PreferenceGroup`, `SwitchPreference`,
  `NavigationPreference`, `ListPreference`, `SliderPreference`), `MiniPlayer`, `MuseFlowNavBar`,
  `LyricsView`, `SquigglySlider`, `ColorPickerDialog`

### Navigation
`NavRoutes.kt` — `Routes` object (HOME, SEARCH, LIBRARY, SETTINGS, NOW_PLAYING, HISTORY,
EQUALIZER, BACKUP, THEME_SETTINGS, PLAYLIST). `MainActivity.kt` — the `NavHost`; tab routes in
`topLevelGraph()`, everything else as sibling `composable()` blocks. Non-tab routes hide the bottom
bar and mini-player (`TopLevelDestination.forRoute` returns null).

### Playback & workers
`PlaybackService.kt` · `InnerTubeStreamResolver.kt` · `YouTubeStreamResolver.kt` · `YtHttpClients.kt`
· `ytcipher/` (cipher + PoToken) · `AutoBackupWorker` (24h) · `ArtistReleaseCheckWorker` (12h)

### InnerTube module
`innertube/src/main/kotlin/com/music/innertube/YouTube.kt` is the whole API surface — `search`,
`album`, `artist`, `playlist`, `playlistContinuation`, `home`, `explore`, `getChartsPage`,
`moodAndGenres`, `newReleaseAlbums`, `related`, `lyrics`, `player`, `newPipePlayer`. Grep it before
assuming an endpoint is missing.

---

## 5. What this session (2026-07-26) delivered

**Stage A fully complete (Charts shipped later the same day - see §8). Stage B complete — all
three items built, none device-verified.** A great deal more shipped after this section was
written; see §8's note.

### Stage B (second half of the session)

1. **Context menus.** The framing in the audit ("1 menu vs Echo's 21") understated the problem:
   the one menu was wired to **one of the five surfaces** that render a long-pressable `TrackRow`,
   so Library, History, PlaylistDetail and CollectionSheet all had a long-press that did nothing.
   New `ui/component/TrackActionsHost.kt` packages the sheet, the add-to-playlist dialog, the four
   observed repository flows and the queue calls into one composable; a screen now supplies only
   "which row is selected". Actions added: play next, add to queue, start radio, share, remove
   from playlist, delete download.
   - **Not built: go to album / go to artist.** Doubly blocked — `TrackResult` carries no
     album/artist browseId, and there are no destination screens. Both close in Stage C.
   - Needed `PlayerViewModel.playNext`/`addToQueue` (only `setMediaItems` existed, which *replaces*
     the queue) and `getRadioTracks` on the provider + router via `YouTube.next()`.
2. **Queue editing.** Drag-to-reorder from an explicit handle, plus per-row remove.
3. **Multi-select batch operations.** Selection is **positional**, not identity-keyed — the same
   reason the `LazyColumn` keys here are positional (§3): duplicate rows are real in this app, and
   `"title|artist"` would tick both copies. `TrackSelection.kt` holds the pure set ops;
   `TrackSelectionHost` is the drop-in surface, mirroring `TrackActionsHost`.
   - **Entered from the actions sheet's new "Select" row, not from long-press** — long-press
     already opens that sheet, and one gesture with two meanings means guessing.
   - The bar **replaces** the screen's header (sort controls / filter chips / play-shuffle row)
     rather than stacking below it: those are exactly the controls that would renumber the list
     under a live selection. Selection is also cleared on section change, re-sort and new query.
   - Actions: play next, add to queue, add to playlist, like/unlike, download, delete downloads,
     remove from playlist. The last two are behind the overflow because they can lose data.
   - Live on **Library (list + grid), History, PlaylistDetail, Search → Songs**. Deliberately
     **not** in `CollectionSheet` — a contextual bar inside a modal sheet has nowhere to live, so
     `onEnterSelection` is null there and the "Select" row simply doesn't appear.
   - Needed list overloads of `playNext`/`addToQueue` (one `addMediaItems` block, not one call per
     track — repeated inserts at `currentIndex + 1` play the selection backwards) and batch
     like/download/playlist methods on `TrackActionsViewModel`.
4. **Playlist long-press menu** (`ui/component/PlaylistActionsSheet.kt`), wired into Library's
   `PlaylistList` only - playlists aren't shown anywhere else. Cover, name, song count, a pin
   toggle (header heart *and* a list row both flip the same flag - deliberate, not a duplicate
   bug), a Shuffle pill, then Start radio / Play next / Add to queue / Pin / Download / Delete.
   Delete is behind a confirm dialog.
   - **DB migration `MIGRATION_10_11`** (v10 -> v11): adds `PlaylistEntity.isPinned`. Pinned
     playlists sort first in `PlaylistDao.observeAll()` and in
     `sortedByLibraryOption(PlaylistSortOption, ...)` regardless of the chosen sort - pin is an
     override on top of sort, not a sort option of its own.
   - `PlaylistRepository.delete()` also wipes the playlist's rows in `playlist_tracks` - no FK
     cascade is declared, so skipping that step would orphan them permanently.
   - **Deliberately not built**, per this session's scope call: **Share** (a local playlist has no
     public link) and **Pin to Speed dial** (no Speed Dial surface exists on Home; per §2 rule 6 a
     control ships only when it changes behaviour, and there's nowhere for this one to send anyone
     yet).
   - Radio seeds from the playlist's first track via the existing `PlayerViewModel.startRadio`;
     Play next/Add to queue/Download reuse the list overloads and `downloadAll` from item 3 above.
   - `LibraryViewModel.tracksForPlaylist(id)` only starts emitting once collected
     (`WhileSubscribed` stateIn) - the sheet does `collectAsState()` on it itself so a playlist
     whose detail screen was never opened this session still has a populated track count and
     track list to act on.

### Stage A (first half)

1. **Search filters** — Songs/Albums/Artists/Playlists tabs, fetched lazily (one request, not four),
   per-tab cache keyed to the committed query, per-tab error+empty states. Albums/artists/playlists
   open a `CollectionSheet` with Play/Shuffle and a tracklist. Added
   `getAlbumTracks`/`getArtistTracklist`/`getPlaylistTracks` to the InnerTube provider **and the
   router** (they existed only on the legacy provider).
2. **On-device files** — new `LibrarySection.OnDevice`. **Fixed the real blocker:**
   `READ_MEDIA_AUDIO` was declared in the manifest but never *requested*, so MediaStore returned
   an empty cursor on Android 13+. `LocalMediaGate` asks contextually, falls back to an app-settings
   deep link after refusal, re-checks on resume. Verified: 50 files found, `content://` playback works.
3. **History screen** — `Routes.HISTORY`, reached from Library → Recent → "View full history".
   Day-grouped, play counts, per-row remove, clear-all behind a confirm. Added `observeAll`,
   `deleteByKey`, `clearAll` to the DAO (**no schema change**).
4. **Backup & restore UI** — `BackupSettingsScreen`, SAF export/import, auto-backup toggle (the
   worker was already real), scope stated up front.
5. **Followed artists** — was **completely unreachable**: nothing called `follow()`. Added the
   Follow/Following toggle to the artist sheet.
6. **CSV playlist import** — Settings → Backup & restore → Import. Parses an Exportify-style CSV,
   matches each track via `isLikelyMatch`, reports unmatched tracks by name.

---

## 6. Bugs fixed this session (all compiled cleanly first)

1. `ArtistReleaseCheckWorker` fetched via the **legacy** provider, not the router — checking a
   backend the app no longer uses.
2. Playlist rows showed fabricated song counts. `songCountText` is the subtitle's last run — often a
   duration — and `filter { isDigit() }` concatenated every digit, so "2 hours, 7 minutes" → "27".
3. `PlaylistDetailScreen` flicker — new `StateFlow` per recomposition (see §3). **Pre-existing.**
4. Restore duplicated playlists. Now skips a playlist whose **name and exact track set** already
   exist; still refuses to merge on name alone.
5. Duplicate Compose keys in search lists, collection sheets and `LibraryScreen.TrackList`.
6. Backup export said "Written to 2353" — `uri.lastPathSegment` is the document id; now queries
   `OpenableColumns.DISPLAY_NAME`.
7. `:innertube` `SearchVideoTest` never ran — trailing `?.forEach` made the return type `Unit?`,
   which JUnit4 rejects as "should be void". The root `test` task was never actually green.
8. Empty search results said "Search for something to get started" even after searching.

9. **`Track.asTrackResult()` fabricates a YouTube identity.** It sets `id = sourceId ?: "title|artist"`
   *and* defaults `sourceType` to `YOUTUBE_MUSIC`. A stored track with no `sourceId` therefore
   reports itself as a YouTube track carrying an id YouTube has never seen. Caught while wiring
   Share and Start radio — gating those on `sourceType` (the obvious implementation) would have
   produced share links to `watch?v=Some Song|Some Artist` and radio seeds that resolve to nothing.
   The guard is `TrackIdentity.hasRealVideoId()`, with tests. **Anything that turns a track id into
   a URL or an API argument must call it first.**

**A correction worth carrying:** an earlier claim that a `fillMaxSize` child overflows a `Column`
was **wrong** — Compose measures non-weighted children against the *remaining* space. Don't "fix"
that pattern again.

---

## 7. Not verified on device (do these first — cheap)

- **Context menus (Stage B item 1)** — long-press a row on each of the five surfaces. The one to
  watch: long-pressing inside a `CollectionSheet` stacks a second `ModalBottomSheet` on the first.
  Material3 allows it; whether it *reads* well is the open question. Fallback if it doesn't is to
  dismiss the collection sheet first, at the cost of losing the user's place in the album.
- **Queue reorder/remove (Stage B item 2)** — drag the handle; confirm the move survives the
  resync from `MediaController`. Also: removing the *currently playing* row should advance, not
  stop; tapping the drag handle must **not** start playback (the row's click is scoped to the
  artwork and titles for exactly this reason); reorder with shuffle on (indices are timeline
  order, not shuffle order).
- **Multi-select (Stage B item 3)** — sheet → "Select", then tick rows. The ones to watch:
  select-all on a long Library list then Play next (order must survive); switching Library section
  or re-sorting mid-selection (must clear, not retarget); the Search → Songs bar replacing the
  filter chips; batch remove-from-playlist shortening the list under the selection; and that
  "Select" is absent inside a `CollectionSheet`.
- **Playlist long-press menu** - long-press a playlist row in Library. Watch: the DB migration
  (v10 -> v11) actually runs on the existing installed database rather than crashing on first
  open; pin from the header heart and pin from the list row agree (same flag, two entry points);
  a newly-pinned playlist jumps above ones created after it under every sort option, not just
  "Date created"; delete's confirm dialog, and that Home/other screens don't still reference a
  deleted playlist id; Download queues every track without visiting the playlist's own screen.
- Restore de-duplication (restore the same backup twice → no duplicates).
- Follow toggle persisting across an app restart.
- `ArtistReleaseCheckWorker` firing on its real 12h schedule. WorkManager refuses forced early runs
  of periodic work (`cmd jobscheduler run` → "executed before schedule"). Registration *is*
  confirmed via the diagnostics broadcast; the routed fetch is confirmed because the artist sheet
  uses the same router call.

---

## 8. Next work, in order

**This section is stale as of later the same day (2026-07-26) — a lot shipped after it was
written.** `full-gap-audit.md` and `PLAN-2026-07-26-playlist-track-ui.md` are the current source
of truth; read those first. Summary of what closed since this section was written: Charts,
multi-select, the playlist long-press menu, the track actions sheet redesign (pills/Details/View
artist/View album), the playlist detail screen redesign, a DB migration (v11→v12, `albumId`/
`artistId`), the mini-player-hidden-on-non-tab-routes bug, the playlist-pin-reactivity bug, and an
in-app crash log viewer (Settings → Crash logs). **None of it has been device-verified yet** —
that's genuinely the next thing to do, not more building. See `PLAN-2026-07-26-playlist-track-ui.md`'s
own "still open" list for what's left (Edit song metadata, Set as Ringtone, Refetch stream - each
has a real technical blocker written up there, not just "not built yet").

**Stage C — the one structural investment, still ahead**
Artist/Album screens now exist (`ArtistScreen.kt`/`AlbumScreen.kt`) with Follow/Following and
Albums/"Fans might also like" shelves, but are a **narrower slice** than the original Stage C
framing: fetched live with no local caching - re-visiting the same artist twice in a session means
two network calls. The full ~20-entity DB expansion this section originally called for
(Song/Album/Artist + junctions, so browsing doesn't refetch everything) is **still not done** -
only `albumId`/`artistId` got persisted, not a cached copy of the pages themselves. The
**followed-artists management list is done** (Library → Following, `FollowedArtistsList` in
`LibraryScreen.kt`) - it turned out not to need the collection-sheet plumbing this note used to
say it was blocked on, once `ArtistScreen` existed as a real navigation destination. **New
releases and Explore/Mood & genres are done** too - `NewReleasesScreen.kt`; `ExploreScreen.kt`
(mood/genre tiles) + a genuinely generic `BrowseScreen.kt` (any browseId+params, mixed
track/album/artist/playlist shelves) built on `YouTube.browse()`, which was ported but sitting
completely unused. Playlists opened from `BrowseScreen` use the existing `CollectionSheet` modal
(no dedicated remote-playlist screen exists) - same pattern Search's own playlist results already
use. Still ahead: a real multi-tab artist page (Echo's is 4 screens - this is still 1 + two
shelves), Charts (screen built, **data broken** - `getChartsTracks()` returns zero tracks, cause
not yet found, see `full-gap-audit.md` §2.9).

**Then** Stage D (player depth), E (settings breadth), F (platform surfaces), G (accounts).

---

## 9. Spotify import — decided, don't re-litigate

**Decision: CSV import only. Done.** Investigated and rejected the API route.

Findings so you don't repeat the research:
- Echo's Spotify import **requires a login**. `addPlaylistByUrl()` calls `ensureAuthenticated()`,
  which throws when no `sp_dc` cookie is stored. Their "import by link" dialog means *someone
  else's* playlist, **not** login-free.
- Echo avoids a Client ID, not a login: WebView → `sp_dc` cookie → web-player token minted with a
  TOTP whose secret comes from a **community GitHub Gist**, hitting
  `api-partner.spotify.com/pathfinder/v2/query` with **rotating persisted-query hashes**.
- An **anonymous token is obtainable with no cookie** (verified: `isAnonymous: true`). The data call
  wasn't provable — `api.spotify.com` 429'd from WSL. So login-free is *unproven*, not impossible.
- Cost if revisited: ~2,300 lines in Echo, plus a permanent dependency on a stranger's gist and on
  hashes Spotify rotates.

---

## 10. Device testing runbook

Phone: Xiaomi 14 Ultra, `com.aistudio.museflow.kqfzyw`. **Bus ID changes between sessions.**

```
powershell.exe -Command "& 'C:\Program Files\usbipd-win\usbipd.exe' list"      # find BUSID
powershell.exe -Command "& '...\usbipd.exe' attach --wsl --busid <BUSID>"
# then ask the user to run: sudo chmod 666 /dev/bus/usb/*/*     (needed after every attach)
adb kill-server && adb start-server && adb devices -l
```

- `no permissions` in `adb devices` = the chmod step hasn't been done. The user runs the sudo
  chmod themselves interactively, or hands you the password to pipe in (`echo <pass> | sudo -S
  chmod 666 /dev/bus/usb/*/*`) - ask rather than assuming either way.
- **The user installs APKs themselves.** Build, give the path, wait.
- Use `adb install -r` — a plain reinstall wipes app data.
- **Tapping: use `uiautomator dump`, not screenshot pixel-guessing.** The 900×2000-screenshot
  ×1.2-scale approach is unreliable and wasted many turns this session before being replaced.
  Correct method:
  ```
  adb shell uiautomator dump /sdcard/w.xml && adb pull /sdcard/w.xml w.xml
  grep -o 'text="Charts"[^>]*bounds="\[[0-9,]*\]\[[0-9,]*\]"' w.xml   # -> [158,454][279,501]
  adb shell input tap 218 477   # centre, real device px, no scaling math
  ```
  Long-press: `adb shell input swipe 400 755 400 755 700` (same start/end, held). Scroll:
  `adb shell input swipe 540 1800 540 600 300`. A `testTag` does **not** show as `resource-id` in
  the dump - match on visible `text=`/`content-desc=` instead, and if a leaf text node shows
  `clickable="false"`, walk up to the enclosing node for the real bounds.
  **`uiautomator dump` reliably fails with "could not get idle state" whenever the mini-player is
  visible with something playing** (a continuously-recomposing element never reads as idle) -
  retry once or twice, and if it keeps failing, screenshot instead (screenshots don't need idle
  state) rather than burning turns on repeated dumps.
- **The keyboard moves dialogs.** A dialog's buttons are in a different place once the IME is up;
  re-screenshot after typing before tapping Create/OK. This cost a false "playlist creation is
  broken" conclusion.
- Prefer non-visual verification: `adb shell dumpsys media_session | grep -i "queueTitle\|state="`,
  `adb logcat -d -b crash`, `adb shell dumpsys package <pkg> | grep lastUpdateTime`.
- **`Modifier.blur()` silently no-ops on this device** (real `RenderEffect`-backed Gaussian blur
  renders as a hard, completely unblurred edge - not an error, no crash, just no effect). Don't
  build a "soft glow"/blur effect assuming `.blur()` works; use a `Brush.radialGradient` that's
  already transparent at its own edge instead - it degrades gracefully everywhere blur doesn't.

---

## 11. Build

```
./gradlew assembleDebug testDebugUnitTest
```
APK: `app/build/outputs/apk/debug/app-debug.apk`. Both modules must be green — `:innertube` has its
own tests. Treat any `w:` warning as something to fix before handing over.

---

## 12. Session 2026-07-26 (later) — Stage D started, redesigns, playback fixes

**Stage A/B/C are fully complete** (see `full-gap-audit.md` §3). Charts/New Releases are
**deliberately hidden** from Search, not fixed - real backend bugs, see
`docs/SESSION-HANDOFF.md`-adjacent memory (`museflow-charts-new-releases`) for the diagnostic
technique if revisited.

### Redesigns (device-verified)
- Artist page: full-bleed fading cover, subscriber/listener capsules, About+expand, non-sticky
  tabs (Overview/Songs/Albums/Related) - a `stickyHeader` was tried first and rejected: it parks at
  the same fixed screen position a floating back button occupies, so the two permanently overlap
  once scrolled. Plain scrolling `item` instead.
- Playlist detail: 2×2 track-thumbnail mosaic cover (falls back to a real cover, then one track's
  art, then a plain icon), same capsule/About pattern.
- Both screens' back buttons (and Playlist's search toggle) now fade+slide out via `graphicsLayer`
  as the cover scrolls past, driven by `LazyListState.firstVisibleItemScrollOffset` vs the cover
  item's own measured height (from `layoutInfo.visibleItemsInfo`) - not fixed overlays anymore.
- Search's Artist tab navigates to the real `ArtistScreen` now, not the `CollectionSheet` modal
  (Album/Playlist search results still use the sheet - user hasn't asked for those yet).

### Playback (device-verified)
- **Instant seek**: `StreamCache.kt` wraps the resolving data source in a `CacheDataSource`/
  `SimpleCache` (500MB, `cacheDir`, separate from the permanent Downloads folder). A background
  `CacheWriter` prefetches the rest of the current track the moment it starts, so a far-ahead seek
  is usually already on disk. Cache key is the *placeholder* URI (stable, videoId-based) - the
  resolved CDN URL isn't usable as a key, it carries a short-lived signed token that changes every
  resolve.
- **Play downloaded tracks locally**: `PlayerViewModel.toMediaItem()` is now a suspend member
  (moved inside the class) that checks `DownloadRepository.getByKey()` (new method) before falling
  through to `directStreamUrl`/the YouTube placeholder - added `TrackResult.downloadKey()`
  (title/artist, matching `Track.downloadKey()`) since a download made from search shares no `id`
  with the same track reached from a shelf/playlist later.
- **HQ cover art**: `ThumbnailRenderer.getThumbnailUrl()` (the one shared choke point for every
  thumbnail) upgrades YouTube's URL to 544px. **Backfilled at read time**, not migrated: every
  `...Entity.toTrack()`/cache-parse function that reads a persisted `imageUrl` now re-applies
  `upgradeThumbnailSize()` (idempotent - already-upgraded URLs pass through unchanged), so old
  history/downloads/playlists/followed-artists/Home-shelf-cache/artist-album-cache rows get sharp
  art too, with zero DB migration.
- **Now Playing "Glow" background**: `BackgroundStyle.Glow`, three `Brush.radialGradient` discs
  (dominant/vibrant/muted from `AlbumPalette`, which now has a third `vibrant` swatch) drifting via
  `rememberInfiniteTransition`. **Radial gradient, not `Modifier.blur()`** - blur silently doesn't
  render on this device (see §10's new blur note). User feedback pending on this session's end:
  colors need more visibility/intensity - **next session should start by boosting the glow's
  color/alpha values** in `GlowBlob` (`ui/screens/NowPlayingScreen.kt`), not re-litigating the
  gradient-vs-blur approach (that part is confirmed correct/working).

### Stage D — done this session
Sleep timer (`SleepTimer.kt`, self-contained singleton, survives ViewModel teardown), speed/pitch
(`PlaybackParameters`, dialog with a "Preserve pitch" toggle for real time-stretch vs pitch-follows-
speed), audio normalization (`NormalizerAudioProcessor` - dynamic-range compressor, not a fabricated
per-track loudness DB), crossfade (fade-based, formula-driven off position/duration each 500ms
tick - honestly *not* true dual-decoder mixing, same as most mobile players under that name), bass
boost (`BassBoostAudioProcessor`, separate low-shelf from the 7-band EQ so they don't fight over one
number), headphone crossfeed (`CrossfeedAudioProcessor`, classic "Chu Moy" technique - explicitly
*not* a 3D/HRTF spatial virtualizer, which is a much bigger unbuilt feature), swipe gestures on Now
Playing artwork (horizontal = skip, vertical = **device volume via `AudioManager`**, deliberately
not `MediaController.volume` since crossfade already drives that every tick).

**Still open, in original priority order**: boost Glow's visibility (see above, quick), Search
Album/Playlist redesign + an "Add to playlist" button on those results (asked for, not started),
lyrics depth (romanization/translation/more providers/karaoke - the biggest remaining chunk), then
Ambient/AOD, Ringtone maker, SponsorBlock (explicitly deferred by the user, lowest priority).

---

## 13. Session 2026-07-26 (later still) — Glow intensity + Search Album/Playlist redesign

Both built and compiling/testing green (`assembleDebug testDebugUnitTest`). **Neither device-verified
yet** - do that first next session, cheap.

- **Glow background intensity** - `GlowBlob` (`NowPlayingScreen.kt`) now saturates/brightens the
  source palette colour via `boostSaturation()` (HSV sat ×1.4, value ×1.15) before applying it,
  widened the gradient's opaque core (4 colour stops instead of 3, higher peak alpha), grew each
  blob 500dp→560dp, and dropped the flattening scrim from 0.4 alpha to 0.22. Watch on-device for
  whether it's now *too* intense against light-coloured album art - alpha/saturation constants are
  the knobs if so.
- **Search Album/Playlist redesign** - both now navigate to real destination screens instead of the
  `CollectionSheet` modal, matching the Artist tab's existing pattern:
  - **Albums** → the existing `AlbumScreen` (just rewired `onOpen`, no new screen).
  - **Playlists** → new `RemotePlaylistScreen.kt`, same visual language as `AlbumScreen` (centred
    140dp cover, headline title, Play/Shuffle/**Add** pill row) rather than `PlaylistDetailScreen`'s
    mosaic-cover treatment, which is specific to library-owned playlists (pin/delete/download menu,
    tracks come from Room). Reached via a new route `Routes.REMOTE_PLAYLIST` -
    **title/subtitle/imageUrl travel as nav args alongside the browseId**, not fetched, because no
    InnerTube endpoint returns a remote playlist's own header by id alone; the search result row
    already has them. No local cache table (unlike Artist/Album's `MIGRATION_12_13`) - re-opening the
    same playlist refetches every time. Watch on-device: long playlist titles/subtitles surviving
    URL encoding through nav args round-trip; the "Add" pill opening `AddToPlaylistDialog` and
    actually adding every track (uses `TrackActionsViewModel.addToPlaylist`/`createPlaylistWith`,
    same calls the per-track sheet already uses - batch, not looped).
  - Cleaned up now-dead code this forced: `SearchViewModel.openAlbum`/`openPlaylist`/
    `openCollection`/`closeCollection`/`toggleFollow`/`followedArtistIds`/`collectionJob` all
    deleted (only Search used them; `CollectionTracks`/`CollectionKind`/`CollectionSheet` itself
    stay - `BrowseScreen` still uses the sheet for its own playlist results, out of scope this pass
    per the audit note that nobody asked for that one yet).

**Next up**: device-verify both of the above, then lyrics depth (the big one) or Ambient/AOD/
Ringtone/SponsorBlock (explicitly deferred, lowest priority).

---

## 14. Session 2026-07-26 (retouch pass) — design parity + Library grid + one-tap Add

User feedback on §13's first pass: the redesign needed to match Library's playlist screen more
precisely, and the remote-playlist "Add" flow was wrong (opened a naming dialog instead of just
saving the playlist as-is). All four points addressed, compiling/testing green, **none
device-verified yet**.

- **`RemotePlaylistScreen` now visually identical to `PlaylistDetailScreen`** - full-bleed 2x2
  track-mosaic cover fading into the background, headline title, song-count/duration capsules, an
  About card (shows the search result's own subtitle text, since a remote playlist has no
  "created" date to show), and the same floating fade-on-scroll back button. The one addition is
  the third circle button next to Shuffle/Play: Library's copy there is a "⋮" menu
  (pin/delete/download - all Library-only concepts that don't apply to a playlist not yet saved
  anywhere), this screen's is **Add** instead.
- **`AlbumScreen` got the same cover/capsule/back-button treatment** (full-bleed square cover,
  fading gradient, floating back button, a song-count capsule) - **the Play/Shuffle pill buttons
  themselves were left untouched**, per instruction, not converted to the circle-button style.
- **Add button fixed to one-tap, no dialog** - it used to open `AddToPlaylistDialog` (pick an
  existing playlist or type a name for a new one), which is wrong for a playlist that already has a
  name and cover from the source. New `PlaylistRepository.importOnlinePlaylist()` call site (the
  method already existed, written for exactly this, but had never been wired to any UI) via a new
  `TrackActionsViewModel.addRemotePlaylistToLibrary(name, coverImageUrl, tracks)` - saves the
  playlist under its own title/cover immediately, swaps the icon to a checkmark once done, no
  dialog at any point.
- **Library's Playlists section is now a 2-column grid**, not a list - each tile is a big square
  showing the same 2x2 track-thumbnail mosaic `PlaylistDetailScreen`'s own cover uses (not a literal
  4x4/16-image grid - four is what the existing mosaic pattern everywhere else in the app already
  uses, so tiles read consistently with the full playlist screen's own cover), title, song count,
  and the pin glyph. `PlaylistList` now takes the `LibraryViewModel` directly (was just a plain
  list before) since each tile needs its own `tracksForPlaylist(id)` subscription to build its
  mosaic.

**Next**: device-verify this whole retouch pass (Search → Album, Search → Playlist → Add, Library →
Playlists grid) before touching lyrics depth.
