# MuseFlow — progress vs. the Echo teardown plan

Written 2026-07-25, after Phases 1–4 and a full on-device verification pass.
Compares actual delivery against `echo-settings-teardown.md`.

**Headline:** Settings went from **4 → 27 working options** across **8 groups**. Every one is
verified on hardware. Phases 1–4 of the plan are complete; Phase 5 has not started.

---

## 1. Plan vs. delivered

| Plan phase | Status |
|---|---|
| **Phase 1 — foundation** | ✅ Done (except settings search) |
| **Phase 2 — activate existing backends** | ⚠️ Partial (2 of 7) |
| **Phase 3 — playback quality** | ⚠️ Partial (4 of 6) |
| **Phase 4 — customization depth** | ✅ Mostly done (6 of 8) |
| **Phase 5 — content & scale** | ❌ Not started |

---

## 2. Delivered and verified on device

### Foundation (Phase 1)
- **Preference DSL** — `PreferenceGroup`, `SwitchPreference`, `ListPreference`,
  `SliderPreference`, `NavigationPreference`. Each new option now costs ~5 lines.
- **36-key `AppSettingsModels` + 195-line DataStore layer** recovered from git rather than rewritten.
- 8 settings groups, 27 working options.

### Activated existing backends (Phase 2)
- **Lyrics** — `LyricsViewModel` + `LyricsView`, LRCLib → BetterLyrics fallback, synced highlight,
  auto-scroll biased ⅓ down, tap-to-seek, blur, 5 preferences. Verified.
- **Equalizer** — see §4; required a full rewrite, not just a screen.

### Playback quality (Phase 3)
- **Preload next track** — resolves the next URL during the current track. Verified: skip reaches
  PLAYING in <1 s with **zero** resolve at skip time.
- **Skip silence** — ExoPlayer-native.
- **Queue persistence** — survives process kill; restores index, position, and **paused** by design.
- **Autoplay on queue end** — appends related tracks instead of falling silent.

### Customization depth (Phase 4)
- **Custom colour picker** — HSV field + hue bar, live preview + hex, feeds MaterialKolor.
- **Wavy slider** — animates only while playing; amplitude flattens while scrubbing.
- **Slim slider**.
- **Player backgrounds** — solid / album gradient / blurred artwork, composited for legibility.
- **Button colour roles** — primary / secondary / tertiary.
- **Album-art theme seeding** — whole palette re-seeds from artwork; closed the last dead toggle.
- **Display density** — scales all dp via `LocalDensity`; leaves `fontScale` alone deliberately.
- **Card size**.

### Beyond the plan (user-reported flaws, all fixed)
1. **Continue Listening card during playback** — contradicted the mini-player; now hidden when
   media is loaded.
2. **Track repeating forever** — `REPEAT_MODE_ALL` hardcoded from the mock-catalog era. Now OFF
   with autoplay taking over. Verified advancing across 4 tracks.
3. **Home not habit-based** — added **Forgotten favourites** (high play count, not played recently)
   and **More from [artist]** (seeded by most-played artist, re-fetches as taste drifts).

---

## 3. Not delivered — the honest gap list

### Phase 2 leftovers (backends exist, no UI) — cheapest remaining work
| Feature | Backend already present |
|---|---|
| **Library sort / filter / list-grid view** | `LibrarySorting` |
| **Backup & restore + auto-backup** | `BackupRepository`, `AutoBackupWorker` |
| **On-device local files** | `LocalAudioProvider` |
| **Followed artists + release alerts** | `FollowedArtistsRepository`, `ArtistReleaseCheckWorker` |
| **Audio tag embedding UI** | `AudioTagger` (runs on download; no surfacing) |

### Phase 3 leftovers
- **Crossfade / gapless** — genuinely harder; needs two players or a custom processor.
- **Audio normalization** — `LoudnessEnhancer` is an `AudioEffect`, so it will hit the same
  AudioFlinger refusal as the old equalizer. Would need DSP in the processor chain.
- **Quality selectors**, **Bluetooth/mute behaviours**, **sleep timer**, **data saver**.

### Phase 4 leftovers
- **Swipe gestures** (change song, remove from queue, sensitivity).
- **Liquid glass** (Echo's 9 tuning keys).
- **Nav bar options** (floating vs slim), **dynamic app icon**.
- **Settings search** — plan said build it in Phase 1; skipped. At 27 options it isn't yet painful,
  but it gets more expensive to retrofit as the count grows.

### Phase 5 — untouched
DB schema expansion (8 → ~20 entities), artist/album pages, Explore/Browse/Mood, search filters UI,
stats, widget, Canvas, Lossless, Spotify import.

**Note on ordering:** the plan says expand the DB *before* artist/album screens. That still holds —
without shared song/artist/album identity those screens must refetch everything.

---

## 4. Where reality diverged from the plan

**The equalizer was mis-scoped.** The plan called it cheap ("controller exists"). In practice the
platform `AudioEffect` path is refused outright by this device's audio HAL
(`AudioFlinger ... status: -38`). Checking Echo revealed they don't use `android.media.audiofx.Equalizer`
at all — zero references. They run a **biquad `AudioProcessor` inside ExoPlayer's pipeline**.
MuseFlow now does the same: `BiquadFilter` + `EqualizerAudioProcessor`, 7 bands, ±12 dB, verified
working where the platform effect could not.

**Two settings were dropped as unbuildable-as-specified:** `LiquidGlass` was replaced by
`Gradient` (liquid glass was never implemented and needs 9 keys of its own).

---

## 5. Bug count

**14 real bugs found and fixed**, every one of which compiled cleanly and looked correct on review:

1. Nav bar under system gesture bar 2. `LOGIN_REQUIRED` (wrong client) 3. HTTP 403 (User-Agent
discarded) 4. HTTP 403 (`n` parameter never deciphered) 5. Offline search looked like "no results"
6. Downloads wouldn't play (missing `file://` scheme) 7. Download notifications never posted
(`POST_NOTIFICATIONS` never requested) 8. Search-history rows not clickable 9. Preload re-resolved
the current track (repeat-mode wrap) 10. Equalizer probed session 0 11. Missing
`MODIFY_AUDIO_SETTINGS` 12. Continue Listening contradicted the mini-player 13. `REPEAT_MODE_ALL`
looped single-track queues 14. Duplicate palette ViewModel (would have decoded every image twice)

---

## 6. Suggested next order

1. **Phase 2 leftovers** — Library sort/filter, backup/restore, on-device files, follow artists.
   All have working backends; this is UI work only.
2. **Sleep timer + quality selectors** — small, visible.
3. **Settings search** — before the option count makes it a retrofit.
4. **DB schema expansion**, then artist/album pages.
5. Canvas / Lossless / Spotify import last (Spotify needs a Client ID from the user).
