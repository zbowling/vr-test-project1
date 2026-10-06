package io.github.zbowling.lightdeck.ha

import io.github.zbowling.lightdeck.ha.FakeHomeAssistant.Companion.lightState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LightRepositoryTest {
    private val ha = FakeHomeAssistant().start()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @After fun tearDown() {
        scope.cancel()
        ha.close()
    }

    private fun repository(token: String = FakeHomeAssistant.TOKEN) = LightRepository(
        connect = { HaSession.connect(ha.client, ha.url, token, timeoutMs = 5_000) },
        retryDelaysMs = listOf(10),
    )

    private fun <T> eventually(block: suspend () -> T): T = runBlocking { withTimeout(5_000) { block() } }

    @Test fun loadsRoomsAndFollowsStateChanges() {
        ha.acceptConnections()
        ha.states = listOf(lightState("light.desk", name = "Desk"), lightState("light.porch", state = "off", name = "Porch"))
        ha.areas = FakeHomeAssistant.registry("office" to "Office", key = "name")
        ha.entityRegistry = FakeHomeAssistant.registry("light.desk" to "office")
        val repo = repository()
        scope.launch { repo.run() }

        eventually { repo.status.first { it is ConnectionStatus.Connected } }
        val rooms = eventually { repo.rooms.first { it.isNotEmpty() } }
        assertEquals(listOf("Office", "Other lights"), rooms.map { it.name })
        assertFalse(rooms[1].lights.single().isOn)

        ha.sendStateChanged("light.porch", lightState("light.porch", name = "Porch"))
        eventually { repo.rooms.first { it.last().lights.single().isOn } }

        ha.sendStateChanged("light.porch", null)
        eventually { repo.rooms.first { it.size == 1 } }
    }

    @Test fun sendsServiceCalls() {
        ha.acceptConnections()
        ha.states = listOf(lightState("light.desk", colorModes = listOf("rgb")))
        val repo = repository()
        scope.launch { repo.run() }
        eventually { repo.status.first { it is ConnectionStatus.Connected } }

        runBlocking { repo.toggle("light.desk") }
        assertService(ha.awaitMessage("call_service"), "toggle", buildJsonObject {}, "light.desk")

        runBlocking { repo.setBrightness("light.desk", 40) }
        assertService(ha.awaitMessage("call_service"), "turn_on", buildJsonObject { put("brightness_pct", 40) }, "light.desk")

        runBlocking { repo.setBrightness("light.desk", 0) }
        assertService(ha.awaitMessage("call_service"), "turn_off", buildJsonObject {}, "light.desk")

        runBlocking { repo.setColor("light.desk", Rgb(1, 2, 3)) }
        assertService(
            ha.awaitMessage("call_service"),
            "turn_on",
            buildJsonObject { put("rgb_color", buildJsonArray { add(JsonPrimitive(1)); add(JsonPrimitive(2)); add(JsonPrimitive(3)) }) },
            "light.desk",
        )

        val room = eventually { repo.rooms.first { it.isNotEmpty() } }.single()
        runBlocking { repo.setRoomPower(room, on = false) }
        assertService(ha.awaitMessage("call_service"), "turn_off", buildJsonObject {}, "light.desk")
    }

    @Test fun reconnectsAfterConnectionDrops() {
        ha.acceptConnections(2)
        ha.states = listOf(lightState("light.desk"))
        val repo = repository()
        scope.launch { repo.run() }
        eventually { repo.status.first { it is ConnectionStatus.Connected } }

        ha.states = listOf(lightState("light.desk"), lightState("light.new"))
        ha.dropConnection()
        val rooms = eventually { repo.rooms.first { rooms -> rooms.sumOf { it.lights.size } == 2 } }
        assertEquals(2, rooms.single().lights.size)
        eventually { repo.status.first { it is ConnectionStatus.Connected } }
    }

    @Test fun stopsOnRejectedToken() {
        ha.acceptConnections()
        val repo = repository(token = "wrong")
        val job = scope.launch { repo.run() }
        eventually { job.join() }
        assertTrue(repo.status.value is ConnectionStatus.AuthFailed)
    }

    @Test fun worksWithoutRegistryAccess() {
        ha.acceptConnections()
        ha.states = listOf(lightState("light.desk"))
        ha.failingCommands = setOf("config/entity_registry/list", "config/area_registry/list", "config/device_registry/list")
        val repo = repository()
        scope.launch { repo.run() }
        val rooms = eventually { repo.rooms.first { it.isNotEmpty() } }
        assertEquals("Other lights", rooms.single().name)
    }

    @Test fun commandsFailWhenDisconnected() {
        val error = runCatching { runBlocking { repository().toggle("light.desk") } }.exceptionOrNull()
        assertTrue(error is java.io.IOException)
    }

    private fun assertService(message: JsonObject, service: String, data: JsonObject, entityId: String) {
        assertEquals(JsonPrimitive("light"), message["domain"])
        assertEquals(JsonPrimitive(service), message["service"])
        assertEquals(data, message["service_data"])
        assertEquals(
            buildJsonObject { put("entity_id", JsonArray(listOf(JsonPrimitive(entityId)))) },
            message["target"],
        )
    }
}
