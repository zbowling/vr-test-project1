package io.github.zbowling.lightdeck.ha

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

sealed interface ConnectionStatus {
    data object Connecting : ConnectionStatus
    data class Connected(val haVersion: String?) : ConnectionStatus
    data class Retrying(val message: String, val retryInMs: Long) : ConnectionStatus
    data class AuthFailed(val message: String) : ConnectionStatus
}

/**
 * Keeps a live view of the home's lights: loads them once per connection, then
 * applies `state_changed` events so changes made anywhere show up immediately.
 * [run] reconnects with backoff until cancelled or the token is rejected.
 */
class LightRepository(
    private val connect: suspend () -> HaSession,
    private val retryDelaysMs: List<Long> = listOf(1_000, 2_000, 5_000, 10_000, 30_000),
) {
    private val _status = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Connecting)
    val status: StateFlow<ConnectionStatus> = _status.asStateFlow()

    private val home = MutableStateFlow(Home())
    val rooms: Flow<List<Room>> = home.map { it.rooms() }

    @Volatile
    private var session: HaSession? = null

    suspend fun run() {
        var failures = 0
        while (true) {
            _status.value = ConnectionStatus.Connecting
            val error = try {
                val connected = connect()
                try {
                    // Subscribe before loading so no change slips in between.
                    val events = connected.subscribeEvents("state_changed")
                    home.value = loadHome(connected)
                    session = connected
                    failures = 0
                    _status.value = ConnectionStatus.Connected(connected.haVersion)
                    for (event in events) applyStateChange(event)
                    IOException("Connection closed")
                } finally {
                    session = null
                    connected.close()
                }
            } catch (e: HaAuthException) {
                _status.value = ConnectionStatus.AuthFailed(e.message ?: "Invalid access token")
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e
            }
            val wait = retryDelaysMs[failures.coerceAtMost(retryDelaysMs.lastIndex)]
            failures++
            _status.value = ConnectionStatus.Retrying(
                message = error.message ?: error.javaClass.simpleName,
                retryInMs = wait,
            )
            delay(wait)
        }
    }

    suspend fun toggle(entityId: String) = callLight("toggle", listOf(entityId))

    suspend fun setBrightness(entityId: String, percent: Int) {
        if (percent <= 0) {
            callLight("turn_off", listOf(entityId))
        } else {
            callLight("turn_on", listOf(entityId)) { put("brightness_pct", percent.coerceAtMost(100)) }
        }
    }

    suspend fun setColor(entityId: String, color: Rgb) = callLight("turn_on", listOf(entityId)) {
        putJsonArray("rgb_color") {
            add(color.r)
            add(color.g)
            add(color.b)
        }
    }

    suspend fun setRoomPower(room: Room, on: Boolean) {
        val ids = room.lights.filter { it.isAvailable }.map { it.entityId }
        if (ids.isNotEmpty()) callLight(if (on) "turn_on" else "turn_off", ids)
    }

    private suspend fun callLight(
        service: String,
        entityIds: List<String>,
        data: JsonObjectBuilder.() -> Unit = {},
    ) {
        val current = session ?: throw IOException("Not connected to Home Assistant")
        current.command("call_service") {
            put("domain", "light")
            put("service", service)
            put("service_data", buildJsonObject(data))
            putJsonObject("target") {
                putJsonArray("entity_id") { entityIds.forEach { add(it) } }
            }
        }
    }

    private fun applyStateChange(event: JsonObject) {
        val data = event.obj("data") ?: return
        val entityId = data.string("entity_id") ?: return
        if (!entityId.startsWith("light.")) return
        val light = data.obj("new_state")?.let(Light::fromState)
        home.update { it.withLight(entityId, light) }
    }

    private suspend fun loadHome(session: HaSession): Home = buildHome(
        states = session.command("get_states"),
        // Registries only add room grouping; a user without access still gets their lights.
        areaRegistry = session.optionalCommand("config/area_registry/list"),
        entityRegistry = session.optionalCommand("config/entity_registry/list"),
        deviceRegistry = session.optionalCommand("config/device_registry/list"),
    )

    private suspend fun HaSession.optionalCommand(type: String): JsonElement? = try {
        command(type)
    } catch (e: HaCommandException) {
        null
    }
}
