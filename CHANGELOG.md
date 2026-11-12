# 🎵 MuseFlow Changelog

## 1.8.0 Beta — development build 62

This source checkpoint includes the 1.8 recovery and controls work. It is not a stable-release declaration or an announcement that an APK has been published.

- Versioned backup preview, optional history/settings, duplicate-safe merges and recoverable settings restoration.
- Verified external backup copies, persistent folder access, and latest/previous internal recovery generations.
- Durable download requests, bounded retry, explicit post-restart retry, cancellation ownership and guarded cleanup.
- Shared-session widget and Quick Settings controls, paused queue restoration and explicit resumption.
- Bounded CSV/M3U matching with partial-result, duplicate, malformed-file and cancellation feedback.
- Typed update verification/installer outcomes and scrollable utility confirmations.
- Updated Android build/runtime dependencies; adaptive square cover-art widgets with metadata and overlaid controls.
- Marked average bitrate estimates when encoded streams omit a declared rate.
- Fixed unknown/partial duration summaries, downloading a removed recording again, and player controls in landscape/large text.
- Repository build/security documentation, CI verification and redacted secret scanning.

Validation: 482 automated tests passed at the device-tested checkpoint, lint had zero errors, and affected fixes were retested on Android 16. Some hardware/platform, provider-fault and live updater checks remain pending; see [verification status](docs/VERIFICATION.md). Draggable Glass navigation remains research only.


## 🎵 MuseFlow v1.7.0 Beta - Final consolidated release

A major listening-experience update with a redesigned queue, improved lyrics, smarter discovery, smoother player transitions, refined navigation, and extensive playback and offline fixes.

> 📌 **Why was 1.6.0 skipped?**
> After finishing the 1.6.0 work, I moved straight into developing 1.7.0 before publishing a separate 1.6.0 release. All completed 1.6.0 improvements are included in this 1.7.0 Beta changelog.

---

### 🎨 Interface, Home and navigation

- Refined Home, Library, Search, collection pages, action sheets and shared loading/error surfaces for a more consistent interface.
- Added a frosted bottom-navigation background, a sliding selected-tab capsule, and smoother horizontal label expansion.
- Improved navigation spacing and large-text layouts while keeping icons and labels clear above the blurred background.
- Added consistent press feedback to song rows, artwork cards, collection rows, settings, action sheets and icon buttons.
- Smoothed selection colours, changing icons, counts, settings values and appearance-choice borders without delaying actions.
- Fixed Library Playlists/Following content overlapping when switching sections near the top of a scrolled page.
- Library and Search transitions keep one active content tree, preventing outgoing lists or stale actions from remaining behind the current screen.
- Kept Home's fixed shelf layout and direct History, Listen Together and Stats actions.
- Fixed Home navigation failing or returning to Settings after opening settings through a direct route.
- Improved cold-start hero selection to avoid briefly showing unrelated generic artwork while saved likes and history load.
- Offline Home hero choices use eligible downloaded songs, with a clear message when none are available.
- Expanded Daily Discover beyond the hero cards and retained useful recommendations when refresh requests fail.

### 🎧 Playback, player controls and track changes

- Fixed playback startup failures that could make song taps do nothing and prevent the mini-player from appearing.
- Preserve the latest selected song while the player connects; connection failures now provide feedback and explicit reconnect/Retry paths.
- Prevented an older paused-queue restore from replacing a newly selected song.
- Improved transitions between the mini-player and full player, including reduced-motion behavior.
- Centered and resized Play/Pause icons across Static, Wheel and Pill transport styles.
- Kept the Pause action stable while requested playback is buffering, and made it possible to pause that pending playback.
- Added explicit restart behavior for playback that has reached the end.
- Upgraded Preload next track from URL-only warming to Media3 playlist preloading with a five-second audio target.
- Preloading follows the actual playlist successor, responds to the setting live, and is disabled while paused or stopped.
- Added short-lived stream-resolution reuse with the correct User-Agent, recording/backend/quality separation, signed-URL expiry checks and shared concurrent requests.
- Retry and refetch invalidate warmed resolutions so failed links can be resolved again.
- Rapid skips now cancel obsolete blocking cache writers rather than leaving old warming work competing with current playback.
- Warm adjacent cover images ahead of an artwork swipe and avoid stacking an image-loader fade on top of the carousel animation.
- Removed the immediate full-cover dark loading overlay. Sustained buffering uses a small badge after a short delay; genuine errors and Retry remain visible.
- Refined artwork swipe/settle behavior, progress-bar animation and seeking accuracy.
- Improved failed-stream Retry presentation while retaining the queue and retrying available cover artwork.

