# MuseFlow

MuseFlow is a personal music player app for Android, built with Kotlin and Jetpack Compose. It searches and streams real songs (via JioSaavn), plays them in the background with a proper media notification, shows synced lyrics, and can save tracks for offline listening.

**Made by Mynul Kabir Nayem.**

This is a hobby project, not a commercial product or a finished app. It's actively being worked on, so expect bugs, rough edges, and features that are visibly still in progress.

## Features

- **Real navigation.** A single Navigation-Compose `NavHost` with a proper back stack across Home, Search, Library, Settings (and every Settings sub-screen), and the Now Playing screen. The system back button always returns to the previous screen, and only exits the app from the Home tab.
- **First-launch onboarding.** A one-time welcome flow and profile setup (display name + optional photo via the Android Photo Picker), shown only the first time the app runs and persisted via DataStore.
- **Search and streaming via JioSaavn.** Search returns real songs from JioSaavn's public API and plays them end to end through Media3 ExoPlayer.
- **Background playback with a real media notification.** Playback runs in a `MediaSessionService`, so it survives the app being backgrounded or the screen turning off. The system derives a MediaStyle notification (cover art, title/artist, play/pause/skip) straight from the session, with audio focus handling (pausing for calls, ducking for other audio) managed by ExoPlayer itself.
- **Real cover art**, pulled from JioSaavn's search results and shown in search results, the mini-player, Now Playing, and the media notification — with Coil handling image loading and caching.
- **Synced lyrics.** Now Playing can show real, time-synced lyrics fetched from LRCLib, highlighting and auto-scrolling to the current line as the track plays.
- **Offline downloads.** Download the currently playing (or any searched) track to app-private storage; downloaded tracks are tracked in a local Room database and play from disk instead of streaming. Library's "Offline mode" toggle filters down to just what's actually downloaded.
- **Appearance theming.** Switch the app's background between AMOLED black and a set of gradient palettes, applied instantly across every screen and persisted between launches.
- **Settings**, organized into Account, Appearance, Player & Audio, Lyrics, Library & Playlists, Listen Together, Storage, Service Uptime, and About. Account, About, and the Theme picker are fully functional. Most of the rest (lyrics styling, swipe gestures, auto playlists, and similar) are visible but not yet wired to real behavior — tapping or toggling them shows a "coming soon" message rather than silently doing nothing. Listen Together and Storage management are explicitly not implemented yet and say so on their own screens.

## Built with

- Kotlin + Jetpack Compose + Material3
- Media3 / ExoPlayer for playback, background `MediaSessionService`, and the media notification
- Room for the offline-downloads database
- Navigation-Compose for app navigation
- DataStore Preferences for persisted settings (theme, onboarding, user profile)
- OkHttp + Moshi/org.json for the JioSaavn API client and LRCLib lyrics client
- Coil for image loading (cover art, profile photos)

## Run locally

**Prerequisites:** [Android Studio](https://developer.android.com/studio) or a JDK 17+ command-line setup, and the Android SDK.

1. Clone the repo and open it in Android Studio, or use the command line.
2. Make sure `local.properties` has `sdk.dir` pointing at your Android SDK installation.
3. Generate a debug signing key (not checked into the repo):
   ```
   keytool -genkeypair -v -keystore debug.keystore -storepass android \
     -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 \
     -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
   ```
4. Build and run: `./gradlew assembleDebug`, or run the app from Android Studio on an emulator or physical device.
