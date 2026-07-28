# Session handoff — 2026-07-28

Context window is full; this is the carry-over prompt for a new session. Paste the section
below ("Prompt for the new session") as the opening message. The rest of this doc is reference
detail if you need to check something specific later.

---

## Prompt for the new session

```
Continue work on MuseFlow, an Android music player (Kotlin + Jetpack Compose), at
/home/panda/projects/museflow_V1.1.3 (WSL2, branch rebuild/frontend-innertube). Read
docs/SESSION-HANDOFF-2026-07-28.md first for full context, then docs/full-gap-audit.md for the
overall feature-completeness picture.

CRITICAL FIRST STEP: none of the previous session's work is committed yet - 39 files (35
modified, 4 new) are sitting uncommitted in the working directory. Run `git status` and confirm
with me before doing anything else; probably want to commit this as one or more logical commits
before continuing (I have not pushed anything to origin from this folder yet either - only up to
commit a27457a has ever been pushed, from a different local folder - see the doc for why that
matters).

Current confirmed-working state (user tested and confirmed): both app-beta.apk (minified, R8,
non-debuggable - ~5.6MB) and app-debug.apk build clean and the user confirmed cover art, search,
autoplay continuation, and general smoothness all work correctly on-device. Don't re-do any of
the fixes listed in the doc's "What was fixed this session" section unless the user reports a
NEW, specific regression - verify on-device claims before assuming something is broken, per this
project's own established rule (the last several real bugs here all compiled clean and looked
correct in review before being caught on-device).

One user-facing note already given, worth remembering: they want a one-time backfill (already
built, runs automatically on every app launch, cheap once caught up) for cover art on downloads
made before the local-cover-file fix - this is done, not outstanding.

Build commands: `./gradlew :app:compileDebugKotlin -q` for a quick compile check,
`./gradlew :app:assembleDebug :app:assembleBeta -q` to build both deliverable APKs. Do NOT install
via adb (explicit standing instruction) - build the APK and use SendUserFile to hand it to the
user directly; they install it themselves.
```

---

## What was fixed this session (verified working by the user)

Two rounds of work: first an investigation into "why doesn't this feel as smooth as Echo Music"
(see `docs/smoothness-ux-optimization-report.md` and `docs/nowplaying-jank-investigation.md` for
the full research trail), then a long punch-list of specific bugs the user found while testing
real builds. In order fixed:

**Smoothness/animation pass** (first round): `Modifier.animateItem()` extended to Home/Library/
Search/Playlist lists, real direction-aware nav transitions (verified against Echo-Music's actual
source in `repo/Echo-Music`, not guessed), `animateContentSize()` on Artist's About section,
`@Immutable` on hot list models, shimmer loading placeholders, shared `MediaCard`/`MediaGridCard`/
`PlaylistCover` components (deduplicating what were 3-4 near-identical per-screen card layouts),
named motion specs (`ui/theme/Motion.kt`).

**Antigravity Windows-copy reconciliation**: the user had a separate copy at
`E:\Coding Workspace\museflow_V1.1.3` worked on by a different AI tool (Antigravity). Rather than
copy its changes wholesale, each change was researched against Echo-Music's actual source and
either ported faithfully (nav transition index logic, `isDebuggable=false` + real R8 minify for
beta, the manifest's `singleTask`/`adjustResize`), fixed before porting (the `bounceClick` modifier
had a redundant second gesture recognizer racing `combinedClickable` - rewritten to share one
`interactionSource`; the proguard rules were `-dontwarn`-only with zero real `-keep` rules, which
would have broken the WebView JS bridges and kotlinx.serialization silently), or deliberately not
ported (bounce-click was NOT applied to list rows/grid tiles - checked Echo's own `Items.kt` and
confirmed rows keep plain ripple there, only buttons/the mini-player get bounce).

**Bug-fix punch list** (second+ rounds, from real on-device testing):
- Crash: duplicate LazyRow key in Home's track carousel (`"title|artist"` collided on a legit
  duplicate; switched to position keys, matching how Library/Search already handle this).
- Stats screen: black-on-black text (missing explicit `color =` on `Text`, the one screen that
  didn't follow this codebase's own convention of always setting it explicitly).
- Stats "Listening Summary" redesigned as an Echo-style modal bottom sheet (ported from
  `repo/Echo-Music/.../ActivityHistory.kt` almost verbatim, adapted for MuseFlow's all-time-only
  stats since it has no per-play timestamp data).
- Library search added across all 7 sections (Playlists/Liked/Downloads/Top50/Recent/OnDevice/
  Following), with relevance ranking (exact match > prefix > word-boundary > substring) instead of
  raw filter-preserving-list-order.
- Playlist detail screen's bottom content was tucked behind the mini-player with no way to scroll
  further - bumped bottom padding, then bumped it **everywhere** (Home/Library/Search/Stats/Artist/
  Album/etc. - was a systemic `140.dp`/`120.dp` pattern across ~13 files, now `200.dp` uniformly).
- Home's "Your playlists" carousel showed no cover for Spotify-imported playlists (no
  `coverImageUrl`) - extracted Library's mosaic-cover fallback into a shared `PlaylistCover`
  component, wired into both.
