# Echo-Music vs MuseFlow — comprehensive comparison

Written 2026-07-25. Echo-Music analysed at commit `ad66171` (2026-07-25, verified current with
origin). MuseFlow analysed at its post-frontend-wipe state.

**Important framing:** MuseFlow's frontend was deliberately deleted before this comparison. Where a
row says MuseFlow "had" something, that means the backend capability survives but **the UI must be
rebuilt**. Rows marked ❌ mean the capability never existed at all.

---

## 1. Scale and shape

| Metric | Echo-Music | MuseFlow |
|---|---|---|
| Kotlin files | 625 | 54 (42 top-level + 12 `ytcipher/`) |
| Lines of Kotlin | 146,881 | 6,218 (backend only; was 17,466 with UI) |
| Gradle modules | 14 | 1 (`:app`) |
| UI layer | 227 files / ~81k lines | **0 — deleted** |
| ViewModels | 30 | 0 (deleted; 10 previously) |
| DB entities | 28 | 8 |
| Source root | `app/src/main/kotlin`, pkg `com.music.echo` | `app/src/main/java`, pkg `com.example` |
| License | GPL-3.0 | GPL-3.0 (compatible) |

Echo is **~8.4× larger**. It is a mature multi-contributor project; MuseFlow is a single-developer
app. Scale differences below are mostly *scope* decisions, not quality gaps.

---

## 2. Architecture

| Concern | Echo-Music | MuseFlow |
|---|---|---|
| Dependency injection | **Hilt 2.59** (`di/AppModule`, `NetworkModule`, qualifiers) | Manual `getInstance(context)` singletons |
| Modularisation | 14 modules; each source/lyrics provider is its own Gradle module | Single module, providers are plain classes |
| Navigation | `NavHost` + extracted `ui/screens/NavigationBuilder.kt` | Was a single `NavHost` in `MainActivity` (deleted) |
| Entry point | `MainActivity.kt` — 1,560 lines | Deleted (must rebuild) |
| State | 30 ViewModels + Compose state | Was 10 ViewModels (deleted) |
| Persistence | Room (28 entities, normalised w/ junction tables) | Room (8 entities, flat per-feature) + DataStore |
| Image loading | Coil 3.3.0 | Coil |
| Player | Media3 1.7.1 + Cast | Media3 (no Cast) |

**Key structural difference:** Echo models a *normalised music library* — `SongEntity`,
`AlbumEntity`, `ArtistEntity` plus `SongAlbumMap`, `SongArtistMap`, `AlbumArtistMap`,
`PlaylistSongMap`, sorted variants, and `SongWithStats`. MuseFlow stores flat, feature-scoped tables
(`LikedSongEntity`, `DownloadedTrackEntity`, `PlaybackHistoryEntity`…) with no shared song identity.
Echo's model enables artist/album pages backed by local data; MuseFlow's requires refetching.

---

## 3. Music sources and content

| Source | Echo-Music | MuseFlow |
|---|---|---|
| YouTube Music | ✅ `:innertube` module — 106 files, 8,302 lines | ✅ `YouTubeMusicProvider` (590 lines) |
| YT cipher / PoToken | ✅ configs synced from upstream `zemer-cipher` | ✅ own `ytcipher/` — 12 files incl. WebView + PoToken |
| Spotify | ✅ `spotify/` (15 files) + `spotifyimport/` (3) | ❌ |
| **JioSaavn** | ❌ | ✅ `JioSaavnProvider` (266 lines) |
| **NetEase** | ❌ | ✅ `NetEaseProvider` (273 lines) |
| Local device files | ✅ `localmedia/` | ✅ `LocalAudioProvider` (MediaStore) |
| Apple Music | ✅ scraper (for Canvas art) | ❌ |
| Multi-source merge/ranking | Limited — YouTube-Music-centric | ✅ `SearchMatching` ranks across sources w/ fallback |

