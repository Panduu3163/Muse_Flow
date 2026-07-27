# MuseFlow vs Echo-Music — full gap audit

**Rewritten 2026-07-27 (second pass, same day).** Replaces the prior "Rewritten 2026-07-27" revision
of this document — that one was already a full current-state accounting, but a full Stage E session
landed immediately after it (Home feed, Stats, custom thumbnails, expanded context menus, onboarding,
plus a device-reported smoothness/perf pass and several real bug fixes), so the numbers and status
below are checked against source again, not against what was planned.

Echo comparison counted from source at commit `ad66171`.

---

## 1. The real scale difference

| Metric | MuseFlow | Echo | Ratio |
|---|---|---|---|
| Kotlin files | ~235 | 625 | 2.7× |
| Lines of Kotlin | ~32,500 | 146,881 | 4.5× |
| **Screens** | **17** | **87** | **5.1×** |
| **Settings groups / controls** | **8 groups, 37 controls** | **~290 options across 21 screens** | **~8×** |
| **Context menus** | **7** (song, playlist, album, artist, queue-item, player, lyrics) | **21** | **3×** |
| ViewModels | ~15 | 30 | 2×|
| DB entities | 10 | 28 | 2.8× |
| Gradle modules | 2 | 14 | 7× |

Screen/ViewModel/line counts didn't move much this session — the work was almost entirely *inside*
existing screens (more Home shelves, richer menus, a new Stats screen) rather than new top-level
surfaces. The one real jump is **context menus, 2 → 7** (see §2.3) — previously the single biggest
gap-by-ratio in this whole doc, now the smallest. The remaining gap is concentrated in the same three
areas as before: **settings breadth**, **accounts/sync**, and **platform surfaces** — none of which
were touched this session.

---

## 2. Gap by feature area

Legend: ✅ done · ⚠️ partial · ❌ missing · 🔒 deliberately excluded

### 2.1 Browsing & discovery
| Feature | Echo | MuseFlow | Notes |
|---|---|---|---|
| **Home feed** | ✅ 8 section types | ✅ 8 sections | Was 5 this morning. Added **Your playlists** (data already loaded, just never rendered), **Daily Discover** (5 random liked-song seeds → one radio track each), **From the community** (third-party playlists via `searchPlaylists(topArtist)`) — see `HomeViewModel.kt` |
| **Artist page** | ✅ 4 screens (overview/albums/songs/related) | ✅ 1 screen, 4 tabs (Overview/Songs/Albums/Related), local cache, **now has an overflow menu** (radio/follow-toggle/share) | `ArtistScreen.kt`, cached via `artist_page_cache` |
| **Album page** | ✅ | ✅ same cache/redesign treatment as Artist, **now has an overflow menu** (shuffle/radio/queue/download/view artist/share) | `AlbumScreen.kt`, `AlbumActionsSheet` |
| **Explore / Browse** | ✅ 3 screens | ✅ 1 generic screen | Unchanged |
| **Mood & genres** | ✅ dedicated screen | ✅ folded into Explore | Unchanged |
| **Charts** | ✅ | ⚠️ screen built, **data broken, hidden from UI** | Unchanged — not touched this session |
| **New releases** | ✅ | ⚠️ screen built, **broken, hidden from UI** | Unchanged |
| **Search filters** | ✅ | ✅ | Unchanged |
| **Search result screens** | — | ✅ | Unchanged |
| **Similar/related** | ✅ | ⚠️ artist-name heuristic | Unchanged |

