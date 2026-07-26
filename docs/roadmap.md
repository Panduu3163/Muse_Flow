# MuseFlow Roadmap — done, and what's next

Living document. Supersedes the older `reference-analysis.md` research memo for "what's left" —
that file stays as the original research record (repo comparisons, licensing notes), this one
tracks actual status and next steps.

**Explicitly out of scope going forward, by product decision, not technical difficulty:**
Song identification (Shazam-like), Google Cast/Chromecast SDK, and Listen Together (real-time
synced playback). All three are cut for the same reason: they require either a paid third-party
API (AudD/ACRCloud for song ID) or standing backend infrastructure with real hosting cost
(a WebSocket relay server for Listen Together). Cast itself has no license fee but pulls in the
Play Services Cast SDK and a route-picker UI for a feature with unclear payoff for a
free/ad-free personal project — parked alongside the other two rather than pursued alone.

---

## Done

### Dependency base (this session)
Compose BOM 2024.09.00 → 2026.06.01 (Material3 into the 1.4 "Expressive" API surface), Kotlin
2.2.10 → 2.4.10, AGP 9.1.1 → 9.3.0 (Gradle wrapper 9.3.1 → 9.6.1 to match), Room 2.7 → 2.8.4,
Navigation Compose 2.8.9 → 2.9.8, Lifecycle 2.8.7 → 2.11.0, Retrofit 2.12 → 3.0, OkHttp 4.10 →
5.4, compileSdk/targetSdk 36 → 37 (platform 37.1 installed locally), plus DataStore, WorkManager,
CameraX, Credentials, Firebase BOM, and Roborazzi bumped to latest stable. Verified via a full
clean `assembleDebug` + `testDebugUnitTest` pass, not just editing version numbers.

### Material3 1.4 polish pass (this session)
Predictive back gesture enabled (`android:enableOnBackInvokedCallback`), `TabRow` →
`SecondaryTabRow` in Search and the Add-Songs dialog, and RTL-correct `Icons.AutoMirrored.*` swapped
in across 8 files (the app declares `supportsRtl="true"`, so this was a real fix, not cosmetic).

### Test coverage + a real bug fix (this session)
Extracted the pure logic out of four monolithic singletons so it's actually unit-testable —
`ResolvedUrlCache` (TTL/LRU math out of `YouTubeStreamResolver`), `RefreshCooldownGate` (out of
`YtCipherConfigStore`), `poTokenSessionNeedsRecreate` (out of `PoTokenGenerator`), and the
backup JSON round-trip functions in `BackupRepository` — then wrote 35 new tests against them.
Writing the backup round-trip tests caught a real, shipped bug: `org.json.JSONObject.NULL`'s
`toString()` is the literal string `"null"`, so `optString(name).takeIf { it.isNotBlank() }`
restored the *string* `"null"` instead of an actual null for any backed-up track with a missing
`imageUrl`/`streamUrl`/`sourceId`/`sourceType`/playlist `coverImageUrl` — fixed with a proper
`JSONObject.isNull()` check. Also caught and fixed a latent bug in the cooldown-gate logic itself
(a `0L` "never happened" sentinel that only worked by accident because real wall-clock epoch
millis are always huge).

### Everything from the original repo-comparison roadmap (`reference-analysis.md`), confirmed
implemented by reading the actual code, not just filenames:
- **Speed**: shared OkHttp client (`YtHttpClients`), merged category search, PoToken session
  reuse across tracks, TTL-based resolved-URL caching against the real server-declared expiry.
- **Stability**: global crash handler, bounded WebView-failure backoff, a remote-refreshable
  cipher config table (bundled + 6h-TTL background overlay).
- **UI/UX**: shimmer/skeleton loading, palette-driven Now Playing background, a hand-rolled
  physics-based drag mini-player, springy "morphing" play/pause and nav-icon feedback.
- **Larger features**: sleep timer, a full equalizer, and auto-backup/restore (WorkManager-driven).