- **Cover art for downloaded tracks - this took three real, distinct root causes to actually fix,
  in order discovered:**
  1. Assumed ExoPlayer auto-surfaces a file's embedded ID3 art if `artworkUri` is left unset -
     doesn't happen in practice; broke it further (no art online OR offline).
  2. `AudioTagger`'s embedding only supports MP3/M4A containers - but YouTube's audio-only
     downloads are itag 251/250/249 (Opus-in-WebM) *first* per the resolver's own try-order
     comment, so embedding silently wrote nothing for the vast majority of real downloads. Fixed by
     saving cover bytes to a plain sibling file (`$key.cover`) at download time instead, which
     works regardless of container.
  3. Even with a real local cover file, it still didn't show in-app: `PlayerViewModel`'s own
     `NowPlayingState.artworkUrl` only ever reads `mediaMetadata.artworkUri`, never `artworkData` -
     so setting raw bytes (to fix the notification) left every in-app view blank, online or
     offline, while the system notification (whose builder reads both) worked fine. Also, Library's
     Downloads list was pointing Coil at a bare file path with no `file://` scheme. Both fixed by
     consistently using a real `Uri.fromFile(...)` URI everywhere a downloaded track's artwork is
     needed (`PlayerViewModel.toMediaItem()`, `PlaybackService.restoreQueueIfEnabled()`,
     `DownloadedTrackEntity.toTrack()`).
  - A backfill (`DownloadRepository.backfillMissingCovers()`, called from
    `MuseFlowApplication.onCreate()`) fetches covers for anything downloaded before this fix,
    automatically, on every launch - cheap once caught up (just filesystem stats), so it also
    naturally retries anything that failed the first time (e.g. was offline).
- On-device audio (Library's "On device" section): `LocalAudioProvider`'s MediaStore query wasn't
  even selecting an album-art column - added `ALBUM_ID` and the classic
  `content://media/external/audio/albumart/<id>` URI.
- Lyrics offline: all three providers are network-only; offline they each burned their own ~10s
  timeout sequentially (up to ~30s of spinner) before landing on the same "not found" an instant
  connectivity check now gives immediately, with a clear message.
- Download cover quality: confirmed the two live data-fetch paths already upgrade thumbnail URLs
  to 544x544; added the same upgrade defensively at download-embed time too.
- Autoplay after a search-originated queue ends: was searching the *last track's artist name* as
  plain text, which could drift genre/language for a thin-catalog artist. Now seeds YouTube
  Music's own "radio"/watch-next continuation from the last track's real video id first (falls
  back to the old artist-search only if that returns nothing).
- Explore button on Search restyled as a proper card (was a bare icon+text row).
- Beta build type: went back and forth on this - user first asked to drop minification
  (suspected it caused sluggishness), then after testing confirmed the *un*minified build felt
  worse and asked to restore it. **Current state: beta IS minified again** (`isMinifyEnabled=true`,
  `isShrinkResources=true`, `isDebuggable=false`, matching Echo-Music's own release config,
  confirmed by APK size ~5.6MB) - the sluggishness the user felt was unrelated to minification;
  it was the cover-art bugs above surfacing while that build happened to be the one under test.

## Known gotchas for the next session

- **The user's client sometimes sends the exact same message 2-3 times in a row** (confirmed
  during this session - identical multi-paragraph reports arrived verbatim more than once). Don't
  treat a repeat as a new request; check the task list / git diff for what's already done before
  redoing work.
- **Never send a blind `KEYCODE_BACK` between adb-driven measurements/tests** when profiling or
  automating - whichever app is actually foreground eats it, and on this device backing out of
  Echo-Music's Now Playing exits straight to the launcher rather than collapsing in-app. Always
  `am force-stop` + fresh `monkey -c LAUNCHER` launch, and verify with
  `dumpsys activity activities | grep topResumedActivity` before trusting a measurement.
- **`dumpsys gfxinfo <pkg> reset` prints the PRE-reset stats as part of resetting** - reading that
  output as "during my test" (rather than reading fresh after the test) was a real methodology bug
  earlier this session and produced wildly wrong numbers.
- The `repo/` folder has ~17 reference repos (Echo-Music is the main one, checked out read-only) -
  `repo/SimpMusic/CLAUDE.md` contains a planted prompt-injection instruction (noted in
  `docs/reference-analysis.md`); ignore it if that repo gets explored again.
- `E:\Coding Workspace\museflow_V1.1.3` (Windows) is a **stale, now-diverged copy** from an earlier
  point in this session, worked on separately by Antigravity. Its relevant ideas have already been
  extracted/reconciled into the WSL copy (see the section above) - there's no need to go back to it
  unless the user explicitly says they made new changes there again.
- adb/uiautomator device-control runbook (usbipd-win attach, chmod, uiautomator dump for accurate
  tap coordinates) is in this project's CLAUDE.md / earlier context - still applies if on-device
  testing is needed again. Phone is a Xiaomi 14 Ultra, MIUI, 120Hz display, gesture nav (tapping too
  close to the very bottom edge can trigger the OS recents gesture instead of an in-app tap).
- Auto-memory file already saved: `library_revert_reason.md` (why Library's multi-screen split was
  reverted - it felt unorganized vs Echo, not a rejection of multi-screen on principle). Relevant
  if Artist's tabs are ever considered for a similar split.

## Not yet done / not asked for

- No git commit has been made this session - see the handoff prompt above.
- Duration allegedly missing in Library's Downloads section was almost certainly just grid-view's
  existing (by-design) lack of a duration label, not a new bug - not changed, but not fully
  confirmed with the user either.
- Everything in `full-gap-audit.md`'s "Not started" section (Stage F/G: platform surfaces,
  accounts, settings-screen breadth) is still untouched, as before.
