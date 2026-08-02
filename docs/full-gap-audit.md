# MuseFlow vs Echo-Music — full gap audit

**Rewritten 2026-08-02**, closing out a long single session that started from the prior
(2026-07-27) revision of this doc. That session covered a huge amount of ground — a real
settings/UI/customization push, a dozen-plus genuine bugs found and fixed (several of them
pre-existing, not introduced this session, just newly surfaced), and a firm line drawn around a
couple of features that were explicitly declined on legal grounds. This revision folds all of it
in and is checked against the current source, not against what was planned.

Echo comparison counted from source at `repo/Echo-Music` (read-only reference checkout).

---

## 0. Read this first if you're picking up the next session

**Nothing from this session is committed.** 62 files are sitting uncommitted in the working
directory (34 modified, 28 new) — see `docs/SESSION-HANDOFF-2026-08-02.md` for the exact list and
what to do about it before touching anything else.

**Version**: `versionCode` 14, `versionName` "1.1.3". Debug installs as "MuseFlow Debug", Beta as
"MuseFlow Beta" (separate app labels via `src/debug/res`/`src/beta/res` overrides), so both can be
installed side by side.

**Verification discipline established this session, worth keeping**: `./gradlew
:app:compileDebugKotlin -q` gave a false "success" (stale cached result) more than once this
session. Every real compile check from partway through this session on used `--rerun` instead —
keep doing that, `-q` alone is not trustworthy after a background agent or a long gap between
checks.

---

## 1. The real scale difference

| Metric | MuseFlow | Echo | Ratio |
|---|---|---|---|
| Kotlin files | ~260 | 625 | 2.4× |
| **Settings screens** | **12 category screens** | **21** | **1.75×** |
| **Lyrics providers** | **7** (LRCLib, BetterLyrics, YouTube tab, YouLyPlus, PaxSenix, SimpMusic, Kugou) | **6** | **MuseFlow ahead** |
| **Lyrics word-animation styles** | **9** (Karaoke, Bounce, Scale, Wave, Fade, Metro, Fluid/V2, Vivi Music, Apple Music) | **9** | **Parity** |
| **Player background styles** | **4** (Solid, Gradient, Blur, Live Mesh) | **7** (adds Glow-Animated, Apple Music, Liquid Glass) | Echo ahead |
| **Transport button styles** | **3** (Static, Wheel, Pill) | Not a real settings-driven system in Echo — see §5 | MuseFlow ahead here specifically |
| Context menus | 7 | 21 | 3× |

The settings and player-customization gap closed substantially this session — MuseFlow went from
8 flat settings groups to 12 real category screens with Echo's own visual language (icon chips,
picker sheets), and picked up several player-customization options (transport button styles, Live
Mesh, codec-info pill) that either match or, in the transport-style picker's case, are actually
more systematically built than what Echo ships. The remaining gap is concentrated in **accounts/
sync, platform surfaces, and a handful of large standalone subsystems** (Discord RPC, Last.fm,
Listen Together, AI translation, Liquid Glass) — see §6.

---

## 2. This session's work, in full

### 2.1 Real bugs found and fixed

Every one of these was root-caused before being touched. Several were **pre-existing bugs that
only became visible or audible as a side effect of an earlier fix in the same session** — noted
where that's the case, since it explains why some of these weren't caught until deep into the
session.

1. **Audio effects (EQ, normalization, crossfade, bass boost, crossfeed) had zero audible effect**,
   no matter what was toggled. Root cause: Media3's audio pipeline decides which DSP processors
   are active exactly once, when it configures against the current track's format — it never
   re-checks that decision mid-playback. Fixed with a same-position `player.seekTo(currentPosition)`
   whenever an audio-fx setting genuinely changes, forcing the pipeline to reconfigure (no real
   skip, no network refetch). `PlaybackService.kt`.

2. **Volume audibly "pumping"** in sync with the music — only surfaced *after* fix #1 made the
   Normalizer actually engage for the first time; the bug itself predates this session. Root
   cause: the compressor's attack/release constants were raw per-sample multipliers with no
   relationship to sample rate, and the envelope updated once per *interleaved sample* rather than
   once per audio frame (double the intended rate on stereo). Rewritten with real
   `1 - e^(-1/(τ·sampleRate))` time-constant math, one envelope update per frame using the loudest
   channel. `audio/NormalizerAudioProcessor.kt`.

