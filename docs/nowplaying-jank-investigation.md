# Now Playing jank — profiling investigation

**2026-07-28.** Follow-up to `smoothness-ux-optimization-report.md` after the user reported
MuseFlow still wasn't on par with Echo despite that session's 8 animation/transition fixes.
This doc is the profiling investigation that followed: real on-device frame-timing data
(`dumpsys gfxinfo`) and a CPU trace (`atrace`), same Xiaomi 14 Ultra (120Hz), same conditions,
for both MuseFlow and Echo-Music side by side. No code was changed this session — investigation
only, per the user's "will do the work later someday."

**Read this note before trusting any number below**: the first pass at this investigation
(same session, discarded) produced a false result — a `KEYCODE_BACK` sent between measurements
was assumed to act on MuseFlow but actually hit Echo (whichever app is frontmost eats the back
press), and on this device a `BACK` from Echo's Now Playing exits the app to the launcher
entirely rather than collapsing in-app. That silently invalidated one comparison. All numbers
below were re-collected with a strict protocol to prevent a repeat:
- Every app switch goes through `am force-stop` + fresh `monkey -p <pkg> -c LAUNCHER` — never a
  bare `BACK` or assumed foreground state.
- `adb shell dumpsys activity activities | grep topResumedActivity` is checked **before and
  after** every gesture, confirming the intended package/task the whole time.
- A screenshot is taken immediately before and after every tap-driven measurement, confirmed by
  actual on-screen content (track name/progress), not assumed from prior state.
- Echo's floating mini-player sits close enough to the screen's bottom edge that a tap can
  register as MIUI's system recents/task-switcher gesture instead of an in-app tap (confirmed:
  happened once, produced a bogus "0% jank" reading because no real transition occurred) — taps
  were moved higher on the pill and re-verified by screenshot before trusting any result.
- `dumpsys gfxinfo <pkg> reset` **also prints the stats from before the reset** — using that
  printed output as if it were "during the test" (rather than reading fresh right after) was
  the very first methodology bug of this whole investigation and produced the initial wildly-bad
  MuseFlow numbers reported earlier in this session. Every number below is a fresh read taken
  immediately after a reset + gesture, not the reset command's own printed output.

---

## 1. Verified result: plain scrolling is NOT the problem

Clean, reset-then-scroll-then-immediate-read, foreground-verified both before and after:

| | MuseFlow | Echo |
|---|---|---|
| 50th percentile frame time | 6ms | 13ms |
| 90th percentile | 7ms | 15ms |
| 95th percentile | 8ms | 18ms |
| 99th percentile | 12ms | 34ms |
| Janky frames (legacy metric) | 2.96% | 18.40% |

MuseFlow's raw Compose scroll rendering is not the bottleneck — it's tighter than Echo's own
under this test. This rules out "the codebase/rendering engine is bad" as an explanation for the
reported smoothness gap.

## 2. Verified result: opening Now Playing IS the problem

Clean, reset-then-tap(mini-player)-then-immediate-read, foreground-and-screenshot-verified both
before and after each tap:

| | MuseFlow | Echo |
|---|---|---|
| Frames in the transition window | 24 | 260 |
| 50th percentile | 31ms | 17ms |
| 90th percentile | **129ms** | 24ms |
| 95th percentile | 133ms | 27ms |
| 99th percentile | 133ms | 46ms |
| Janky frames (legacy metric) | **91.67%** | 63.85% |
| "Slow UI thread" flagged frames | **4** | **0** |

Both apps show real legacy-metric jank on this 120Hz display (expected — the legacy metric is
60Hz-calibrated and the true per-frame budget here is 8.3ms, tight for any complex screen). But
the qualitative difference matters: Echo's frames run *slow-but-bounded* (worst case 46ms,
zero "Slow UI thread" flags — consistent with just not quite hitting 120Hz, not with real
stalls). MuseFlow's frames include **actual multi-frame stalls** (129-133ms, "Slow UI thread"
flagged on 4 of 24 frames) — a different, more serious class of problem than Echo has.

This matches the user's reported symptoms ("screen transitions feel slow/off", general
"heaviness") precisely, and is a real, isolated, reproducible defect — not a general codebase
quality problem (see §1).

