# 🎵 MuseFlow

![Android](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Language-Kotlin-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack_Compose-4285F4?logo=android&logoColor=white)
![Media3](https://img.shields.io/badge/Audio-Media3_ExoPlayer-FF6F00)

**MuseFlow** is a personal music player app for Android, built with Kotlin and Jetpack Compose. It searches and streams real songs (via JioSaavn), plays them in the background with a proper media notification, shows synced lyrics, and can save tracks for offline listening.

> **⚠️ Note:** This is a hobby project, not a commercial product. It is actively being worked on, so expect bugs, rough edges, and features that are visibly still in progress.

## 📋 Table of Contents
- [Features](#-features)
- [Screenshots](#-screenshots)
- [Built With](#-built-with)
- [Run Locally](#-run-locally)
- [Author](#-author)

---

## ✨ Features

*   **🧭 Seamless Navigation:** A single Navigation-Compose `NavHost` manages a proper back stack across Home, Search, Library, Settings, and the Now Playing screen. The system back button predictably returns to the previous screen and only exits from the Home tab.
*   **👋 First-Launch Onboarding:** A one-time welcome flow for profile setup (display name + optional photo via the Android Photo Picker), persisted securely via DataStore.
*   **🔎 Real Search & Streaming:** Search returns real songs from JioSaavn's public API and plays them end-to-end through Media3 ExoPlayer.
*   **🎵 Background Playback:** Playback runs in a `MediaSessionService`, surviving app backgrounding or screen locks. The system derives a MediaStyle notification (cover art, title, play/pause/skip) straight from the session, with automatic audio focus handling.
*   **🖼️ Dynamic Cover Art:** Pulled from JioSaavn search results and displayed across the mini-player, Now Playing, and notifications—with Coil handling all image loading and caching.
*   **🎤 Synced Lyrics:** The Now Playing screen displays real, time-synced lyrics fetched from LRCLib, highlighting and auto-scrolling to the current line as the track plays.
*   **💾 Offline Downloads:** Download any searched track to app-private storage. Downloaded tracks are tracked in a local Room database and play directly from disk. A dedicated "Offline mode" toggle in the Library filters your view to downloaded tracks only.
*   **🎨 Appearance Theming:** Switch the app's background between AMOLED black and a set of gradient palettes. Changes are applied instantly across every screen and persisted between launches.
*   **⚙️ Comprehensive Settings:** Organized into Account, Appearance, Player & Audio, Lyrics, Library, and more. 
    *   *Functional:* Account, About, and Theme picker.
    *   *Work in Progress:* Lyrics styling, swipe gestures, auto playlists, Listen Together, and Storage management currently display "coming soon" placeholders.

---

## 📱 Screenshots

> *(Add screenshots of your app here to make the repository more engaging. You can drag and drop images directly into GitHub to generate links.)*

| Home | Now Playing | Lyrics | Settings |
| :---: | :---: | :---: | :---: |
| `<img src="link_here" width="200"/>` | `<img src="link_here" width="200"/>` | `<img src="link_here" width="200"/>` | `<img src="link_here" width="200"/>` |

---

## 🛠️ Built With

*   **Kotlin** + **Jetpack Compose** + **Material3** for a modern, declarative UI.
*   **Media3 / ExoPlayer** for reliable playback, background `MediaSessionService`, and media notifications.
*   **Navigation-Compose** for robust app routing.
*   **Room** for structuring the offline-downloads database.
*   **DataStore Preferences** for persisting application settings and user profiles.
*   **OkHttp + Moshi/org.json** for robust API client integration (JioSaavn & LRCLib).
*   **Coil** for fast and efficient image loading.

---

## 🚀 Run Locally

**Prerequisites:** [Android Studio](https://developer.android.com/studio) or a JDK 17+ command-line setup, and the Android SDK.

1. **Clone the repository** and open it in Android Studio (or use the command line).
2. **Configure SDK:** Make sure your `local.properties` file has `sdk.dir` pointing to your Android SDK installation.
3. **Generate a debug signing key:** Open your terminal and run the following command to create a key (this is required but not checked into the repository):
   
   ```bash
   keytool -genkeypair -v -keystore debug.keystore -storepass android \
     -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 \
     -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