3. **Rapid track-skipping caused a large delay and a network-usage spike** when finally settling
   on a track. Root cause: the next-track preload job only guarded against re-resolving the *same*
   track twice — it never cancelled a still-running preload for a track already skipped past. Fast
   skipping left a pile of abandoned resolve jobs competing with the one actually needed. Fixed by
   cancelling the previous preload job before starting a new one, the same pattern the adjacent
   prefetch job already used correctly. `PlaybackService.kt`.

4. **Seeking briefly dropped audio quality then recovered** — same root cause and fix as #1; a
   seek forces the pipeline to reconfigure, and the momentary dip during that renegotiation is
   part of the same mechanism.

5. **Slider-style picker: worked once, then stopped responding.** Root cause: each preview cell
   embedded a fully interactive slider inside a tappable card; Compose dispatches gesture
   recognition child-first, so a tap landing on the wave itself was consumed by the preview's own
   drag handler before the card's `onClick` ever fired. Fixed with an `interactive` flag that
   skips attaching the preview's own gesture handling entirely. `ui/component/SquigglySlider.kt`.

6. **Live Mesh background showed a hard edge partway through its rotation**, worse on the
   mini-player. First pass shipped a "mathematically correct" fix (sizing each rotating layer to
   the container's own diagonal) that didn't match any real, shipped implementation. Reading
   Echo's actual source showed the real mechanism: a much larger blur radius (100–120dp, not the
   40dp an earlier performance pass had reduced it to) simply smears the edge into invisibility.
   Reverted the custom geometry, ported Echo's exact numbers (oversize scale, blur radius, rotation
   speed, 128×128 software-decoded source). `ui/screens/NowPlayingScreen.kt`,
   `ui/component/MiniPlayer.kt`.

7. **Mini-player looked transparent while Live Mesh was enabled.** Root cause, found by re-reading
   Echo's container structure: MuseFlow's mini-player's own outer `Surface` is fully transparent by
   design, relying entirely on the background style to paint something opaque. Echo's real
   container always paints a fully opaque base color *first*, with every background style layered
   on top of that — so a coverage gap in their rotating layer never shows through to the screen
   behind it. Added the same opaque base layer MuseFlow was missing. `ui/component/MiniPlayer.kt`.

8. **Now Playing's artist name only worked for freshly-searched-and-played tracks** — not from a
   restored queue, Liked Songs, or a playlist. Root cause, found across three separate passes: (a)
   the artist ID was only embedded into track metadata on the main "play from search" path, not on
   queue-restore-after-restart or autoplay-appended tracks; (b) the backup export/import JSON
   round-trip silently dropped the artist ID field for both Liked Songs and playlist tracks; (c)
   even after both of those were fixed, tracks liked/added *before* the fix existed had the field
   permanently null in the database — no code change touches already-stored rows. Fixed all three,
   plus a one-time launch backfill that re-resolves the field for existing rows by exact video-ID
   match (never a fuzzy title guess). `PlayerViewModel.kt`, `PlaybackService.kt`,
   `BackupRepository.kt`, `LikedSongsRepository.kt`, `PlaylistRepository.kt`,
   `MuseFlowApplication.kt`.

9. **Charts and New Releases showed nothing** — both screens existed but their entry points were
   hidden. Confirmed directly against YouTube Music's live API (not assumed) that neither is
   sourced from Apple Music as suspected. Charts' item-parsing shape no longer matched what the
   page actually returns; New Releases had a stale browse ID, and the page itself changed shape —
   Charts now links to chart *playlists* rather than inlining songs directly. Charts now resolves
   those linked playlists for real song data; New Releases parses the corrected response shape
   (which turned out to be individual songs, not albums — the "New Releases" UI was adjusted to
   match what the endpoint actually returns). Entry points restored in Search.
   `innertube/.../YouTube.kt`, `InnerTubeMusicProvider.kt`, `ui/screens/ChartsScreen.kt`,
   `ui/screens/NewReleasesScreen.kt`.

