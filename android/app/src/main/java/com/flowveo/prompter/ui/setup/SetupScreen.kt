package com.flowveo.prompter.ui.setup

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.flowveo.prompter.ui.theme.PrompterColors
import com.flowveo.prompter.ui.theme.PrompterFonts
import com.flowveo.prompter.util.Permissions

/** First-launch wizard. One card per permission. */
@Composable
fun SetupScreen(onContinue: () -> Unit) {
    val context = LocalContext.current
    // Bumped on resume and after each request so the cards re-read the real permission state.
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }

    val status = remember(refresh) { SetupStatus.read(context) }
    val runtimePermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refresh++
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PrompterColors.Background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("PROMPTER · SETUP", color = PrompterColors.AccentText, fontFamily = PrompterFonts.Mono, style = MaterialTheme.typography.labelSmall)
        Text("Three quick permissions, then you're ready to record.", style = MaterialTheme.typography.headlineMedium, color = PrompterColors.Text)
        Progress(status.grantedCount)
        Spacer(Modifier.height(4.dp))

        PermissionCard(
            title = "Display over other apps",
            body = "So the script can float on top of your camera app.",
            granted = status.overlay,
            buttonLabel = "Open settings",
            onClick = { context.startActivity(Permissions.overlaySettingsIntent(context)) },
        )
        PermissionCard(
            title = "Microphone and notifications",
            body = "The mic lets the script follow your voice; nothing is recorded or uploaded. The notification keeps the overlay alive.",
            granted = status.micAndNotifications,
            buttonLabel = "Allow",
            onClick = {
                runtimePermissions.launch(Permissions.runtimePermissions)
            },
            secondaryLabel = "App settings",
            onSecondary = { context.startActivity(Permissions.appDetailsIntent(context)) },
        )
        PermissionCard(
            title = "Battery: Unrestricted",
            body = "Otherwise the phone can close the overlay mid-take.",
            hint = if (Permissions.isSamsung) "Samsung: also keep Prompter out of Sleeping apps." else null,
            granted = status.battery,
            buttonLabel = "Open battery settings",
            onClick = { context.startActivity(Permissions.batteryIntent(context)) },
        )

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onContinue,
            enabled = status.overlay,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = PrompterColors.Accent, disabledContainerColor = PrompterColors.Surface2),
        ) {
            Text(if (status.all) "Continue" else "Continue anyway", style = MaterialTheme.typography.labelLarge)
        }
        Text(
            text = if (status.overlay) "Checked again every time you tap Start." else "\"Display over other apps\" is required.",
            color = PrompterColors.Muted,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

private data class SetupStatus(val overlay: Boolean, val micAndNotifications: Boolean, val battery: Boolean) {
    val grantedCount get() = listOf(overlay, micAndNotifications, battery).count { it }
    val all get() = grantedCount == 3

    companion object {
        fun read(context: Context) = SetupStatus(
            overlay = Permissions.canDrawOverlays(context),
            micAndNotifications = Permissions.hasMic(context) && Permissions.hasNotifications(context),
            battery = Permissions.isBatteryUnrestricted(context),
        )
    }
}

@Composable
private fun Progress(done: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(3) { i ->
            Box(
                Modifier
                    .weight(1f)
                    .height(4.dp)
                    .background(if (i < done) PrompterColors.Accent else PrompterColors.Surface2, CircleShape),
            )
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    body: String,
    granted: Boolean,
    buttonLabel: String,
    onClick: () -> Unit,
    hint: String? = null,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(24.dp)
    val borderColor = if (granted) PrompterColors.Success.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.08f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PrompterColors.Surface, shape)
            .border(1.dp, borderColor, shape)
            .padding(18.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier
                .size(40.dp)
                .background(if (granted) PrompterColors.Success.copy(alpha = 0.15f) else PrompterColors.Surface2, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (granted) "✓" else "•", color = if (granted) PrompterColors.Success else PrompterColors.Text2, style = MaterialTheme.typography.titleMedium)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = PrompterColors.Text)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = PrompterColors.Text2)
            if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = PrompterColors.Muted)
            if (granted) {
                Text("Granted", style = MaterialTheme.typography.bodySmall, color = PrompterColors.Success)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    Button(
                        onClick = onClick,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = PrompterColors.Accent),
                    ) { Text(buttonLabel) }
                    if (secondaryLabel != null && onSecondary != null) {
                        OutlinedButton(onClick = onSecondary, shape = CircleShape) {
                            Text(secondaryLabel, color = PrompterColors.Text)
                        }
                    }
                }
            }
        }
    }
}