---

## What's next, ranked

Everything below avoids the three excluded features and anything else that needs a paid
third-party API or standing server infrastructure to work at all.

### Small, high-confidence
1. **Home-screen widget (Glance).** A compact now-playing card — `GlanceAppWidget` +
   `AppWidgetReceiver` + a layout, wired to `PlaybackService`'s existing playback state. No new
   paid dependency; Glance ships in Jetpack. Reference: ArchiveTune's `widget/NowPlayingCardWidget.kt`
   and Metrolist/Echo-Music/SimpMusic/vivi-music/Flow all have an equivalent.
2. **Always-On-Display-style ambient Now Playing view.** A dim, lock-screen-like view for when
   the screen would otherwise just show the media notification — low effort, no new dependency.
   Reference: ArchiveTune's `ui/player/AodPlayerScreen.kt`.
3. **`FloatingBottomBar`'s scroll-away-on-scroll behavior**, using the real M3
   `HorizontalFloatingToolbar`'s `scrollBehavior` now that it's actually available — the bar hides
   while scrolling down a long Home/Search/Library list and reappears on scroll-up, rather than
   staying pinned. Deliberately skipped in the earlier polish pass since it wasn't a clear win for
   a plain 4-tab bar with no FAB; worth reconsidering once there's a genuinely long-scrolling
   screen where it'd matter (Search results, a big playlist).
4. **RSS / new-release notifications** for followed artists — local WorkManager polling against
   data already fetched for artist pages, no new backend. Reference: SimpMusic, vivi-music.

### Medium
5. **Charts/Discover screen**, consuming Echo Charts' already-public, already-hosted API
   (`https://stats.echomusic.fun/api/v1`) directly rather than replicating its aggregation —
   just a new screen + HTTP client, no backend build-out. The one open item before shipping this
   isn't cost, it's confirming acceptable-use terms with that API's owner first (its client repo
   has no LICENSE file, though the API itself is public).
6. **Multi-account switching**, if MuseFlow ever supports more than one signed-in identity per
   provider. Reference: ArchiveTune's `AccountSettings.kt`.
7. **Native M4A cover-art/metadata embedding for downloads** — currently downloads presumably
   don't embed art/tags into the file itself. Reference: `metrolist-coverart-lib` (NDK/CMake +
   JNI) — but it bundles Bento4, which is GPLv2, not v3; check GPLv2/v3 interaction before
   depending on it, or reimplement the embedding without that specific library.
8. **Clean per-feature nav-graph split**, if `MainActivity.kt`'s single `NavHost` starts feeling
   unwieldy as more destinations get added — SimpMusic's `AppNavigationGraph.kt` pattern
   (`homeScreenGraph()`, `libraryScreenGraph()`, ... as separate composable graph builders) is a
   pure refactor, no new dependency.

### Larger, no hard cost but a real scope decision each
9. **Canvas video (Spotify-style looping backgrounds).** Needs a content pipeline (where do the
   loop videos come from per track?) more than money — no paid API is strictly required, but
   someone has to source/host/maintain the video registry, which is an ongoing content-ops
   commitment, not a one-time build. Worth a dedicated scoping conversation before starting.
10. **AI recommendations + Spotify playlist import.** Spotify's own API has a free developer
    tier (OAuth-gated, no cost), so the import half is viable without money; the recommendation
    half needs either a real ML model or enough heuristic engineering to be worth shipping —
    scope that half separately and possibly ship import-only first.

---

## Verification note

Everything marked "Done" above was confirmed by reading the actual current source, not by trust
in a filename or an earlier session's notes, and by running `./gradlew clean :app:assembleDebug
:app:testDebugUnitTest` — both green as of this doc's writing. No emulator/device is attached in
this environment, so on-device/visual verification (predictive-back swipe feel, tab indicator
look, widget layout once built) is still worth a manual pass in Android Studio before shipping.
