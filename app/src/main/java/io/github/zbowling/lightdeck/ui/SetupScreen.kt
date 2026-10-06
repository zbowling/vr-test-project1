package io.github.zbowling.lightdeck.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.zbowling.lightdeck.data.DiscoveredServer
import metavrx.uiset.compose.Text
import metavrx.uiset.compose.button.ButtonStyle
import metavrx.uiset.compose.button.LabelButton
import metavrx.uiset.compose.card.SecondaryCard
import metavrx.uiset.compose.input.TextField
import metavrx.uiset.compose.theme.LocalContentColors
import metavrx.uiset.compose.theme.UiSetTheme
import metavrx.uiset.compose.theme.icons.Icons

@Composable
fun SetupScreen(
    initialAddress: String,
    discovered: List<DiscoveredServer>,
    onConnect: (address: String, token: String) -> Unit,
    onCancel: (() -> Unit)?,
) {
    var address by rememberSaveable { mutableStateOf(initialAddress) }
    var token by rememberSaveable { mutableStateOf("") }
    val spacing = UiSetTheme.dimensions.spacing
    val insecure = address.trim().startsWith("http://", ignoreCase = true)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(spacing.twoXLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Narrow, centered form: easy to read at panel distance, and it reflows
        // down to the 360dp minimum window width.
        Column(
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(spacing.large),
        ) {
            Text("Connect to Home Assistant", style = UiSetTheme.typography.display)

            if (discovered.isNotEmpty()) {
                Text("Found on your network", style = UiSetTheme.typography.headline)
                // Each server is one clickable card: one gaze target with one hover shape.
                discovered.forEach { server ->
                    SecondaryCard(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { address = server.address },
                    ) {
                        Text(server.name, style = UiSetTheme.typography.title)
                        Text(
                            server.address,
                            style = UiSetTheme.typography.bodySmall,
                            color = LocalContentColors.current.secondary,
                        )
                    }
                }
            }

            TextField(
                value = address,
                label = "Address",
                onValueChange = { address = it },
                placeholder = "homeassistant.local:8123",
                supportingText = if (insecure) {
                    "Over http:// the token travels unencrypted. Use it only on your home network."
                } else {
                    "Your Home Assistant URL or host name"
                },
                keyboardType = KeyboardType.Uri,
                modifier = Modifier.fillMaxWidth(),
            )

            TextField(
                value = token,
                label = "Long-lived access token",
                onValueChange = { token = it },
                placeholder = "Paste your token",
                supportingText = "Home Assistant → your profile → Security → Long-lived access tokens",
                keyboardType = KeyboardType.Password,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(spacing.medium)) {
                LabelButton(
                    label = "Connect",
                    onClick = { onConnect(address, token) },
                    style = ButtonStyle.Primary,
                    leadingIcon = icon { Icons.Regular.Wifi },
                )
                // Horizon OS has no system back button for every input method, so the
                // way out of this screen is an explicit button.
                if (onCancel != null) {
                    LabelButton(
                        label = "Back",
                        onClick = onCancel,
                        style = ButtonStyle.Secondary,
                        leadingIcon = icon { Icons.Regular.ArrowLeft },
                    )
                }
            }
        }
    }
}
