# 🎵 MuseFlow Changelog


## 🎉 MuseFlow v1.1.1 Beta

### 📚 Library and discovery

- Redesigned Library with quick access to **Playlists, Liked Songs, Downloads, Local Audio,** and **Cached** music.
- Added local audio playback alongside streamed songs, including embedded cover art.
- Added a **Stats** screen for top songs, artists, and albums with time filters.
- Cached Home shelves so previously loaded content remains visible offline.

### 🎧 Playback and lyrics

- Added word-by-word synchronized lyrics.
- Added a sleep timer with **10, 15, 30, and 45-minute** options.
- Added an interactive **Up Next** queue sheet.
- Showed each track’s source in Now Playing and made artist names open their profiles.

### ✨ Polish and fixes

- Added tactile control animations, cover-art fades, smoother list changes, and navigation transitions.
- Fixed Back navigation unexpectedly exiting from sub-screens.
- Improved download scheduling and progress notifications.
- Added database migrations for playback-tracking data.

---

## 🎵 MuseFlow v1.1.2 Beta

- 👋 Added first-run onboarding with an app introduction, support information, a repository link, and a Continue button.
- 🎤 Smoothed karaoke lyric highlighting using Compose’s frame clock.
- ⚡ Reduced playback-state work so position updates no longer rebuild the entire queue and metadata state twice each second.
- 📋 Added swipe-to-remove in the queue and fixed swipe state appearing on the wrong row after removal.
- 🎨 Fixed the accent-colour picker so saturation changes affect the generated theme.
- 🛠️ Removed duplicate Now Playing menu actions and fixed the song-details dialog.

---

## 🎛️ MuseFlow v1.1.3 Beta

### ✨ Player and settings

- Organized settings into **12 focused screens** instead of one long list.
- Redesigned Now Playing and connected its queue, sleep timer, lyrics, shuffle, repeat, download, and Like controls.
- Added **Static, Wheel,** and **Pill** transport-button styles.
- Added the animated **Live Mesh** background.
- Expanded lyrics to **six sources** with drag-to-reorder priority.
- Added four lyric animations, bringing the total to **nine styles**.
- Added taste-aware autoplay that blends the current track’s radio continuation with listening history.
- Added **M3U playlist import** alongside CSV.
- Connected Charts and New Releases to live music data.

### 🎧 Playback reliability

- Fixed the equalizer, normalization, crossfade, bass boost, and crossfeed so their controls affect audio.
- Reduced volume pumping from normalization.
- Reduced delays and extra requests during rapid track skipping.
- Added a Like action to the media notification and lock-screen controls.
- Added an optional codec and bitrate indicator.
- Made artist links work from restored queues, Liked Songs, and playlists.
- Preserved artist and album links in library backups.
- Added an in-app GitHub update check that notifies users without silently installing.

### 🎨 UI and fixes

- Fixed Live Mesh transparency and a hard edge in its rotation.
- Improved text contrast in Stats, Search, and Lyrics.
- Improved navigation-bar colour consistency.
- Made Wavy and Squiggly seek-bar speed and wavelength independently adjustable.
- Gave Debug and Beta builds distinct app names.
- Expanded list and navigation animations across Home, Library, Search, and playlists.
- Improved Library search coverage and ranking.
- Fixed a duplicate-key crash in Home, missing downloaded cover art, and slow failures during offline lyrics lookup.

---

## 🔎 MuseFlow v1.1.4 Beta

- Extended song search into a continuous recommendation feed seeded by the search and listening history.
- Fixed a fallback stream-selection case that could choose lower-bitrate audio when a better stream was available.

---

## 📥 MuseFlow v1.2.0 Beta

- Showed fully downloaded playlists as cards in Downloads while retaining an **All downloaded songs** view.
- Added **Download playlist** to online playlists.
- Added a compact offline status indicator based on Android connectivity.
- Made offline queues skip network-only songs and stop cleanly when no downloaded song remains.
- Prevented autoplay and new downloads from starting while offline.
- Combined YouTube Music Songs and Videos search results so covers, live sessions, and other uploads can be found.

---

## 🌐 MuseFlow v1.3.0 Beta

- Adapted the new MuseFlow visual direction across Home, Library, navigation, Mini player, Search, Stats, History, and Player actions.
- Added **Listen Together** room creation, joining, host approval, and synchronized playback with a compatible WebSocket server.
- Expanded search to YouTube Music songs and videos and supported pasted YouTube video links as audio tracks.
- Improved Stats and History by recording actual playback events, including automatic queue advances.
- Improved offline recovery under shuffle and repeat.
- Protected completed downloads when partial downloads fail, limited parallel transfers, and surfaced download errors.
- Preserved imported playlist identity across repeat imports and library backup and restore.
- Showed a playlist in Downloads as soon as one of its songs is available offline.

---

## 📱 MuseFlow v1.3.1 Beta

- Improved readability with the device’s system font and stronger text hierarchy.
- Fixed Home shortcut visibility at compact widths.
- Added regular YouTube uploads to search alongside YouTube Music results.
- Added dedicated Library destinations for **Liked, Downloads, Top 50,** and **On device**.

