package io.github.zbowling.lightdeck.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.zbowling.lightdeck.LightsViewModel

@Composable
fun LightDeckApp(viewModel: LightsViewModel) {
    val showSetup by viewModel.showSetup.collectAsStateWithLifecycle()
    val config by viewModel.config.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
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
