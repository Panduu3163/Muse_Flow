package com.example.ui.theme

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

val LocalReducedMotion = staticCompositionLocalOf { false }

/** Follow Android's animator scale, including changes made while MuseFlow is open. */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    val resolver = context.contentResolver
    fun isDisabled() = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    var disabled by remember(resolver) { mutableStateOf(isDisabled()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { disabled = isDisabled() }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return disabled
}
