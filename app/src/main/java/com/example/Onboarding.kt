package com.example

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap

/**
 * First-launch, two-step onboarding: a welcome/developer-credit screen, then a profile-setup
 * screen. Shown by [MainLayout] only while [UserProfileState.hasSeenOnboarding] is false;
 * completing it persists the name/photo and the completion flag via [UserProfileViewModel].
 */
@Composable
fun OnboardingFlow(
    onComplete: (displayName: String, photoUri: String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var step by remember { mutableStateOf(1) }
    var displayName by remember { mutableStateOf("") }
    var photoUri by remember { mutableStateOf<String?>(null) }

    when (step) {
        1 -> OnboardingWelcomeScreen(
            onNext = { step = 2 },
            modifier = modifier
        )
        else -> OnboardingProfileScreen(
            displayName = displayName,
            onDisplayNameChange = { displayName = it },
            photoUri = photoUri,
            onPhotoUriChange = { photoUri = it },
            onGetStarted = { onComplete(displayName.trim(), photoUri) },
            modifier = modifier
        )
    }
}

@Composable
fun OnboardingWelcomeScreen(
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appIconPainter = remember {
        val drawable = ContextCompat.getDrawable(context, R.mipmap.ic_launcher)!!
        BitmapPainter(drawable.toBitmap().asImageBitmap())
    }

    ThemedBackground(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .statusBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(48.dp))

                Image(
                    painter = appIconPainter,
                    contentDescription = "MuseFlow app icon",
                    modifier = Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .testTag("onboarding_app_icon")
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Welcome to MuseFlow",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Discover your rhythm",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(40.dp))

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("onboarding_developer_card"),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "Made by Mynul Kabir Nayem",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "MuseFlow is a personal hobby project, not a commercial " +
                                "product. It may have bugs — I'm actively working to fix them.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Button(
                onClick = onNext,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp)
                    .navigationBarsPadding()
                    .height(52.dp)
                    .testTag("onboarding_next_button")
            ) {
                Text("Next", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
            }
        }
    }
}

@Composable
fun OnboardingProfileScreen(
    displayName: String,
    onDisplayNameChange: (String) -> Unit,
    photoUri: String?,
    onPhotoUriChange: (String?) -> Unit,
    onGetStarted: () -> Unit,
    modifier: Modifier = Modifier
) {
    ThemedBackground(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .statusBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(56.dp))

                Text(
                    text = "What should we call you?",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(32.dp))

                ProfileEditorContent(
                    displayName = displayName,
                    onDisplayNameChange = onDisplayNameChange,
                    photoUri = photoUri,
                    onPhotoUriChange = onPhotoUriChange,
                    nameLabel = "Your name",
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Button(
                onClick = onGetStarted,
                enabled = displayName.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp)
                    .navigationBarsPadding()
                    .height(52.dp)
                    .testTag("onboarding_get_started_button")
            ) {
                Text("Get Started", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
            }
        }
    }
}