10. **Low-contrast text on Stats and Search cards.** Dynamic Material You theming can generate a
    foreground/background pair with insufficient contrast for certain seed colors. Fixed with
    explicit white text, independent of the generated theme. `ui/screens/StatsScreen.kt`,
    `ui/screens/SearchScreen.kt`.

11. **Lyrics word-by-word animation read as "the whole line," not per-word.** Same underlying
    mechanism as #10: theme-driven sung/upcoming word colors could land close enough in contrast
    that the per-word transition was barely perceptible. Fixed with a fixed white/dimmed-white
    scale, independent of theme. `ui/component/LyricsView.kt`.

12. **Nav bar / mini-player color seam.** Diagnosed twice. First pass added album-palette tinting
    to the nav bar to match the mini-player above it — the wrong fix, reverted at the user's
    explicit request (the nav bar tracks the app theme only, never the currently playing track).
    Real root cause: Android's edge-to-edge API draws its own translucent legibility scrim over the
    system navigation bar by default, independent of what the app renders underneath. Fixed by
    making both system bar styles fully transparent. `MainActivity.kt`.

13. **Build failures from stale caches.** A Gradle transform cache referenced a differently-named
    project folder from an earlier clone, causing a hard failure unrelated to any code change.
    Fixed by clearing the stale build directories once.

14. **Two background agents hit an API session limit mid-edit** (one removing the old Glow
    background, one adding lyrics animation styles) and left broken code behind — a dangling
    enum-value reference in one case, a non-`@Composable` function calling `MaterialTheme` in the
    other. Both caught by a forced `--rerun` recompile (not by trusting the agents' own "done"
    reports) and fixed directly.

### 2.2 Features added

- **Taste-aware autoplay** — blends the just-finished track's radio with a weighted-random pick
  from top-played history, instead of always following only the last track.
- **M3U playlist import**, alongside the existing CSV importer, sharing the same match/resolve
  pipeline.
- **4 new lyrics providers**: YouLyPlus, PaxSenix, SimpMusic, Kugou — plus a drag-to-reorder
  priority list in Settings (`ui/screens/LyricsProviderPriorityScreen.kt`).
- **Settings restructured into 12 category screens** (General, Appearance, Player, Playback,
  Lyrics, Audio, Mini Player, Library Sections, Privacy, Storage, Backup, About) instead of one
  flat list — matching Echo's own navigation shape, with Echo's real row visual language (icon
  chips, picker-sheet Cancel buttons) ported directly rather than re-derived.
- **Storage settings screen** — cache/download size + clear actions.
- **Privacy settings** — listening/search history clearing.
- **About screen** — the *actual installed app icon* via `PackageManager.getApplicationIcon` (not
  a guessed mipmap resource — guaranteed to match the launcher exactly), developer info, email,
  GitHub link.
- **GitHub-release update checker** — launch-time only, notifies once per genuinely new release,
  never repeats for the same tag, fails silently on any network issue.
- **Recently Played, properly linked** — the header shortcut now opens the real History screen
  directly, replacing an earlier duplicate surface that showed the same data separately (that
  duplicate was removed, along with the dead "Recent" chip in Library).
- **Codec info badge** — real-time format/bitrate readout below the timeline, styled as a pill,
  toggleable in Settings.
- **Lock-screen / notification like button** — a real heart button in the system media
  notification and lock-screen controls, wired through Media3's custom session command using the
  built-in `ICON_HEART_FILLED`/`ICON_HEART_UNFILLED` constants. Reads/writes the same Liked Songs
  table as the in-app button, so both stay in sync automatically.
- **Now Playing redesign** — boxed queue/sleep-timer/lyrics/shuffle/repeat row (pill-rounded ends,
  square-ish middles, filled when active) and a joined download+like pill next to the title,
  both ported directly from Echo's own component code, not reconstructed from a screenshot alone.
- **3 selectable transport button styles** (Settings → Player): Static (unchanged default), Wheel
  (Echo's rotating "cookie"/scalloped play-pause shape, ported field-for-field including the
  flatten-to-circle-when-paused animation), Pill (prev/play/next as three distinct rounded
  segments with real spacing between them).
