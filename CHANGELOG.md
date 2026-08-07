# Changelog

## MuseFlow Beta v1.1.4 🎵

A smaller, focused update: a real search-to-recommendation feed, and a fix to make sure streamed
audio is always the actual highest-bitrate version available.

### ✨ Major Features
* **Search now blends into personalized recommendations**, the same way YouTube's own search
  does: scroll past a song search's direct matches and the feed seamlessly continues into an
  infinite stream of recommendations - seeded from what you were just looking at and blended with
  your own listening history, instead of just stopping. A subtle "Recommended for you" divider
  marks exactly where direct matches end and the personalized feed begins.

### 🎧 Playback & Player Upgrades
* **Streamed audio quality fixed for a real edge case**: the fallback stream picker used when the
  primary resolver has no bitrate data to compare against was ranking a lower-bitrate AAC stream
  above a higher-bitrate one available on the same track. Reordered to genuinely always pick the
  best bitrate available, for every codec combination YouTube serves - not just the common case.

---

## MuseFlow Beta v1.1.3 🎵

The biggest player-customization and settings update yet, plus a real pass through the audio
engine to make sure every effect toggle actually does something audible.

### ✨ Major Features
* **Settings, properly organized**: the old flat settings list is now 12 focused screens
  (General, Appearance, Player, Playback, Lyrics, Audio, Mini Player, Library Sections, Privacy,
  Storage, Backup, About), each with its own scoped options instead of one long scroll.
* **Now Playing redesign**: transport controls, queue/sleep-timer/lyrics/shuffle/repeat, and the
  download+like buttons have been rearranged into a cleaner boxed layout, with every button now
  fully wired up (no leftover mockups).
* **3 transport button styles**, selectable in Player settings: **Static** (the classic three
  separate buttons), **Wheel** (play/pause grows a slowly rotating scalloped edge while playing),
  and **Pill** (previous/play/next joined into one continuous rounded pill).
* **Live Mesh background**: a new animated Now Playing / mini-player background — three blurred,
  saturated copies of the album art rotating independently behind the content — joining Solid,
  Album Gradient, and Blurred Artwork.
* **6 lyrics sources now, up from 3**: added YouLyPlus, PaxSenix, SimpMusic, and Kugou alongside
  the existing LRCLib and Better Lyrics, plus a new drag-to-reorder priority screen so you control
  which source gets tried first.
* **9 lyric word-animation styles**: added Metro, Fluid, Vivi Music, and Apple Music styles
  alongside the existing Karaoke, Bounce, Scale, Wave, and Fade.
* **Taste-aware autoplay**: when a queue runs out, MuseFlow now blends the last track's own
  "radio" continuation with a weighted pick from your most-played history, instead of always
  drifting toward whatever song happened to play last.
* **M3U playlist import**, alongside the existing CSV importer.
* **Charts & New Releases, fixed for real**: both now pull live from YouTube Music's actual
  charts/new-music data.

### 🎧 Playback & Player Upgrades
* **The equalizer and audio effects actually work now.** EQ, normalization, crossfade, bass boost,
  and crossfeed previously had zero audible effect no matter what was toggled — fixed at the root,
  so every audio setting genuinely changes the sound.
* Fixed an audible volume "pumping" issue once normalization was actually engaging.
* Fixed rapid track-skipping causing a noticeable delay and extra network usage before the app
  settled on the track you actually wanted.
* **A heart/like button on the lock screen and notification**, synced with your Liked Songs.
* **Codec/bitrate info pill** under the seek bar, toggleable in Settings.
* **Clickable artist names now work everywhere** — restored queues, Liked Songs, and playlists,
  not just freshly searched-and-played tracks.
* Full backup/restore now correctly carries artist and album links through export and import.
* **In-app update checker**: notifies you once when a new version is available on GitHub — never
  silently updates itself.

### 🎨 UI & Personalization
* Fixed the mini-player looking transparent when Live Mesh was enabled.
* Fixed a visible hard edge in the Live Mesh rotation.
* Fixed low-contrast text on the Stats, Search, and Lyrics screens.
* Fixed a color mismatch between the navigation bar and the rest of the app.
* Wavy/squiggly seek-bar speed and wavelength are now independently tunable, and a tap on the
  style picker no longer silently fails to register.
* Distinct **"MuseFlow Debug"** and **"MuseFlow Beta"** app names so both builds can be installed
  side by side without confusion.

### 🐛 Bug Fixes & Under The Hood
* List animations, direction-aware navigation transitions, and shared card components extended
  further across Home, Library, Search, and Playlists for a smoother overall feel.
