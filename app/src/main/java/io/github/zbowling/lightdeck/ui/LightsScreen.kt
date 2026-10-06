package io.github.zbowling.lightdeck.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.zbowling.lightdeck.ha.ConnectionStatus
import io.github.zbowling.lightdeck.ha.Light
import io.github.zbowling.lightdeck.ha.Rgb
import io.github.zbowling.lightdeck.ha.Room
import metavrx.uiset.compose.Icon
import metavrx.uiset.compose.Text
import metavrx.uiset.compose.button.ButtonStyle
import metavrx.uiset.compose.button.IconButton
import metavrx.uiset.compose.button.LabelButton
import metavrx.uiset.compose.theme.UiSetTheme
import metavrx.uiset.compose.theme.icons.Icons

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
    val spacing = UiSetTheme.dimensions.spacing
    Column(Modifier.fillMaxSize()) {
        // Wraps instead of clipping when the window is narrow (down to 360dp).
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.twoXLarge, vertical = spacing.large),
            horizontalArrangement = Arrangement.spacedBy(spacing.large),
            verticalArrangement = Arrangement.spacedBy(spacing.small),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Lights", style = UiSetTheme.typography.display, modifier = Modifier.weight(1f))
            StatusBadge(status)
            IconButton(
                icon = icon { Icons.Regular.Settings },
                onClick = onEditServer,
                contentDescription = "Server settings",
                style = ButtonStyle.Secondary,
            )
        }

        when {
            status is ConnectionStatus.AuthFailed -> CenteredMessage(
                title = "Home Assistant rejected the access token",
                body = status.message,
                action = "Update token",
                onAction = onEditServer,
            )
            rooms.isEmpty() && status is ConnectionStatus.Connected -> CenteredMessage(
                title = "No lights found",
                body = "Lights you add in Home Assistant show up here automatically.",
            )
            rooms.isEmpty() && status is ConnectionStatus.Retrying -> CenteredMessage(
                title = "Can't reach Home Assistant",
                body = status.message,
                action = "Retry now",
                onAction = onRetry,
            )
            rooms.isEmpty() -> CenteredMessage(title = "Connecting to Home Assistant…")
            else -> LazyVerticalGrid(
                // 320dp columns: one column at the 360dp minimum window width,
                // three at the 1024dp default.
                columns = GridCells.Adaptive(minSize = 320.dp),
                contentPadding = PaddingValues(
                    start = spacing.twoXLarge,
                    end = spacing.twoXLarge,
                    bottom = spacing.twoXLarge,
                ),
                horizontalArrangement = Arrangement.spacedBy(spacing.large),
                verticalArrangement = Arrangement.spacedBy(spacing.large),
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
private fun StatusBadge(status: ConnectionStatus) {
    val colors = UiSetTheme.colorScheme
    val (label, iconTint) = when (status) {
        is ConnectionStatus.Connected -> "Connected" to colors.positive.content
        ConnectionStatus.Connecting -> "Connecting…" to colors.background.content.icon
        is ConnectionStatus.Retrying -> "Reconnecting…" to colors.warning.content
        is ConnectionStatus.AuthFailed -> "Token rejected" to colors.negative.content
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(UiSetTheme.dimensions.spacing.small),
    ) {
        Icon(
            imageVector = when (status) {
                is ConnectionStatus.Connected -> Icons.Regular.CheckCircle
                ConnectionStatus.Connecting -> Icons.Regular.Refresh
                is ConnectionStatus.Retrying -> Icons.Regular.Warning
                is ConnectionStatus.AuthFailed -> Icons.Regular.ErrorCircle
            },
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = iconTint,
        )
        Text(label, style = UiSetTheme.typography.body)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RoomHeader(room: Room, onRoomPower: (Room, Boolean) -> Unit) {
    val spacing = UiSetTheme.dimensions.spacing
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(top = spacing.small),
        horizontalArrangement = Arrangement.spacedBy(spacing.medium),
        verticalArrangement = Arrangement.spacedBy(spacing.small),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(room.name, style = UiSetTheme.typography.headline, modifier = Modifier.weight(1f))
        LabelButton(
            label = "All on",
            onClick = { onRoomPower(room, true) },
            style = ButtonStyle.Secondary,
            leadingIcon = icon { Icons.Regular.BrightnessOn },
        )
        LabelButton(
            label = "All off",
            onClick = { onRoomPower(room, false) },
            style = ButtonStyle.Bordered,
            leadingIcon = icon { Icons.Regular.BrightnessOff },
            enabled = room.anyOn,
        )
    }
}
