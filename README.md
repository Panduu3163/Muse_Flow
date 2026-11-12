<div align="center">
<img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp" alt="MuseFlow app icon" width="112">
<h1>MuseFlow</h1>
<p><strong>Music that moves with you.</strong></p>
<p>An ad-free Android music player for streaming, downloads and songs on your device.</p>

[![Android checks](https://github.com/Panduu3163/Muse_Flow/actions/workflows/android.yml/badge.svg)](https://github.com/Panduu3163/Muse_Flow/actions/workflows/android.yml)
[![Secret scan](https://github.com/Panduu3163/Muse_Flow/actions/workflows/secrets.yml/badge.svg)](https://github.com/Panduu3163/Muse_Flow/actions/workflows/secrets.yml)
![Android 7+](https://img.shields.io/badge/Android-7.0%2B-3DDC84)
![Development Beta](https://img.shields.io/badge/development-1.8.0--beta%20(62)-F59E0B)
[![GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-64748B)](LICENSE)
</div>

MuseFlow combines background playback, offline downloads, a local music library, lyrics and customizable player controls. It uses Kotlin, Jetpack Compose and Media3.

> **Development status:** this source includes **1.8.0-beta (62)**. It remains a Beta with some hardware and platform checks pending. Published downloads are listed separately on [GitHub Releases](https://github.com/Panduu3163/Muse_Flow/releases); a source update does not mean a new APK release has been published.

[What's new](#whats-new-in-18-beta) · [Features](#features) · [Install](#installation) · [Build](docs/BUILDING.md) · [Verification](docs/VERIFICATION.md) · [Security](SECURITY.md)

## Screenshots

These screenshots illustrate existing app screens; they are not a claim that every image was recaptured from build 62.

<table>
  <tr>
    <td align="center"><strong>Home</strong><br><br><img src="docs/screenshots/Home.jpg" alt="MuseFlow Home" width="220"></td>
    <td align="center"><strong>Player</strong><br><br><img src="docs/screenshots/Player.jpg" alt="MuseFlow player" width="220"></td>
    <td align="center"><strong>Player styles</strong><br><br><img src="docs/screenshots/Player_2.jpg" alt="Another player appearance" width="220"></td>
  </tr>
  <tr>
    <td align="center"><strong>Lyrics</strong><br><br><img src="docs/screenshots/Lyrics.jpg" alt="MuseFlow lyrics" width="220"></td>
    <td align="center"><strong>MuseChart</strong><br><br><img src="docs/screenshots/MuseChart.jpg" alt="Music charts" width="220"></td>
    <td align="center"><strong>Library</strong><br><br><img src="docs/screenshots/Library.jpg" alt="MuseFlow library" width="220"></td>
  </tr>
</table>

## What's new in 1.8 Beta

- Preview backups before restoring. Optionally include listening history and portable settings; matching library items merge without creating duplicate playlists.
- Keep latest/previous internal recovery copies and verified external backups in a chosen folder.
- Manage durable download requests with visible failures, explicit retry after restart, removal and guarded storage cleanup.
- Restore the queue paused, use a square cover-art widget, and pause/open the player through Quick Settings.
- Receive clear CSV/M3U import results, including duplicates, unmatched tracks and cancellation.
- Reach player controls in landscape and at large text sizes. Unknown track durations no longer produce misleading totals.
- Show an estimated average bitrate with a `~` marker when the active stream omits a declared rate.

The hold-and-slide navigation gesture and selectable Glass navigation style are **not implemented**. This update retains the current frosted navigation.

Read the [changelog](CHANGELOG.md) and [verification details](docs/VERIFICATION.md).

## Features

| Area | Included behavior |
| --- | --- |
| Playback | Background audio, queue, shuffle/repeat, speed, sleep timer, media controls and explicit resumption |
| Offline | Downloads, device audio, durable request status, retry and separate cache cleanup |
| Library | Likes, playlists, followed artists, history, stats, sorting, search and view preferences |
| Discovery | Music search, general YouTube uploads, recommendations, charts, releases and artist/album pages |
| Lyrics | Multiple sources, synchronized lyrics where available, timing and appearance options |
| Appearance | Follow system, Light, Dark and AMOLED; fonts, density, accents, player and progress styles |
| Audio | Equalizer, volume smoothing, track fades, bass boost and crossfeed |
| Utilities | Backup/restore, playlist import/export, crash reports and visible update outcomes |

Volume smoothing is peak-based processing; track fades are sequential fades. Guaranteed gapless output, measured loudness normalization and true overlapping crossfade are not established features. Lyrics and online availability depend on upstream services. Listen Together requires a compatible server.

## Installation

Get a published APK from [Releases](https://github.com/Panduu3163/Muse_Flow/releases). Android 7.0 / API 24 or newer is required.

For an existing Beta installation, use a newer **Beta APK signed with the same certificate** to retain app data. Regular and Verification variants are separate applications. Keep an exported library backup before a major upgrade; metadata backups do not include downloaded audio.

An in-app update opens Android's installer. Installer handoff is not proof of completed installation, and the public GitHub API can be rate-limited. Manual release downloads remain available when update checks fail.

## Development and verification

See [BUILDING.md](docs/BUILDING.md) for JDK 21, compile SDK 37.1, signing and variant setup. The current playback features do not need a developer-owned API key.

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug --no-daemon --max-workers=1
```

At the device-tested checkpoint, **482 automated tests passed** and lint reported **zero errors**. Device checks covered a Xiaomi phone on Android 16; other hardware/platform and recovery cases remain pending. [Full coverage and limitations](docs/VERIFICATION.md)

CI runs tests, lint, a debug build and secret scanning. CI uses an ephemeral signing key and never publishes a release APK automatically.

For changes, read [CONTRIBUTING.md](CONTRIBUTING.md). Report reproducible problems through [Issues](https://github.com/Panduu3163/Muse_Flow/issues), including app version, Android version and launcher. Remove private details from logs and screenshots. Report security problems privately using [SECURITY.md](SECURITY.md).

## How it works

Kotlin/Compose provide the interface; Media3 handles playback; Room and DataStore hold local state. The included InnerTube module, YouTube client integrations and NewPipeExtractor resolve online sources. Lyrics are fetched separately from audio.

MuseFlow does not host audio or provide an official YouTube service. These unofficial integrations can change or stop working by track, region or upstream update. A third-party service's public web-client constants are distinct from private application credentials; see the [credential audit](docs/SECURITY-AUDIT.md).

## Credits and license

MuseFlow benefits from open-source work and research by [Metrolist](https://github.com/MetrolistGroup/Metrolist), [SimpMusic](https://github.com/maxrave-dev/SimpMusic), [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor), [zemer-cipher](https://github.com/ZemerTeam/zemer-cipher) and [Echo Music](https://github.com/EchoMusicApp/Echo-Music), alongside the lyrics providers and library maintainers referenced in the source.

MuseFlow is licensed under [GPL-3.0](LICENSE). Dependencies and adapted components retain their respective licenses and attribution. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and the bundled notices.

## Developer

**Mynul Kabir Nayem** · [@Panduu3163](https://github.com/Panduu3163)
