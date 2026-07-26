# MuseFlow — Status & Roadmap

Written 2026-07-25, after the frontend rebuild + on-device test pass.
Supersedes the "what's next" half of `roadmap.md`; `echo-vs-museflow.md` stays the feature-gap reference.

---

## 1. Done — built and verified on a real device

Everything below was tested on a Xiaomi 14 Ultra, not just compiled.

### Architecture
- Old frontend deleted entirely (39 files + 10 ViewModels + theme). Backend survived with **one**
  dangling reference (`PlaybackService` → `MainActivity`), now decoupled via `packageManager`.
- **`:innertube` module ported** from Echo-Music (106 files, GPL-3.0, attributed). Ktor +
  NewPipeExtractor + brotli added; minSdk/Java adapted to MuseFlow's toolchain.
- JioSaavn + NetEase removed; search is YouTube-Music-centric.

### Playback (the hard-won part)
- **InnerTube resolution works.** Order matters: NewPipe extraction runs **first**, because
  `player()` returns URLs whose `n` parameter is still obfuscated → HTTP 403. NewPipe deciphers it.
- 10-client fallback chain (`ANDROID_VR_1_43_32` → … → `IOS`) behind that.
- User-Agent travels with the URL (`StreamResolution.userAgent`) — YouTube 403s a mismatch.
- **Strict routing, no silent fallback** between extractors.
- MuseFlow's own `ytcipher/` pipeline is **still compiled in** but dormant. Note: it is currently
  *broken* — no cipher-config entry for player.js hash `7a7969c2`. Not slow: broken.

### UI (all new)
| Screen | State |
|---|---|
| App shell | `MainActivity`, nav graph split into `NavGraphBuilder` extensions, floating nav bar |
| Home | Continue-listening, Recently played, On repeat, 4 genre shelves w/ Room cache |
| Search | Debounced type-ahead, history, results, long-press actions sheet |
| Library | 5 sections (Playlists/Liked/Downloads/Top 50/Recent) |
| Now Playing | Artwork, seekbar, transport, queue, shuffle, repeat, like, download |
| Playlist detail | Play-all + shuffle |
| Settings | Accent picker, dark, AMOLED, album-art colour |

- **Theme: MaterialKolor seed-based.** Whole M3 palette generated from one colour + `pureBlack()`
  AMOLED transform. 8 accent seeds.
- Shared `TrackRow` used by every list (the thing that kept old screens under control).

### Verified working on device
Cold start · InnerTube search + suggestions · playback · queue jump · shuffle · repeat cycle · seek
accuracy · background playback · media notification · relaunch re-binding · **swipe-from-recents
kills the process** · like · download · **download progress + completion notifications** · offline
playback of downloads · offline Home from Room cache · offline→online recovery · theme at runtime.