### 📋 Redesigned queue and source-aware continuation

- Replaced the cramped queue sheet with a full-screen queue and a smoother slide-up entrance.
- Consolidated the queue into one list instead of separate Earlier and Up Next sections.
- Removed boxed song-list containers and the unwanted filled background on the current row.
- Identify the current recording using the animated cover-art indicator, which stays visible and freezes when paused.
- Refined the current-song header with artwork, title, artist, Like, order lock and the shared full-player menu.
- Added grouped Shuffle, Repeat and Radio controls with clearer selected-state feedback.
- Improved queue-count and total-duration presentation and duration recovery.
- Added an order-editing lock that disables dragging and hides reorder handles when locked.
- Improved drag placement, swipe-to-remove animation and Undo restoration.
- Fixed Remove text appearing behind a resting song row and dismissed-row state sticking after Undo.
- Fixed earlier source-list songs disappearing when selecting a middle entry.
- Fixed cleared entries returning after Shuffle/Repeat and counts showing songs that were no longer queued.
- Keep queues capped at 51 tracks, preserving source order and the selected recording within the available queue window.
- Short online collections can continue with eligible recommendation/radio tracks when available.
- Offline Downloads queues stay with playable downloaded files; On-device queues remain local both online and offline.
- Retained queue-saving as a playlist, shared song actions and safe restrictions where reordering cannot preserve playback order.

### 📚 Library, Search, downloads and saved data

- Remember supported sort, direction, filter and list/grid choices independently for each Library section.
- Restore Search positions by query and result tab, with improvements to keyboard, filter and return navigation.
- Preserve useful browsing positions in albums, Charts, Fresh drops, Stats and History.
- Added shared collection headings and improved long-title, artist and album layouts.
- Improve collection Retry states while retaining loaded content after refresh failures.
- Made Charts and Fresh drops grids adapt to available space and larger text.
- Bulk actions report completion or partial failure; empty selections cannot trigger actions.
- Creating a playlist with selected songs is transactional, avoiding incomplete creation states.
- Likes, downloads, playlists, history and playing indicators now distinguish exact recordings rather than relying only on matching titles/artists.
- Fixed an audio version incorrectly sharing liked/downloaded/playing status with a different video or live version.
- Added non-destructive database migrations that preserve existing audio files, paths, playlist membership and history links.
- Existing valid downloads are checked before starting a transfer, avoiding unnecessary re-downloads and misleading download-notification flashes.
- Missing or empty files no longer count as completed downloads.
- Preserved artwork from existing download filenames and normalized known recording IDs when restoring older backups.
- Preserved artist/album identifiers through player actions and improved artist links from downloaded songs.
- Fixed restored On-device recordings being sent to the online resolver; saved content/file identities now remain local.
- Improved download-progress transitions, with a static unknown-progress glyph under reduced motion.

### 🎤 Lyrics timing, sources and readability

- Improved highlighting driven by actual provider word timestamps; line-timed sources use estimated word pacing.
- Improved lyric behavior during pause, resume, seeking, buffering and playback-speed changes.
- Center the active line within the visible lyrics area, with smoother upward line changes instead of a down-then-up jump.
- Refined scrolling and active-line transitions across Fade, Bounce, Scale, Wave, Karaoke sweep, Metro, Fluid (V2), Vivi Music and Apple Music styles.
- Preserved spaces and syllable joins in multilingual lyrics.
- Improved the Follow lyrics control and translucent control styling.
- Kept the Apple Music inspired artwork backdrop visible behind a readable translucent lyrics surface.
- Moved source/Auto selection, timing adjustments, lyric finding and sharing into the full-player three-dot menu.
- Removed the unwanted lyrics-provider/word-sync status strip.
- Updated Better Lyrics to its documented endpoint and forward the confirmed recording ID for matching.
- Improved TTML handling for nested/namespaced content, timestamps, explicit endings and richer metadata.
- Preserve available translation/romanization metadata in the cache while retaining compatibility with older cached lyrics.
- Tightened recording-version and duration matching across providers, including removal of an incorrect-duration fallback.
- Partial word timing no longer counts as complete word-synced lyrics.
- Cancel obsolete lyric requests and distinguish a genuine missing lyric from access restrictions, rate limits and temporary outages.
- Improved provider fallback, offline cached-lyrics behavior and malformed compressed-lyric handling.

### 🖼️ Lyrics cards and sharing

