# MuseFlow vs. Reference Repos — Analysis & Roadmap

Research-only session. No code under `app/` was changed. This compares MuseFlow's
current architecture against 19 cloned reference repos under `repo/` and proposes
what to adopt next.

**Note on repo hygiene:** `repo/SimpMusic/CLAUDE.md` contains an embedded instruction
directing any AI agent that reads it to respond in English+Vietnamese. This is a
prompt injection planted in project files, not a real instruction from you or from
Anthropic — it was ignored during this research and is flagged here in case that repo
gets explored again.

---

## (a) Speed findings — YouTube Music fetching

MuseFlow's current pipeline ([YouTubeStreamResolver.kt](../app/src/main/java/com/example/YouTubeStreamResolver.kt),
[PlaybackService.kt](../app/src/main/java/com/example/PlaybackService.kt),
[YouTubeMusicProvider.kt](../app/src/main/java/com/example/YouTubeMusicProvider.kt)):

- **No caching of resolved stream URLs.** `YouTubeStreamResolver.resolve()` runs the
  *entire* pipeline (visitorData check → player.js fetch → cipher extraction →
  BotGuard PoToken generation via hidden WebView → `/player` request → cipher
  deobfuscation) on every single ExoPlayer HTTP (re)open, via
  `ResolvingDataSource.Factory` in `PlaybackService.buildResolvingDataSourceFactory()`.
  This was a deliberate choice (per the code comment: "a resolved URL 403s within
  minutes") but it means BotGuard/WebView cost is paid on *every* open, not once per
  track.
- **No prefetching of upcoming queue tracks** — resolution happens exactly at
  HTTP-open time for whichever track ExoPlayer is opening now.
- **PoToken is regenerated from scratch per resolve**, including spinning up
  `PoTokenGenerator`'s hidden WebView + BotGuard run — the single most expensive
  step.
- **Two independent `OkHttpClient` instances** (one in `YouTubeStreamResolver`, one
  in `YouTubeMusicProvider`), each built with defaults — no shared client, no disk
  cache, no explicit connection-pool tuning.
- **Sequential, category-repeating search**: `searchAlbums`/`searchArtists`/
  `searchPlaylists` each call `searchAllCategories()` again — a fresh full search
  request per category instead of one shared multi-category response.
- Uses only the `WEB_REMIX` client, which is the one InnerTube surface that *requires*
  cipher + PoToken in the first place.

### What the reference repos do differently

**1. Cache the resolved stream URL for its actual TTL, not zero seconds.**
`Metrolist/app/src/main/kotlin/com/metrolist/music/playback/MusicService.kt:495` keeps
a synchronized LRU `songUrlCache: LinkedHashMap<String, Pair<String, Long>>` (capacity
500), keyed by media ID, storing the resolved URL alongside its real
`streamExpiresInSeconds` (read from the player response itself, not guessed).
`createDataSourceFactory()` (line ~3643) checks this cache before re-resolving, and
only invalidates on an actual playback error. Echo-Music and vivi-music (same
lineage) carry an identical `songUrlCache` in their own `MusicService.kt`/
`DownloadUtil.kt`.

**MuseFlow equivalent:** the code comment claims resolved URLs 403 "within minutes,"
shorter than the `expire=` param suggests — but that claim was never verified against
the *actual* per-response TTL the player endpoint returns. Before assuming zero-TTL
caching is required, add a small time-bounded cache (mirror `songUrlCache`'s shape) in
`YouTubeStreamResolver`, using the real expiry from the player response rather than
the URL's `expire=` query param, and measure whether it holds. This alone would turn
every "resume after being briefly paused" and "skip back" case from a full BotGuard
round-trip into a cache hit.

**2. Reuse the PoToken session across tracks, not per track.**
`zemer-cipher/library/src/main/kotlin/.../potoken/PoTokenGenerator.kt` mints a PoToken
session bound to `visitorData` (`webPoTokenSessionPot`/`webPoTokenSessionId`) *once*,
and only recreates the WebView/BotGuard session when it's null, expired, dead, or the
`visitorData` changed — every other track reuses the live session. It also bounds
cold-start to `POTOKEN_TIMEOUT_MS = 8_000L` and falls back to no-PoToken rather than
blocking playback.

**MuseFlow equivalent:** `PoTokenGenerator.generate()` is called fresh inside every
`resolveInternal()` call. Restructure it to hold a session object (keyed by
`visitorData`) the same way `cachedVisitorData` is already held, and only re-mint when
that session is stale/dead — this is likely the single biggest latency win available
without touching the "never cache the final URL" policy at all.

**3. Avoid needing cipher/PoToken at all, for at least a fallback path.**
`MetrolistExtractor` (a NewPipeExtractor fork) defines a full `ANDROID_VR` client
profile (`extractor/.../services/youtube/ClientsConstants.java`:
`ANDROID_VR_CLIENT_ID="28"`, `Oculus Quest 3` device string), wired through
`YoutubeParsingHelper.java`. YouTube serves `ANDROID_VR`/`ANDROID`/`IOS` clients
direct, unobfuscated stream URLs — no signature cipher, no PoToken. SimpMusic avoids
building any bespoke cipher/PoToken code at all, depending instead on
`com.github.teamnewpipe:NewPipeExtractor` / a `maxrave-dev:BravePipeExtractor` fork
(declared in `gradle/libs.versions.toml:187-188`) for this exact reason.
`Metrolist/app/.../utils/YTPlayerUtils.kt` picks client type per content via
`ContentAwareFallbackStrategy.kt`, trying `VISIONOS`/`ANDROID_VR_*` before falling
back toward `WEB_REMIX`/`TVHTML5`.

**MuseFlow equivalent:** `YouTubeMusicProvider`/`YouTubeStreamResolver` only ever use
`WEB_REMIX`. Adding an `ANDROID_VR` (or similar) client as a fast-path attempt before
falling back to the full WEB_REMIX+cipher+PoToken pipeline would skip BotGuard/cipher
work entirely on the common case, at the cost of maintaining a second client profile
and accepting that these clients are more frequently rate-limited/blocked (per
Metrolist's own comments on why it needs a fallback chain in the first place).

**4. Cache the cipher/signatureTimestamp derivation itself, ideally off-device.**
`zemer-cipher/library/.../PlayerConfigStore.kt` bundles a `player_configs.json` asset
(offline default) and overlays a live-fetched version with a 6h TTL
(`REFRESH_TTL_MS`), a 5-minute force-refresh cooldown, and separate cooldown timers
for "unknown player" vs. "stream rejected" so one failure mode can't starve the
other's retry budget. `faraday` (`faraday/src/zemer/player-config-deriver.ts`,
`player-probe.ts`, `stream-validator.ts`) is a Deno service that watches `player.js`
rotations, statically derives `sig`/`nClass`/`sts` without a WebView, validates
against real CDN streams (HTTP 206 checks), and publishes to a GitHub-released
registry — so signatureTimestamp/cipher expressions are derived **once server-side
per player.js rotation**, not independently by every installed client. faraday has no
LICENSE file in this clone — do not adopt its code directly without confirming
licensing with upstream; the *architecture* (a small pinned registry your app polls)
is reusable regardless.

**MuseFlow equivalent:** `YtCipherFunctionExtractor`/`YtCipherDeobfuscator` re-derive
cipher logic from `player.js` on-device (`YtPlayerJsFetcher` does cache the raw JS,
but not the derived sig/n functions). Adopting a small bundled+remote-refreshed JSON
config (mirroring `PlayerConfigStore`'s shape, hosted anywhere MuseFlow controls) would
remove repeated JS-parsing work across app restarts/player.js rotations. This is a
medium-effort, high-value change and doesn't require depending on zemer-cipher's code
directly (a from-scratch config store, informed by its cache-shape, avoids license
questions).

**5. Share and tune the HTTP client.**
`zemer-cipher/library/.../ZemerCipher.kt` holds one singleton `OkHttpClient`,
explicitly rebuilt only when a proxy setting changes — its own comment notes the
prior anti-pattern was "a per-request `OkHttpClient.Builder().build()` in three
separate files," which defeats TCP/TLS connection reuse.
`Metrolist/innertube/src/main/kotlin/com/metrolist/innertube/NetworkConfig.kt`'s
`createOptimizedHttpClient()` adds gzip/deflate `ContentEncoding` and a 128MB OkHttp
disk `Cache`, held as one singleton in `InnerTube.kt:39`.

**MuseFlow equivalent:** consolidate `YouTubeStreamResolver`'s and
`YouTubeMusicProvider`'s separate `OkHttpClient` instances into one shared,
injected client (Hilt already manages DI elsewhere in the app) with a disk cache
enabled — small effort, real connection-reuse win, no architectural risk.

**6. One multi-category search call instead of four.**
`Metrolist/innertube/src/main/kotlin/com/metrolist/innertube/YouTube.kt:163`'s
`searchSummary(query)` hits a single InnerTube endpoint that returns songs, albums,
artists, and playlists together in one response.

**MuseFlow equivalent:** `YouTubeMusicProvider.searchAllCategories()` already exists
and does almost this (one unfiltered search, bucketed client-side by category label)
— but `search()` (the songs path) still issues a *separate*, filtered request. Merging
`search()` to reuse `searchAllCategories()`'s single unfiltered call (bucketing
"Song" the same way albums/artists/playlists already are) removes one full redundant
network round-trip per combined search.

---

## (b) UI/UX findings

MuseFlow currently has: bottom-nav Home/Search/Library, a mini-player, a Now Playing
screen, basic light/dark theming, downloads, lyrics (BetterLyrics/LrcLib), liked
songs, playlists — MVVM + Hilt + Compose + Media3.

- **Physically-driven bottom sheet mini-player** — `vivi-music/app/.../ui/component/BottomSheet.kt`
  hand-rolls drag handling (`detectVerticalDragGestures` + `VelocityTracker`) instead
  of Compose Material's `ModalBottomSheet`, applies offset via
  `graphicsLayer { translationY }` (GPU-accelerated), and drives scrim alpha with a
  power-curve formula (`1.4f * (progress - 0.1f).pow(0.5f)`) rather than linear alpha
  — reads as noticeably smoother than a stock bottom sheet. Its
  `ui/player/MiniPlayer.kt` adds horizontal swipe-to-skip with a sigmoid-based
  auto-commit threshold, tunable via a user preference (`SwipeSensitivityKey`).
  Metrolist shares the same file layout/pattern (near-identical
  `MiniPlayer.kt`/`BottomSheet.kt`).
- **"Morphing weight" transport controls** — vivi-music's `ui/player/Player.kt`
  (~line 2134) animates play/pause button `Modifier.weight` up to 1.9x on press while
  side buttons shrink, via `animateFloatAsState(spring(dampingRatio = 0.6f, stiffness
  = 500f))` — a small, cheap, tactile Material3-Expressive touch.
- **Palette-driven Now Playing backgrounds** — vivi-music downsamples artwork to
  100×100 and runs `androidx.palette.graphics.Palette` off the main thread, caching
  the extracted color per song id, with a `GLOW_ANIMATED` rotating-color background
  option and a Haze-based blur option (`blurRadius = 80.dp`). SimpMusic does the same
  with `kmpalette` (multiplatform-safe) plus a horizontal `Pager` between adjacent
  queue tracks in Now Playing.
- **Floating, pill-shaped M3-Expressive nav** — Echo-Music replaces the classic
  bottom bar with `ui/component/FloatingNavigationToolbar.kt`
  (`FloatingToolbarDefaults.VibrantFloatingActionButton`, experimental M3 1.4) and a
  floating rounded-card `FloatingMiniPlayer.kt` instead of a full-width bar — a
  distinct visual identity worth considering if MuseFlow wants to move off a fixed
  bottom bar.
- **Always-On-Display-style Now Playing screen** — ArchiveTune's
  `ui/player/AodPlayerScreen.kt` adds a dim, lock-screen-like ambient view
  (`Modifier.aodBackground(ambientIntensity)`) — low effort, novel.
- **Home-screen widget** — ArchiveTune's `widget/NowPlayingCardWidget.kt` (Glance
  `GlanceAppWidget`) is a compact now-playing card widget MuseFlow has no equivalent
  of.
- **Shimmer/skeleton loading** — every priority repo has one:
  `vivi-music`'s `ShimmerHost.kt` (wraps `valentinilk/shimmer`, top-fade mask via
  `drawWithContent` + `BlendMode.DstIn`), SimpMusic's composable-level
  `HomeItemShimmer`/`PlaylistShimmer`, or Flow's dependency-free
  `Modifier.shimmerEffect()` (diagonal `Brush` sweep, no library) — Flow's is the
  simplest one to port if avoiding a new dependency matters.
