# Echo-Music settings & features teardown → MuseFlow plan

Extracted from Echo-Music source at commit `ad66171`. Numbers are counted from their code, not estimated.

**Headline:** Echo has **290 persisted preference keys**, **27 multi-choice enums**, and
**14,784 lines** across ~21 settings screens. MuseFlow currently has **4**.

---

## 1. Echo's settings architecture

| Screen | LOC | What it covers |
|---|---|---|
| `AppearanceSettings` | 2,053 | theme, nav bar, density, thumbnails, sliders, animations |
| `DiscordSettings` | 1,521 | Rich Presence (~20 keys of its own) |
| `ContentSettings` | 1,258 | region, language, filters, history, sync |
| `PlayerSettings` | 1,193 | playback behaviour, audio processing, queue |
| `LosslessContributeScreen` | 814 | community FLAC contribution + GitHub auth |
| `AiSettings` | 648 | AI provider, OpenRouter/Gemini keys, recommendations |
| `LastFMSettingsScreen` | 630 | scrobbling |
| `StorageSettings` | 565 | cache, downloads, quality |
| `GlassEffectSettings` | 531 | liquid-glass tuning (9 dedicated keys) |
| `ThemeScreen` | 459 | colour picker |
| `SearchableSettings` | 458 | **search across all settings** |
| `RomanizationSettings` | 440 | script romanization (11 languages) |
| `AccountSettingsScreen` | 431 | YouTube Music account |
| `BackupAndRestore` / `UpdateSettings` / `EchoExtractorSettings` / `UptimeScreen` / `About` | 332/283/324/327/469 | — |

Two structural ideas worth stealing regardless of features:
1. **`SearchableSettings`** — with this many options, a settings search box stops being optional.
2. **A preference DSL** — every option is a few declarative lines, not a hand-built row.

---

## 2. Full preference inventory (290 keys, grouped)

### Player / Playback — 48
`crossfadeEnabled`, `crossfadeDuration`, `crossfadeGapless`, `automixCrossfade`, `skipSilence`,
`skipSilenceInstant`, `audioNormalization`, `audioQuality`, `spatial_audio_enabled`,
`spatial_audio_strength`, `playerVolume`, `pauseOnMute`, `resumeOnBluetoothConnect`,
`autoSkipNextOnError`, `persistentQueue`, `persistentShuffleAcrossQueues`,
`rememberShuffleAndRepeat`, `preventDuplicateTracksInQueue`, `shufflePlaylistFirst`,
`queueEditLockV2`, `seekExtraSeconds`, `stopMusicOnTaskClear`, `pauseListenHistory`,
`pauseSearchHistory`, `showCodecOnPlayer`, `showSpeedDial`, `hidePlayerSlider`,
`hidePlayerThumbnail`, `playerBackgroundStyle`, `miniPlayerBackgroundStyle`, `miniPlayerOutline`,
`pureBlackMiniPlayer`, `player_buttons_style`, `useNewPlayerDesign`, `useNewMiniPlayerDesign`,
`enable_player_swipe`, `disableLoadMoreWhenRepeatAll`, `enableLyricsThumbnailPlayPause`, …

### Appearance / Theme — 38
`darkMode`, `pureBlack`, `dynamicTheme`, `selectedThemeColor`, `thumbnailCornerRadius`,
`gridItemSize`, `density_scale_factor`, `custom_density_scale_value`, `sliderStyle`,
`squigglySlider`, `useFloatingNavBar`, `slimNavBar`, `defaultOpenTab`, `rotatingThumbnail`,
`canvasThumbnailAnimation`, `swipeThumbnail`, `swipeSensitivity`, `SwipeToSong`,
`SwipeToRemoveSong`, `hideStatusBarOnFullscreen`, `enableDynamicIcon`, `enableLegacyIcon`,
plus **9 liquid-glass keys**: `liquidGlassGlobalEnabled`, `liquidGlassBlurRadius`,
`liquidGlassLensAmount`, `liquidGlassLensHeight`, `liquidGlassVibrancy`,
`liquidGlassChromaticAberration`, `liquidGlassDepthEffect`, `liquidGlassSurfaceOpacity`,
`liquidGlassSurfaceTintColor`, `liquidGlassNavBarEnabled`.