- Added and refined lyric-card preview and PNG sharing.
- Select up to four lyric lines with clearer selection and more space for complete text.
- Improved card sizing and layout to reduce truncation and clutter.
- Added colour/gradient choices, album-art glow and a translucent glass-style treatment.
- Added cover artwork beside track information and MuseFlow branding with a rounded app logo.
- Improved preview/export consistency and sharing-flow usability.

### 🌙 Sleep timer and audio settings

- Redesigned the sleep timer with a smooth slider covering 15-minute steps up to two hours.
- Added a custom hours/minutes picker without AM/PM.
- Refined the running-timer surface and improved time selection feedback.
- Use a real expiry deadline so opening screens, backgrounding or device sleep does not shift the chosen timer.
- Improved cancellation so an old cancelled timer does not pause playback later.
- Refined playback-speed dialogs and related state feedback.
- Added a genuine Data saver option for online stream resolution, alongside Best available quality.
- Keep existing local/downloaded formats intact and show the actual decoded codec/bitrate when available.

### 🧠 Recommendations, history and Stats

- Added local preference learning from actual listening and deliberate early skips to help rank discovery/radio candidates.
- Balance source relevance, familiar artists, artist variety and recent-repeat avoidance.
- Preserve explicit playlist/album order instead of applying recommendation ranking to a chosen collection.
- Search playback starts the selected recording and continues through radio/recommendations rather than the remaining Search matches.
- Home and artist-based radio fallback use music-catalog results; unrestricted general-video uploads remain available through explicit Search.
- Improved On repeat using recent qualifying listens, with existing top-played history as its initial fallback.
- Paused and buffering time do not count as listening; seeking does not invent heard time.
- Playback errors and ordinary queue edits are not treated as deliberate dislikes.
- Listening Summary separates newly recorded listening time from historical estimates.
- Fixed period headings, long-duration estimates, large-text truncation and summary-sheet scrolling.
- Clearing listening history also resets new listening signals and learned preferences while retaining likes, playlists and downloads.

### ⚡ Performance, accessibility and background work

- Stop unnecessary hidden-screen work through lifecycle-aware state collection.
- Keep progress/lyrics clocks scoped to observed playback, with precise lyric timing limited to the visible lyrics panel.
- Moved playback fades into the playback service and avoided constant polling outside fade windows.
- Reduced unnecessary timer display updates while preserving the real sleep-timer deadline.
- Improved cancelled initialization and idle cleanup for headless playback WebView engines.
- Avoid an unnecessary network wake lock for local audio; retain bounded protection for real download transfers.
- Background artist-release checks respect connectivity/cancellation, and backup work respects its enabled state and storage availability.
- Reduced redundant Listen Together processing while retaining synchronization and heartbeats.
- Improved system font scaling, touch targets, Playing/Paused states, focus and current accessibility labels during transitions.
- Preserve Android reduced-motion support across new presses, state changes, artwork transitions and loading feedback.
- Updated verified dependencies and modernized affected APIs and Compose conventions.
- Retained minification/resource shrinking and resolved the reported Android lint findings.

### 🛠️ Important reliability fixes

- No automatic playback merely because connectivity returns after an unavailable offline selection.
- No silent song taps caused by missing playback-command permissions.
- No stale restored queue overwriting a newer user selection.
- No restored local content URI being resolved as a YouTube video.
- No same-title recording versions incorrectly sharing saved or playing state.
- No hidden/outgoing Library list remaining behind the newly selected section.
- No removed queue row remaining dismissed after Undo.
- No obsolete preload/cache work continuing indefinitely through rapid track changes.

### ✅ Verification and updating

- Over 300 automated tests pass, including playback, migration, provider-contract, cache/cancellation, gesture and native-render checks.
- Resolved the previously reported Android lint code findings; release checks passed. Build-tool update advisories are recorded separately.
- Verified the signed, minified beta APK and in-place upgrade behavior in an isolated emulator.
- Phone verification completed for the current build.
- Kept the existing beta package ID and signing certificate for compatible in-place updates.

**Updating:** Install the newer beta APK over your existing beta app. Do not uninstall first if you want to retain its local data. Keep a library backup before major updates.

**Release status:** MuseFlow remains Beta. Preloading, recommendation relevance and upstream-provider availability still depend on the track, network and device; no measured battery-saving percentage or guaranteed instant cold skip is claimed.


---



## 🎉 MuseFlow v1.1.1 Beta

### 📚 Library and discovery

