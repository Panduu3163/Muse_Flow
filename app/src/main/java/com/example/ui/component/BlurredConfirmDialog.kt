package com.example.ui.component

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** The caller retains its blurred content; the native dialog owns modal focus and Back/outside. */
@Composable
fun BlurredConfirmDialog(title: String, text: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val cancelFocus = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("blurred_confirm_dialog"),
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        title = { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = { Text(text, modifier = Modifier.verticalScroll(rememberScrollState()).testTag("blurred_confirm_text")) },
        confirmButton = {
            Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
                modifier = Modifier.testTag("blurred_confirm_confirm")) { Text(confirmLabel) }
        },
        dismissButton = {
            val window = LocalWindowInfo.current
            LaunchedEffect(window.isWindowFocused) {
                if (window.isWindowFocused) {
                    withFrameNanos { }
                    cancelFocus.requestFocus()
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.focusRequester(cancelFocus).testTag("blurred_confirm_cancel")) { Text("Cancel") }
        },
    )
}
