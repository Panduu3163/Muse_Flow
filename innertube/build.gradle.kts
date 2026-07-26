/*
 * InnerTube (YouTube Music internal API) client.
 *
 * Ported from Echo-Music (GPL-3.0) - https://github.com/EchoMusicApp/Echo-Music
 * MuseFlow is GPL-3.0 as well, so this reuse keeps the same licence. Build config is adjusted to
 * match MuseFlow's toolchain (minSdk 24, Java 11) rather than Echo's (minSdk 26, Java 21).
 */
plugins {
    // Unversioned: AGP is already on the build classpath via the root project.
    id("com.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.music.innertube"
    compileSdk { version = release(37) { minorApiLevel = 1 } }

    defaultConfig {
        // Matches :app - a library's minSdk may not exceed the consuming application's.
        minSdk = 24
    }

    compileOptions {
        // java.time and other API 26+ library types this module uses are backported by this.
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.ktor.client.encoding)
    implementation(libs.brotli)
    implementation(libs.newpipeextractor)
    testImplementation(libs.junit)

    coreLibraryDesugaring(libs.desugaring)
}