- Redesigned Library with quick access to **Playlists, Liked Songs, Downloads, Local Audio,** and **Cached** music.
- Added local audio playback alongside streamed songs, including embedded cover art.
- Added a **Stats** screen for top songs, artists, and albums with time filters.
- Cached Home shelves so previously loaded content remains visible offline.

### 🎧 Playback and lyrics

- Added word-by-word synchronized lyrics.
- Added a sleep timer with **10, 15, 30, and 45-minute** options.
- Added an interactive **Up Next** queue sheet.
- Showed each track’s source in Now Playing and made artist names open their profiles.

### ✨ Polish and fixes

- Added tactile control animations, cover-art fades, smoother list changes, and navigation transitions.
- Fixed Back navigation unexpectedly exiting from sub-screens.
- Improved download scheduling and progress notifications.
- Added database migrations for playback-tracking data.

---

## 🎵 MuseFlow v1.1.2 Beta

- 👋 Added first-run onboarding with an app introduction, support information, a repository link, and a Continue button.
- 🎤 Smoothed karaoke lyric highlighting using Compose’s frame clock.
- ⚡ Reduced playback-state work so position updates no longer rebuild the entire queue and metadata state twice each second.
- 📋 Added swipe-to-remove in the queue and fixed swipe state appearing on the wrong row after removal.
- 🎨 Fixed the accent-colour picker so saturation changes affect the generated theme.
- 🛠️ Removed duplicate Now Playing menu actions and fixed the song-details dialog.

---

## 🎛️ MuseFlow v1.1.3 Beta

### ✨ Player and settings

- Organized settings into **12 focused screens** instead of one long list.
- Redesigned Now Playing and connected its queue, sleep timer, lyrics, shuffle, repeat, download, and Like controls.
- Added **Static, Wheel,** and **Pill** transport-button styles.
- Added the animated **Live Mesh** background.
- Expanded lyrics to **six sources** with drag-to-reorder priority.
- Added four lyric animations, bringing the total to **nine styles**.
- Added taste-aware autoplay that blends the current track’s radio continuation with listening history.
- Added **M3U playlist import** alongside CSV.
- Connected Charts and New Releases to live music data.

### 🎧 Playback reliability

- Fixed the equalizer, normalization, crossfade, bass boost, and crossfeed so their controls affect audio.
- Reduced volume pumping from normalization.
- Reduced delays and extra requests during rapid track skipping.
- Added a Like action to the media notification and lock-screen controls.
- Added an optional codec and bitrate indicator.
- Made artist links work from restored queues, Liked Songs, and playlists.
- Preserved artist and album links in library backups.
- Added an in-app GitHub update check that notifies users without silently installing.

### 🎨 UI and fixes

- Fixed Live Mesh transparency and a hard edge in its rotation.
- Improved text contrast in Stats, Search, and Lyrics.
- Improved navigation-bar colour consistency.
- Made Wavy and Squiggly seek-bar speed and wavelength independently adjustable.
- Gave Debug and Beta builds distinct app names.
- Expanded list and navigation animations across Home, Library, Search, and playlists.
- Improved Library search coverage and ranking.
- Fixed a duplicate-key crash in Home, missing downloaded cover art, and slow failures during offline lyrics lookup.

---

## 🔎 MuseFlow v1.1.4 Beta

- Extended song search into a continuous recommendation feed seeded by the search and listening history.
- Fixed a fallback stream-selection case that could choose lower-bitrate audio when a better stream was available.

---

## 📥 MuseFlow v1.2.0 Beta

- Showed fully downloaded playlists as cards in Downloads while retaining an **All downloaded songs** view.
- Added **Download playlist** to online playlists.
- Added a compact offline status indicator based on Android connectivity.
- Made offline queues skip network-only songs and stop cleanly when no downloaded song remains.
- Prevented autoplay and new downloads from starting while offline.
- Combined YouTube Music Songs and Videos search results so covers, live sessions, and other uploads can be found.

---

## 🌐 MuseFlow v1.3.0 Beta

- Adapted the new MuseFlow visual direction across Home, Library, navigation, Mini player, Search, Stats, History, and Player actions.
- Added **Listen Together** room creation, joining, host approval, and synchronized playback with a compatible WebSocket server.
- Expanded search to YouTube Music songs and videos and supported pasted YouTube video links as audio tracks.
- Improved Stats and History by recording actual playback events, including automatic queue advances.
- Improved offline recovery under shuffle and repeat.
- Protected completed downloads when partial downloads fail, limited parallel transfers, and surfaced download errors.
- Preserved imported playlist identity across repeat imports and library backup and restore.
- Showed a playlist in Downloads as soon as one of its songs is available offline.