### Lyrics — 33
`lyricsProvider`, `lyricsProviderOrder`, `enableLrclib`, `enableKugou`, `enableBetterLyrics`,
`enablePaxsenix`, `enableYouLyPlus`, `enableSimpMusic`, `lyricsClick`, `lyricsGlowEffect`,
`lyricsLineSpacing`, `lyricsAnimationStyle`, `lyricsStandardBlur`, `appleMusicLyricsBlur`,
`swipeLyrics`, `autoTranslate`, `deeplApiKey`, `deeplFormality`,
plus **11 romanization languages**: Japanese, Korean, Chinese, Hindi, Punjabi, Russian, Ukrainian,
Serbian, Bulgarian, Belarusian, Macedonian, Kyrgyz (+ `lyricsRomanizeAsMain`, `…ByLine`).

### Library / Content — 34
Per-entity sort + direction (`SongSortType`, `ArtistSortType`, `AlbumSortType`, `PlaylistSortType`,
`MixSortType`, `PlaylistSongSortType`, `AutoPlaylistSongSortType`, `ArtistSongSortType`),
view types (`albumViewType`, `artistViewType`, `playlistViewType` — list/grid), filters
(`SongFilter`, `ArtistFilter`, `AlbumFilter`, `LibraryFilter`), `hideExplicit`, `hideVideoSongs`,
`hideYoutubeShorts`, `autoLoadMore`, `randomizeHomeOrder`, `discover`, `MyTopFilter`, `StatPeriod`.

### Downloads / Storage — 7
`downloadQuality`, `audioQuality`, cache sizes, `enableExportAsMp3`, `dataSaverEnabled`,
`local_songs_min_duration_seconds`.

### Integrations — 43
Discord RPC (~20 keys: token, refresh, activity name/type/state/details, 2 configurable buttons,
large/small image sources, status), Last.fm (session, username, scrobble toggles, now-playing,
send-likes), ListenBrainz (token), Spotify (`sp_dc`, `sp_key`, access token, account),
Lossless GitHub contribution, `lastLosslessSync`.

### Account / Sync — 20
`innerTubeCookie`, `visitorData`, `dataSyncId`, `accountName/Email/ChannelHandle`, `ytmSync`,
per-entity sync timestamps (`last_full_sync`, `last_playlist_sync`, `last_album_sync`,
`last_artist_sync`, `last_like_song_sync`), **proxy support** (`proxyEnabled`, `proxyType`,
`proxyUrl`, `proxyUsername`, `proxyPassword`, `ipVersion`), `useLoginForBrowse`.

### Audio DSP & system — from "Other" (60)
`bass_boost`, `virtualizer`, `crossfeed_enabled`, `enableOffload`, `preload_next_song_enabled`,
`preload_next_song_limit`, `keepScreenOn`, `enableHighRefreshRate`, `enableHaptics`,
`disableScreenshot`, `sponsor_block_enabled`, `cropAlbumArt`, `searchSource`, `developerMode`,
`aiProvider`, `aiRecommendations`, `openRouterApiKey/BaseUrl/Model`, `enableGoogleCast`,
`enableListenTogether`, artist-page toggles (`showArtistVideo`, `showArtistDescription`,
`showArtistSubscriberCount`, `showMonthlyListeners`), cipher-update keys.

---

## 3. What MuseFlow should adopt — and what it shouldn't

Copying all 290 is the wrong goal: ~75 belong to features MuseFlow deliberately excludes
(Discord ~20, ListenTogether ~12, Cast, Shazam, AI ~8, Spotify ~6). **A realistic strong target is
~110–130 working preferences.**

### Tier A — high value, backend already exists in MuseFlow (do first)
| Setting group | Why it's cheap |
|---|---|
| **Lyrics** (provider on/off, order, text size, line spacing, position, glow, blur, tap-to-seek, auto-scroll) | `LrcLibProvider` + `BetterLyricsProvider` already ported |
| **Equalizer** (bands, presets, bass boost, virtualizer) | `EqualizerController`/`Repository` exist |
| **Library sorting + filters + list/grid view** | `LibrarySorting` exists |
| **Auto-playlist visibility** (Liked/Downloaded/Top/Cached) | Library sections already built |
| **Backup / restore + auto-backup** | `BackupRepository` + `AutoBackupWorker` exist |
| **Album-art palette theming** | `AlbumPalette` exists; toggle already in UI but inert |
| **On-device local files** | `LocalAudioProvider` exists |
| **Followed artists + release alerts** | Repository + Worker exist |

