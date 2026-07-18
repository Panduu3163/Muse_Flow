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

It also relies on unofficial/reverse-engineered access to some music platforms' internal APIs (details below), which exists in a legal gray area regarding those platforms' Terms of Service. This is the same trade-off made by several well-known open-source music apps this project draws inspiration and code from. Use accordingly.

---

## ✨ Features

### 🎧 Playback
- Background playback with a real, controllable media notification (play/pause, cover art)
- Offline downloads with real download progress notifications, for on-the-go listening
- Local device file playback alongside streaming — toggle search between Online and On-Device
- Automatic fallback across sources if one is down or has no results

### 🔍 Discovery
- Search across **Songs, Albums, Artists, and Playlists**
- Results merged and deduplicated across multiple sources automatically
- Recent search history
- Real artist pages, including monthly listener counts
- Real Home feed shelves (Recently Played, mood/genre-based shelves) — cached for offline viewing, auto-refreshes when you're back online

### 🎤 Lyrics
- Real-time synced lyrics, scrolling in time with playback
- Word-by-word lyric highlighting where available
- Multiple lyrics sources with automatic fallback for better coverage

### 🎨 Personalization
- First-launch onboarding with a custom display name and profile photo
- AMOLED (true black) and Gradient theme modes, with selectable color palettes
- Deep Appearance/Player/Lyrics customization options

### 📚 Library
- Liked Songs, Downloaded tracks, Recently Played — all backed by real local data, nothing hardcoded
- Create and manage your own playlists

---

## 🛠️ Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Playback | Media3 / ExoPlayer |
| Architecture | MVVM, Hilt (DI), Kotlin Coroutines & Flow |
| Local Storage | Room, DataStore Preferences |
| Networking | Retrofit, OkHttp |
| Images | Coil |

### How music sourcing works

MuseFlow doesn't host or own any music. It resolves playable audio through a **provider-chain architecture** — multiple independent sources, tried and merged so no single point of failure takes down the app:

- **JioSaavn** — primary catalog source, public API
- **YouTube Music** — a full authenticated streaming pipeline (visitor identity, BotGuard proof-of-origin token generation, signature/cipher deobfuscation) for access to YouTube's much broader catalog
- **LRCLib + BetterLyrics** — synced lyrics, with automatic fallback between sources for better coverage

Each source is isolated behind a shared `Provider` interface, so if one breaks (which does happen — these are unofficial integrations reacting to platform changes), the others keep the app functional. Search results are merged and deduplicated across sources automatically.

---

## 🙏 Credits & Acknowledgements

MuseFlow wouldn't exist without the open-source music-client community. Significant logic, architecture patterns, and research in this project were adapted from:

- [Metrolist](https://github.com/MetrolistGroup/Metrolist) — reference implementation for YouTube Music integration
- [zemer-cipher](https://github.com/ZemerTeam/zemer-cipher) — YouTube cipher deobfuscation and PoToken generation
- [SimpMusic](https://github.com/maxrave-dev/SimpMusic) — cross-reference for YouTube Music streaming
- [Echo Music](https://github.com/EchoMusicApp/Echo-Music) — architectural inspiration (provider-chain/fallback pattern, feature set)
- [LRCLib](https://lrclib.net) — synced lyrics API
- [Better Lyrics](https://github.com/better-lyrics/better-lyrics) — lyrics fallback source

Genuine thanks to the maintainers of these projects for their work being open enough to learn from.

---

## 📦 Getting the App

This is currently a personal build, not published to any app store. To build it yourself:

**Prerequisites:** Android Studio (or the Android SDK + JDK 17+ directly), Kotlin 2.2+

1. Clone this repo
2. Open in Android Studio and let it sync
3. Build a debug APK: `./gradlew assembleDebug`
4. Install on your device

---

## 🚧 Roadmap

- [ ] Deeper Now Playing and Library UI polish
- [ ] Smooth animations and transitions throughout the app
- [ ] Playlist covers (auto-generated collages), playlist import from online sources
- [ ] Additional music source integrations
- [ ] Listen Together (real-time synced listening sessions)
- [ ] Higher audio quality tier for YouTube-sourced tracks

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