---

## 📱 MuseFlow v1.3.1 Beta

- Improved readability with the device’s system font and stronger text hierarchy.
- Fixed Home shortcut visibility at compact widths.
- Added regular YouTube uploads to search alongside YouTube Music results.
- Added dedicated Library destinations for **Liked, Downloads, Top 50,** and **On device**.

---

## 🏠 MuseFlow v1.4.0 Beta

- Added an auto-sliding **Made for you** Home hero based on listening taste.
- Redesigned Liked, Downloads, Top 50, and On device with cover collages, floating headers, playback actions, and details sheets.
- Unified Search results into **Top result, Songs, Videos, Albums, Artists,** and **Playlists** sections.
- Added a search transition that preserves the previous list position.
- Distinguished local-device songs from downloaded online songs.
- Combined bulk-download progress into one notification that clears when finished.
- Improved offline and reconnection notices.
- Added a changelog popup after updates.
- Fixed stale search suggestions, playback restarting after downloading the current song, skip actions leaving playback paused, end-of-queue behaviour, and silent playback failures.
- Slowed overflowing Player and Mini player title scrolling for readability.

---

## 🔧 MuseFlow v1.4.1 Beta

- Fixed a crash that could occur when a download started.
- Prepared autoplay continuation before the final queued song ends to reduce gaps.
- Improved offline queue recovery so partly cached songs are skipped and playback resumes when connectivity returns.
- Fixed artist links on songs with multiple artists.
- Improved scrolling of long artist names and Home card titles.
- Added a heart-burst animation when liking a song.
- Fixed an update popup appearing immediately after that update was installed.
- Improved version comparison for GitHub release tags.
- Added **Settings → Check for updates** with an on-demand check, download action, and changelog view.

---

## 🔗 MuseFlow v1.4.2 Beta

- Added sharing and importing of MuseFlow playlists.
- Shared saved YouTube playlists as YouTube links.
- Accepted YouTube and YouTube Music playlist links in the same import field as MuseFlow share codes.
- Fixed an import crash caused by duplicate song titles and artists.
- Made the import screen usable with long pasted codes or links.
- Simplified online playlist actions into **Shuffle, Play,** and a **More** menu.
- Fixed spacing below the expanded changelog in Settings.

---

## 💿 MuseFlow v1.4.3 Beta

- Fixed playback from filtered playlist and collection searches so the complete list remains queued.
- Fixed the liked-heart animation replaying when Now Playing opens on an already liked track.
- Fixed collection and artist-song lists jumping to the top after returning from Now Playing.
- Kept on-device songs visible while a background rescan runs.
- Added a current-song animation on cover art throughout music lists.

---

## ✨ MuseFlow v1.5.0 Beta

### 🎨 Appearance and settings

- Redesigned Appearance with **Follow system, Light, Dark,** and **AMOLED** theme cards.
- Added album-art accent colours and a manual colour preview, hue slider, and fine-tune picker.
- Added visual selectors for font style, display density, and Home card size.
- Added **Dot matrix** typography. Font cards show each font accurately, and Dot matrix applies to supporting text as well as titles.
- Rounded the Settings search field and made visible controls searchable.
- Added live Appearance, Player, and Mini player previews and separate reset actions.
- Improved spacing, shapes, large-text layouts, touch targets, and screen-reader labels.
- Removed Appearance and Player quick presets so individual settings remain directly adjustable.
- Hid three settings that saved values but had no visible effect.

### 🎧 Player and lyrics

- Added **Glow animated** and **Apple Music inspired** Player backgrounds.
- Made full-screen Glow more visible and extended animated backgrounds behind the device navigation area.
- Refined the Apple Music inspired backdrop with clear and blurred artwork, subtle motion, and readable controls.
- Kept the artwork backdrop visible when lyrics open and placed Apple-style lyrics on a translucent panel.
- Added rotating artwork and refined artwork crop, corners, and visibility controls.
- Added directional artwork transitions and swipes that reveal the adjacent queued cover, including under shuffle.
- Refined **Default, Slim, Wavy,** and **Squiggly** progress controls and their previews.
- Matched progress and transport settings rows to the other icon-backed settings controls.
- Gave the liked heart a consistent pink colour and soft baby-pink glow.

### 📱 Mini player and navigation

