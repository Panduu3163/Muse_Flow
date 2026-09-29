# MuseFlow 1.5.0 RC7 phone checklist

Install RC7 beta over RC6 beta without uninstalling or clearing data. It is the same beta package
and signing certificate. Note your current queue, one playlist, one download and your Appearance
choices before updating; verify them afterward.

## 1. Current-track artwork badge

1. Start a song from Library, Search, a playlist or an album. Find that song in its list. Its
   small cover should have three moving bars; other songs should not.
2. Pause from Mini player, full Player, notification or headphones. The bars should **stop at the
   heights they reached** and remain over the same cover. They should not disappear or jump to a
   standard three-bar shape.
3. Resume from a different control. The bars should move again from their paused heights. Repeat
   after scrolling the song off-screen and back, and in Library grid view. Switching songs should
   move the badge to the newly selected song.
4. Turn Android Animator duration scale Off. The badge should remain still even during playback.
   Restore 1× and check it moves again.

## 2. Apple Music inspired lyrics

1. In Settings → Player choose **Apple Music inspired**. Play tracks with a bright, dark and busy
   multicolour cover. Open the full Player and tap Lyrics.
2. The large clear cover in the background should fade away while the blurred, artwork-coloured
   backdrop remains. Lyrics should sit on a rounded, translucent surface that makes active and
   inactive lines readable. Look at the top, centre and bottom of the panel.
3. Scroll lyrics, tap a synced line to seek, pause/resume, and skip to another song while Lyrics is
   open. Check the lyric panel does not overlap the title, progress or transport controls. Try an
   unsynced song and a song with no lyrics if available.
4. Close Lyrics. The clear artwork should return smoothly. Open Queue, then Lyrics, then Queue:
   only the chosen panel should show. With Android animations Off, the art should switch without
   lingering over the text.
5. Repeat in Light, Dark and AMOLED and with larger Android font size. Other Player backgrounds
   should retain their previous lyrics appearance.

## 3. Horizontal navigation and Mini player

1. On Home, only **Home** should show an icon and name in the nav pill. Search, Library and Settings
   should show icons. Tap Search: its name should expand **beside its icon**, while Home's name
   folds away. Repeat for Library and Settings. There should be no vertical collapsing on scroll.
2. Check the whole navigation pill fits in one line without clipping. Try slow and fast tab taps,
   then scroll long lists and swipe a horizontal carousel. The active label should stay tied to the
   selected tab. With TalkBack, all four tabs should still announce their names and selected state.
3. Play a song. The Mini player should be slightly narrower than the navigation pill below it,
   centred with equal side gaps. Its title, artist, previous, play/pause and next controls should
   remain readable and tappable. Compare the Mini player Settings preview with the real bar.
4. Repeat with Compact and Comfortable display density and larger Android font/display size.
   On a narrow phone, watch for squeezed text, overlapping controls, or an active tab label that
   disappears entirely.

## 4. Regression and evidence

1. Recheck song skip, queue, lyrics, full Player collapse, offline download playback, notification
   controls, and your saved preferences after the update.
2. Capture screenshots of Home, Search, Library and Settings both with and without a Mini player,
   plus Apple-style lyrics on bright and dark covers. If you have RC6 screenshots, compare spacing
   and contrast side by side.
3. To collect frame data on a USB-connected device, run
   `.\tools\Capture-UiBaseline.ps1 -Label 'nav-switch-rc7'` from the repository, switch tabs on
   the phone, then press Enter. Repeat with `apple-lyrics-rc7` and `library-scroll-rc7`.

Report the phone model, Android version and exact step for any problem; attach a screenshot or
short recording when the issue is visual.