**MuseFlow's genuine edge:** it is *multi-source by design* (4 sources incl. JioSaavn and NetEase,
which Echo lacks entirely) with cross-source result ranking and automatic fallback. Echo is broader
but overwhelmingly YouTube-Music-centric.

---

## 4. Lyrics

| Provider | Echo-Music | MuseFlow |
|---|---|---|
| LRCLIB | ✅ `:lrclib` module | ✅ `LrcLibProvider` |
| BetterLyrics | ✅ `:betterlyrics` module | ✅ `BetterLyricsProvider` |
| KuGou | ✅ `:kugou` | ❌ |
| PaxSenix | ✅ `:paxsenixlyrics` | ❌ |
| YouLy+ | ✅ `:youlyplus` | ❌ |
| SimpMusic | ✅ `:simpmusic` | ❌ |
| Synced/word-by-word | ✅ `Lyrics.kt` 2,343 lines + `MetroLyrics` 884 | ✅ backend supports; UI deleted |
| Lyrics image card (share) | ✅ `LyricsImageCard.kt` | ❌ |
| Romanization | ✅ dedicated settings screen | ❌ |
| Lyrics translation/AI | ✅ `ai/` (4 files) | ❌ |

**6 lyrics sources vs 2.** Echo's biggest content advantage.

---

## 5. Playback and audio

| Feature | Echo-Music | MuseFlow |
|---|---|---|
| Background playback + media notification | ✅ | ✅ `PlaybackService` |
| Queue management | ✅ `Queue.kt` (1,492 lines) | ✅ backend; UI deleted |
| Equalizer | ✅ `eq/` (8 files) + `AxionEqScreen`, `CircularEqControl` | ✅ `EqualizerController` + `EqualizerRepository` |
| Sleep timer | ✅ | ✅ (was in UI) |
| Google Cast | ✅ `media3-cast` | ❌ (explicitly out of scope) |
| Song recognition (Shazam) | ✅ `:shazamkit` (774 lines) + `recognition/` | ❌ (explicitly out of scope) |
| Listen Together (sync playback) | ✅ `listentogether/` (6 files) | ❌ (explicitly out of scope) |
| Audio format/codec display | ✅ `FormatEntity` | ✅ codec info |
| Lossless / hi-res | ✅ "Lossless Contribute" screen | ❌ |
| Beat detection | ✅ `BeatInfoEntity` | ❌ |
| Ringtone maker | ✅ `RingtoneViewModel` | ❌ |

---

## 6. Library and organisation

| Feature | Echo-Music | MuseFlow |
|---|---|---|
| Playlists (local) | ✅ full CRUD + `LocalPlaylistScreen` | ✅ `PlaylistRepository` |
| Liked songs | ✅ | ✅ `LikedSongsRepository` |
| Playback history | ✅ `HistoryScreen`, `Event` entities | ✅ `PlaybackHistoryRepository` |
| Auto playlists (Top/Cached) | ✅ `AutoPlaylistScreen`, `TopPlaylistScreen`, `CachePlaylistScreen` | ✅ Top 50 / Cached (UI deleted) |
| Library by Albums/Artists/Songs/Mix | ✅ 6 dedicated library screens | ⚠️ single Library screen w/ chips (deleted) |
| Followed artists | ✅ | ✅ `FollowedArtistsRepository` |
| New-release notifications | ✅ `NewReleaseScreen` | ✅ `ArtistReleaseCheckWorker` + notifications |
| Listening stats | ✅ `StatsScreen`, `SongWithStats` | ⚠️ play counts only |
| Sorting | ✅ | ✅ `LibrarySorting` (logic survives) |
| YouTube Music account sync | ✅ `LoginScreen`, `AccountScreen` | ❌ |
| Spotify playlist import | ✅ | ❌ |

---

## 7. Downloads and offline

