package io.github.zbowling.lightdeck.ha

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlin.math.roundToInt

data class Rgb(val r: Int, val g: Int, val b: Int)

data class Light(
    val entityId: String,
    val name: String,
    val isOn: Boolean,
    val isAvailable: Boolean,
    /** 1-100 while the light is on, null when off or not dimmable. */
    val brightnessPct: Int?,
    val rgb: Rgb?,
    val supportsBrightness: Boolean,
    val supportsColor: Boolean,
) {
    companion object {
        private val COLOR_MODES = setOf("hs", "xy", "rgb", "rgbw", "rgbww")

        /** Parses a Home Assistant state object for a `light.*` entity. */
        fun fromState(state: JsonObject): Light? {
            val entityId = state.string("entity_id") ?: return null
            val attributes = state.obj("attributes") ?: JsonObject(emptyMap())
            val rawState = state.string("state")
            val modes = (attributes["supported_color_modes"] as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                ?.toSet()
            val rgb = (attributes["rgb_color"] as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.intOrNull }
                ?.takeIf { it.size == 3 }
                ?.let { (r, g, b) -> Rgb(r, g, b) }
            return Light(
                entityId = entityId,
                name = attributes.string("friendly_name")
                    ?: entityId.substringAfter('.').replace('_', ' '),
                isOn = rawState == "on",
                isAvailable = rawState != "unavailable",
                brightnessPct = attributes.double("brightness")
                    ?.let { (it * 100 / 255).roundToInt().coerceIn(1, 100) },
                rgb = rgb,
                // Lights that predate color modes only report a brightness attribute.
                supportsBrightness = modes?.any { it != "onoff" }
                    ?: attributes.containsKey("brightness"),
                supportsColor = modes?.any { it in COLOR_MODES } ?: false,
            )
        }
    }
}