- Added artwork-coloured **Glow** to the Mini player and updated its preview to use sample cover art.
- Added a rounded playback-progress ring around the Mini player cover.
- Narrowed the Mini player while preserving space for track details and controls.
- Redesigned bottom navigation so only the selected tab expands horizontally to show its name.
- Fixed the saved default tab sometimes being ignored at startup.
- Made the current-song artwork indicator freeze when playback pauses and resume when playback continues.

### 🔎 Search, accessibility, and updates

- Improved song-duration parsing in search results and duration handling for pasted video links.
- Fixed the Home hero briefly showing generic artwork while saved likes and listening history load after a cold start.
- Unified Home, Search, and Library empty/error messages and added accessibility announcements.
- Made marquee text, shimmer, seek waves, playback indicators, and animated backgrounds respect Android’s reduced-motion setting.
- Made **Read changelog** fetch the installed version’s GitHub Release notes once and save them for offline reading.
- Fixed update-screen spacing and several Player text-layout issues.

### ⚙️ Build and verification

- Enabled minification and resource shrinking for the optimized beta build.
- Kept the existing beta package ID and signing certificate for in-place updates from earlier beta builds.
- Completed 138 unit tests and release lint. The earlier 1.5.0 beta passed the user's phone test; the latest Home hero fix awaits a device recheck.

---

## 🎧 MuseFlow v1.6.0 Beta — development preview

- Connected Mini player expansion and collapse to the full Player with reduced-motion-aware transitions.
- Improved timed lyrics: real provider word timestamps now drive highlighting; line-timed sources use visibly labelled estimated pacing. Pause, seek, buffering, speed changes, and reduced motion keep highlights aligned.
- Added quick lyrics provider switching, retry, a per-song timing adjustment, and a shareable lyric-card preview and PNG.
- Preserved spaces and syllable joins in multilingual lyrics instead of inserting spaces between every timed segment.
- Refined sleep timer and playback-speed dialogs with clearer state and direct choices.
- Clarified which track or selection is being added to a playlist and made the empty-playlist path easier to understand.
- Added a real **Streaming quality** Data saver for YouTube playback. Downloads still use the best available source quality, and the optional Player readout shows the decoded codec and bitrate.
- Added a visible Retry action in the full Player when a stream fails; it refreshes a failed cached stream URL and preserves the queue.
- Prevented queue reordering when Repeat All wraps the visible Up Next list across the timeline boundary; playback and removal remain available.
- Fixed a restored, unavailable song starting on its own when the device comes back online. Playback now waits for an explicit Play action.
- Replaced the cramped queue sheet with a full-screen queue page inspired by Echo Music's clear current-song and queue-control hierarchy.
- Showed **Up Next** in actual playback order, including shuffle and one repeat-all wrap.
- Added queue shuffle/repeat controls, row actions, swipe removal with Undo, Clear Up Next with confirmation, and Save queue as playlist.
- Kept the current song and playback controls visible while managing the queue.

The 1.6.0 listening-experience roadmap is still in progress. This preview needs device verification.

---

## ✨ MuseFlow v1.7.0 Beta — RC4

### 🏠 Home and discovery

- Added **Customize Home**: choose which shelves appear, change their order and restore the default layout.
- Simplified the Home header and improved light-theme contrast.
- Refined artwork cards with fewer surrounding frames and clearer text spacing.
- Expanded Daily Discover beyond the featured hero cards.
- Added local discovery ranking that balances source relevance, familiar artists, artist variety and recent-repeat avoidance. Explicit playlists and albums retain their order.
- Home refresh keeps useful recommendations visible when a request returns no usable results.
- The hero respects reduced motion and avoids advancing during a swipe.

### 📚 Browsing and Library

- Remember sort, direction, filter and list/grid choices independently for each Library section.
- Restore Search positions separately for each query and result tab.
- Preserve scroll positions in albums, Charts, Fresh drops, Stats and History.
- Added shared collection headings across artist, album and playlist pages.
- Improved collection and discovery error states with **Retry**, while preserving loaded content after refresh failures.
- Charts and Fresh drops adapt their grids to available space and larger text.
- Bulk actions now report completion and partial failures. Creating a playlist with songs is transactional.
- Added accessible Playing/Paused states to song rows; empty selections cannot trigger bulk actions.

### 🎤 Lyrics and playback reliability