| Feature | Echo-Music | MuseFlow |
|---|---|---|
| Offline downloads | ✅ | ✅ `DownloadService` + `DownloadRepository` |
| Download progress notification | ✅ | ✅ `DownloadNotificationHelper` |
| **Metadata/tag embedding** | ⚠️ not a dedicated module | ✅ `AudioTagger` + `AudioTagFormat` |
| Offline home cache | ✅ | ✅ `HomeShelfCache` |
| Storage settings | ✅ `StorageSettings` | ⚠️ partial |
| Backup / restore | ✅ `BackupAndRestore` | ✅ `BackupRepository` + `AutoBackupWorker` |

---

## 8. Discovery

| Feature | Echo-Music | MuseFlow |
|---|---|---|
| Home feed / shelves | ✅ | ✅ `HomeShelfCache` (UI deleted) |
| Charts | ✅ `ChartsScreen` | ✅ was `ChartsScreen` (deleted) |
| Explore / Browse | ✅ `ExploreScreen`, `BrowseScreen`, `YouTubeBrowseScreen` | ❌ |
| Mood & genres | ✅ dedicated screen | ⚠️ canned genre shelves on Home |
| Artist pages | ✅ 4 screens (artist/albums/songs/items) | ✅ was single artist screen |
| Album pages | ✅ | ✅ (deleted) |
| Search suggestions | ✅ `OnlineSearchSuggestionViewModel` | ⚠️ history only |
| Regional suggestions | ✅ `SuggestionRegionSheet` | ❌ |
| AI recommendations | ✅ `ai/` | ❌ |

---

## 9. Theming and personalisation — *most relevant to the rebuild*

| Aspect | Echo-Music | MuseFlow (old, deleted) |
|---|---|---|
| Theme engine | **MaterialKolor `rememberDynamicColorScheme(seed)`** — generates full M3 tonal palette from one seed colour | Hand-written hex constants |
| Theme file size | 426 lines total (6 files) | ~113 lines, incomplete role coverage |
| Android dynamic colour | ✅ wallpaper-based on Android 12+ | ✅ supported but disabled |
| AMOLED | ✅ `ColorScheme.pureBlack()` transform | ✅ separate `BackgroundMode.Amoled` |
| Album-art driven colour | ✅ `PlayerColorExtractor` — Palette + vibrancy scoring + saturation boost | ✅ `AlbumPalette.kt` (survives) |
| User-selectable accent | ✅ `ColorPicker.kt` (389 lines) + `ThemeScreen` | ⚠️ 6 fixed gradient presets |
| Glass/blur effects | ✅ `backdrop/DrawBackdropModifier` (432 lines) + `GlassEffectSettings` | ⚠️ setting existed, not fully wired |
| Custom fonts | ✅ `Font.kt`, `bbh_bartle` | ❌ system default |
| Typography scale | ✅ `Type.kt` 124 lines | ⚠️ 36 lines, one style defined |

**This is the single most important takeaway for the rebuild.** Echo's theme layer is *smaller* than
what a hand-written equivalent would be, yet far more capable, because it **generates** schemes from
a seed instead of hard-coding values.

---

## 10. Shared UI component library

Echo's 91-file / 19,133-line `ui/component/` is why 87 screens stay maintainable:

| Component | Lines | Purpose |
|---|---|---|
| `Lyrics.kt` | 2,343 | synced lyric rendering |
| `Items.kt` | 1,844 | **one shared song/album/artist/playlist renderer for every screen** |
| `floatingtabbar/FloatingTabBar.kt` | 1,088 | floating bottom nav |
| `MetroLyrics.kt` | 884 | alternate lyric style |
| `FloatingNavigationToolbar.kt` | 596 | floating toolbar |
| `backdrop/DrawBackdropModifier.kt` | 432 | glass/blur backdrop |
| `LyricsImageCard.kt` | 419 | shareable lyric card |
| `ColorPicker.kt` | 389 | accent colour picker |
| `BottomSheet.kt` / `Dialog.kt` | 353 / 350 | shared sheet + dialog shells |
| `Preference.kt` | 345 | settings DSL |
| `SquigglySlider.kt` | 301 | wavy player slider |
| `Material3SettingsGroup.kt` | 261 | grouped settings cards |
| `ChipsRow.kt` | 271 | filter chips |
| + `shimmer/` | — | skeleton loading |

