package io.github.zbowling.lightdeck.ha

import java.net.URI
import java.net.URISyntaxException

object HaUrls {
    private const val DEFAULT_PORT = 8123
    private const val WEBSOCKET_PATH = "/api/websocket"

    /**
     * Turns what a user types ("homeassistant.local", "192.168.1.5:8123",
     * "https://example.ui.nabu.casa/") into the WebSocket API URL.
     * A bare host with no scheme gets Home Assistant's default port 8123.
     */
    fun webSocketUrl(input: String): String {
        val trimmed = input.trim()
        require(trimmed.isNotEmpty()) { "Enter your Home Assistant address" }
        val hasScheme = trimmed.contains("://")
        val uri = try {
            URI(if (hasScheme) trimmed else "http://$trimmed")
        } catch (e: URISyntaxException) {
            throw IllegalArgumentException("\"$trimmed\" is not a valid address", e)
        }
        val scheme = when (uri.scheme?.lowercase()) {
            "http", "ws" -> "ws"
            "https", "wss" -> "wss"
            else -> throw IllegalArgumentException("Use an http:// or https:// address")
        }
        val host = uri.host ?: throw IllegalArgumentException("\"$trimmed\" has no host name")
        val port = when {
            uri.port != -1 -> uri.port
            !hasScheme -> DEFAULT_PORT
            else -> -1
        }
        val basePath = uri.path.orEmpty().trimEnd('/').removeSuffix(WEBSOCKET_PATH)
        return URI(scheme, null, host, port, basePath + WEBSOCKET_PATH, null, null).toString()
    }
}