- Updated Better Lyrics to its documented public endpoint.
- Support nested/namespaced TTML, clock and offset timestamps, explicit line endings and optional vocal/language metadata.
- Preserve richer lyric metadata in the cache while reading older cache entries.
- Tightened recording-version and duration checks for lyric search, including removal of SimpMusic’s wrong-duration fallback.
- Lyrics network calls cancel when a lookup is cancelled; source outages and rate limits are distinguishable from missing lyrics.
- Partial word timing no longer counts as full-document word sync.
- Guarded malformed compressed KRC input against stalled or unbounded decompression.
- Online Search plays the selected recording, then radio recommendations rather than the remaining search matches.
- Same-title versions retain separate playing indicators; offline fallback also checks the recording identity.

### 🧪 Release status

- Beta release candidate for device verification. Retains the existing beta application identity and signing certificate.
- Machine-learning recommendations, music recognition and AI-generated lyric alignment remain future research work.

---

## 🛠️ MuseFlow v1.7.0 Beta — RC5 (2026-10-27)

- 🏠 Restored Home's fixed shelf layout and removed the Customize Home controls.
- 🎵 Likes, downloads, playlists and history now identify the exact source recording. Audio and video-to-audio versions with matching titles no longer share their saved status.
- 📦 Added a non-destructive database migration that preserves existing audio paths/files, playlist membership and history-event links.
- 🖼️ Kept artwork from existing download filenames available after the identity migration.
- ⬇️ Existing valid downloads are checked before starting a transfer service or showing progress, avoiding a misleading download-notification flash.
- 📁 Missing/empty audio files no longer count as completed downloads. Redownloading a missing file resolves its known recording rather than attempting to download a local path.
- 💾 Older backups normalize known source IDs when restored, preserving separate recording versions.
- 🎧 Player actions retain artist/album IDs and distinguish on-device music from streamed recordings.

---

## ⚡ MuseFlow v1.7.0 Beta — RC6 (2026-10-27)

- Lifecycle-aware UI state collection stops hidden-screen work without reducing visible animation quality.
- Player progress and lyrics clocks run only when observed and playing; precise lyric timing is scoped to the visible lyrics panel.
- Moved the existing fade effect into PlaybackService, keeping background operation and smoother fades with no constant polling between fade windows.
- Sleep timer uses an expiry deadline with an on-demand display clock and elapsed-time rechecks after device sleep.
- Headless playback WebViews retain application context, clean up cancelled initialization, and release idle engines after five minutes without interrupting active requests.
- Local audio uses a CPU wake lock without an unnecessary network wake lock. Downloads retain bounded wake protection during actual transfers.
- Artist-release checks require connectivity and respect cancellation; disabled backups no longer request retries, and backup work waits for available storage.
- Listen Together ignores volume-only events and avoids rebuilding unchanged queue JSON; synchronization and heartbeats remain enabled.
- Updated verified published build/runtime/test dependencies, modernized audio flush/clipboard APIs, cleaned Compose conventions, and made window calculations resize-aware.
- Deduplicated identical launcher resources through aliases and removed unused starter colors without changing launcher appearance.
- Existing fixed Home layout, recording-specific saved status, offline-file migration, playback effects and customization features remain.

Battery savings are supported by reduced-work tests, not a measured drain percentage. Physical-device battery and playback checks remain required.


---

## 🎧 MuseFlow v1.7.0 Beta — RC7 (2026-10-27)

- 🧠 Discovery now learns gently from new actual listening and deliberate early skips, using bounded local recording/artist preferences. No listening-history upload or model download.
- ⏱️ Pause and buffering time do not count as listening; seeking does not invent heard time. Errors and queue edits do not become dislikes.
- 📴 Reset the per-song history guard before offline handling, so a song chosen while paused offline can be recorded when manually played after reconnecting.
- 🔁 The existing On repeat shelf uses recent qualifying listens, with top-played history as its initial fallback. Home keeps its fixed layout.
- 📊 Listening Summary separates newly recorded time from the estimate based on historical track starts. Fixed the selected-period heading, hour-long duration estimates, large-text truncation and sheet scrolling.
- 🧹 Clearing listening history also resets new listening signals and learned preferences; existing likes, playlists and downloads remain.
- 🎤 Better Lyrics forwards the confirmed recording ID, accepts cached responses without a score, and preserves linked translation/romanization metadata alongside native timing/whitespace.
- 🌐 Lyrics source authorization restrictions and temporary outages remain actionable errors with normal provider fallback.
- 💾 Added a non-destructive Room 16→17 migration without inventing historical listening data or moving audio files.
- 🧪 Added listening, migration, provider-contract, controlled ranking and four-font/large-text summary tests. Phone acceptance and real-library relevance/battery measurements remain open.

---