---

## 🏠 MuseFlow v1.4.0 Beta

- Added an auto-sliding **Made for you** Home hero based on listening taste.
- Redesigned Liked, Downloads, Top 50, and On device with cover collages, floating headers, playback actions, and details sheets.
- Unified Search results into **Top result, Songs, Videos, Albums, Artists,** and **Playlists** sections.
- Added a search transition that preserves the previous list position.
- Distinguished local-device songs from downloaded online songs.
- Combined bulk-download progress into one notification that clears when finished.
- Improved offline and reconnection notices.
- Added a changelog popup after updates.
- Fixed stale search suggestions, playback restarting after downloading the current song, skip actions leaving playback paused, end-of-queue behaviour, and silent playback failures.
- Slowed overflowing Player and Mini player title scrolling for readability.

---

## 🔧 MuseFlow v1.4.1 Beta

- Fixed a crash that could occur when a download started.
- Prepared autoplay continuation before the final queued song ends to reduce gaps.
- Improved offline queue recovery so partly cached songs are skipped and playback resumes when connectivity returns.
- Fixed artist links on songs with multiple artists.
- Improved scrolling of long artist names and Home card titles.
- Added a heart-burst animation when liking a song.
- Fixed an update popup appearing immediately after that update was installed.
- Improved version comparison for GitHub release tags.
- Added **Settings → Check for updates** with an on-demand check, download action, and changelog view.

---

## 🔗 MuseFlow v1.4.2 Beta

- Added sharing and importing of MuseFlow playlists.
- Shared saved YouTube playlists as YouTube links.
- Accepted YouTube and YouTube Music playlist links in the same import field as MuseFlow share codes.
- Fixed an import crash caused by duplicate song titles and artists.
- Made the import screen usable with long pasted codes or links.
- Simplified online playlist actions into **Shuffle, Play,** and a **More** menu.
- Fixed spacing below the expanded changelog in Settings.

---

## 💿 MuseFlow v1.4.3 Beta

- Fixed playback from filtered playlist and collection searches so the complete list remains queued.
- Fixed the liked-heart animation replaying when Now Playing opens on an already liked track.
- Fixed collection and artist-song lists jumping to the top after returning from Now Playing.
- Kept on-device songs visible while a background rescan runs.
- Added a current-song animation on cover art throughout music lists.

---

## ✨ MuseFlow v1.5.0 Beta — Current build

### 🎨 Appearance and settings

- Redesigned Appearance with **Follow system, Light, Dark,** and **AMOLED** theme cards.
- Added album-art accent colours and a manual colour preview, hue slider, and fine-tune picker.
- Added visual selectors for font style, display density, and Home card size.
- Added **Dot matrix** typography. Font cards show each font accurately, and Dot matrix applies to supporting text as well as titles.
- Rounded the Settings search field and made visible controls searchable.
- Added live Appearance, Player, and Mini player previews and separate reset actions.
- Improved spacing, shapes, large-text layouts, touch targets, and screen-reader labels.
- Removed Appearance and Player quick presets so individual settings remain directly adjustable.
- Hid three settings that saved values but had no visible effect.

### 🎧 Player and lyrics

- Added **Glow animated** and **Apple Music inspired** Player backgrounds.
- Made full-screen Glow more visible and extended animated backgrounds behind the device navigation area.
- Refined the Apple Music inspired backdrop with clear and blurred artwork, subtle motion, and readable controls.
- Kept the artwork backdrop visible when lyrics open and placed Apple-style lyrics on a translucent panel.
- Added rotating artwork and refined artwork crop, corners, and visibility controls.
- Added directional artwork transitions and swipes that reveal the adjacent queued cover, including under shuffle.
- Refined **Default, Slim, Wavy,** and **Squiggly** progress controls and their previews.
- Matched progress and transport settings rows to the other icon-backed settings controls.
- Gave the liked heart a consistent pink colour and soft baby-pink glow.

### 📱 Mini player and navigation

- Added artwork-coloured **Glow** to the Mini player and updated its preview to use sample cover art.
- Added a rounded playback-progress ring around the Mini player cover.
- Narrowed the Mini player while preserving space for track details and controls.
- Redesigned bottom navigation so only the selected tab expands horizontally to show its name.
- Fixed the saved default tab sometimes being ignored at startup.
- Made the current-song artwork indicator freeze when playback pauses and resume when playback continues.

### 🔎 Search, accessibility, and updates

- Improved song-duration parsing in search results and duration handling for pasted video links.
- Unified Home, Search, and Library empty/error messages and added accessibility announcements.
- Made marquee text, shimmer, seek waves, playback indicators, and animated backgrounds respect Android’s reduced-motion setting.
- Made **Read changelog** fetch the installed version’s GitHub Release notes once and save them for offline reading.
- Fixed update-screen spacing and several Player text-layout issues.

### ⚙️ Build and verification

- Enabled minification and resource shrinking for the optimized beta build.
- Kept the existing beta package ID and signing certificate for in-place updates from earlier beta builds.
- Completed unit tests and release lint. **On-device verification remains pending.**