### Bugs found and fixed during testing
1. Nav bar drew under the system gesture bar (Scaffold doesn't inset `bottomBar`).
2. `LOGIN_REQUIRED` — wrong single client, and bailing before NewPipe.
3. HTTP 403 — User-Agent discarded after resolution.
4. HTTP 403 again — `n` parameter never deciphered (ordering).
5. Offline search looked like "no results" — provider swallowed errors with `getOrNull()`.
6. **Downloads wouldn't play** — bare filesystem path had no `file://` scheme.
7. **Download notifications never appeared** — `POST_NOTIFICATIONS` never requested at runtime.
8. Search-history rows had no `.clickable` — taps did nothing.

---

## 2. Built but NOT wired to any UI — highest value, lowest effort

**This is the most important list in this document.** These already exist, tested, in the backend.
They need a screen, not new engineering.

| Capability | File | Missing |
|---|---|---|
| **Equalizer** | `EqualizerController`, `EqualizerRepository` | A settings screen |
| **Lyrics** | `LrcLibProvider`, `BetterLyricsProvider` | ViewModel + Now Playing lyrics view |
| **Backup / restore** | `BackupRepository`, `AutoBackupWorker` | Settings entry |
| **Followed artists + new-release alerts** | `FollowedArtistsRepository`, `ArtistReleaseCheckWorker` | Follow button + artist screen |
| **On-device files** | `LocalAudioProvider` | An "On device" search toggle |
| **Album-art palette** | `AlbumPalette` | Wire to `dynamicAlbumColor` (toggle exists, does nothing yet) |
| **Library sorting** | `LibrarySorting` | Sort header in Library |
| **Tag embedding** | `AudioTagger` | Already runs on download; no UI needed |
| **Artist / album pages** | `YouTubeMusicProvider.getArtistTracklist` etc. | Detail screens |

---

## 3. Customization — the honest answer

**Currently thin: 4 options.** You're right to flag it.

The old (deleted) `AppSettingsModels.kt` defined **~36 preferences**, all persisted. It's in git
history and is a ready-made blueprint:

- **Player**: background style (Solid/Liquid Glass/Blur), hide thumbnail, thumbnail corner radius,
  crop album art, button colour, slider style (Default/Wavy), swipe-to-change-song, animated canvas,
  rotating thumbnail, codec info, mini-player swipe sensitivity
- **Lyrics**: text position, word animation style, glow, blur inactive lines, text size, line
  spacing, tap-to-seek, auto-scroll, fullscreen swipe, hide status bar
- **Misc**: default tab, default library chip, swipe-to-queue, haptics, grid cell size, display
  density
- **Auto playlists**: toggle Liked / Downloaded / Top / Cached visibility

**Plan:** restore that model, then build a settings DSL (Echo's `Preference.kt` + grouped cards
pattern) so each new option costs ~5 lines instead of a hand-built row. Add a real colour-wheel
picker (Echo's `ColorPicker.kt`, 389 lines) so accents aren't limited to 8 presets.

Order: settings DSL → restore preference model → wire the ones with real behaviour → colour wheel.

---

## 4. Planned but not started

| Feature | Effort | Notes |
|---|---|---|
| **Canvas video** | Medium | 4,817 entries. **Needs `media3-exoplayer-hls`** or you lose 1,644 `.m3u8` entries. Opt-in (battery/data). Use `isLikelyMatch()` — exact string matching will miss constantly. |
| **Lossless FLAC** | Small | Only 661 tracks / 330 artists. 30–50 MB files, single host. Opt-in + Wi-Fi gate. Different legal posture than platform streaming — decide deliberately. |
| **Spotify import** | Medium | Needs **your** Client ID + redirect URI, OAuth 2.0 **PKCE**. Metadata only — Web API can't stream; match to YouTube for playback. |
| **Lyrics UI** | Small | Providers already exist. |
| **Equalizer UI** | Small | Controller already exists. |

---

## 5. What a "complete app" still needs

**Tier 1 — expected in any music app (do first)**
1. Lyrics view (synced) — providers exist
2. Equalizer screen — controller exists
3. Artist + album detail screens
4. Sleep timer
5. Library sorting UI
6. Queue reordering / add-to-queue
7. "On device" local files toggle

**Tier 2 — completeness**
8. Backup/restore UI · 9. Follow artists + release alerts · 10. Explore/Browse/Mood screens
(needs innertube browse endpoints — already available in the ported module) · 11. Search filters
(songs/albums/artists/playlists — `MusicSearchRouter` already has the methods, no UI) ·
12. Listening stats · 13. Home-screen widget (Glance) · 14. Ambient/AOD player

**Tier 3 — nice to have**
15. Canvas · 16. Lossless · 17. Spotify import · 18. Last.fm / ListenBrainz · 19. Crossfade &
gapless · 20. Sharing

**Deliberately excluded** (cost/infrastructure, per the original roadmap): song identification
(Shazam), Google Cast, Listen Together.

---

## 6. Structural work worth doing before piling on features

1. **Expand the DB schema.** 8 flat tables vs Echo's 28 normalised. Without shared song/artist/album
   identity, artist and album screens must refetch everything. Do this *before* those screens.
2. **Settings DSL** — see §3. Every future option gets cheaper.
3. **Home recommendations** — shelves currently reuse *search* as a stand-in. Innertube's browse
   endpoints are already ported and would make Home genuinely personalised.
4. **Instrumented tests** — every regression this session was caught by hand. The unit tests only
   cover surviving backend logic.

---

## 7. Verdict on "baselines are done, time for features"

Agreed. Playback, search, library, downloads, offline, and notifications are all verified working
on hardware. The foundation is sound.

**Suggested next order:**
1. Settings DSL + restore the ~36 preferences (unlocks all future customization cheaply)
2. Lyrics + Equalizer UI (backends already exist — fastest visible wins)
3. DB schema expansion → artist/album screens
4. Canvas (opt-in)
5. Everything else by tier

---

## 8. Operational notes

- **Install with `adb install -r`.** Plain reinstall wipes app data — it deleted downloads, likes and
  theme between test rounds and invalidated one test.
- Device runbook (usbipd bus IDs shift; `chmod 666 /dev/bus/usb/*/*` after every attach) is saved in
  the assistant's project memory.
- Package: `com.aistudio.museflow.kqfzyw`; Kotlin namespace is still `com.example`.
- `versionName` is static (`0.0.9`) — compare `lastUpdateTime` against APK mtime to confirm installs.
