# MuseFlow v1.8.0 Beta

Development checkpoint: backup, recovery, downloads, outside playback controls and utility improvements are included. The wider 1.8 work and phone acceptance checks are still in progress.

## Backups and recovery
- Review backup contents before restoring. Library items merge without creating matching playlists again.
- Include listening history or app settings when you choose. Both options are off by default.
- Included settings replace the corresponding settings on this device. The preview explains which options return to defaults.
- An interrupted settings restore can be retried without importing your library again. You can also explicitly keep the settings currently on your device.
- Choose an external folder for automatic backups, or use Back up now. Each external copy is read back before it is reported as saved.
- Internal and external backup success times are shown separately. Reconnect unavailable storage or select the folder again to retry.
- Review and restore the latest or previous internal recovery copy from Backup & restore.
- Android system backup can return the latest internal recovery copy, subject to your device settings and quota. Review that copy before importing it.
- Downloaded audio, custom cover images, accounts, permissions, queue and device privacy choices are not included in portable backups.
- External backup files are kept until you remove them in your Files app. Uninstalling or clearing app data deletes internal copies.

## Widget and Library
- The Library header now contains Search, Listening history and Stats. Download management is in Storage settings.
- The home-screen widget is now a square cover-art tile. Song and singer appear above the playback buttons over a darkened lower part of the cover. A 3 × 3 launcher size is recommended; 4 × 4 gives more artwork space.
- Widget controls use the same playback session as the app. Adding or resizing a widget does not start music.

## Downloads
- Manage queued, active, completed, failed, cancelled, interrupted and missing downloads from Storage settings.
- Retry an interrupted request when you are ready. Restarting MuseFlow does not automatically start new audio transfers.
- Each transfer may retry once after a temporary failure, with a fresh playback address. Retries start from the beginning and can use mobile data.
- Cancel active requests, or confirm removal of a download and its saved audio and artwork.
- Interrupted final file saving can be completed locally when a verified copy is already present.
- Existing valid downloads are kept. Different recordings retain their original recording identities.
- Partial responses, incomplete files and unavailable storage produce retryable status instead of being shown as available offline.
- Clearing downloads waits for transfers to stop. Artwork recovery cannot recreate removed files after cleanup.

## Storage and readability
- Downloaded audio, partial files, cache and internal backup storage are shown separately.
- Clearing cache keeps downloads, likes, playlists and backup copies.
- Cleanup progress and errors are visible. Repeated cleanup actions are guarded.
- Long restore and removal explanations scroll while their confirmation actions remain reachable at large text sizes.

## Playback controls
- Remember the paused queue, position, shuffle and repeat choices. Starting MuseFlow does not prepare or play restored audio.
- A compact home-screen widget shows the current song, cached artwork and previous, Play/Pause and next controls. Tap the song to open MuseFlow.
- The Quick Settings tile pauses live playback. When paused or stopped, it opens MuseFlow so you can press Play in the foreground.
- Missing local-only songs require re-selection. A missing download can re-resolve only the same known online recording.
- Swiping MuseFlow out of Recents still stops playback. Restarting the phone or reconnecting the network does not issue Play.
- Position is saved periodically during playback; a sudden process kill can lose the most recent few seconds.

## Utility improvements
- CSV/M3U matching creates a playlist only after matching finishes, in one transaction. Cancelling before saving leaves no empty playlist. Duplicate matches and unmatched songs are reported separately.
- CSV rows missing a title or artist are counted in the result so you can correct them. A failed playlist deletion rolls back both the playlist and its songs.
- File imports check size, track count, text encoding and malformed input. Shared codes have compressed/decompressed size limits and carry recording metadata without expired playback addresses.
- Editing a shared-playlist link cancels its old preview request. Save failures return to the preview for an explicit retry.
- Long settings choices scroll and announce the selected option. Track details can be selected and copied, with long values shown vertically.
- Sleep timer content scrolls at large text sizes while its actions remain reachable.
- Updates shows download/verification failures and distinguishes opening Android's installer from completing installation.
- Cancel an update download from either the launch dialog or Updates. A cancelled request cannot replace a newer request's status.
- Update downloads are bounded and cancellable. An APK must match this app's package and current signing certificate and have a newer version code before installer handoff.
- Audio's Volume smoothing adjusts gain during playback. Track fades are sequential; the current player does not overlap two tracks. These names now describe the existing effects.
- EQ presets save the complete curve together. Settings write failures show retry feedback. Destructive playlist confirmation uses a native modal window with scrollable text and keyboard cancellation focus.

## Testing this development build
Keep a current exported backup before testing a development build. Update the existing Beta app in place; uninstalling or clearing app data erases its local data. Phone, accessibility, background playback and battery acceptance for this checkpoint remain pending.

**Release status:** Development Beta. Launcher, lock-screen, physical-device and the remaining 1.8 release checks are still in progress. This is not the completed 1.8 release.
