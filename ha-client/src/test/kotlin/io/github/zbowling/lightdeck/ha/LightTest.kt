package io.github.zbowling.lightdeck.ha

import io.github.zbowling.lightdeck.ha.FakeHomeAssistant.Companion.lightState
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LightTest {
    @Test fun colorLightThatIsOn() {
        val light = Light.fromState(
            lightState("light.desk", name = "Desk", brightness = 128, colorModes = listOf("xy", "color_temp"), rgb = listOf(255, 10, 0)),
        )!!
        assertEquals("Desk", light.name)
        assertTrue(light.isOn)
        assertEquals(50, light.brightnessPct)
        assertEquals(Rgb(255, 10, 0), light.rgb)
        assertTrue(light.supportsBrightness)
        assertTrue(light.supportsColor)
    }

    @Test fun onOffOnlyLightThatIsOff() {
        val light = Light.fromState(lightState("light.porch_lamp", state = "off", colorModes = listOf("onoff")))!!
        assertEquals("porch lamp", light.name)
        assertFalse(light.isOn)
        assertTrue(light.isAvailable)
        assertNull(light.brightnessPct)
        assertFalse(light.supportsBrightness)
        assertFalse(light.supportsColor)
    }

    @Test fun unavailableLight() {
        val light = Light.fromState(lightState("light.garage", state = "unavailable"))!!
        assertFalse(light.isAvailable)
        assertFalse(light.isOn)
    }

    @Test fun legacyLightWithoutColorModes() {
        val state = HaJson.parseToJsonElement(
            """{"entity_id":"light.old","state":"on","attributes":{"brightness":3}}""",
        ).jsonObject
        val light = Light.fromState(state)!!
        assertTrue(light.supportsBrightness)
        assertEquals(1, light.brightnessPct)
    }
}