### 2.2 Library
| Feature | Echo | MuseFlow |
|---|---|---|
| Library structure | ✅ 6 screens (Songs/Albums/Artists/Playlists/Mix + root) | ⚠️ 1 screen, section chips |
| **Playlists view** | ✅ | ✅ 2-column grid, 2×2 track-mosaic tile covers |
| Sort / filter / list-grid view | ✅ 8 sort enums, 4 filter enums | ✅ |
| **Playlist detail screen** | ✅ 5 variants | ✅ 1 redesigned screen |
| **Remote (online) playlist screen** | ✅ | ✅ |
| Queue editing / reorder | ✅ `QueueMenu` | ✅ drag handle + **swipe-left-to-right to remove** (replaced the per-row remove button/menu this session) |
| Multi-select batch ops | ✅ | ✅ |
| History screen | ✅ | ✅ |
| **Stats / listening insights** | ✅ `StatsScreen`, `StatPeriod` (day/week/month/etc.) | ✅ `StatsScreen` — **all-time only**, no period filter (see §5: `PlaybackHistoryEntity` has no per-play timestamp, only a running count + most-recent-play time, so a period breakdown can't be computed from existing data) |
| Import playlist (CSV/file) | ✅ + column mapping | ✅ |
| **Custom playlist thumbnail** | ✅ | ✅ system image picker, `PlaylistEntity.customCoverUri`, wins over the auto mosaic |
| Local device files | ✅ 2 screens | ✅ |
| Playlist context menu | ✅ | ✅ `PlaylistActionsSheet` + Change/Remove custom cover |
| Followed artists + release alerts | ✅ | ✅ |

**Note on Library structure**: a tile-grid + 7-dedicated-screens restructure (matching Echo's model
exactly) was built, then reverted the same session at the user's request back to the single-screen
chip model — see `[[museflow-revert-stability]]` memory. Don't re-attempt the split without being
asked again; the chip model is the current, deliberate state, not an unfinished migration.

### 2.3 Context menus — 7 vs 21 (was 2 vs 21)
Echo has dedicated menus for song, album, artist, playlist, queue, player, lyrics, YouTube variants,
multi-select, and custom thumbnail. MuseFlow now has **five more** than this morning:

**Song menu** (`TrackActionsSheet`): play next, add to queue, start radio, like/unlike,
download/cancel/delete, add to playlist, remove from playlist/history, share, view artist/album,
details. Unchanged, but the sheet gained flags (`showQueueActions`/`showLikeAction`/
`showDownloadAction`, all default `true`) so the **player menu** below can selectively hide rows
without a second sheet implementation.

**Playlist menu** (`PlaylistActionsSheet`): shuffle, radio, play next, queue, pin, download, delete,
**Change cover / Remove custom cover** (new).

**Album menu** (`AlbumActionsSheet`, new): shuffle, start radio, play next, add to queue, download
all, view artist, share. Opened via a ⋮ in `AlbumScreen`'s floating top bar.

**Artist menu** (`ArtistActionsSheet`, new): start radio, follow/unfollow, share. Opened via a ⋮ next
to `ArtistScreen`'s back button. Follow itself still also has its own prominent header button — this
menu is for the less common actions.

**Queue-item menu → replaced with swipe-to-remove.** The old ⋮ (Play now/Play next/Remove) was
removed per the user's explicit request; Play now is already the row's own tap action, and "Play
next" wasn't kept. Swiping a row left-to-right removes it, animated (`Modifier.animateItem()`), keyed
on the song's stable `mediaId` (not raw position) so a removed row's swipe state can't leak onto
whatever song shifts up to replace it.

**Player menu** (new): Now Playing's ⋮ opens the same `TrackActionsSheet`, with queue/like/download
rows hidden (Now Playing already has dedicated controls for those) and two lyrics actions folded in
(see next).

**Lyrics menu → folded into the player menu**, not a separate button. "Copy lyrics" (shows a "Lyrics
copied" toast, or "No lyrics to copy yet" if none loaded) and "Search lyrics online" (opens a web
search for `"<title> <artist> lyrics"`).

**Still entirely missing**: YouTube-variant menu options, multi-select-specific menu (multi-select
already has its own action bar, `TrackSelectionHost`, just not a "⋮ more" inside it).

### 2.4 Player
| Feature | Echo | MuseFlow |
|---|---|---|
| Core transport, queue, seek | ✅ | ✅ |
| **Lyrics providers** | ✅ 6 | ✅ 3: LRCLib → BetterLyrics/Kugou → YouTube Music's own lyrics tab |
| **Lyrics word-sync (karaoke)** | ✅ | ✅ **smoothed this session** — the in-progress word's fraction is now driven by a Compose `Animatable` on the frame clock (`LaunchedEffect` keyed on the *word*, not on polled position), not sampled from a tick — reads as continuous regardless of how often the player reports position |
| **Lyrics animation styles** | ✅ 9 | ✅ 5: Fade, Bounce, Scale, Wave, Karaoke sweep |
| **Lyrics glow effect** | — | ✅ |
| **Romanization / Translation** | ✅ | ❌ deliberately skipped |
| Equalizer | ✅ + Axion circular UI | ✅ 7-band DSP |
| **Crossfade / Normalization / Bass boost / Crossfeed** | ✅ | ✅ (all four, unchanged) |
| **Spatial audio / 3D virtualizer** | ✅ | ❌ not attempted |
| **Sleep timer** | ✅ | ✅ **now shows live remaining time next to the icon** (was icon-tint-only before) |
| **Speed / pitch** | ✅ | ✅ |
| **Swipe gestures** | ✅ 4 keys | ✅ horizontal skip, vertical volume |
| **Instant seek** | — | ✅ |
| **Mini player background** | — | ✅ |
| **Ambient / AOD, Comments, Ringtone maker, SponsorBlock** | ✅ | ❌ deferred, lowest priority |

### 2.5 Settings — 21 screens vs 8 groups
Unchanged this session — not touched. Same 8 groups / 37 controls as before; see the prior revision
of this doc (or `git log` on this file) for the full per-screen Echo comparison if needed again.

### 2.6 Accounts & sync
Unchanged this session. Still entirely untouched — no login of any kind, everything anonymous/local.

### 2.7 Platform surfaces
| Feature | Echo | MuseFlow |
|---|---|---|
| Home-screen widget | ✅ | ❌ |
| Quick Settings tile | ✅ | ❌ |
| Android Auto | ✅ | ⚠️ exists, never device-verified against a head unit |
| In-app updater | ✅ | ❌ |
| Crash screen/log retrieval | ✅ | ✅ |
| **Onboarding** | ✅ `WelcomeDialog` | ✅ `OnboardingDialog` (new this session) — shown once per `versionCode` (fresh install and every update both trigger it, same "-1 default" comparison Echo uses), app-intro card, hobby-project/bug-report disclosure with a tappable email row, muted "Star the Repo" button + solid "Continue" button |

### 2.8 Deliberately excluded 🔒
Discord RPC, ListenTogether, Google Cast, Shazam recognition, AI recommendations, Canvas video,
Lossless FLAC, liquid glass, Hilt, the 14-module split, romanization/translation. Settled scope
decisions, not gaps to close.

---

## 3. Honest tally

| Bucket | Count |
|---|---|
| ✅ Done | ~63 |
| ⚠️ Partial | ~9 |
| ❌ Missing (in scope) | ~58 |
| 🔒 Excluded | ~12 |

What's left is now almost entirely **Stages F/G** (platform surfaces, accounts) plus **Stage E's
settings-screen breadth** — none of which have been started, and none of which were in scope for
this session's work (which targeted Home/Library/Stats/thumbnails/context-menus/onboarding
specifically, per the user's own prioritization).

---

## 4. What's actually shipped, by stage

**Stages A–D**: unchanged from the prior revision of this doc — search filters, on-device files,
history, backup/restore, followed artists, CSV import, context menus (song/playlist), queue reorder,
multi-select, Artist/Album/Explore/Browse screens with stale-while-revalidate caching, and the full
player-depth stack (sleep timer, speed/pitch, normalization/crossfade/bass-boost/crossfeed, swipe
gestures, instant seek, lyrics depth, mini-player rebuild).

**Stage E** (this session — Home/Library/Stats/thumbnails/context-menus/onboarding from
`docs`'s prior "next-session plan"): **done**, except Library structure (attempted, then reverted
back to chips at the user's explicit request — see §2.2's note). Also folded in, same session: a
device-reported startup crash fix (onboarding's `painterResource(R.mipmap.ic_launcher)` hit the
adaptive-icon XML on API 26+, which `painterResource` can't render — switched to the raster
`ic_launcher_foreground`), and a general playback-state performance pass (see §5).

**Not started**: Stage F (platform surfaces), Stage G (accounts), and Stage E's own settings-screen
breadth (this session's "Stage E" reused the label for a different, user-prioritized batch of work -
the *original* Stage E content, dedicated settings screens, is still untouched).

---

## 5. Known bugs, deferrals, fixes, and decisions worth not re-litigating

**Fixed this session (real bugs, not just polish):**
- **Now Playing's "Details" dialog silently not opening.** Root cause: the player-menu's
  `TrackActionsHost` call was wrapped in `if (menuTrack != null) { TrackActionsHost(...) }`.
  `TrackActionsHost` owns its own dialog state (`remember`), entered *after* the sheet dismisses -
  but dismissing is what set `menuTrack = null`, tearing the whole `if` block (and the state inside
  it) down in the same frame the dialog was supposed to open. Fixed by mounting it unconditionally
  with `track` passed as a nullable param - the pattern every other screen already used. **Lesson:
  never wrap a stateful host composable like this in an `if (track != null)`.**
- **Custom accent color picker's saturation/value square doing nothing to the actual theme.**
  Root cause (found by decompiling the MaterialKolor 4.1.1 aar): `Theme.kt` used
  `PaletteStyle.TonalSpot`, which normalizes every seed to a fixed, moderate chroma regardless of
  input saturation - the square moved its own preview correctly but the generated palette never
  reflected it. Switched to `PaletteStyle.Fidelity`, which keeps chroma tied to the seed. **Residual,
  by-design limitation**: the square's brightness/value axis still won't meaningfully affect the
  theme even now - Material 3 role tones are fixed by light/dark mode, not derived from seed
  lightness. Not a bug, inherent to Material You.
- **Queue swipe-to-remove "stays red"/doesn't fill correctly.** Root cause: rows were keyed by raw
  index; after a removal, later rows shift up an index and Compose reused the old row's remembered
  (dismissed/red) swipe state for the different song now at that slot. Fixed by keying on
  `mediaId + index` instead.
- **`PlayerViewModel`'s 500ms tick rebuilt the entire queue + metadata every tick**, not just
  position, while anything played - a real, continuous perf cost (queue `.map{}` reallocation twice
  a second) behind every screen observing playback state. Tick now only updates `positionMs`; real
  metadata/queue changes still reach state immediately via the player's own event listener.
- **Startup crash**: see §4.

**Still open / explicitly deferred, unchanged from before:**
- Charts/New Releases: both broken on-device, root cause not found, entry points hidden.
- Spotify import: CSV-only by decision (real import needs a login or a fragile token flow).
- Lyrics word-sync data coverage: real per-word timing is Kugou/Chinese-only; every other track uses
  synthesized per-word timing. Permanent ceiling without a new paid provider.
- Ambient/AOD, Ringtone maker, SponsorBlock, Romanization, Translation: deferred, not started.

**General smoothness (2026-07-27, user-reported, partially addressed)**: the user reported the app
"feels laggy" compared to Echo Music. An Explore-agent diff against Echo found MuseFlow using
`Modifier.animateItem()` in exactly one place (the queue list, added this session) vs. Echo's
extensive use across Home/Stats/Album/Explore lists, and confirmed the `PlayerViewModel` tick-loop
issue above as the single biggest real contributor (now fixed). **Not yet done**: adding
`animateItem()` to Home's shelves, Library's lists, Search results, and Playlist detail - flagged as
the next concrete smoothness lever if the user wants more, not attempted this session since it wasn't
tied to an active reported bug the way the queue was.

---

## 6. What to avoid copying from Echo

Unchanged from the prior revision: their 14-module split, Hilt, liquid glass, duplicated New/Old
designs, 11-language romanization, and their provider-racing lyrics architecture (our typed
`LyricsResult` + explicit priority selection is already cleaner for 3 providers).

---

## 7. Realistic framing

Echo is ~147k lines from many contributors over a long period. MuseFlow is ~32.5k, single-developer.
The "does this feature exist at all" gap is now almost fully closed for browsing/library/player/menus
- what's left is settings breadth, accounts, and platform surfaces, none of which touch playback
correctness or data integrity. The non-negotiable rule still applies: **a feature ships when it works
on device, not when it compiles.** This session alone found three real bugs (Details dialog, accent
picker, queue swipe) that all compiled cleanly and looked correct in review before being reported
from actual device use - the pattern holds.
