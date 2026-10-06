package io.github.zbowling.lightdeck.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.zbowling.lightdeck.LightsViewModel
import kotlinx.coroutines.delay
import metavrx.uiset.compose.theme.UiSetTheme

private const val MESSAGE_DURATION_MS = 5_000L

@Composable
fun LightDeckApp(viewModel: LightsViewModel) {
    val showSetup by viewModel.showSetup.collectAsStateWithLifecycle()
    val config by viewModel.config.collectAsStateWithLifecycle()
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message = it }
    }
    LaunchedEffect(message) {
        if (message != null) {
            delay(MESSAGE_DURATION_MS)
            message = null
        }
    }

    // UI Set's dark scheme is the default: Horizon OS panels are opaque, and a dark
    // panel is the least intrusive over passthrough or a virtual environment.
    UiSetTheme {
        Column(
            Modifier
                .fillMaxSize()
                .background(UiSetTheme.colorScheme.background.container.brush),
        ) {
            message?.let {
                MessageBanner(it, Modifier.padding(UiSetTheme.dimensions.spacing.large))
            }
            if (showSetup) {
                val discovered by viewModel.discoveredServers.collectAsStateWithLifecycle()
                SetupScreen(
                    initialAddress = config?.address.orEmpty(),
                    discovered = discovered,
                    onConnect = viewModel::connect,
                    onCancel = if (config != null) viewModel::cancelEdit else null,
                )
            } else {
                val status by viewModel.status.collectAsStateWithLifecycle()
                val rooms by viewModel.rooms.collectAsStateWithLifecycle()
                LightsScreen(
                    status = status,
                    rooms = rooms,
                    onToggle = viewModel::toggle,
                    onBrightness = viewModel::setBrightness,
                    onColor = viewModel::setColor,
                    onRoomPower = viewModel::setRoomPower,
                    onEditServer = viewModel::editServer,
                    onRetry = viewModel::retry,
                )
            }
        }
    }
}
