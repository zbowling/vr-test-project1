package io.github.zbowling.lightdeck.ha

import io.github.zbowling.lightdeck.ha.FakeHomeAssistant.Companion.lightState
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeTest {
    private val home = buildHome(
        states = JsonArray(
            listOf(
                lightState("light.sofa", name = "Sofa"),
                lightState("light.ceiling", name = "Ceiling"),
                lightState("light.bed", name = "Bed"),
                lightState("light.hallway", name = "Hallway"),
                lightState("light.secret", name = "Hidden"),
                buildJsonObject {
                    put("entity_id", "switch.fan")
                    put("state", "on")
                },
            ),
        ),
        areaRegistry = FakeHomeAssistant.registry("living" to "Living Room", "bedroom" to "Bedroom", key = "name"),
        entityRegistry = JsonArray(
            listOf(
                entity("light.sofa", areaId = "living"),
                entity("light.ceiling", deviceId = "dev-1"),
                entity("light.bed", areaId = "bedroom"),
                entity("light.secret", areaId = "living", hiddenBy = "user"),
            ),
        ),
        deviceRegistry = JsonArray(
            listOf(
                buildJsonObject {
                    put("id", "dev-1")
                    put("area_id", "living")
                },
            ),
        ),
    )

    @Test fun groupsLightsByAreaWithUnassignedLast() {
        val rooms = home.rooms()
        assertEquals(listOf("Bedroom", "Living Room", "Other lights"), rooms.map { it.name })
        assertEquals(listOf("Ceiling", "Sofa"), rooms[1].lights.map { it.name })
        assertEquals(listOf("Hallway"), rooms[2].lights.map { it.name })
    }

    @Test fun hiddenEntitiesStayHiddenAfterUpdates() {
        val updated = home.withLight("light.secret", Light.fromState(lightState("light.secret")))
        assertEquals(4, updated.lights.size)
    }

    @Test fun removedLightDisappears() {
        assertEquals(3, home.withLight("light.hallway", null).lights.size)
    }

    private fun entity(id: String, areaId: String? = null, deviceId: String? = null, hiddenBy: String? = null) =
        buildJsonObject {
            put("entity_id", id)
            put("area_id", areaId)
            put("device_id", deviceId)
            put("hidden_by", hiddenBy)
            put("disabled_by", JsonNull)
        }
}