- **Per-build-type app identity** — "MuseFlow Debug" / "MuseFlow Beta" labels, version bumped to
  1.1.3 (code 14).

### 2.3 Declined, on legal grounds — not a gap to close

- **"Lossless" streaming** via a third-party JSON index (`lossless.echomusic.fun`). Checked Echo's
  actual source rather than taking the request at face value: their real "Lossless" mode
  fuzzy-matches against an external index and streams a FLAC URL from it, fabricating a fake
  stream-format wrapper — an unauthorized-copy distribution mechanism regardless of which app does
  it. Echo's own *legitimate* quality logic (picking the highest-bitrate lossy stream YouTube
  already serves) is what MuseFlow already does.
- **"Canvas" background video** from the same class of source (`canvas.echomusic.fun`). Same
  reasoning — Echo's real Canvas providers use Tidal/Apple Music embed tokens with their own
  licensing questions, a materially bigger and murkier lift than anything else in this session, not
  attempted in any form.

---

## 3. Gap by feature area

Legend: ✅ done · ⚠️ partial · ❌ missing · 🔒 deliberately excluded

### 3.1 Browsing & discovery
| Feature | Echo | MuseFlow | Notes |
|---|---|---|---|
| Home feed | ✅ 8 section types | ✅ 8 sections | Unchanged this session |
| Artist / Album pages | ✅ | ✅ | Unchanged this session |
| **Charts** | ✅ | ✅ | **Fixed this session** — real InnerTube data, resolves chart-playlists for actual songs |
| **New releases** | ✅ (real albums) | ✅ (real songs) | **Fixed this session** — the live endpoint no longer serves distinct album data unauthenticated, so this is genuinely songs now, not a gap |
| Search, suggestions, recents | ✅ | ✅ | Unchanged |