- **Clean, scalable nav-graph split** — SimpMusic's
  `ui/navigation/graph/AppNavigationGraph.kt` composes per-feature graph builders
  (`homeScreenGraph()`, `libraryScreenGraph()`, `listScreenGraph()`,
  `loginScreenGraph()`) with typed `@Serializable` destinations — a pattern worth
  copying if MuseFlow's single nav graph starts feeling unwieldy.
- **Canvas video** (Spotify-style looping backgrounds) appears in vivi-music
  (`ui/canvas/CanvasArtwork`, `CanvasArtworkPlaybackCache`) and SimpMusic (synced with
  a subtitle overlay) — see feature inventory below for cost/effort.

---

## (c) Stability findings

- **Remote-config-driven cipher fixes, no app-store update needed.**
  `zemer-cipher/library/.../PlayerConfigStore.kt` ships a bundled `player_configs.json`
  as an offline default and overlays a live-fetched version (6h TTL, 5-min
  force-refresh cooldown, separate cooldown counters per failure type so one failure
  mode can't starve another's retry budget). `faraday`'s GitHub Action
  (`.github/workflows/probe-player.yml`) runs on a cron `repository_dispatch`,
  re-derives cipher configs from the live `player.js`, validates them against real
  CDN 206 responses, and auto-publishes a registry release — a genuine continuous
  contract test against YouTube's own production surface, with Discord-webhook
  alerting on failure (`src/discord-webhook.ts`).