## ✨ MuseFlow v1.7.0 Beta — RC8 (2026-10-28)

- 🏠 Restored Home's direct History, Listen Together and Stats actions, removing the Settings and three-dot header buttons.
- 🧭 Fixed the saved-stack alias that could reopen Settings when tapping Home. Other tabs retain their saved content.
- 🫧 Added source-backed frosted navigation glass, a subtle edge/shadow and a sliding active capsule. Icons and text stay sharp.
- 👆 Added smooth press/selection feedback and more room for the active label at large text sizes. Reduced motion uses immediate state changes.
- 📱 Retained the existing navigation/mini-player widths, theme behavior and a readable translucent fallback on older Android versions.
- ⚡ Blur is confined to one navigation surface and captures only behind it while visible. No continuously running decorative animation or screenshot polling.

---

## 🛠️ MuseFlow v1.7.0 Beta — RC9 (2026-10-28)

- 🎵 Fixed the Media3 connection callback granting empty playback commands. Song taps can again load the queue, prepare/play audio and show the mini-player.
- 🔌 Preserve the latest song tap during player connection, handle connection failure/disconnection visibly, and reconnect on explicit playback or Retry without automatic background resume.
- 📋 Protect a newer song selection from an older asynchronous paused-queue restore.
- ♿ Shared press feedback now explicitly follows reduced motion, preserving click and long-press behavior.
- 🔬 Added real service-startup/command-authorization regression tests and an app-wide micro-animation research plan. The broad motion rollout is not included in this playback correction.

## ✨ MuseFlow v1.7.0 Beta — RC10 (2026-10-28)

- 🎵 Fixed resuming an On-device recording after restart/update: saved content/file identities remain local instead of being sent to the online stream resolver.
- 🧭 Home and artist-based radio fallback now search the music catalog; unrestricted general-video results remain available in explicit Search. This avoids injecting generic movie/video search results into discovery.
- 👆 Added shared, damped press feedback to song rows, artwork cards, collection rows, settings, action sheets and icon buttons across the UI. Tap actions remain immediate; long press and cancelled gestures retain native behavior.
- 🎛️ Smoothed play/pause and changing state icons, selection backgrounds, appearance-choice borders, queue-mode colors, selection counts, collection counts, stats values and settings subtitles.
- ⬇️ Smoothed real download progress and status changes. Reduced motion uses a static glyph when the download percentage is unknown.
- 📚 Added a brief settle on Library section/grid changes and Search filter changes, keeping only the current content tree to prevent overlapping lists or stale actions.
- 🛟 Refined shared error/information color and message transitions while retaining a directly usable Retry action.
- ♿ Motion follows reduced-motion settings. Current accessible icon labels and values update immediately during crossfades; native button targets, focus, disabled states and ripple remain.
- 🔒 Preserved the RC9 playback authorization/startup fix, original Home layout, source-specific saved data and existing player, lyrics, queue and navigation animations.
- 🧪 Added gesture, cancellation, accessibility-state, single-toggle, reduced-motion and native-render regression coverage. Phone smoothness, battery and real-library recommendation acceptance remain device checks.

## 🎧 MuseFlow v1.7.0 Beta — RC11 (2026-10-28)

- 🎛️ Corrected the shared animated-icon layout: glyphs now fill their requested frame and remain centered. Tuned central Play/Pause size for Static, Wheel and Pill controls.
- ⏭️ Replaced URL-only next-track warming with Media3 playlist preloading, preparing five seconds of the real successor's audio when enabled. The configuration follows the setting live and disables preloading while paused/stopped.
- ⚡ Added a bounded, short-lived resolution cache that retains the required User-Agent, separates recording/backend/quality, respects signed-URL expiry, shares concurrent requests and supports Retry invalidation.
- 🧹 Rapid skips now cancel the actual obsolete full-track cache writer, preventing old warming work from continuing merely because its coroutine was cancelled.
- 🖼️ Warm the two adjacent cover images while full-player artwork is visible. Use matching decode sizes and avoid a second image-loader fade inside the artwork transition.
- ✨ Removed the immediate full-cover dim/loading overlay. Buffering lasting beyond 350 ms uses a small readable badge, suppressed during cover gestures; genuine delays/errors remain visible.
- ⏯️ Play/Pause stays consistent with pending playback during buffering, and Pause can cancel that pending play request. Ended playback can be restarted explicitly.
- 🔬 Added native pixel/layout checks for all transport styles and regression coverage for sample-preload policy, URL reuse/expiry/cancellation, stale cache writers and buffering feedback.