### 3.2 Library
| Feature | Echo | MuseFlow |
|---|---|---|
| Library structure | ✅ 6 screens | ⚠️ 1 screen, section chips (deliberate, see prior revision's note — not re-attempted) |
| Playlists, import (CSV) | ✅ | ✅ |
| **Playlist import (M3U)** | ✅ | ✅ **Added this session** |
| History screen | ✅ | ✅, **properly linked from Library's shortcut this session** (was a duplicate surface before) |
| Stats | ✅ with period filter | ✅ all-time only (same data limitation as before, unchanged) |

### 3.3 Player
| Feature | Echo | MuseFlow |
|---|---|---|
| Core transport, queue, seek | ✅ | ✅ |
| **Lyrics providers** | ✅ 6 | ✅ **7 — ahead of Echo**, added YouLyPlus/PaxSenix/SimpMusic/Kugou this session + a reorderable priority list |
| **Lyrics word-animation styles** | ✅ 9 | ✅ **9 — parity**, added Metro/Fluid/Vivi Music/Apple Music this session |
| **Player background styles** | ✅ 7 | ⚠️ 4 (Solid/Gradient/Blur/Live Mesh) — Glow-Animated, Apple Music-style, and Liquid Glass still missing |
| **Live Mesh background** | ✅ | ✅ **Added this session**, ported exactly from Echo's real numbers after an initial custom-geometry attempt didn't match |
| **Transport button customization** | Cookie/wavy shape exists but isn't a real settings-driven system | ✅ **Added this session** — 3 real, settings-picked styles |
| **Codec info display** | ✅ | ✅ **Added this session**, styled as a pill |
| **Lock-screen/notification like button** | ✅ | ✅ **Added this session** |
| Equalizer | ✅ + Axion UI | ✅ 7-band DSP, **all audio-fx settings genuinely apply now** (see bug #1) |
| Crossfade / Normalization / Bass boost / Crossfeed | ✅ | ✅, **Normalizer's real pumping bug fixed this session** |
| Romanization / Translation | ✅ | ❌ deliberately skipped |
| Spatial audio / 3D virtualizer | ✅ | ❌ not attempted |
| Sleep timer, speed/pitch, swipe gestures, instant seek | ✅ | ✅ unchanged |
| **"Lossless"/Canvas** | Backed by an unauthorized third-party content mirror | 🔒 **Declined this session**, see §2.3 |

### 3.4 Settings — 12 screens vs 21
Substantially closed this session (was 8 flat groups; now 12 real category screens). Remaining
gap is concentrated in large standalone subsystems, not settings-screen breadth for its own sake —
see §4.

### 3.5 Accounts & sync
Unchanged. Still entirely untouched — no login of any kind, everything anonymous/local.

### 3.6 Platform surfaces
| Feature | Echo | MuseFlow |
|---|---|---|
| Home-screen widget | ✅ | ❌ |
| Quick Settings tile | ✅ | ❌ |
| Android Auto | ✅ | ⚠️ exists, never device-verified against a head unit |
| **In-app update notifier** | ✅ (auto-update) | ✅ **Added this session** — notifies of new GitHub releases, does not silently self-update |
| Onboarding | ✅ | ✅ unchanged |

### 3.7 Deliberately excluded 🔒
Discord RPC, Last.fm/ListenBrainz, Listen Together, AI-LLM lyrics translation, Liquid Glass shader
effects, romanization, proxy/network config, self-updater (beyond notify), the 14-module split,
Hilt — settled scope decisions, not gaps to close casually. See §4 for why each of these
specifically wasn't attempted this session (most are new standalone subsystems, not settings
work).

---

## 4. What's still open, and why

Each of these is its own subsystem, not a settings screen — scoped out deliberately rather than
attempted partially, per this project's own standing "no half-finished implementations" rule.

| Area | Status | Why it's not in this session |
|---|---|---|
| Discord Rich Presence | Not started | New IPC/websocket client, no shared code with anything built this session |
| Last.fm / ListenBrainz scrobbling | Not started | New external API clients + an auth flow |
| Listen Together | Not started | Needs a real-time sync server and protocol — infrastructure, not UI |
| AI lyrics translation | Not started | Requires a user-supplied LLM API key and a new provider-agnostic client |
| Liquid Glass shader effects | Not started | AGSL/RuntimeShader pipeline, its own substantial effort |
| Glow-Animated / Apple Music player background styles | Not started | Two more `BackgroundStyle` variants Echo has that MuseFlow doesn't yet — moderate effort, just not reached this session |
| Lyrics romanization | Not started | Needs a per-script transliteration engine (Chinese/Japanese/Korean/Cyrillic/etc.) |
| Home-screen widget / Quick Settings tile | Not started | Platform-surface work, untouched this session |
| Android Auto | Exists, unverified | Never tested against a real head unit |
| Broader settings parity with Echo | Partial | Closed the highest-leverage subset (~15+ items across 12 new screens); Echo's full ~290-option surface is far larger and includes several full subsystems listed above |
| Library structure (chips vs. Echo's 6-screen split) | Deliberate, not a gap | Reverted once already at the user's request in an earlier session — not re-attempted |

---

## 5. Notes worth not re-litigating

- **"Lossless"/Canvas are not gaps** — they were investigated directly against Echo's real source
  and declined on legal grounds (see §2.3). Don't re-propose implementing them the same way; a
  genuinely licensed high-quality-audio or video-background source would be a different
  conversation.
- **The transport-button "Wheel"/"Pill" styles are ported from Echo's real code**, not
  independently designed — `WavyShape`/`shareShape`/`favShape` in Echo's `Player.kt` and
  `PlayerQueueButton` in `Queue.kt` are the actual source. If further customization is requested,
  check those files again before inventing new geometry — the Live Mesh saga (§2.1 #6) is the
  cautionary example of what happens when a "mathematically correct" independent fix doesn't
  match what a real, shipped implementation actually does.
- **Always verify a background agent's "done" report with a real, `--rerun` recompile** before
  trusting it — two agents this session hit an API session limit mid-edit and left broken code
  behind despite (implicitly) appearing to be mid-task, not because they lied about finishing.
- **New Releases is genuinely songs now, not albums** — this isn't a regression or a shortcut, the
  live YouTube Music endpoint stopped serving distinct album data to unauthenticated clients
  sometime before this session. Don't "fix" it back to albums without re-confirming against the
  live API first.