## 3. Where the time goes (CPU trace, `atrace`)

Captured a text trace (`gfx view sched dalvik input am wm freq` categories) around the exact
mini-player tap on MuseFlow. In the worst frame (64.7ms total):

```
Choreographer#doFrame                          64.67ms
  animation                                     33.44ms
  traversal                                     31.04ms
    draw-VRI[MainActivity]                      30.94ms
      Record View#draw()                        30.60ms
        (several "computePalette" markers, small individually, interleaved with
         "Lock contention on task queue lock" - source not conclusively identified)
```

**Ruled out** (checked directly against source, not guessed):
- Background style cost: default is `BackgroundStyle.Solid` (`else -> Box().background(base)`,
  the cheapest branch) - not blur/gradient. `PlayerBackground` at
  [`NowPlayingScreen.kt:192`](../app/src/main/java/com/example/ui/screens/NowPlayingScreen.kt).
- Eager queue rendering: gated behind `if (showQueue)`, `showQueue` defaults `false` - the
  queue's `LazyColumn` (line 886) isn't composed until the user opens it.
- Eager/blocking lyrics fetch: the `LaunchedEffect(state.title, state.artist)` in
  [`MainActivity.kt:530`](../app/src/main/java/com/example/MainActivity.kt) launches a clean
  coroutine into `LyricsViewModel.load()`, which does nothing synchronous before its first
  suspension point.
- `AlbumPaletteViewModel`'s own palette extraction: already properly
  `withContext(Dispatchers.IO)` / `withContext(Dispatchers.Default)` in
  [`AlbumPalette.kt:38-49`](../app/src/main/java/com/example/AlbumPalette.kt) - structurally
  cannot be what's showing up inside `Record View#draw()` on the main thread.

**Not ruled out, best-supported remaining explanation**: `NowPlayingScreen.kt` is 1,118 lines -
the single largest, densest screen in the app (transport controls, capsules, sliders, artwork,
menu, queue/lyrics slots). Unlike Home/Search/Library, which stay composed and "warm" across a
session, Now Playing's composable tree is built fresh - new RenderNodes, full measure/layout
pass - every single time it's entered, because it's torn down when collapsed. That's consistent
with both halves of the worst frame being expensive (animation-phase AND traversal/draw-phase,
not just one), and consistent with it being the single most frequently re-entered screen in the
app, which is exactly what would make this the most *noticeable* jank source even if no other
screen has an equivalent problem.

The repeated `computePalette` trace markers on the main thread are flagged, not blamed - most
likely Android/MIUI's own notification icon color extraction reacting to the media session
around the same time, but this wasn't confirmed with symbolication.

## 4. Where this investigation is blocked

Two real tool limits, both worth knowing about before picking this back up:

1. **Precision**: `atrace` gives phase-level names (`animation`, `traversal`,
   `Record View#draw()`) but not symbolicated function names inside those phases. Pinpointing
   the exact function eating the 30ms needs a proper Perfetto/Android-Studio-Profiler capture,
   which needs either GUI tooling not available in this environment, or `trace_processor_shell`
   for offline analysis of a `perfetto`-format trace (not attempted this session).
2. **Verification loop**: confirming any code fix requires reinstalling the app, which the
   working agreement for this project says to avoid. Any structural change to
   `NowPlayingScreen.kt` made in a future session will need the user to install and either
   re-report or let the agent re-run this exact profiling protocol.

## 5. Suggested next step, whenever this is picked back up

Given §3's best-supported hypothesis (screen-size/cold-composition cost, not one bad line), the
lowest-risk starting point is *not* a rewrite - it's confirming the hypothesis with a real
symbolicated trace first (Android Studio Profiler's System Trace, capturing the identical
mini-player tap), which would turn "probably the tree is just big" into a precise target. Only
after that should `NowPlayingScreen.kt` actually be restructured - guessing at a 1,118-line
screen's internals without on-device verification is exactly the kind of change this project's
own history (`full-gap-audit.md` §5) warns compiles clean and looks correct in review, every
time, right up until it's tested for real.
