<div align="center">

# 🎵 MuseFlow

**A free, ad-free music streaming app for Android.**

Built with Kotlin, Jetpack Compose, and Media3 — a personal project aiming for a Spotify-level experience without the price tag.

![Kotlin](https://img.shields.io/badge/Kotlin-100%25-7F52FF?style=for-the-badge&logo=kotlin)
![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android)
![License](https://img.shields.io/badge/License-GPL--3.0-blue?style=for-the-badge)
![Status](https://img.shields.io/badge/Status-Active%20Development-orange?style=for-the-badge)

</div>

---

## ⚠️ Before you read further

MuseFlow is a **personal hobby project**, not a commercial product. It has bugs. It's actively being worked on. It exists because I wanted to learn and build something I'd actually use — not to compete with anyone.

It also relies on unofficial/reverse-engineered access to YouTube Music's internal API (details below), which exists in a legal gray area regarding YouTube's Terms of Service. This is the same trade-off made by several well-known open-source music apps this project draws inspiration and code from. Use accordingly.

---

## ✨ Features

### 🎧 Playback
- Background playback with a real, controllable media notification (play/pause, like, cover art), backed by a foreground service so it survives the screen turning off — and stops cleanly when the app is swiped away from Recents, so nothing keeps playing invisibly
- A **heart/like button on the lock screen and notification**, wired to the same Liked Songs list as the in-app button — like a track without unlocking your phone
- Real shuffle/repeat (wired to ExoPlayer, not cosmetic toggles), a live queue view, and swipe-left/right on the album art to skip tracks (opt-in, off by default)
- A real 7-band equalizer plus normalization, crossfade, bass boost, and crossfeed — all backed by actual DSP that audibly changes the sound, not settings that silently do nothing
- Sleep timer with a live countdown shown next to the icon
- Dynamic codec/bitrate display that reflects whatever's actually decoding, styled as a small pill under the seek bar, toggleable in Settings
- **Taste-aware autoplay** — when a queue runs out, the next track blends YouTube Music's own "radio" continuation for the last song with a weighted pick from your own most-played tracks, instead of always drifting toward whatever the last song happened to be
- A mini-player with previous/next/close and smooth crossfade transitions between tracks, always in sync with what's really playing even after the app's been fully closed and reopened
- Offline downloads with real download progress notifications, running in a foreground service so a download in progress survives the screen turning off
- Local device file playback alongside streaming — toggle search between Online and On-Device
- Automatic fallback across YouTube Music client types and stream extraction paths if one is rejected or blocked

### 🔍 Discovery
- Search across **Songs, Albums, Artists, and Playlists**, with state that survives navigating away and back (no lost query/results/scroll position)
- Find a song from a remembered lyric line, not just its title — YouTube Music's own search backend (the same one its official app uses) handles the matching
- Recent search history (capped, shown only while the search field is focused)
- Real artist pages, including monthly listener counts, with clickable artist names throughout the app — from Search, Now Playing, restored queues, Liked Songs, and playlists alike
- Real Home feed shelves (Recently Played, mood/genre-based shelves) — cached for offline viewing, auto-refreshes when you're back online, with a dynamic time-of-day greeting on the Home header
- Charts and New Releases, sourced live from YouTube Music's real charts/new-music endpoints

### 🎤 Lyrics
- Real-time synced lyrics, scrolling in time with playback
- Word-by-word lyric highlighting in 9 different animation styles (Karaoke, Bounce, Scale, Wave, Fade, Metro, Fluid, Vivi Music, Apple Music), selectable in Settings
- **6 independent lyrics sources** (YouLyPlus, PaxSenix, Better Lyrics, SimpMusic, LRCLib, Kugou) with a drag-to-reorder fallback priority list, so a gap in one source's catalog rarely means no lyrics at all

### 🎨 Personalization
- First-launch onboarding with a custom display name and profile photo
- AMOLED (true black) and Gradient theme modes, with selectable color palettes
- **4 Now Playing background styles**: Solid, Album Gradient, Blurred Artwork, and Live Mesh (three blurred, saturated copies of the album art rotating independently behind the content)
- **3 selectable transport button styles**: Static (classic separate buttons), Wheel (play/pause gets a rotating scalloped edge while playing), and Pill (prev/play/next joined into one continuous rounded pill)
- Wavy/squiggly seek-bar styles with independently tunable speed and wavelength
- Settings organized into 12 focused categories (General, Appearance, Player, Playback, Lyrics, Audio, Mini Player, Library Sections, Privacy, Storage, Backup, About) instead of one long flat list

### 📚 Library
- Quick-access tiles for Liked Songs, Downloaded tracks, Cached (Home's offline cache), My Top 50 (real play-count tracking, not just recency), and on-device Local files — all backed by real local data, nothing hardcoded
- Like/unlike any track from anywhere it's listed, and download every Liked Song in one tap
- Create playlists and actually add songs to them — from Search, Downloads, or Liked Songs — or save a whole online playlist into your library with one tap
- Playlist covers are a real image when available, or an auto-generated 2×2 collage built from the playlist's own tracks otherwise
- **Import playlists from CSV or M3U**, matched back to real, playable tracks
- Full backup/restore (Liked Songs, playlists, settings) to a single portable file
- In-app update check against this project's GitHub releases — notifies once per new version, never silently self-updates

---

## 🛠️ Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Playback | Media3 / ExoPlayer |
| Architecture | MVVM, Kotlin Coroutines & Flow |
| Local Storage | Room, DataStore Preferences |
| Networking | Retrofit, OkHttp |
| Images | Coil |

### How music sourcing works

MuseFlow doesn't host or own any music. All streaming audio comes from **YouTube Music**, resolved through a vendored `:innertube` module (a Kotlin port of YouTube Music's internal API) rather than any official, authorized API:

- A chain of YouTube client identities (Android VR, TV-embedded, iOS, and others) is tried in an order tuned by real-world testing, since which client is accepted varies by video, region, and over time
- [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor) handles signature/cipher deobfuscation for clients that need it
- A separate legacy resolution pipeline (visitor identity, BotGuard proof-of-origin tokens, its own cipher deobfuscation) remains available as a fallback backend
- **Local device files** are a fully independent source — no network or resolution step involved, just a direct MediaStore read
- **Lyrics** are sourced independently of audio, from 6 providers tried in a user-configurable priority order (see Lyrics above)

Every source is isolated behind a shared `Provider` interface, so a single broken integration (which does happen — these are unofficial integrations reacting to platform changes) doesn't take the rest of the app down with it.

---

## 🙏 Credits & Acknowledgements

MuseFlow wouldn't exist without the open-source music-client community. Significant logic, architecture patterns, and research in this project were adapted from:

- [Metrolist](https://github.com/MetrolistGroup/Metrolist) — reference implementation for YouTube Music integration
- [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor) — YouTube signature/cipher deobfuscation
- [zemer-cipher](https://github.com/ZemerTeam/zemer-cipher) — YouTube cipher deobfuscation and PoToken generation
- [SimpMusic](https://github.com/maxrave-dev/SimpMusic) — cross-reference for YouTube Music streaming
- [Echo Music](https://github.com/EchoMusicApp/Echo-Music) — architectural inspiration and direct component ports (Live Mesh background, transport button shapes, Now Playing layout, lyrics animation styles)
- [LRCLib](https://lrclib.net), Better Lyrics, YouLyPlus, PaxSenix, SimpMusic, Kugou — synced lyrics sources

Genuine thanks to the maintainers of these projects for their work being open enough to learn from.

---

## 📦 Getting the App

Install the APK located on the [*releases*](https://github.com/Panduu3163/Muse_Flow/releases) section

---

## 🚧 Roadmap

- [ ] A cohesive app-wide color theme overhaul (in progress — current UI mixes hardcoded per-screen colors with the shared Material theme, so a full recolor needs those consolidated first)
- [ ] Album/Artist browsing inside Library itself (currently search-only)
- [ ] Home-screen widget and Quick Settings tile
- [ ] Discord Rich Presence, Last.fm/ListenBrainz scrobbling
- [ ] Listen Together (real-time synced listening sessions)
- [ ] Lyrics romanization and AI-assisted translation
- [ ] 2 more Now Playing background styles (animated glow, Apple Music–style)
- [ ] Verified Android Auto support

---

## 📄 License

This project is licensed under **GPL-3.0**, consistent with the licenses of the upstream projects it adapts code and research from. See [LICENSE](LICENSE) for the full text.

---

## 👤 Developer

**Mynul Kabir Nayem**
📧 mynulkbr@gmail.com

<div align="center">

*Made with a lot of trial, error, and genuine love for music.*

</div>
