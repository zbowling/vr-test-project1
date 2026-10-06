package io.github.zbowling.lightdeck.ha

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.io.Closeable
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** A minimal Home Assistant WebSocket API server for tests. */
class FakeHomeAssistant(private val validToken: String = TOKEN) : WebSocketListener(), Closeable {
    private val server = MockWebServer()
    private val stateSubscriptions = CopyOnWriteArrayList<Int>()

    /** Every non-auth message clients sent, in order. */
    val received = LinkedBlockingQueue<JsonObject>()

    @Volatile var states: List<JsonObject> = emptyList()
    @Volatile var areas: JsonElement = JsonArray(emptyList())
    @Volatile var entityRegistry: JsonElement = JsonArray(emptyList())
    @Volatile var deviceRegistry: JsonElement = JsonArray(emptyList())
    @Volatile var failingCommands: Set<String> = emptySet()
    @Volatile private var socket: WebSocket? = null

    val url: String get() = server.url("/api/websocket").toString().replaceFirst("http", "ws")

    val client: OkHttpClient = OkHttpClient()

    /** Lets [count] more clients connect. */
    fun acceptConnections(count: Int = 1) = repeat(count) {
        server.enqueue(MockResponse().withWebSocketUpgrade(this))
    }

    fun start(): FakeHomeAssistant = apply { server.start() }

    /** Ends the current connection from the server side, like a Home Assistant restart. */
    fun dropConnection() {
        stateSubscriptions.clear()
        socket?.close(1001, "Going away")
    }

    fun sendStateChanged(entityId: String, newState: JsonObject?) {
        val ws = socket ?: error("No client connected")
        stateSubscriptions.forEach { id ->
            ws.send(
                buildJsonObject {
                    put("id", id)
                    put("type", "event")
                    putJsonObject("event") {
                        put("event_type", "state_changed")
                        putJsonObject("data") {
                            put("entity_id", entityId)
                            put("new_state", newState ?: JsonNull)
                        }
                    }
                }.toString(),
            )
        }
    }

    /** Waits for the next message of [type] a client sent. */
    fun awaitMessage(type: String, timeoutSeconds: Long = 5): JsonObject {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds)
        while (true) {
            val remaining = deadline - System.nanoTime()
            val message = received.poll(remaining.coerceAtLeast(0), TimeUnit.NANOSECONDS)
                ?: error("No \"$type\" message within ${timeoutSeconds}s")
            if (message.string("type") == type) return message
        }
    }

    override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
        socket = webSocket
        webSocket.send("""{"type":"auth_required","ha_version":"2026.10.0"}""")
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        val message = HaJson.parseToJsonElement(text).jsonObject
        val type = message.string("type")
        if (type == "auth") {
            if (message.string("access_token") == validToken) {
                webSocket.send("""{"type":"auth_ok","ha_version":"2026.10.0"}""")
            } else {
                webSocket.send("""{"type":"auth_invalid","message":"Invalid access token or password"}""")
            }
            return
        }
        received.add(message)
        val id = message.int("id")!!
        if (type in failingCommands) {
            webSocket.send(error(id, "unauthorized", "Unauthorized"))
            return
        }
        val result: JsonElement = when (type) {
            "get_states" -> JsonArray(states)
            "config/area_registry/list" -> areas
            "config/entity_registry/list" -> entityRegistry
            "config/device_registry/list" -> deviceRegistry
            "subscribe_events" -> {
                if (message.string("event_type") == "state_changed") stateSubscriptions.add(id)
                JsonNull
            }
            "call_service" -> buildJsonObject { putJsonObject("context") { put("id", "ctx") } }
            else -> {
                webSocket.send(error(id, "unknown_command", "Unknown command."))
                return
            }
        }
        webSocket.send(
            buildJsonObject {
                put("id", id)
                put("type", "result")
                put("success", true)
                put("result", result)
            }.toString(),
        )
    }

    private fun error(id: Int, code: String, message: String) = buildJsonObject {
        put("id", id)
        put("type", "result")
        put("success", false)
        putJsonObject("error") {
            put("code", code)
            put("message", message)
        }
    }.toString()

    override fun close() {
        socket?.close(1001, null)
        server.shutdown()
        client.dispatcher.executorService.shutdown()
    }

    companion object {
        const val TOKEN = "good-token"

        fun lightState(
            entityId: String,
            state: String = "on",
            name: String? = null,
            brightness: Int? = if (state == "on") 255 else null,
            colorModes: List<String> = listOf("brightness"),
            rgb: List<Int>? = null,
        ): JsonObject = buildJsonObject {
            put("entity_id", entityId)
            put("state", state)
            putJsonObject("attributes") {
                name?.let { put("friendly_name", it) }
                brightness?.let { put("brightness", it) }
                putJsonArray("supported_color_modes") { colorModes.forEach { add(it) } }
                rgb?.let { putJsonArray("rgb_color") { it.forEach { c -> add(c) } } }
            }
        }

        fun registry(vararg entries: Pair<String, String?>, key: String = "area_id") = buildJsonArray {
            entries.forEach { (id, value) ->
                add(
                    buildJsonObject {
                        put(if (key == "name") "area_id" else "entity_id", id)
                        put(key, value)
                    },
                )
            }
        }
    }
}
