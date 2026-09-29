# MuseFlow 1.5.0 RC6: phone verification

Install the RC6 **beta** APK over RC5 beta without clearing app data. The beta package is
`com.aistudio.museflow.kqfzyw.beta`. Before installing, note your current theme, font, liked song
count, one playlist, one downloaded song, and the current queue. These are your update checks.

## 1. Startup and settings

1. Open **Settings → General → Default tab**. Choose Library. Fully close MuseFlow and reopen it.
   It should start on Library. Repeat with Search, then restore your preferred tab. This checks the
   saved preference is loaded before the first navigation screen is built.
2. Search Settings for `compact`, `quick looks`, `progress bar`, `background style`, `font`,
   `auto backup`, and `clear cache`. Open each result. The correct row or choice should be visible
   and highlighted. `background style` should produce separate Player and Mini player results.
3. Change one option in each settings category, leave and return, then fully close and reopen the
   app. The selection should still be there and affect its advertised behavior. For destructive
   actions such as Clear downloads, just verify the action opens the expected confirmation; do not
   confirm it for this check.

## 2. Compact navigation experiment

1. With **Settings → General → Compact navigation on scroll** off, browse a long Home or Library
   list. The four tab labels should stay visible.
2. Turn it on. Scroll **down** through a long list by roughly one screen. The floating bar should
   become shorter and hide its labels, while all four icons remain visible and easy to tap.
3. Scroll **up**. Labels should return. Repeat in Home, Search results, Library, and Settings.
   The mini player should remain usable above the bar while a song plays.
4. With the bar compact, tap each tab. It should open the right screen, return to the full labeled
   bar, and show the correct selected tab. Swipe horizontally on a carousel: the bar should not
   collapse from that gesture. Turn the option off; the compact behavior should stop immediately.
5. Repeat with Android's largest comfortable font/display size and with TalkBack. All four tabs
   should still have their names announced when labels are hidden. Check that tapping near each
   icon edge still works. If the bar repeatedly changes size during gentle scrolling, note where.

## 3. Shared UI and accessibility

1. Search for a nonsense phrase so results are empty. The message should be centered and readable
   in Light, Dark, and AMOLED themes, at normal and large Android font size.
2. Open an empty Library section or search within Library for an impossible title. The empty
   message should have the same card, icon, spacing and readable text as Search. If On-device
   browsing reports an error, it should be clearly distinguished from an ordinary empty list.
3. Check Home shelves while online and offline. Loading placeholders should be stable; any empty
   or failed shelf message should use the same visual language and should not overlap a carousel.
4. Check Settings controls in **Compact** display density. Switches, pickers, slider tracks and
   navigation tabs should remain easy to tap. With TalkBack, each control should have a useful
   name and state; an error should be announced without repeated interruptions.
5. In Android Developer options, set **Animator duration scale** to Off. Navigation selection,
   shimmer, marquee, seek waves, playing indicators, Glow and Live mesh should become still or
   switch immediately. Restore 1×, then try 0.5× and 2×; animations should follow Android's scale
   without freezing, jumping or continuing indefinitely when Off.

## 4. Existing 1.5.0 features and regression checks

1. Switch through Follow system, Light, Dark and AMOLED. For Follow system, change the phone's
   theme while MuseFlow is open. Check screens, dialogs and status/navigation icon contrast.
2. Turn album-art color on, play tracks with distinct bright and dark covers, then turn it off and
   move the hue control. Check the whole app and Player update. Try every font and density choice,
   and check the live previews before and after restarting.
3. In Player settings, select Solid, Album gradient, Blur, Live mesh, Glow and Apple Music inspired.
   Check the full Player, its navigation-button area and the settings preview. Skip songs quickly:
   the navigation-button area should not flash black. Check long titles and large text.
4. Check Default, Slim, Wavy and Squiggly progress styles. Tap and drag to seek; pause and resume;
   compare the picker preview with the full Player. Wavy and Squiggly should flatten while paused
   or while seeking. Check artwork swipe transitions and rotating artwork.
5. Play online, open Mini player → full Player → queue → lyrics → back. Verify next/previous,
   shuffle/repeat, background playback, media notification and headset controls. Go offline, play
   a downloaded song, and return online. Verify your likes, playlist, download, queue and custom
   settings survived the RC5 → RC6 update.

## 5. Screenshots and frame times

If RC5 is still installed, capture its baseline **before** the RC6 update. For each version, take
screenshots of Home, Search, Library, Settings, Appearance, Player and Mini player in Dark; repeat
the key screens in Light, AMOLED and large text. Keep the same track and theme where possible.
Compare alignment, contrast, clipping and navigation-bar color side by side.

With a USB-connected phone and `adb` available, run this command from the repository in
PowerShell, then perform the named action on the phone and press Enter:

```powershell
.\tools\Capture-UiBaseline.ps1 -Label 'library-scroll-rc6'
```

Use separate labels for `player-open`, `library-scroll`, `tab-switch` and `glow-playing` on RC5 and
RC6. The script saves a screenshot plus `gfxinfo.txt` with frame statistics in
`artifacts/ui-baselines/`. Compare janky frames for the same action on the same device; also note
any visible stutter. There is no meaningful RC5-versus-RC6 comparison without the RC5 capture.

When reporting a problem, include the phone model, Android version, beta version, setting choices,
the exact step, and a screenshot or short recording if possible. The compact navigation experiment
should only remain in the stable 1.5.0 release if its controls stay obvious and its measured scroll
performance is no worse than the regular bar.
