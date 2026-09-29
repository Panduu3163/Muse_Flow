# MuseFlow release roadmap: 1.5.0 → 1.8.0, then v2 research

Created 2026-09-28. Baseline: `rebuild/frontend-innertube` at `7912c4a`, MuseFlow 1.4.3 (versionCode 22). This plan uses the current source and the Echo UX study in `../../analysis/` as inputs. It replaces older feature-gap assumptions where those documents predate 1.4.3. It is a product and engineering plan, not a claim that listed work is implemented.

## Release strategy

Ship four coherent minor releases, each with a user-visible theme and its own stabilization pass. Patch releases fix regressions within that minor line. Do not open a 1.9 line: after a stable 1.8.0, start a separately researched v2 project. The version assignments below are proposed scope boundaries; move a feature to the next release if it does not meet the quality gate.

Preserve MuseFlow's package IDs, signing identity, Room data, DataStore preferences, playlists, downloads and queue across every update. The `.beta` build is a separate installed app and needs its existing `debug.keystore` for in-place beta updates. Keep the current 1.4.3 queue/scroll fixes intact.

## 1.5.0 — coherent foundation and customization

**User outcome:** the whole app feels like one product, and people can understand and find its existing options.

- Define MuseFlow design tokens: typography, spacing, shapes, color/surface roles, icon treatment, sheet/dialog patterns, and motion durations. Apply them first to shared rows, cards, menus, nav, loading/empty/error states and settings.
- Audit all current persisted settings against their actual consumers. Wire or remove controls that have no visible effect. Add settings search with stable IDs and navigation to the exact control.
- Add live previews and reset actions for accent/theme, font, player background, slider, artwork shape, density and mini-player choices. Keep individual controls discoverable without appearance presets.
- Use a compact horizontal navigation pill with the selected tab's icon and label expanded beside the Mini player. Check touch targets, large text and device frame times before stable release.
- Improve contrast, text scaling, touch targets, screen-reader labels and reduced-motion behavior. Respect Android animation scale.
- Establish screenshot baselines and device frame-time measurements for the current and new UI.

**Exit checks:** every visible setting persists and demonstrably changes behavior; top-level screens and shared components use the design system; search finds every setting; no regression in playback, downloads or navigation; debug/beta build and test results are recorded.

**Development status (2026-09-29):** RC8 refines lyrics transparency, album-art progress, search duration, artwork swipes, liked-heart treatment and cached GitHub release notes. The stable candidate removes Player presets and uses the existing sideload signing certificate with minification and resource shrinking. RC6 fixed persisted default-tab startup, extended shared empty/error and sheet treatments, strengthened Settings search coverage and prepared screenshot/frame-time capture. RC5 refined the Apple Music inspired backdrop and seek controls; RC4 added full Player Glow, Apple Music inspired artwork and directional transitions; RC3 fixed typography and added album-colour Glow; RC2 added theme and visual customization; RC1 added shared tokens, Settings search and accessibility fixes. Device interaction, screenshot, frame-time and update-in-place checks remain open.

## 1.6.0 — listening experience

**User outcome:** moving from a song list to mini player, full player, lyrics and queue feels continuous and dependable.

- Redesign mini player and Now Playing as one connected flow. Keep track identity, artwork color and playback state continuous through expand/collapse.
- Prioritize track identity, seek and transport; make queue, lyrics, sleep timer, audio settings, download and like actions obvious but uncluttered.
- Add direct queue editing: clear current/up-next structure, drag reorder, play next, remove with undo, and save queue as playlist where data supports it.
- Improve full-screen lyrics readability, timing adjustment, quick provider switching and an optional shareable lyric card. Keep existing nine animation styles and six providers; offer reduced-motion alternatives.
- Clarify playback quality, data saver, preloading and error/retry controls. Show the actual quality/state delivered, not a cosmetic choice.
- Profile Now Playing entrance, artwork effects, queue opening and lyrics on a real target phone; optimize long frames before adding new visual effects.

**Exit checks:** queue order remains correct with shuffle, repeat, offline recovery and filtered lists; no stalled mini-to-full transition on the target device; transport and media notification remain synchronized; player controls work with large text and screen readers.

## 1.7.0 — discovery and library

**User outcome:** finding, saving and organizing music is fast even in a large library or unreliable network.

