package io.github.zbowling.lightdeck.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.zbowling.lightdeck.ha.ConnectionStatus
import io.github.zbowling.lightdeck.ha.Light
import io.github.zbowling.lightdeck.ha.Rgb
import io.github.zbowling.lightdeck.ha.Room
import kotlin.math.roundToInt

private val ColorPresets = listOf(
    "Warm white" to Rgb(255, 183, 76),
    "Cool white" to Rgb(255, 244, 229),
    "Red" to Rgb(255, 40, 30),
    "Orange" to Rgb(255, 128, 0),
    "Green" to Rgb(40, 220, 80),
    "Blue" to Rgb(30, 110, 255),
    "Purple" to Rgb(150, 60, 255),
    "Pink" to Rgb(255, 60, 160),
)

// Meta's design requirements: 48dp minimum targets, 60dp for primary controls.
private val PrimaryControlHeight = 60.dp

private fun Rgb.toColor() = Color(r, g, b)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LightsScreen(
    status: ConnectionStatus,
    rooms: List<Room>,
    onToggle: (Light) -> Unit,
    onBrightness: (Light, Int) -> Unit,
    onColor: (Light, Rgb) -> Unit,
    onRoomPower: (Room, Boolean) -> Unit,
    onEditServer: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        // Wraps instead of clipping when the panel is narrow (down to 360dp).
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Lights", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            StatusLabel(status)
            OutlinedButton(onClick = onEditServer, modifier = Modifier.heightIn(min = PrimaryControlHeight)) {
                Text("Server")
            }
        }

        when {
            status is ConnectionStatus.AuthFailed -> Message(
                text = "Home Assistant rejected the access token: ${status.message}",
                action = "Update token",
                onAction = onEditServer,
            )
            rooms.isEmpty() && status is ConnectionStatus.Connected -> Message("No lights found in Home Assistant.")
            rooms.isEmpty() && status is ConnectionStatus.Retrying -> Message(
                text = "Can't reach Home Assistant: ${status.message}",
                action = "Retry now",
                onAction = onRetry,
            )
            rooms.isEmpty() -> Message("Connecting to Home Assistant…")
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 300.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                rooms.forEach { room ->
                    item(key = "room:${room.area?.id}", span = { GridItemSpan(maxLineSpan) }) {
                        RoomHeader(room, onRoomPower)
                    }
                    items(room.lights, key = { it.entityId }) { light ->
                        LightTile(
                            light = light,
                            onToggle = { onToggle(light) },
                            onBrightness = { onBrightness(light, it) },
                            onColor = { onColor(light, it) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusLabel(status: ConnectionStatus) {
    val (text, color) = when (status) {
        is ConnectionStatus.Connected -> "Connected" to Color(0xFF66BB6A)
        ConnectionStatus.Connecting -> "Connecting…" to MaterialTheme.colorScheme.secondary
        is ConnectionStatus.Retrying -> "Reconnecting…" to Color(0xFFFFA726)
        is ConnectionStatus.AuthFailed -> "Token rejected" to MaterialTheme.colorScheme.error
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun Message(text: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium)
        if (action != null) {
            Spacer(Modifier.size(16.dp))
            Button(onClick = onAction, modifier = Modifier.heightIn(min = PrimaryControlHeight)) { Text(action) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RoomHeader(room: Room, onRoomPower: (Room, Boolean) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(room.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        FilledTonalButton(onClick = { onRoomPower(room, true) }, modifier = Modifier.heightIn(min = PrimaryControlHeight)) {
            Text("All on")
        }
        OutlinedButton(
            onClick = { onRoomPower(room, false) },
            enabled = room.anyOn,
            modifier = Modifier.heightIn(min = PrimaryControlHeight),
        ) {
            Text("All off")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LightTile(
    light: Light,
    onToggle: () -> Unit,
    onBrightness: (Int) -> Unit,
    onColor: (Rgb) -> Unit,
) {
    val glow = if (light.isOn) light.rgb?.toColor() ?: Amber else MaterialTheme.colorScheme.surfaceVariant
    ElevatedCard(Modifier.fillMaxWidth().alpha(if (light.isAvailable) 1f else 0.5f)) {
        // The header row is a single toggle target. Its Switch only shows state, so
        // Look and Pinch sees one interactable element and the system hover highlight
        // follows the row's rounded shape.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 88.dp)
                .clip(RoundedCornerShape(12.dp))
                .toggleable(
                    value = light.isOn,
                    enabled = light.isAvailable,
                    role = Role.Switch,
                    onValueChange = { onToggle() },
                )
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(glow))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    light.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(stateLabel(light), style = MaterialTheme.typography.bodyMedium)
            }
            Switch(checked = light.isOn, onCheckedChange = null, enabled = light.isAvailable)
        }

        if (light.isAvailable && light.supportsBrightness) {
            // Hold the dragged value until Home Assistant reports the light's new state.
            var dragged by remember(light.entityId) { mutableStateOf<Float?>(null) }
            LaunchedEffect(light) { dragged = null }
            Slider(
                value = dragged ?: (light.brightnessPct ?: 0).toFloat(),
                onValueChange = { dragged = it },
                onValueChangeFinished = { dragged?.let { onBrightness(it.roundToInt()) } },
                valueRange = 0f..100f,
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .semantics { contentDescription = "${light.name} brightness" },
            )
        }

        if (light.isAvailable && light.supportsColor) {
            FlowRow(
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ColorPresets.forEach { (name, preset) ->
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(preset.toColor())
                            .clickable(onClickLabel = "Set ${light.name} to $name") { onColor(preset) }
                            .semantics { contentDescription = name },
                    )
                }
            }
        } else {
            Spacer(Modifier.size(8.dp))
        }
    }
}

private fun stateLabel(light: Light): String = when {
    !light.isAvailable -> "Unavailable"
    light.isOn && light.brightnessPct != null -> "On · ${light.brightnessPct}%"
    light.isOn -> "On"
    else -> "Off"
}
