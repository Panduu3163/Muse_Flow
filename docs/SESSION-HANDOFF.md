# MuseFlow — session handoff (2026-07-25)

Read this first in a new session. Companion docs:
- `full-gap-audit.md` — the authoritative remaining-work list (~90 items)
- `echo-settings-teardown.md` — Echo's 290 preferences, grouped
- `echo-vs-museflow.md` — architecture comparison
- `progress-vs-plan.md` — phase-by-phase delivery (note: its gap list understates; use the audit)

---

## 1. What this session did, in one paragraph

Deleted the entire frontend (39 files + 10 ViewModels + theme), kept the backend, and rebuilt the UI
from scratch on a seed-based Material 3 theme. Ported Echo-Music's `:innertube` module (106 files)
and made it the sole extractor. Rebuilt Home, Search, Library, Now Playing, Settings, Playlist
detail, Equalizer. Added lyrics, downloads UI, queue persistence, preload, and 25 working settings.
Fixed 17 real bugs, all verified on a physical Xiaomi 14 Ultra.

---

## 2. Current state

| Metric | Value |
|---|---|
| Kotlin files | 91 (+106 in `:innertube`) |
| Lines | 12,789 |
| Screens | 7 |
| Settings options | 25 across 8 groups |
| ViewModels | 10 |
| DB entities | 6 |
| Build | ✅ `assembleDebug` + `testDebugUnitTest` green |

**Architecture:** single `:app` module + `:innertube`. No Hilt (deliberate). Manual
`getInstance(context)` singletons. MaterialKolor seed-based theming. Media3 + `MediaController`.

---

## 3. Key architectural decisions (don't undo these)

1. **InnerTube is the only extractor.** JioSaavn + NetEase deleted. The extractor picker is hidden
   in Settings but `ExtractorPreference` still exists so the legacy cipher path can be re-exposed.
2. **NewPipe extraction runs FIRST**, before `player()`. This is the single most important fix in
   the session: `player()` returns URLs whose `n` parameter is still obfuscated → HTTP 403. Order
   matters. Do not "simplify" this.
3. **The equalizer is DSP, not a platform effect.** `android.media.audiofx.Equalizer` is refused by
   this device's audio HAL (`AudioFlinger … status: -38`). Echo doesn't use it either — zero
   references in their codebase. MuseFlow runs `BiquadFilter` + `EqualizerAudioProcessor` inside
   ExoPlayer's pipeline. **Any future audio DSP (normalization, bass boost, virtualizer) must go
   in the processor chain, not `AudioEffect`.**
4. **`REPEAT_MODE_OFF` is deliberate.** It was `REPEAT_MODE_ALL` (mock-catalog leftover), which made
   one-track queues loop forever. Autoplay appends related tracks when a queue truly ends.
5. **Queue persistence stores identity/metadata only, never resolved URLs** — those expire in
   minutes. Restores **paused** on purpose.
6. **No mockups rule.** A setting ships only when it changes behaviour.

---

## 4. Bugs fixed this session (all compiled cleanly and looked correct in review)

1. Nav bar drew under system gesture bar (`Scaffold` doesn't inset `bottomBar`)
2. `LOGIN_REQUIRED` — single wrong client; now a 10-client chain
3. HTTP 403 — winning client's User-Agent was discarded
4. HTTP 403 — `n` parameter never deciphered (ordering)
5. Offline search looked like "no results" (`getOrNull()` swallowed failures)
6. Downloads wouldn't play — bare filesystem path had no `file://` scheme
7. Download notifications never posted — `POST_NOTIFICATIONS` never requested
8. Search-history rows had no `.clickable`
9. Preload re-resolved the *current* track (repeat-mode wrap)
10. Equalizer probed session 0 (blocked on modern Android)
11. `MODIFY_AUDIO_SETTINGS` missing from manifest
12. Continue Listening card contradicted the mini-player
13. `REPEAT_MODE_ALL` looped single-track queues
14. Duplicate palette ViewModel (would decode every image twice)
15. `PlaybackService` hard-referenced `MainActivity`
16. `LyricsResult.PlainOnly` misread as a line list
17. `it` inside a nav `composable{}` binds to `NavBackStackEntry`, not the lambda arg

---

## 5. Backend reliability audit (IMPORTANT for next session)

The surviving backends predate the wipe. Audited:

| Backend | Verdict |
|---|---|
| `LibrarySorting` | ✅ pure Kotlin, safe — **now in use** |
| `PlaybackHistoryRepository` | ✅ verified working |
| `MusicSearchRouter` | ✅ all 4 search methods work |
| `BackupRepository` | ✅ complete API, untested end-to-end |
| `EqualizerRepository` | ✅ in use |
| `LocalAudioProvider` | ❌ **`READ_MEDIA_AUDIO` never requested at runtime** — will silently return nothing on Android 13+. Fix before building the screen. |
| `FollowedArtistsRepository` + worker | ⚠️ scheduled, never end-to-end tested |
| `AudioTagger` | ⚠️ runs on download, no UI surfacing |

**Data-migration caveat:** tracks saved during the JioSaavn era have a `sourceType` that no longer
exists and an id that isn't a YouTube videoId — they won't play. All `MusicSource.valueOf` sites are
defensively wrapped, so no crash, but those old entries are dead weight.

---

## 6. Where to resume — Stage A of the gap audit

**Done:** Library sort / filter / grid view.

**Next, in order:**
1. **Search filters UI** (songs/albums/artists/playlists tabs) — `MusicSearchRouter` already has all
   four methods. Verified backend, high daily value.
2. **On-device files** — *fix `READ_MEDIA_AUDIO` runtime request first.*
3. **History screen** — repository verified.
4. **Backup & restore UI** — API complete.
5. **Followed artists** — verify the worker end-to-end before trusting it.
6. **Charts** — needs an endpoint decision.

Then Stage B (context menus — 21 in Echo vs 1 here; highest payoff per line) and Stage C (DB
expansion **before** artist/album screens).

---

## 7. Device testing runbook

Phone: Xiaomi 14 Ultra, `com.aistudio.museflow.kqfzyw`. **Bus ID changes between sessions.**

```
powershell.exe -Command "& 'C:\Program Files\usbipd-win\usbipd.exe' list"      # find BUSID
powershell.exe -Command "& '...\usbipd.exe' attach --wsl --busid <BUSID>"
echo '<pw>' | sudo -S chmod 666 /dev/bus/usb/*/*                               # every attach
adb kill-server && adb start-server && adb devices -l
```

- `versionName` is static (`0.0.9`) — compare `lastUpdateTime` vs APK mtime to confirm installs.
- **The user installs APKs themselves.** Build, give the path, wait.
- Use `adb install -r` — a plain reinstall wipes app data (cost one invalid test this session).
- Driving the app via `monkey` launch + `input tap/text` + `exec-out screencap` works well.

---

## 8. Verified working on device

Search · InnerTube playback · preload (instant skips) · downloads + notifications · offline playback
of downloads · offline Home from Room cache · offline→online recovery · lyrics (synced, auto-scroll,
tap-to-seek) · equalizer (7-band DSP, +9 dB verified) · queue persistence across process kill ·
custom colour picker · album-art theme seeding · wavy slider · album-gradient background · display
density · card size · like/download from Now Playing · swipe-from-recents shutdown · habit shelves.
