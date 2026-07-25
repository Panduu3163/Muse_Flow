# MuseFlow vs Echo-Music — full gap audit

Written 2026-07-25. Supersedes the "not delivered" section of `progress-vs-plan.md`, which
**significantly understated the gap** — it listed ~25 remaining items. The real figure is ~90+.

Counted from source at Echo commit `ad66171`.

---

## 1. The real scale difference

| Metric | MuseFlow | Echo | Ratio |
|---|---|---|---|
| Kotlin files | 91 | 625 | 6.9× |
| Lines of Kotlin | 12,789 | 146,881 | 11.5× |
| **Screens** | **7** | **87** | **12.4×** |
| **Settings options** | **25** | **290** | **11.6×** |
| **Context menus** | **1** | **21** | **21×** |
| ViewModels | 10 | 30 | 3× |
| DB entities | 6 | 28 | 4.7× |
| Gradle modules | 2 | 14 | 7× |

The earlier report's "8 groups / 27 options, Phases 1–4 complete" was true but misleading: those
four phases covered **settings depth**, not **feature breadth**. Breadth is where the real gap is.

---

## 2. Gap by feature area

Legend: ✅ done · ⚠️ partial · ❌ missing · 🔒 deliberately excluded

### 2.1 Browsing & discovery — the biggest gap
| Feature | Echo | MuseFlow | Backend ready? |
|---|---|---|---|
| Home feed | ✅ 8 section types | ⚠️ 5 sections | — |
| **Artist page** | ✅ 4 screens (overview/albums/songs/items) | ❌ | `getArtistTracklist` exists |
| **Album page** | ✅ | ❌ | `searchAlbums` exists |
| **Explore / Browse** | ✅ 3 screens | ❌ | innertube browse ported |
| **Mood & genres** | ✅ dedicated screen | ⚠️ canned shelves | innertube |
| **Charts** | ✅ | ❌ | was built pre-wipe |
| **New releases** | ✅ | ❌ | `ArtistReleaseCheckWorker` exists |
| **Search filters** (songs/albums/artists/playlists tabs) | ✅ | ❌ | `MusicSearchRouter` has all 4 |
| Search suggestions | ✅ + regional | ✅ | — |
| **Similar/related** | ✅ | ⚠️ artist-name heuristic | innertube `related` |

**9 missing screens here alone.** Most have working backends.

### 2.2 Library
| Feature | Echo | MuseFlow |
|---|---|---|
| Library structure | ✅ **6 screens** (Songs/Albums/Artists/Playlists/Mix + root) | ⚠️ 1 screen, 5 chips |
| **Sort / filter / list-grid view** | ✅ 8 sort enums, 4 filter enums | ❌ (`LibrarySorting` unused) |
| Playlist detail | ✅ 5 variants (local/online/auto/top/cache) | ⚠️ 1 |
| **Queue editing / reorder** | ✅ `QueueMenu` | ❌ view only |
| **Multi-select batch ops** | ✅ `SelectionSongsMenu` | ❌ |
| **History screen** | ✅ | ❌ (chip only) |
| **Stats / listening insights** | ✅ `StatsScreen`, `StatPeriod` | ❌ |
| **Import playlist (CSV/file)** | ✅ + column mapping | ❌ |
| **Custom playlist thumbnail** | ✅ | ❌ |
| Local device files | ✅ 2 screens | ❌ (`LocalAudioProvider` unused) |

### 2.3 Context menus — 21 vs 1
Echo has dedicated menus for song, album, artist, playlist, queue, player, lyrics, YouTube
variants, multi-select, and custom thumbnail. MuseFlow has **one** `TrackActionsSheet`
(like/download/add-to-playlist).

Missing actions: play next, add to queue, go to album, go to artist, share, remove from playlist,
delete download, edit metadata, start radio, add to another playlist.

### 2.4 Player
| Feature | Echo | MuseFlow |
|---|---|---|
| Core transport, queue, seek | ✅ | ✅ |
| Lyrics | ✅ 6 providers, 9 animation styles, karaoke | ⚠️ 2 providers, 1 style |
| **Romanization** | ✅ 11 languages | ❌ |
| **Translation** | ✅ (DeepL) | ❌ |
| Equalizer | ✅ + Axion circular UI | ✅ 7-band DSP |
| **Crossfade / gapless** | ✅ | ❌ |
| **Audio normalization** | ✅ | ❌ |
| **Spatial audio / bass boost / virtualizer / crossfeed** | ✅ | ❌ |
| **Sleep timer** | ✅ | ❌ |
| **Speed / pitch** | ✅ | ❌ |
| **Swipe gestures** | ✅ 4 keys | ❌ |
| **Ambient / AOD mode** | ✅ | ❌ |
| **Comments** | ✅ | ❌ |
| **Ringtone maker** | ✅ | ❌ |
| **SponsorBlock** | ✅ | ❌ |