### Tier B — real features, moderate work, big perceived quality
- **Crossfade + gapless + skip-silence + audio normalization** (ExoPlayer supports all natively)
- **Preload next song** — removes the resolve pause between tracks; likely the single biggest
  *felt* improvement
- **Persistent queue across restarts** + remember shuffle/repeat
- **Resume on Bluetooth connect**, **pause on mute**, **auto-skip on error**
- **Audio/download quality selectors** (itag already chosen in `InnerTubeStreamResolver`)
- **Data saver** + Wi-Fi-only downloads
- **Sleep timer** (was in the old app)
- **Keep screen on**, **high refresh rate**, **haptics**
- **Content filters**: hide explicit / video songs / shorts
- **Search filters UI** — `MusicSearchRouter` already exposes albums/artists/playlists, no screen yet

### Tier C — customization depth (the "enriched" ask)
- **Colour wheel picker** (replace 8 fixed seeds) + palette style choice
- **Density scale**, **grid item size**, **thumbnail corner radius**, **crop album art**
- **Slider styles** incl. squiggly/wavy
- **Player + mini-player background styles** (solid / blur / glass)
- **Liquid glass** — Echo's 9 tuning keys; high effort, high visual payoff
- **Swipe gestures**: change song, remove from queue, sensitivity
- **Nav bar**: floating vs slim, default open tab
- **Dynamic app icon**
- **Settings search** (`SearchableSettings` pattern) — needed once past ~50 options

### Tier D — later / optional
Romanization (11 languages — needs transliteration libs), translation (needs DeepL key),
proxy support, SponsorBlock, export-as-MP3, Last.fm / ListenBrainz scrobbling, stats screen,
YouTube Music account sync.

### Explicitly skip
Discord RPC, ListenTogether, Cast, Shazam, AI recommendations — matches existing scope decisions.

---

## 4. Ordered execution plan

**Phase 1 — foundation (do before anything else)**
1. **Preference DSL + grouped settings cards** — every later option becomes ~5 lines.
2. **Restore the ~36-key `AppSettingsModels`** from git history as the seed schema.
3. **Settings search** scaffold (cheap now, expensive to retrofit).

**Phase 2 — activate what already exists (fastest visible wins)**
4. Lyrics screen + settings · 5. Equalizer screen · 6. Library sort/filter/view ·
7. Backup/restore · 8. Album-art theming · 9. On-device files · 10. Follow artists.

**Phase 3 — playback quality**
11. Preload next song · 12. Crossfade/gapless/skip-silence/normalization · 13. Persistent queue ·
14. Quality selectors · 15. Bluetooth/mute behaviours · 16. Sleep timer.

**Phase 4 — customization depth**
17. Colour wheel · 18. Density/grid/corner radius · 19. Slider + background styles ·
20. Swipe gestures · 21. Liquid glass · 22. Nav bar options.

**Phase 5 — content & scale**
23. **DB schema expansion (8 → ~20 entities)** — do before artist/album screens ·
24. Artist/album pages · 25. Explore/Browse/Mood (innertube browse already ported) ·
26. Search filters UI · 27. Stats · 28. Widget · 29. Canvas · 30. Lossless.

---

## 5. Non-negotiable: no mockups

Rules to hold to, since the old codebase had settings that looked real but drove nothing:

1. **A setting ships only when it changes behaviour.** If the backend isn't ready, don't add the row.
2. **Persist everything** through DataStore, verified across process death.
3. **On-device verification** for each feature before it counts as done — this session found 8 real
   bugs (403s, missing `file://` scheme, unrequested notification permission, dead click handlers)
   that all compiled fine and looked correct in code review.
4. **Delete dead toggles.** `dynamicAlbumColor` currently persists but does nothing — either wire
   `AlbumPalette` to it or remove it.

---

## 6. Scale reality

Echo: ~14.8k lines of settings UI, built by many contributors over a long period. Phases 1–3 are
realistically the bulk of the value; Phase 4 is where it starts *feeling* like Echo. Phase 1 is the
multiplier — without the DSL, every one of the ~120 target settings is hand-built and the effort
compounds badly.
