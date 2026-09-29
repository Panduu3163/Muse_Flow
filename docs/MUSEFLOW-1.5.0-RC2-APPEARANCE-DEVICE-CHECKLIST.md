# MuseFlow 1.5.0 RC2 appearance checks

Install RC2 over RC1 without clearing beta data. Version code 24 follows RC1 code 23.

1. Settings search has pill-shaped left and right ends. Search `Follow system`, `AMOLED`, `Accent colour`, `Font style`, `Display density`, and `Card size` and check that each result opens and highlights its control.
2. In Appearance, select each of the four theme cards. Light and Dark must stay fixed even if the phone theme changes. Follow system must switch with the phone. AMOLED must have a true black background. Check Home, Search, Library, Settings, Player and system bar icons in every mode.
3. Turn Colour from album art off. Slide the hue control and release it. Check the selected-hue bar, settings surfaces, nav, Player and other Material accent controls update together. Relaunch to verify the hue persists. Fine tune a colour and repeat.
4. Turn Colour from album art on and play two songs with visibly different covers. The app palette should change per cover. Stop playback or play a track without art: the saved hue should return. Toggle the switch off again and confirm the saved hue is still there.
5. Open Font style. Each card should show its own actual display typography. Choose System, Serif, Monospace and Dot matrix, checking headers across the app and relaunch persistence. Dot matrix uses Doto for display text; body text remains readable system text.
6. Open Display density. Compare the three skeletons, choose each, and check spacing in Home, Library, Settings and Player. The phone's accessibility font size must remain respected.
7. Open Card size. Compare the sample covers, choose each, and check Home shelf artwork matches Small, Medium and Large. Relaunch and verify persistence.
8. Open Mini player settings. The demo should have sample cover art and the same artwork, controls, progress line, shape and background behavior as the actual Mini player. Change Solid, Album gradient, Blurred artwork and Live mesh; compare each with a playing track.
9. Check a phone with large text and TalkBack if available. Theme and selection cards should announce their selection; the hue slider should have a useful label. Check dialog scrolling and close/back behavior.
