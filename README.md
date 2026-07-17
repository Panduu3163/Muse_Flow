# MuseFlow

MuseFlow is a personal hobby music app for Android, built with Kotlin and Jetpack Compose. It's not a commercial product — just an ongoing project by [Mynul Kabir Nayem](mailto:mynulkbr@gmail.com) to build a real, working music player from scratch.

## Features

- **Real search and playback via JioSaavn.** Search returns real songs from JioSaavn's public API, and playback runs through Media3 ExoPlayer end to end — this is the primary, working music source.
- **YouTube Music search (experimental).** A second `Provider` implementation talks directly to YouTube Music's InnerTube API for search. Full playback is currently blocked by YouTube's CDN restrictions on the unauthenticated client types used to avoid needing PoToken/signature deciphering — this is documented, known, and paused rather than silently broken.
- **Real navigation.** The whole app runs on a single Navigation-Compose `NavHost` with a proper back stack across Home, Search, Library, Settings (and every Settings sub-screen), and the Now Playing overlay. The system back button always returns to the previous screen, and only exits the app from the Home tab.
- **First-launch onboarding.** A one-time welcome screen and profile setup (display name + optional photo via the Android Photo Picker) shown only the first time the app runs, persisted via DataStore Preferences.
- **Appearance theming.** Switch the app's background between AMOLED black and a set of gradient palettes, applied instantly across every screen and persisted between launches.
- **Settings**, organized into Account, Appearance, Player & Audio, Lyrics, Library & Playlists, Listen Together, Storage, Service Uptime, and About. Some of these (e.g. lyrics styling, swipe gestures, auto playlists) currently hold their UI state but aren't wired to real playback behavior yet — this is a work in progress, not a finished feature set.

## Tech stack

- Kotlin + Jetpack Compose + Material3
- Navigation-Compose for app navigation
- Media3 ExoPlayer for audio playback
- DataStore Preferences for persisted settings (theme, onboarding, user profile)
- OkHttp + Moshi/org.json for the JioSaavn/YouTube Music API clients
- Coil for image loading (profile photos)

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
