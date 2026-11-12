# 🎵 MuseFlow v1.7.0 Beta

A major listening-experience update with a redesigned queue, improved lyrics, smarter discovery, smoother player transitions, refined navigation, and extensive playback and offline fixes.

> 📌 **Why was 1.6.0 skipped?**
> After finishing the 1.6.0 work, I moved straight into developing 1.7.0 before publishing a separate 1.6.0 release. All completed 1.6.0 improvements are included in this 1.7.0 Beta changelog.

---

## 🎨 Interface, Home and navigation

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

## 🎧 Playback, player controls and track changes

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

## 📋 Redesigned queue and source-aware continuation

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

## 📚 Library, Search, downloads and saved data

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

## 🎤 Lyrics timing, sources and readability

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

## 🖼️ Lyrics cards and sharing

- Added and refined lyric-card preview and PNG sharing.
- Select up to four lyric lines with clearer selection and more space for complete text.
- Improved card sizing and layout to reduce truncation and clutter.
- Added colour/gradient choices, album-art glow and a translucent glass-style treatment.
- Added cover artwork beside track information and MuseFlow branding with a rounded app logo.
- Improved preview/export consistency and sharing-flow usability.

## 🌙 Sleep timer and audio settings

- Redesigned the sleep timer with a smooth slider covering 15-minute steps up to two hours.
- Added a custom hours/minutes picker without AM/PM.
- Refined the running-timer surface and improved time selection feedback.
- Use a real expiry deadline so opening screens, backgrounding or device sleep does not shift the chosen timer.
- Improved cancellation so an old cancelled timer does not pause playback later.
- Refined playback-speed dialogs and related state feedback.
- Added a genuine Data saver option for online stream resolution, alongside Best available quality.
- Keep existing local/downloaded formats intact and show the actual decoded codec/bitrate when available.

## 🧠 Recommendations, history and Stats

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

## ⚡ Performance, accessibility and background work

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

## 🛠️ Important reliability fixes

- No automatic playback merely because connectivity returns after an unavailable offline selection.
- No silent song taps caused by missing playback-command permissions.
- No stale restored queue overwriting a newer user selection.
- No restored local content URI being resolved as a YouTube video.
- No same-title recording versions incorrectly sharing saved or playing state.
- No hidden/outgoing Library list remaining behind the newly selected section.
- No removed queue row remaining dismissed after Undo.
- No obsolete preload/cache work continuing indefinitely through rapid track changes.

## ✅ Verification and updating

- Over 300 automated tests pass, including playback, migration, provider-contract, cache/cancellation, gesture and native-render checks.
- Resolved the previously reported Android lint code findings; release checks passed. Build-tool update advisories are recorded separately.
- Verified the signed, minified beta APK and in-place upgrade behavior in an isolated emulator.
- Phone verification completed for the current build.
- Kept the existing beta package ID and signing certificate for compatible in-place updates.

**Updating:** Install the newer beta APK over your existing beta app. Do not uninstall first if you want to retain its local data. Keep a library backup before major updates.

**Release status:** MuseFlow remains Beta. Preloading, recommendation relevance and upstream-provider availability still depend on the track, network and device; no measured battery-saving percentage or guaranteed instant cold skip is claimed.
