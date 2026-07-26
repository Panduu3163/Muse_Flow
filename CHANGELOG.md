# Changelog

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

---

## MuseFlow Beta v1.1.2

* **First-run onboarding screen**: shown once on a fresh install and once again after every update, matching Echo Music's own "show once per version" pattern - an app intro card, a hobby-project/bug-report notice with a one-tap email link, a muted "Star the Repo" action, and a solid "Continue" button.
* **Karaoke lyrics sweep smoothed further**: the in-progress word's highlight now animates on Compose's own frame clock instead of being sampled from the player's polled position, so it reads as continuous rather than stepped.
* **General playback-state performance pass**: the periodic position tick no longer rebuilds the entire queue/metadata state twice a second - only the position itself updates on tick, cutting real per-second allocation/recomposition work while anything plays.
* **Queue swipe-to-remove**: replaces the old per-row "⋮" menu - swipe a queued track left-to-right to remove it, with the list reflowing smoothly instead of snapping; fixed a key-reuse bug where a removed row's swipe state could visually "stick" to whatever song took its place.
* **Custom accent color picker fixed**: the saturation/vibrancy square now actually affects the generated theme (previously only hue did, due to the color-scheme generation style ignoring the seed's chroma).
* Assorted Now Playing menu cleanup: removed duplicate queue/like/download actions from the track menu (already available as dedicated on-screen controls), folded the lyrics panel's copy/search actions into the main menu, and fixed the song-details dialog not opening.