* Library search now covers every section (Playlists, Liked, Downloads, Top 50, Recent, On-Device,
  Following) with real relevance ranking instead of raw filtering.
* Fixed a crash from a duplicate list key in Home's track carousel.
* Fixed downloaded-track cover art not showing, in-app or in the notification, across three
  separate root causes spanning embedding, offline storage, and how artwork URIs were read.
* Fixed offline lyrics lookups taking up to 30 seconds to fail instead of surfacing instantly.

---

## MuseFlow Beta v1.1.2

* **First-run onboarding screen**: shown once on a fresh install and once again after every update, matching Echo Music's own "show once per version" pattern - an app intro card, a hobby-project/bug-report notice with a one-tap email link, a muted "Star the Repo" action, and a solid "Continue" button.
* **Karaoke lyrics sweep smoothed further**: the in-progress word's highlight now animates on Compose's own frame clock instead of being sampled from the player's polled position, so it reads as continuous rather than stepped.
* **General playback-state performance pass**: the periodic position tick no longer rebuilds the entire queue/metadata state twice a second - only the position itself updates on tick, cutting real per-second allocation/recomposition work while anything plays.
* **Queue swipe-to-remove**: replaces the old per-row "⋮" menu - swipe a queued track left-to-right to remove it, with the list reflowing smoothly instead of snapping; fixed a key-reuse bug where a removed row's swipe state could visually "stick" to whatever song took its place.
* **Custom accent color picker fixed**: the saturation/vibrancy square now actually affects the generated theme (previously only hue did, due to the color-scheme generation style ignoring the seed's chroma).
* Assorted Now Playing menu cleanup: removed duplicate queue/like/download actions from the track menu (already available as dedicated on-screen controls), folded the lyrics panel's copy/search actions into the main menu, and fixed the song-details dialog not opening.

---

## MuseFlow v1.1.1 Beta 🎵

Welcome to the biggest update to MuseFlow yet! **v1.1.1 Beta** introduces a massive overhaul to the Library, beautiful UI/UX animations, syllable-synced lyrics, and full support for your local audio files.

### ✨ Major Features
* **Ultimate Library Redesign**: The library has been rebuilt from the ground up with interactive chips for easy access to **Playlists**, **Liked Songs**, **Downloads**, **Local Audio**, and **Cached** tracks.
* **Full Local Audio Support**: MuseFlow now scans your device for local MP3 files and integrates them perfectly alongside streamed music, complete with embedded cover art!
* **Advanced Analytics Dashboard**: We've added a robust new **Stats** screen! Track your top songs, artists, and albums dynamically with precise timeframe filters (1 week, 1 month, 6 months, 1 year, and continuous).
* **Syllable-Synced Lyrics**: Experience a true karaoke feel with new word-by-word synced lyrics that highlight perfectly in time with the artist.
* **Offline Home Caching**: The Home Screen now caches your shelves (Recently Played, genres) to the database and displays an offline indicator when you lose connection, meaning the app is always functional.

### 🎧 Playback & Player Upgrades
* **Sleep Timer**: A brand new sleep timer (10, 15, 30, and 45 minutes) has been added directly to the Now Playing screen.
* **Up Next Modal**: Quickly view and skip to upcoming tracks via the new interactive queue bottom-sheet.
* **Dynamic Track Sources**: The Now Playing screen now accurately displays track streaming origins (e.g., *JioSaavn • Stream* or *YouTube • Stream*).
* **Clickable Artist Profiles**: Artist names in the player are now clickable, jumping you straight to an enhanced Artist Profile that features total monthly listener/subscriber counts.

### 🎨 Deep UI & Performance Polish
* **Smooth Micro-Animations**: Introduced tactile shrink-and-ripple animations when tapping transport controls (Play, Next, Previous, Heart).
* **Elegant Image Loading**: Cover art and artist portraits now fade in gracefully using `Crossfade`.
* **Fluid List Rendering**: Applied Compose item tracking to seamlessly animate dragging, deleting, and updating lists without stuttering.
* **Shared-Element Transitions**: Added smooth vertical sliding and cross-fade animations when navigating through the app and opening the full-screen player.

### 🐛 Bug Fixes & Under The Hood
* Fixed a major navigation bug where pressing "Back" inside sub-screens would abruptly exit the app.
* Reduced parallel download queues to completely eliminate stuttering and provide reliable Android system notification download bars.
* Bumped internal Room Database migrations safely to support the new playback tracking engine.