### 2.5 Settings screens — 21 vs 1
| Echo screen | MuseFlow |
|---|---|
| AppearanceSettings (2,053 LOC) | ⚠️ 1 group |
| PlayerSettings (1,193) | ⚠️ 1 group |
| ContentSettings (1,258) | ❌ |
| StorageSettings (565) | ❌ |
| **SearchableSettings** (458) | ❌ |
| ThemeScreen (459) | ⚠️ inline |
| BackupAndRestore (332) | ❌ (`BackupRepository` unused) |
| PrivacySettings / UpdateSettings / About / Uptime / EchoExtractor | ❌ |
| AccountSettings / LastFM / ListenBrainz / Discord / AI / Lossless / Romanization / GlassEffect | ❌ / 🔒 |

### 2.6 Accounts & sync
| Feature | Echo | MuseFlow |
|---|---|---|
| **YouTube Music login + library sync** | ✅ (`innerTubeCookie`, 5 sync timestamps) | ❌ |
| **Proxy support** | ✅ 6 keys | ❌ |
| Last.fm / ListenBrainz scrobbling | ✅ | ❌ |
| Spotify import | ✅ | ❌ (needs your Client ID) |

### 2.7 Platform surfaces
| Feature | Echo | MuseFlow |
|---|---|---|
| **Home-screen widget** | ✅ 7 files | ❌ |
| **Quick Settings tile** | ✅ | ❌ |
| **Android Auto** | ✅ (media session) | ⚠️ session exists, untested |
| **In-app updater** | ✅ | ❌ |
| Crash screen | ✅ | ⚠️ handler, no UI |
| Onboarding | ✅ `WelcomeDialog` | ❌ (deleted in wipe) |

### 2.8 Deliberately excluded 🔒
Discord RPC (~20 keys), ListenTogether (~12), Google Cast, Shazam recognition, AI recommendations,
Canvas video, Lossless FLAC. These match earlier scope decisions and stay out.

---

## 3. Honest tally

| Bucket | Count |
|---|---|
| ✅ Done | ~30 |
| ⚠️ Partial | ~12 |
| ❌ Missing (in scope) | **~90** |
| 🔒 Excluded | ~10 |

My previous report implied ~25 remaining. The realistic in-scope backlog is **~90 items**, of which
**~20 have working backends already in the codebase**.

---

## 4. Adoption plan — maximum uptake, minimum breakage

Ordered so nothing destabilises what already works. Each stage is independently shippable.

### Stage A — free wins (backends exist, UI only) · ~7 items
Library sort/filter/view · Backup & restore · On-device files · Followed artists + release alerts ·
History screen · Search filters UI · Charts.
*No new dependencies, no schema change, no playback risk.*

### Stage B — context menus & queue · ~10 items
Expand `TrackActionsSheet` into per-entity menus (song/album/artist/playlist/queue). Add play-next,
add-to-queue, go-to-album/artist, share, remove, delete download. Queue reorder + multi-select.
*Pure UI on existing data. Highest daily-use payoff per line of code.*

### Stage C — DB expansion, then entity screens · ~8 items
Grow 6 → ~20 entities (Song/Album/Artist + junctions). **Must precede** artist/album pages, or they
refetch everything. Then Artist (×4), Album, Explore/Browse/Mood, New releases.
*The one structural change. Do it in a single migration, verified, before the screens.*

### Stage D — player depth · ~12 items
Sleep timer · speed/pitch · swipe gestures · crossfade/gapless · normalization · quality selectors ·
Bluetooth/mute behaviours · more lyrics providers · lyrics animation styles.
*Note: normalization/bass-boost via `AudioEffect` will hit the same AudioFlinger refusal as the old
equalizer — they belong in the DSP processor chain instead.*

### Stage E — settings breadth · ~15 items
Split into dedicated screens (Appearance/Player/Content/Storage/Privacy/About) + **SearchableSettings**.
*Do the search scaffold early in this stage; retrofitting it later is the expensive path.*

### Stage F — platform surfaces · ~6 items
Widget (Glance) · Quick Settings tile · onboarding · in-app updater · crash screen · Android Auto verify.

### Stage G — accounts & external · ~8 items
YTM login + library sync · proxy · Last.fm/ListenBrainz · Spotify import (needs your Client ID).

---

## 5. What to avoid copying

- **Their module split** (14) — unnecessary at this scale.
- **Hilt** — would touch every surviving backend file for little single-module gain.
- **Liquid glass** — 9 tuning keys for one visual effect.
- **Duplicated "New/Old" designs** (`useNewPlayerDesign`, `OldPlayerMenu`) — legacy carry-over, not
  something to reproduce.
- **Romanization (11 languages)** — needs transliteration libraries; low payoff for the cost.

---

## 6. Realistic framing

Echo is ~147k lines from many contributors over a long period. MuseFlow is 12.8k, single-developer.
Closing the *whole* gap isn't the goal — Stages A and B alone (~17 items, mostly UI over existing
data) would deliver most of the day-to-day feel, and Stage C is the one structural investment that
unlocks the rest.

The non-negotiable rule still applies: **a feature ships when it works on device, not when it
compiles.** 14 real bugs this session all compiled cleanly and looked correct in review.