- Refine Home into a clear hierarchy of continue listening, personal picks, recent music, new releases and discovery. Add user control for shelf visibility/order only after the default order is strong.
- Make recommendations understandable where possible (for example, based on recent listening or a followed artist) and expand listening summaries/smart collections using actual playback events, without inventing historical data.
- Unify Search suggestions, song/video/album/artist/playlist filters, local and online source feedback, direct-link handling, and return-to-results state.
- Give Songs, Albums, Artists, Playlists, Downloads and On-device coherent browsing patterns. Persist sorting, filter and list/grid choice per view.
- Standardize track and collection actions: play next, add to queue, like, download, add to playlist, share and multi-select where applicable. Show scope and completion for bulk actions.
- Use shared initial-loading, refreshing, empty, offline-cached, offline-empty, partial-data and error states. Preserve last useful content during refresh.

**Exit checks:** opening Player/detail and returning restores query, filter and scroll; all collection actions operate on the intended full set rather than only visible search matches; offline badges correspond to playable files; long lists stay smooth.

## 1.8.0 — durability, platform reach and final polish

**User outcome:** MuseFlow feels dependable as a daily player, inside and outside the app.

- Make backup/restore match its promise: explicitly cover or disclose settings, likes, playlists and listening history; import atomically; protect against duplicates and partial failure. Downloaded audio needs a separate, honest policy.
- Add migration fixtures and restore round-trip tests using representative existing data.
- Improve download manager visibility: pending/active/completed/failed, retry/cancel, storage use and cleanup. Verify offline behavior after app/process restart.
- Add a compact home-screen widget and Quick Settings playback tile if platform testing and accessibility meet the gate.
- Complete consistency/performance/accessibility pass across light, dark, AMOLED, low-memory and older supported Android devices; tidy onboarding, update messaging and release notes.

Large service or content additions—Spotify sync, podcasts, recognition, Cast, new canvas sources and AI translation—are **not promised by 1.8.0**. Assess each during v2 research or as a separately approved project, with account, licensing, privacy, infrastructure and maintenance costs written down first.

**Exit checks:** data survives upgrade and restore; the beta package updates in place with the expected certificate; minified beta works on device; background playback, notification, headset controls, downloads, queue and widgets/tiles pass interaction checks; no open release-blocking regressions.

## Gate for every release

1. Define a small acceptance checklist and screenshots before implementation. Keep each change focused and reviewable.
2. Run relevant unit and migration tests. The saved 1.4.3 test run has one Robolectric setup failure caused by a Windows path with a space; rerun using the existing `MUSEFLOW_TEST_HOME` override before calling the baseline green.
3. Build debug and minified beta. Validate package/version/signature before installing over an existing beta.
4. On a device, check Home → Search → Library → mini player → Player → queue/lyrics → back, plus offline recovery, downloads, notification/headset controls and existing user data.
5. Record frame times for changed animations and review accessibility, empty/error states and low bandwidth behavior. Fix failures before increasing versionName/versionCode for a release candidate.

## v2 — research before specification

After 1.8.0 stabilizes, create a v2 knowledge base from evidence rather than a wish list. Research current music apps and open-source players, official Android/Media3 and accessibility guidance, public community requests/issues, MuseFlow user feedback, and measured pain points in 1.8.0. Echo is one reference among several, not the target design. Record source date, evidence, user problem, implementation cost, data/privacy impact and MuseFlow fit for each idea.

Study at least these journeys: first launch, play something quickly, discovery, search, large-library management, offline travel, lyrics, queue control, personalization, sharing, backup/restore, and controls outside the app. Compare apps with consistent criteria, test real interactions where permitted, and separate demonstrated behavior from marketing claims. Include accessibility and performance in every comparison.

Deliver before v2 implementation:

- a ranked problem list backed by user feedback and measurements;
- a competitive pattern matrix with sources and dates;
- MuseFlow v2 design principles and a distinct visual direction;
- clickable prototypes for the core journeys and user feedback on them;
- architecture/data migration proposal and compatibility plan from 1.8.0;
- a scoped v2 release plan with cost, dependency, and verification estimates.

Do not attempt to literally survey the entire internet. Use a reproducible search and evidence process, revisit time-sensitive findings, and make decisions from the best relevant sources. Do not publish or push a v2 release until the research, prototype, data migration and device validation gates are met.
