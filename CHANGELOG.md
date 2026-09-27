# Changelog

## MuseFlow Beta v1.4.2

- Added playlist sharing: send any of your own playlists, or any YouTube playlist, to another MuseFlow user, and import one someone sends you from Library > Import shared playlist.
- A playlist you originally saved from YouTube now shares as a YouTube link, like it should - it was going out as a MuseFlow-only code instead.
- Import shared playlist now accepts a YouTube/YouTube Music playlist link as well as a MuseFlow code, from the same paste field.
- Fixed a crash pasting a playlist code or link whose songs included a duplicate title/artist.
- Fixed the paste-a-code screen: a long paste no longer pushes the Preview button off-screen with no way to reach it.
- A YouTube playlist's own screen now shows Shuffle, Play, and a single "more" menu instead of five crowded buttons; Save/Download/Share live in that menu.
- Fixed a missing gap between Settings' changelog text and the mini player when the changelog was expanded.

## MuseFlow Beta v1.4.1

- Fixed a crash that could kill the app while a download was starting, on some devices.
- The queue now gets a taste-blended continuation queued up in the background *before* the last song ends, instead of only starting to fetch it once playback actually ran out - so there's no gap, whether that's a short Home/Search queue or Liked/Top 50/Downloads running to its last track. Never applies to an on-device queue, which stays local-only.
- Going offline mid-queue now skips straight to the next downloaded song instead of starting one that's only partly cached and then stalling partway through; if nothing downloaded is left, it stops cleanly and resumes the queue on its own once you're back online.
- Fixed: tapping a multi-artist song's byline in Now Playing always opened the first artist, no matter which name you tapped - now each artist's name opens their own page.
- Now Playing's artist line, and truncated titles across Home's cards, now scroll at that same slow, readable pace when they don't fit.
- Added a little heart-burst and bounce to Now Playing's like button when you like a song.
- Fixed the update popup still appearing right after installing the very update it was offering.
- Fixed the update check itself missing a genuinely newer release whenever its GitHub tag had anything other than plain periods between the version numbers.
- Added Settings > Check for updates: check for a new version and download it on demand, or read this version's own changelog, without a popup.

## MuseFlow Beta v1.4.0

- Home now opens with an auto-sliding "Made for you" hero of big cover art seeded from your own taste, replacing the old Moods & genres/Fresh drops buttons.
- Redesigned Liked, Downloads, Top 50, and On device: a dynamic cover collage at the top, a floating header that fades as you scroll, Shuffle/Play/More pills, and a details sheet with download-all and add-to-queue actions.
- Search's default results page now shows YouTube Music's own Top result, Songs, Videos, Albums, Artists, and Playlists sections together on one page, with like/download status on every song and video row.
- Tapping search now slides the screen up to the search field and restores your position when you close it, across Library, Downloads, Liked, On device, and playlists.
- Added a distinct "on this device" indicator for local files, kept separate from "downloaded," in Now Playing and every song list.
- Bulk downloads now show one combined progress notification instead of one per song, and it disappears on its own when the batch finishes instead of leaving a message to dismiss.
- Added an "Online, enjoy limitless music" notice when your connection comes back, and the offline notice now clears itself after a few seconds instead of staying up.
- Added this changelog popup, shown once after an update; a first-time install still sees the usual support/about screen instead.
- Fixed: pressing Enter right after typing in search could show stale suggestions instead of your results; downloading the currently-playing song restarting it from the beginning; skipping to the next/previous song leaving playback paused; skipping past the last song in a queue doing nothing; a playback failure now shows a message instead of failing silently.
- Now Playing and mini-player titles scroll at a slower, more readable pace when they don't fit.

## MuseFlow Beta v1.3.1

- Follow the device's system font, with stronger title, body, and label weights for readability.
- Make Home's History, Listen Together, Stats, and Settings icons white and visible at compact widths.
- Search regular YouTube videos alongside YouTube Music songs and videos, including uploads absent from the Music catalog, while playing their audio through the existing resolver.
- Open Liked, Downloads, Top 50, and On device in dedicated Library destinations with cover headers and actions suited to each collection.

## MuseFlow Beta v1.3.0

- Adapt the Stitch MuseFlow Liquid Glass colors, library cards, bottom navigation, mini player, search discovery grid, stats controls, history filters, and player actions to native Compose, while following the device font.
- Add working Listen Together room creation, joining, host approval, and playback synchronization using an Echo-compatible JSON WebSocket server.
- Search both YouTube Music song and video pages, and open a pasted YouTube video link as an audio track.
- Track each actual playback event for period stats and reliable history, including automatic queue advances; preserve older lifetime totals.
- Make offline recovery follow shuffle and repeat order, prefer newly downloaded local files, and stop clearly when no playable track remains.
- Keep partial downloads from destroying completed files, bound parallel transfers, and show download failures.
- Preserve imported playlist identity across repeated imports and show playlists in Downloads as soon as one song is available offline.
- Keep imported playlist identity through library backup and restore.


## MuseFlow Beta v1.2.0 🎵

An offline-first reliability and library update.

### Downloads & offline playback
* Fully downloaded playlists now appear as playlist cards in Downloads, while individual files
  remain available under **All downloaded songs**.
* Online playlists now have a direct **Download playlist** action. It saves the playlist to the
  Library and downloads its tracks as one understandable workflow.
* MuseFlow now observes Android's validated connectivity state and shows a compact offline status
  pill instead of waiting for each network request to time out.
* When an offline queue reaches a network-only track, playback skips ahead to the next downloaded
  item. If none remains, it stops cleanly and keeps the queue available for retry.
* Autoplay and new downloads no longer start while the device is offline.

### Search
* Song search now combines YouTube Music's Songs and Videos result sets, so covers, live sessions,
  and uploads with no separate audio release can be found and played through the existing audio
  stream resolver.

---

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
