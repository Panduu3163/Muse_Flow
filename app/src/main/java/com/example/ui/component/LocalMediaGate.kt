package com.example.ui.component

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.LocalMediaPermission

/**
 * Shown in place of the on-device track list until MuseFlow may read the device's audio files.
 *
 * Asked for here rather than at startup: a permission prompt makes sense the moment someone opens
 * "On device", and means nothing on first launch. If the request is refused twice Android stops
 * showing the dialog entirely, so the button then hands off to the app's system settings page
 * instead of silently doing nothing.
 */
@Composable
fun LocalMediaGate(onGranted: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var refusedInApp by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) onGranted() else refusedInApp = true
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp)
            .testTag("local_media_gate"),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.FolderOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(40.dp),
            )
            Text(
                text = if (refusedInApp) {
                    "MuseFlow needs permission to read the music stored on this phone. " +
                        "You can grant it in system settings."
                } else {
                    "Let MuseFlow read the music stored on this phone to play it here."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp, bottom = 20.dp),
            )
            Button(
                onClick = {
                    if (refusedInApp) {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null),
                            )
                        )
                    } else {
                        launcher.launch(LocalMediaPermission.name)
                    }
                },
                modifier = Modifier.testTag("local_media_allow"),
            ) {
                Text(if (refusedInApp) "Open settings" else "Allow access")
            }
        }
    }
}
