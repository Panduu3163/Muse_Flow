# Building MuseFlow

## Toolchain

| Tool | Required version |
| --- | --- |
| JDK | 21, including desktop Robolectric tests |
| Gradle | 9.9.1 through the committed wrapper |
| Android Gradle Plugin | 9.5.1 through the version catalog |
| Android compile platform | `platforms;android-37.1` |
| Android Build Tools | 37.0.0 |
| Android source/bytecode target | Java 17 |
| App compatibility | Android 7+ / API 24 minimum; target API 37 |

Use the committed Gradle wrapper instead of installing a separate Gradle version. No NDK is required. Point Android Studio or `ANDROID_HOME` at your SDK; machine-specific SDK paths belong in ignored `local.properties`, not Git.

## Fresh checkout

```sh
git clone https://github.com/Panduu3163/Muse_Flow.git
cd Muse_Flow
sdkmanager "platforms;android-37.1" "build-tools;37.0.0"
```

Create a **local test key only if this is a fresh checkout without an existing signing key**:

```sh
keytool -genkeypair -keystore debug.keystore -storepass android \
  -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 \
  -validity 10000 -dname "CN=Local Test,O=MuseFlow,C=US"
```

The build expects this ignored file. Never replace the maintainer's existing key: a newly generated key cannot update an APK signed with the existing MuseFlow certificate. The passwords above are the conventional local Android test-key values, not a distributed private signing credential. CI creates an ephemeral test key and never receives the maintainer's key.

## Build and verify

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug --no-daemon --max-workers=1
./gradlew assembleBeta assembleVerification --no-daemon --max-workers=1
```

On Windows use `gradlew.bat` in a normal checkout path. Avoid shell metacharacters such as `&` in the checkout path when using the batch launcher. Keep the SDK and JDK paths quoted when invoking their executables.

| Variant | Application ID suffix | Purpose |
| --- | --- | --- |
| Debug | none | Local development |
| Beta | `.beta` | Optimized Beta app |
| Verification | `.verification` | Optimized, separately stored test app |
| Release | none | Regular application identity |

APKs are written under `app/build/outputs/apk/`. The Verification app has its own library and settings; do not restore its test library into the normal Beta app.

## Configuration

The current playback/library features do not require a developer-owned API key. `.env.example` contains a placeholder retained for the optional Secrets Gradle plugin. A missing `google-services.json` is allowed; Firebase-backed optional capabilities need the developer's own local configuration.

Keep private configuration and keystores out of Git. See [SECURITY.md](../SECURITY.md). A successful build does not validate every upstream music/lyrics service or Android hardware configuration.
