# 1.5.0 visible settings audit

Static code review for RC6. This identifies a consumer for every visible persisted control; the
phone checklist remains necessary to prove behavior, persistence and accessibility on a device.
`SettingsSearchIndexTest` scans the standard preference declarations so a newly added visible row
cannot be omitted from Settings search without failing the test. Appearance theme cards, color
controls and Player's custom progress picker are indexed explicitly.

| Settings area | Persisted controls and observed consumer |
| --- | --- |
| Appearance | Theme mode and accent seed drive the app Material color scheme. Album-art color switches the seed to the playing cover. Font and density are applied at the app root. Card size drives Home carousel cells and the Appearance sample. |
| Mini player | Background style drives the real Mini player and its live preview. |
| Player | Background, artwork visibility/crop/radius/rotation, button color, progress style, transport style, swipe gesture and codec information are passed from `MainActivity` into Now Playing. |
| Lyrics | Provider order drives source fallback; text size, alignment, scrolling, line spacing, blur, glow, tap-to-seek and word animation drive the lyrics view. |
| Audio and Playback | Skip silence, normalization, bass boost, crossfeed and preloading drive `PlaybackService`; crossfade drives `PlayerViewModel`; Remember queue controls service persistence. |
| General | Default tab creates the navigation start destination after DataStore loads. Navigation now shows the selected tab's label automatically. |
| Library sections | Liked, Downloads, Top 50 and the Recently played shortcut control visibility in Library. |
| Privacy | Disable screenshots controls `FLAG_SECURE`; both clear-history actions call their repositories. |
| Backup and Storage | Export, restore and imports launch their file flows; Auto backup schedules background work; storage actions clear downloads/cache through their view models. |

Legacy DataStore fields with no current runtime consumer remain **hidden**: animated canvas,
comment button, mini-player swipe sensitivity, fullscreen-lyrics status bar, default Library chip,
song swipe-to-queue/remove, haptics, exported/cached Library chips, fullscreen-lyrics song swipe,
thumbnail play/pause overlay, and the RC6 compact-on-scroll experiment. Their saved values are retained for compatibility but they are
not offered as controls that appear to work. Device verification can still reveal a consumer that
is wired but behaves incorrectly; use the RC6 phone checklist for that audit.
