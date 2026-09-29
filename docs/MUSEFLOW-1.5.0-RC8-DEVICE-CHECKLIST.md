# MuseFlow 1.5.0 RC8 phone checklist

Install the RC8 beta over RC7 beta. Keep app data so preferences, downloads and the queue remain available.

## Apple-style lyrics

1. Settings → Player → Player background style → Apple Music inspired. Open a song with bright, detailed cover art.
2. In the full player, tap Lyrics. The same clear cover fading into the blurred cover should remain behind the lyrics. The rounded lyrics panel should show the background through it, with readable words. Close Lyrics and compare the background; it should not change.
3. Repeat on a dark cover, a very bright cover, in Light, Dark and AMOLED modes, and while skipping songs with Lyrics open.

## Mini player progress ring

1. Start a song and inspect the small artwork in the Mini player. A thin rounded progress ring should wrap around it; there should be no straight progress line under the pill.
2. Seek to about halfway and near the end in the full player. The ring should match the position immediately, then advance during playback and stay still while paused.
3. Switch songs, try a track with no cover, and compare the Mini player Settings preview.

## Search duration

1. Search for several songs in All and Songs. Check the time beside each song, especially results whose artist, album and duration appear in a different order.
2. Search for a playlist or album, open it, and check track times there. Paste a YouTube video link into Songs and check its time after the result loads.
3. If a result still has no time, note its title and link. Some sources omit duration entirely; that case needs a specific source sample.

## Full player swipe and liked heart

1. Queue three songs with different covers. In the full player, drag the middle cover slowly left and right. The neighboring cover should follow your finger from the correct side. Release a short drag: it returns to the current song. Release a long drag: the next or previous song should play and its cover should settle in the center.
2. Try the first/last queue items, Shuffle, the Next/Previous buttons, rapid swipes, and a vertical swipe on the art to adjust volume. Also test with Android Animator duration scale Off.
3. Like a song: the heart stays pink even when the app's accent color changes, with a small baby-pink glow. Unlike it: the glow disappears. Reopen the full player for an already liked song.

## Release changelog

1. Settings → Check for updates → Read changelog. This RC includes bundled notes until a matching GitHub Release is published. When that release is published and the phone is online, reopen the app and read this section; it should show the release description.
2. Force close and reopen the app, then turn off internet and read the changelog again. The saved release notes should still appear.
3. After installing a newer version with its own GitHub Release, the section should show that new version's description instead of the previous one.
