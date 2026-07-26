# MuseFlow vs Echo-Music — full gap audit

**Rewritten 2026-07-27.** This replaces every earlier revision of this document (all of which had
accumulated into dated, partially-contradictory fragments) with a single current-state accounting.
Nothing here is "as of an earlier session" — it's what's actually in the codebase right now, checked
directly against source rather than against memory of what was asked for.

Echo comparison counted from source at commit `ad66171`.

---

## 1. The real scale difference

| Metric | MuseFlow | Echo | Ratio |
|---|---|---|---|
| Kotlin files | 227 | 625 | 2.8× |
| Lines of Kotlin | ~30,700 | 146,881 | 4.8× |
| **Screens** | **17** | **87** | **5.1×** |
| **Settings groups / controls** | **8 groups, 37 controls** | **~290 options across 21 screens** | **~8×** |
| **Context menus** | **2** (song, playlist) | **21** | **10.5×** |
| ViewModels | 13 | 30 | 2.3× |
| DB entities | 10 | 28 | 2.8× |
| Gradle modules | 2 | 14 | 7× |

The gap has closed substantially since this doc was first written (files 6.9×→2.8×, lines
11.5×→4.8×, screens 12.4×→5.1×) — most of it from Stages A through D (§4) landing. The remaining
gap is now concentrated in three areas: **settings breadth** (dedicated screens per topic vs.
MuseFlow's flat groups), **accounts/sync** (no login of any kind), and **platform surfaces**
(widget, Quick Settings tile, in-app updater).

---

## 2. Gap by feature area

Legend: ✅ done · ⚠️ partial · ❌ missing · 🔒 deliberately excluded

### 2.1 Browsing & discovery
| Feature | Echo | MuseFlow | Notes |
|---|---|---|---|
| Home feed | ✅ 8 section types | ⚠️ 5 sections | — |
| **Artist page** | ✅ 4 screens (overview/albums/songs/related) | ✅ 1 screen, 4 tabs (Overview/Songs/Albums/Related), local cache | `ArtistScreen.kt`, cached via `artist_page_cache` (stale-while-revalidate) |
| **Album page** | ✅ | ✅ same cache/redesign treatment as Artist | `AlbumScreen.kt`, `album_page_cache` |
| **Explore / Browse** | ✅ 3 screens | ✅ 1 generic screen | `ExploreScreen.kt` (mood/genre tiles) + generic `BrowseScreen.kt` (any browseId+params) via `YouTube.browse()` |
| **Mood & genres** | ✅ dedicated screen | ✅ folded into Explore | Tiles grouped by category, tapping one opens `BrowseScreen` |
| **Charts** | ✅ | ⚠️ screen built, **data broken, hidden from UI** | `ChartsScreen.kt`/`getChartsTracks()` return zero tracks on device. Locale/visitorData ruled out. Root cause not found; entry point pulled from Search rather than shipping a screen that reliably errors |
| **New releases** | ✅ | ⚠️ screen built, **broken, hidden from UI** | `NewReleasesScreen.kt` exists; `getNewReleases()` throws on device. Same treatment as Charts |
| **Search filters** (songs/albums/artists/playlists) | ✅ | ✅ | Lazy per-tab fetch, per-tab cache |
| Search suggestions | ✅ + regional | ✅ | — |
| **Search result screens** | — | ✅ Albums/Playlists navigate to real `AlbumScreen`/`RemotePlaylistScreen` (not a modal), Artists to `ArtistScreen` | Matches Library's own playlist-screen design |
| **Similar/related** | ✅ | ⚠️ artist-name heuristic | innertube `related` ported, not fully wired |

### 2.2 Library
| Feature | Echo | MuseFlow |
|---|---|---|
| Library structure | ✅ 6 screens (Songs/Albums/Artists/Playlists/Mix + root) | ⚠️ 1 screen, section chips |
| **Playlists view** | ✅ | ✅ 2-column grid, 2×2 track-mosaic tile covers (was a plain list) |
| Sort / filter / list-grid view | ✅ 8 sort enums, 4 filter enums | ✅ `LibrarySortHeader`, list+grid toggle |
| **Playlist detail screen** | ✅ 5 variants (local/online/auto/top/cache) | ✅ 1 redesigned screen: 2×2 mosaic cover, capsules, About card, floating back button |
| **Remote (online) playlist screen** | ✅ | ✅ `RemotePlaylistScreen.kt` — same visual language as the local one, plus a one-tap "Add to Library" |
| Queue editing / reorder | ✅ `QueueMenu` | ✅ drag handle + per-row remove |
| Multi-select batch ops | ✅ `SelectionSongsMenu` | ✅ `TrackSelectionHost` on Library/History/PlaylistDetail/Search |
| History screen | ✅ | ✅ |
| **Stats / listening insights** | ✅ `StatsScreen`, `StatPeriod` | ❌ |
| Import playlist (CSV/file) | ✅ + column mapping | ✅ Exportify-style CSV, name/artist match |
| **Custom playlist thumbnail** | ✅ | ❌ |
| Local device files | ✅ 2 screens | ✅ |
| Playlist context menu | ✅ | ✅ `PlaylistActionsSheet`: shuffle, radio, play next, add to queue, pin, download, delete |
| Followed artists + release alerts | ✅ | ✅ Library → Following, `ArtistReleaseCheckWorker` (12h) |

### 2.3 Context menus — 21 vs 2
Echo has dedicated menus for song, album, artist, playlist, queue, player, lyrics, YouTube
variants, multi-select, and custom thumbnail. MuseFlow has two: song and playlist.

**Song menu** (`TrackActionsSheet`): play next, add to queue, start radio, like/unlike,
download/cancel/delete, add to playlist, remove from playlist, remove from history, share, view
artist, view album, details, enter multi-select via long-press.
**Missing**: edit metadata (no shared "the track" model to edit once — denormalized per repository),
set as ringtone (no `RingtoneManager` usage; only viable for local files anyway), refetch stream
(the active InnerTube backend already re-resolves every play, so this would be a no-op), pin to
speed dial (no Speed Dial surface exists on Home).

**Playlist menu** (`PlaylistActionsSheet`): shuffle, start radio, play next, add to queue, pin,
download, delete. Deliberately excluded: share (no public link for a local playlist), pin to speed
dial (same reason as the song menu).

**Entirely missing**: album menu, artist menu, queue-item menu (reorder/remove are inline row
actions, not a menu), player menu, lyrics menu.

### 2.4 Player
| Feature | Echo | MuseFlow |
|---|---|---|
| Core transport, queue, seek | ✅ | ✅ |
| **Lyrics providers** | ✅ 6 | ✅ 3: LRCLib (line-level) → BetterLyrics/Kugou (word-level, Chinese-focused) → YouTube Music's own lyrics tab (plain text, last resort) |
| **Lyrics word-sync (karaoke)** | ✅ | ✅ real word timing when the source has it; character-length-proportional synthesis otherwise, so every track gets word-by-word highlighting, not just Chinese-language ones with real Kugou data |
| **Lyrics animation styles** | ✅ 9 | ✅ 5: Fade, Bounce, Scale, Wave, Karaoke sweep |
| **Lyrics glow effect** | — | ✅ real text shadow on the active word/line (was a dead parameter until fixed) |
| **Romanization** | ✅ 11 languages | ❌ deliberately skipped — needs transliteration libraries, low payoff for the cost |
| **Translation** | ✅ (DeepL) | ❌ deliberately skipped |
| Equalizer | ✅ + Axion circular UI | ✅ 7-band DSP |
| **Crossfade** | ✅ | ✅ fade-based (position/duration-driven, not true dual-decoder mixing — same honest scope most mobile players ship under this name), 1-12s slider |
| **Audio normalization** | ✅ | ✅ `NormalizerAudioProcessor`, dynamic-range compressor in the ExoPlayer chain |
| **Bass boost** | ✅ | ✅ `BassBoostAudioProcessor`, separate from the 7-band EQ |
| **Headphone crossfeed** | ✅ | ✅ `CrossfeedAudioProcessor` (Chu Moy technique) |
| **Spatial audio / 3D virtualizer** | ✅ | ❌ materially bigger than crossfeed; not attempted |
| **Sleep timer** | ✅ | ✅ `SleepTimer.kt`, self-contained singleton, 15/30/45/60min presets |
| **Speed / pitch** | ✅ | ✅ `PlaybackParameters`, "Preserve pitch" toggle |
| **Swipe gestures** | ✅ 4 keys | ✅ horizontal = skip, vertical = device volume |
| **Instant seek** | — | ✅ `StreamCache.kt`, prefetches the rest of the current track on start |
| **Mini player background** | — | ✅ artwork-tinted (Solid/Gradient/Blur), floats over content instead of a docked bottom bar, Previous button added |
| **Ambient / AOD mode** | ✅ | ❌ explicitly deferred, lowest priority |
| **Comments** | ✅ | ❌ |
| **Ringtone maker** | ✅ | ❌ explicitly deferred, lowest priority |
| **SponsorBlock** | ✅ | ❌ explicitly deferred, lowest priority |

### 2.5 Settings — 21 screens vs 8 groups
| Echo screen | MuseFlow |
|---|---|
| AppearanceSettings (2,053 LOC) | ⚠️ 1 group (includes inline theme/seed-color picker) |
| PlayerSettings (1,193) | ⚠️ split across "Mini player" + "Player" groups |
| Lyrics settings | ⚠️ 1 group (text size/alignment/auto-scroll/tap-to-seek/blur/word animation/glow) — smaller in scope than Echo's but real, not stubs |
| Audio | ⚠️ 1 group (EQ, skip silence, normalize, crossfade, bass boost, crossfeed) |
| ContentSettings (1,258) | ❌ |
| StorageSettings (565) | ❌ |
| **SearchableSettings** (458) | ❌ |
| BackupAndRestore (332) | ✅ shipped (`BackupSettingsScreen`, SAF export/import, auto-backup worker) |
| PrivacySettings / UpdateSettings / About / Uptime / EchoExtractor | ❌ |
| AccountSettings / LastFM / ListenBrainz / Discord / AI / Lossless / GlassEffect | ❌ / 🔒 |
| Crash log retrieval | — | ✅ `CrashLogsScreen` (list/view/copy/share/delete) |

37 individual controls total (switches/lists/sliders/navigation rows) across 8 groups: Appearance,
Mini player, Player, Lyrics, Audio, Playback, General, Library sections.

### 2.6 Accounts & sync
| Feature | Echo | MuseFlow |
|---|---|---|
| YouTube Music login + library sync | ✅ | ❌ |
| Proxy support | ✅ 6 keys | ❌ |
| Last.fm / ListenBrainz scrobbling | ✅ | ❌ |
| Spotify import | ✅ | ❌ (CSV import shipped instead — see §5 below; API route investigated and rejected, needs a login Echo itself only avoids via a stranger's gist + rotating hashes) |

**Entirely untouched this project.** No login of any kind exists — everything is anonymous/local.

### 2.7 Platform surfaces
| Feature | Echo | MuseFlow |
|---|---|---|
| Home-screen widget | ✅ 7 files | ❌ |
| Quick Settings tile | ✅ | ❌ |
| Android Auto | ✅ (media session) | ⚠️ `MediaSessionService` exists, never device-verified against an actual Auto head unit |
| In-app updater | ✅ | ❌ |
| Crash screen/log retrieval | ✅ | ✅ |
| Onboarding | ✅ `WelcomeDialog` | ❌ |

### 2.8 Deliberately excluded 🔒
Discord RPC (~20 keys), ListenTogether (~12), Google Cast, Shazam recognition, AI recommendations,
Canvas video, Lossless FLAC, liquid glass, Hilt, the 14-module split, romanization. These are
considered settled scope decisions, not gaps to close.

---

## 3. Honest tally

| Bucket | Count |
|---|---|
| ✅ Done | ~55 |
| ⚠️ Partial | ~10 |
| ❌ Missing (in scope) | ~65 |
| 🔒 Excluded | ~12 |

Stages A, B, C, and D (§4) are all functionally complete except two dead-end bugs (Charts/New
Releases data, both hidden from the UI rather than shipped broken) and three explicitly
lowest-priority deferrals (Ambient/AOD, Ringtone maker, SponsorBlock). What's left of the original
~90-item gap is concentrated in Stages E/F/G: settings breadth, platform surfaces, and accounts —
none of which have been started.

---

## 4. What's actually shipped, by original stage

Kept for orientation — these are no longer "in progress," they're done, device-verified unless
individually flagged.

**Stage A** (browsing/library UI over existing backends): search filters, on-device files, history
screen, backup & restore UI, followed artists + release alerts, CSV playlist import. Charts is the
one exception — screen built, data broken, hidden.

**Stage B** (context menus & queue): song + playlist context menus, queue drag-reorder, multi-select
batch ops on 4 surfaces, playlist long-press menu with pin/download/delete.

**Stage C** (the DB-expansion + entity-screens investment): `artist_page_cache`/`album_page_cache`
tables, multi-tab Artist screen (Overview/Songs/Albums/Related), Album screen, Explore + generic
Browse screen, full visual redesign of Artist/Album/Playlist screens (full-bleed cover art,
capsules, About sections, floating back buttons) later extended to Search's own Album/Playlist
results and to Library's playlist grid.

**Stage D** (player depth): sleep timer, speed/pitch, audio normalization, crossfade, bass boost,
crossfeed, swipe gestures, instant seek via stream caching, HQ cover art backfill, and — the
biggest single chunk — lyrics depth (3 providers, 5 real animation styles, karaoke word-sync with a
synthesized fallback for line-only sources, a real glow effect) plus a full mini-player rebuild
(artwork-tinted backgrounds, Previous button, floating layout instead of a docked bottom bar).

A `BackgroundStyle.Glow` visual effect was built through several iterations on both the mini player
and Now Playing, then **fully removed** at the user's request as unstable — it does not currently
exist as a feature; `BackgroundStyle` has exactly three values (Solid/Gradient/Blur). If revisited,
treat it as new work, not a resume-from-here.

**Not started**: Stage E (settings screen breadth + a searchable-settings scaffold), Stage F
(platform surfaces — widget, Quick Settings tile, in-app updater, Android Auto verification),
Stage G (accounts — YTM login/sync, proxy, scrobbling).

---

## 5. Known bugs, deferrals, and decisions worth not re-litigating

- **Charts / New releases**: both return broken data on-device (`getChartsTracks()` returns zero
  items; `getNewReleases()` throws). Root cause not found for either — investigated locale,
  visitorData init, and browseId correctness, all ruled out or already fixed as real bugs without
  resolving the actual symptom. Entry points are pulled from Search rather than shipping a screen
  that reliably errors. The screens/routes/DAOs all still exist if this gets revisited.
- **Spotify import**: CSV-only, by decision. The API route requires either a real login (Echo's own
  "import by link" only works for *someone else's* public playlist without one) or reverse-engineering
  a web-player token flow that depends on a stranger's GitHub Gist and rotating persisted-query
  hashes Spotify can invalidate at any time. Not worth the maintenance burden for this app's scale.
- **Lyrics word-sync data coverage**: `BetterLyrics` is a Kugou (Chinese lyrics database) scraper —
  real per-word timing realistically only exists for Chinese-language tracks. Every other track's
  karaoke effect uses synthesized per-word timing (character-length-proportional, within the line's
  real on-screen duration) rather than genuine sub-line sync data. This is a real, permanent data
  ceiling unless a broader word-level source (Musixmatch/Spotify, both requiring auth tokens) is
  integrated later.
- **Mini player / Now Playing background styles**: `Glow` was attempted and removed (see §4). Only
  Solid/Gradient/Blur are real, working options.
- **Ambient/AOD mode, Ringtone maker, SponsorBlock**: explicitly deferred by the user as lowest
  priority. Not started, no partial work exists for any of them.
- **Romanization, translation**: explicitly deferred by the user. Not started.

---

## 6. What to avoid copying from Echo

- **Their module split** (14 Gradle modules) — unnecessary at this scale.
- **Hilt** — would touch every surviving backend file for little single-module gain.
- **Liquid glass** — 9 tuning keys for one visual effect.
- **Duplicated "New/Old" designs** (`useNewPlayerDesign`, `OldPlayerMenu`) — legacy carry-over, not
  worth reproducing.
- **Romanization (11 languages)** — needs transliteration libraries; low payoff for the cost.
- **Their lyrics provider-racing architecture** (9 providers queried in parallel, first-synced-wins
  by string-sniffing `"["`) — looked at directly this session. Our typed `LyricsResult` sealed
  interface plus explicit word-timing-priority selection is already a cleaner, more deterministic
  version of the same idea for 3 providers; adopting theirs would add complexity without benefit at
  this scale. The one technique worth taking was the per-word timing synthesis for line-only
  sources, which was adopted (see §2.4/§5).

---

## 7. Realistic framing

Echo is ~147k lines from many contributors over a long period. MuseFlow is ~30.7k, single-developer,
and has closed most of the "does this feature exist at all" gap already — what's left is
overwhelmingly settings breadth, accounts, and platform surfaces, none of which touch playback
correctness or data integrity. The non-negotiable rule still applies: **a feature ships when it
works on device, not when it compiles.** Several real bugs this project has hit (a lyrics-provider
priority bug, a dead glow parameter, a `LazyColumn` padding bug, a background-layer sizing bug) all
compiled cleanly and looked correct in review before device testing caught them.