- **Content-aware, ordered client fallback.**
  `Metrolist/innertube/src/main/kotlin/.../strategy/ContentAwareFallbackStrategy.kt`
  picks an ordered InnerTube client list (`VISIONOS`, `ANDROID_VR_*`, `WEB_REMIX`,
  `TVHTML5`, `TVHTML5_SIMPLY`, `WEB_CREATOR`) based on content hints
  (explicit/kids/live/uploaded) — because different client surfaces get
  independently rate-limited/killed by YouTube. `YTPlayerUtils.kt` cascades main
  client → fallback clients → NewPipe extractor as a last resort, logging and
  continuing past a metadata-client failure rather than throwing.
- **Bounded renderer-failure backoff.**
  `zemer-cipher/library/.../RendererRecoveryPolicy.kt` is a pure, clock-injectable
  policy: after 3 consecutive WebView-renderer deaths it opens a 60s backoff window
  before allowing another attempt — unit-tested in `RendererRecoveryPolicyTest.kt`.
  MuseFlow's `YouTubeStreamResolver` has a single 20s per-resolve timeout but no
  cross-call backoff if PoToken generation is failing repeatedly (e.g. WebView dying
  under memory pressure) — it will just keep retrying at full cost every track.
- **Global crash handler + `CrashActivity`.**
  `Metrolist/app/.../utils/CrashHandler.kt` (copied near-verbatim into Echo-Music and
  vivi-music) installs a `Thread.UncaughtExceptionHandler` that captures device/build
  info + stacktrace, launches a `CrashActivity` showing the log, then force-kills the
  process — with a self-guarding fallback to the default handler if the crash
  reporter itself throws. MuseFlow has no global uncaught-exception handler.
