# MuseFlow 1.5.0 RC1 device checklist

This is a verification build, not a stable release. Version code 23 follows 1.4.3 code 22. The beta package is `com.aistudio.museflow.kqfzyw.beta` and uses this repository's existing debug keystore; it installs alongside the regular package and should update the existing beta in place.

## New 1.5.0 experience

1. Open Settings and search for `color`, `seek bar`, `background style`, `font`, and `cache`. Tap results and check that the matching row is highlighted and scrolled into view. `Background style` must show distinct Player and Mini player results.
2. In Appearance, switch accent, dark/AMOLED, font, density and card size. The sample should respond immediately. Choose each layout preset, relaunch, and check the chosen values persist. Reset appearance and check defaults return.
3. In Player, choose each preset, then individually change background, button colour, slider, transport style and artwork radius. Check the sample and real Now Playing screen. Reset player appearance and check defaults return.
4. In Mini player, choose each background style, then reset. Compare the sample with the actual mini player while a track is playing.
5. Increase Android font size/display size. Check Home cards, Search, Library rows, nav labels, Settings rows, sheets and Player controls for clipped text or unreachable actions.
6. With TalkBack, check the accent choices, Settings switches and sliders, and bottom navigation. Each control should have a useful name, state and reachable touch target.
7. Set Android animation scale to Off. Check scrolling titles, loading placeholders, playing bars, wavy seek and live mesh backgrounds become still. Restore animation scale and check normal motion resumes.

## Regression and release gate

1. Update over an existing beta and verify likes, playlists, downloads, settings and queue remain. Do not clear app data during this check.
2. Follow Home → Search → Library → mini player → Player → queue/lyrics → back. Check scrolling and selection state.
3. Play online, go offline, play a download, return online, and check queue recovery. Verify background playback, notification and headset controls.
4. Capture screenshots of Home, Search, Library, Settings, Appearance, Player and Mini player in light/dark/AMOLED and large text. Compare contrast and alignment.
5. Profile frame times while opening Player, switching tabs, scrolling a long list and using animated player styles. The optional compact-on-scroll navigation remains unshipped until these measurements justify it.

Report the device model, Android version, whether you installed debug or beta, and the exact step for any failure.