MuseFlow's deleted UI **re-implemented track rows per screen** instead of sharing one renderer — a
major reason it grew unwieldy.

---

## 11. Platform surfaces and integrations

| Surface | Echo-Music | MuseFlow |
|---|---|---|
| Home-screen widget | ✅ `widget/` (7 files) | ❌ (was roadmap item #1) |
| Quick Settings tile | ✅ `quicksettings/` | ❌ |
| Ambient / AOD player | ✅ `AmbientModeScreen`, `AmbientGlowBackground` | ❌ (was roadmap item #2) |
| Canvas video backgrounds | ✅ 4 modules (`canvas`, `echomusiccanvas`, `applecanvas`, `artistvideo`) | ❌ (roadmap: needs content pipeline) |
| Discord Rich Presence | ✅ `discord/` (8 files) | ❌ |
| Last.fm scrobbling | ✅ `LastFMSettingsScreen` | ❌ |
| ListenBrainz | ✅ `ListenBrainzManager` | ❌ |
| In-app updater | ✅ `echomusic/updater/` | ❌ |
| Crash screen | ✅ `CrashActivity` | ✅ `CrashHandler` (no UI) |
| Comments | ✅ `CommentSheet` | ⚠️ setting existed only |
| Onboarding | ✅ `WelcomeDialog` | ✅ was `Onboarding.kt` |

---

## 12. Gap summary

### Echo has, MuseFlow does not (23)
Spotify import + API, Apple Music scraping, 4 extra lyrics sources, Google Cast, Shazam recognition,
Listen Together, AI features, Discord RPC, Last.fm, ListenBrainz, home widget, Quick Settings tile,
ambient/AOD mode, Canvas video, in-app updater, YouTube Music account sync, Explore/Browse screens,
regional suggestions, listening stats, lyrics image cards, romanization, ringtone maker, beat
detection.

### MuseFlow has, Echo does not (4)
1. **JioSaavn provider** — a whole music source Echo lacks.
2. **NetEase provider** — another source Echo lacks.
3. **Cross-source search ranking + fallback** (`SearchMatching`) — Echo is YT-centric.
4. **Dedicated audio tag embedding** (`AudioTagger` / `AudioTagFormat`).

### Deliberately out of scope for MuseFlow (per `docs/roadmap.md`)
Song identification, Cast, and Listen Together — all cut for cost/infrastructure reasons, not
difficulty. Canvas needs a content pipeline. These remain valid decisions.

---

## 13. Recommendations for the MuseFlow UI rebuild

**Adopt (high value, low cost):**
1. **Seed-based theming via MaterialKolor** + `pureBlack()` AMOLED transform. Replaces hand-written
   palettes with generated, user-selectable accents. Biggest single win.
2. **A shared `Items.kt`-style component library first**, before any screen. Prevents the per-screen
   duplication that bloated the old UI.
3. **Settings DSL** (`Preference.kt` + grouped cards) so settings screens stay cheap.
4. **Extract navigation** into its own file rather than a 900-line `MainActivity`.
5. **Palette-driven player colours** — `AlbumPalette.kt` already survives; Echo shows the polished
   scoring approach.
6. Individual components worth reimplementing: floating nav bar, squiggly slider, glass backdrop,
   shimmer, chips row, bottom-sheet/dialog shells.

**Do not adopt:**
- **Hilt** — modifying all 42 surviving backend files for marginal single-module benefit.
- **14-module split** — unnecessary at this scale.
- **Echo's feature breadth** — Discord/Cast/Shazam/ListenTogether/Canvas are scope, not quality.
- **Verbatim file copying** — reimplement patterns instead; keeps GPL attribution simple and avoids
  importing Hilt/module assumptions.

**Licence obligation:** both projects are GPL-3.0, so reuse is legal. Any substantially copied code
must retain Echo-Music's copyright headers and be attributed.
