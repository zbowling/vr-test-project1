package io.github.zbowling.lightdeck.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.zbowling.lightdeck.DiscoveredServer

@Composable
fun SetupScreen(
    initialAddress: String,
    discovered: List<DiscoveredServer>,
    onConnect: (address: String, token: String) -> Unit,
    onCancel: (() -> Unit)?,
) {
    var address by rememberSaveable { mutableStateOf(initialAddress) }
    var token by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Connect to Home Assistant", style = MaterialTheme.typography.headlineMedium)

            if (discovered.isNotEmpty()) {
                Text("Found on your network", style = MaterialTheme.typography.titleMedium)
                discovered.forEach { server ->
                    OutlinedButton(
                        onClick = { address = server.address },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) {
                        Text("${server.name} · ${server.address}")
                    }
                }
            }

            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                label = { Text("Address") },
                placeholder = { Text("homeassistant.local:8123") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            if (address.trim().startsWith("http://", ignoreCase = true)) {
                Text(
                    "Over http:// your token travels unencrypted. Only use it on your home network.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }

            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                label = { Text("Long-lived access token") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Create one in Home Assistant under your profile → Security → Long-lived access tokens.",
                style = MaterialTheme.typography.bodySmall,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(
                    onClick = { onConnect(address, token) },
                    modifier = Modifier.weight(1f).height(64.dp),
                ) {
                    Text("Connect", style = MaterialTheme.typography.titleMedium)
                }
                if (onCancel != null) {
                    OutlinedButton(onClick = onCancel, modifier = Modifier.height(64.dp)) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}
