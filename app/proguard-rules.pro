# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# YouTube cipher/PoToken solving runs inside a hidden WebView (see ytcipher/) - these methods are
# only ever called from the WebView's JS engine via reflection, never from Kotlin code directly,
# so R8 would otherwise strip or rename them and silently break stream resolution. Matches the
# pattern Echo-Music's own (working, minified) build uses for the equivalent class.
-keepclassmembers class com.example.ytcipher.YtCipherWebView {
    @android.webkit.JavascriptInterface public *;
}
-keepclassmembers class com.example.ytcipher.potoken.PoTokenWebView {
    @android.webkit.JavascriptInterface public *;
}

# kotlinx.serialization models in :innertube (the entire YouTube Music API response schema,
# ~70+ @Serializable classes) - the library's own consumer rules cover the general mechanism, but
# don't reliably survive R8's more aggressive member-removal for properties that are only ever
# read by the generated serializer, not by name from Kotlin code. Explicit keep rules for our own
# model package, rather than trusting the generic library rules alone - same reasoning Echo-Music's
# proguard-rules.pro documents for its own (Ktor + kotlinx.serialization) model classes.
-keepattributes *Annotation*,InnerClasses
-keep,includedescriptorclasses class com.music.innertube.models.**$$serializer { *; }
-keepclassmembers class com.music.innertube.models.** {
    *** Companion;
}
-keepclasseswithmembers class com.music.innertube.models.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Ktor's OkHttp engine and content-negotiation plugin probe for optional platform classes
# (java.awt.*, javax.imageio.*, older TLS providers) that don't exist on Android - warnings only,
# nothing to keep.
-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# jsoup's optional re2j regex backend and Rhino's optional java.beans reflection path - both
# probed for and silently skipped if absent at runtime (confirmed against R8's
# missing_rules.txt output on this exact dependency set, same fix Echo-Music's own
# proguard-rules.pro applies for the identical libraries).
-dontwarn com.google.re2j.**
-dontwarn java.beans.**
-dontwarn org.mozilla.javascript.**
