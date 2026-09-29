# MuseFlow 1.5.0 stable device gate

The stable APK uses package `com.aistudio.museflow.kqfzyw` and the same signing certificate as earlier sideloaded regular builds. It installs over that regular app. The beta package is separate and remains installed if present. Do not clear app data before testing.

## Update and data

1. Note an existing liked song, playlist, download, queue and Appearance choice. Install 1.5.0 over the regular app, then check all five remain.
2. Confirm Settings reports version 1.5.0. Open Settings → Check for updates → Read changelog online, then offline. Before the `v1.5.0` GitHub Release exists, bundled notes appear; afterward its release body should be saved for offline reading.

## Appearance and settings

1. Check Follow system, Light, Dark and AMOLED against device mode. Turn album-art colour on and change songs, then turn it off and adjust the hue. Check Home, Search, Library, Settings, Mini player and full Player for consistent colours.
2. Preview every font, density and card-size option. Increase Android text/display size. Search Settings for `font`, `seek bar`, `background style`, `colour` and `cache`; each result should reach its control.
3. In Player settings, confirm **Quick looks** presets are absent. Choose background, artwork, progress and transport controls individually; test Reset player appearance.

## Playback and motion

1. Play a three-song queue. Check Mini player artwork progress, then open the full Player and swipe covers slowly and quickly in both directions. Try Shuffle, first/last track, and a vertical artwork swipe for volume.
2. Try Apple Music inspired lyrics over bright and dark covers. The cover backdrop should stay visible through the lyric panel. Check synced, plain and missing lyrics, and skip songs while lyrics are open.
3. Pause a song in a list: its current-track cover badge should freeze in place and continue on resume. Check the liked heart remains pink after changing the app accent.
4. Turn Android Animator duration scale Off. Check marquees, playing indicators, seek waves, artwork transitions, Glow and Live Mesh become still or switch cleanly. Restore 1×.

## Reliability and performance

1. Test an online song, downloaded song offline, queue recovery after reconnecting, notification and headset controls. Return from Player/detail screens to Search and Library and check list positions remain.
2. Compare light, dark and AMOLED screenshots of Home, Search, Library, Settings and Player at normal and large text. Look for clipped controls, weak contrast and navigation bar seams.
3. Capture frame statistics on the target phone using `tools/Capture-UiBaseline.ps1` for `player-open`, `artwork-swipe`, `tab-switch`, `lyrics-open` and `library-scroll`. Record device model and Android version with any failed step.