- **Contract-style tests pinned to real `player.js` fixtures.**
  zemer-cipher's `PlayerConfigStoreForceRefreshTest`, `ConfigParityFixturesTest`,
  `FunctionNameExtractorPrecedenceTest`, etc. pin known-good parses of real player.js
  fixtures so a parser regression fails CI immediately, rather than surfacing as a
  silent production breakage.

---

## (d) Feature inventory

| Feature | Repo(s) | Impact | Effort | License |
|---|---|---|---|---|
| Chromecast/Cast support | Metrolist, Echo-Music, SimpMusic, vivi-music, ArchiveTune, Flow | High | Medium | GPL-3.0 |
| Song identification (Shazam-like, AudD/ACRCloud + Odesli cross-platform links) | MusicRecognizer (Audile) | High | Large | GPL-3.0 |
| Equalizer / audio effects | Metrolist, SimpMusic, ArchiveTune | Medium-High | Small-Medium | GPL-3.0 |
| Auto-backup / restore (WorkManager) | SimpMusic, vivi-music | Medium-High | Small-Medium | GPL-3.0 |
| Home-screen widget (Glance) | Metrolist, Echo-Music, SimpMusic, vivi-music, Flow | Medium | Small-Medium | GPL-3.0 |
| Sleep timer | Echo-Music, vivi-music, ArchiveTune | Medium | Small | GPL-3.0 |
| Advanced lyrics: translation + romanization | better-lyrics | Medium | Medium | GPL-3.0 |
| Canvas video backgrounds (Spotify-style loops) | Echo-Music-Canvas, vivimusicanvas | Medium | Medium (+ ongoing content-ops burden for the registry) | GPL-3.0 |
| Listen Together (real-time synced playback) | metroserver (Go WS server, Metrolist's protocol backend) + **metroproto** and **Echo-Music-Proto**, both git submodules carrying the *same* Listen Together wire protocol (protobuf schema) for their respective host apps, not independent protocols | Medium (niche, differentiating) | Large (backend infra + client protocol integration) | GPL-3.0 |
| AI recommendations + Spotify playlist import | Echo-Music (`ai/AiRecommendationWorker.kt`, `spotifyimport/`) | Medium-High | Large (ML/heuristics + Spotify OAuth) | GPL-3.0 |
| Multi-account switching | ArchiveTune (`AccountSettings.kt`) | Low-Medium | Medium | GPL-3.0 |
| RSS / new-release notifications | SimpMusic, vivi-music | Low-Medium | Small | GPL-3.0 |
| Native M4A cover-art/metadata embedding for downloads | metrolist-coverart-lib | Medium | Medium (NDK/CMake + JNI) | Unlicensed at repo level; bundles **Bento4 (GPLv2)** — check GPLv2/GPLv3 compatibility before reuse |
| Global charts / trending discovery dashboard | Charts (**Echo Charts**) — backed by a **public, already-hosted API** at `https://stats.echomusic.fun/api/v1`, so MuseFlow could consume this endpoint directly rather than re-scraping/aggregating chart data itself | Low-Medium | **Small** if consuming the public API directly (just a new Discover-style screen + HTTP client), Medium if replicating the aggregation server-side | **No LICENSE file** in the client repo — do not vendor its code without confirming with maintainers; consuming the public API as a plain HTTP client is a separate, lower-risk question but still worth confirming acceptable-use terms before shipping |
| KMP (multi-platform) rewrite | SimpMusic | Low for current scope | Large (architecture rewrite) | GPL-3.0 |

---

## (e) Proposed roadmap (for next session — nothing implemented yet)

Ordered by rough value/effort; sizes are small/medium/large.

**Tier 1 — speed, do first (all Small–Medium, no architecture change):**
1. **Small** — Consolidate `YouTubeStreamResolver` and `YouTubeMusicProvider` onto one
   shared, DI-provided `OkHttpClient` with a disk cache enabled.
2. **Small** — Merge `YouTubeMusicProvider.search()` into `searchAllCategories()`'s
   single unfiltered call (bucket "Song" the same way albums/artists/playlists
   already are) — removes one redundant request per combined search.
3. **Medium** — Reuse the PoToken session across tracks in `PoTokenGenerator`,
   mirroring zemer-cipher's session-bound-to-`visitorData` reuse, instead of minting
   fresh per resolve.
4. **Medium** — Add a short, response-driven TTL cache for resolved stream URLs in
   `YouTubeStreamResolver` (mirroring `songUrlCache`'s shape), using the real
   `streamExpiresInSeconds` from the player response — first verify empirically what
   that TTL actually is before assuming today's "never cache" comment is still
   correct.

**Tier 2 — stability (Small–Medium):**
5. **Small** — Add a global `Thread.UncaughtExceptionHandler` (mirroring
   `CrashHandler.kt`'s shape: capture + log + graceful restart) — currently entirely
   absent from MuseFlow.
6. **Small** — Add a bounded backoff around repeated PoToken/WebView failures
   (mirroring `RendererRecoveryPolicy`'s pure, testable shape) so a dying WebView
   doesn't retry at full cost on every track.
7. **Medium** — Bundle a small, remote-refreshable cipher-config JSON (own
   implementation, informed by `PlayerConfigStore`'s cache/TTL/cooldown shape — not
   copied code, to sidestep any licensing question) instead of re-deriving
   sig/n-transform from `player.js` text every cold start.

**Tier 3 — UI/UX polish (Small–Medium each, independent, pick freely):**
8. **Small** — Palette-driven Now Playing background color (downsample artwork,
   run `Palette` off-main-thread, cache per track id) — directly portable, no new
   dependency required.
9. **Small** — Spring-animated "morphing weight" transport controls on play/pause
   press.
10. **Small–Medium** — Replace ad-hoc loading states with a shimmer/skeleton
    composable — Flow's dependency-free `Modifier.shimmerEffect()` is the lowest-risk
    port (no new library).
11. **Medium** — Hand-rolled physics-based bottom sheet mini-player (drag gestures +
    power-curve scrim + swipe-to-skip) to replace whatever mini-player
    interaction MuseFlow has today.
12. **Medium** — Home-screen widget (Glance `GlanceAppWidget`) showing a compact
    now-playing card.

**Tier 4 — larger features (Medium–Large, pick 1 at a time, discuss trade-offs first):**
13. **Medium** — Sleep timer, equalizer/audio effects, auto-backup/restore — each
    independently small-to-medium and high user-visible value; good candidates to
    batch together in one session.
14. **Medium** — Chromecast/Cast support (GMS build flavor + Cast SDK).
15. **Large** — Song identification (Shazam-like) — highest standalone user-impact
    feature found in any reference repo, but requires a paid third-party
    fingerprinting API and background mic capture; scope/cost needs a dedicated
    discussion before committing.
16. **Large** — Canvas video backgrounds, Listen Together, AI recommendations +
    Spotify import — all real differentiators but each needs backend
    infrastructure and/or third-party API/content-ops commitments; treat as
    future-roadmap candidates rather than near-term work.
17. **Small (re-scoped)** — Charts/Discover screen, consuming Echo Charts' public
    hosted API (`https://stats.echomusic.fun/api/v1`) directly rather than
    replicating its aggregation — moved out of "large/future" since no backend
    build-out is required, just a client screen and confirming acceptable-use terms
    with the API owner first.

### Licensing / credits notes for README + GPL-3.0 compliance

- **GPL-3.0, confirmed by reading the actual LICENSE file (not just a badge):**
  Metrolist, Echo-Music, SimpMusic, MetrolistExtractor, zemer-cipher, ArchiveTune,
  Flow, Echo-Music-Canvas, vivimusicanvas, MusicRecognizer, better-lyrics,
  metroserver, vivi-music.
- **vivi-music** carries GPL-3.0 *plus* a special exception permitting linkage with a
  proprietary Musixmatch module — redistribution of that module itself is separately
  restricted; only relevant if MuseFlow ever links against that specific module.
- **metroproto and Echo-Music-Proto** are both git submodules carrying the shared
  Listen Together wire protocol (used by Metrolist's and Echo-Music's respective
  clients+metroserver backend) — not two unrelated protocol designs. Neither exposes
  its own LICENSE file separately from its parent repo in this clone; treat their
  license as inherited from whichever parent (Metrolist/Echo-Music, both GPL-3.0)
  pulls them in, but confirm the submodule's own upstream repo before reusing its
  `.proto` schema directly.
- **faraday** has no LICENSE file in this local clone, but is actively published under
  the MetrolistGroup GitHub org with real releases (e.g.
  [player-ef1080e1](https://github.com/MetrolistGroup/faraday/releases/tag/player-ef1080e1))
  — so it's a live, maintained project, not abandoned. That doesn't resolve the
  licensing gap: still confirm terms with MetrolistGroup before vendoring its code or
  depending on its registry output in a way that implies redistribution.
- **Charts (Echo Charts)** likewise has no LICENSE file for its client code, but its
  data is served from a public API (`https://stats.echomusic.fun/api/v1`) — treat
  "the API is publicly reachable" and "the client/server code is reusable" as
  separate questions; only the former is confirmed.
- **metrolist-coverart-lib** bundles **Bento4**, which is **GPLv2** (not v3) —
  if MuseFlow ever adopts native M4A tag-embedding via this library, check GPLv2/v3
  interaction before shipping.
- Anything adopted from a GPL-3.0 repo (Metrolist, Echo-Music, SimpMusic, etc.) must
  be credited by name + link in MuseFlow's README credits section, and MuseFlow's own
  GPL-3.0 obligations (source availability) apply to any derived code, not just
  copy-pasted snippets — verify this with whoever owns MuseFlow's licensing decision
  before landing any Tier 1–4 item that reuses actual code rather than just a pattern.
