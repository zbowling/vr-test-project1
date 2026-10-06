package io.github.zbowling.lightdeck.ha

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull

/** Builds a [Home] from the results of get_states and the area, entity and device registries. */
internal fun buildHome(
    states: JsonElement?,
    areaRegistry: JsonElement?,
    entityRegistry: JsonElement?,
    deviceRegistry: JsonElement?,
): Home {
    val areas = areaRegistry.objects()
        .mapNotNull { a -> a.string("area_id")?.let { id -> Area(id, a.string("name") ?: id) } }
        .associateBy { it.id }
    val areaOfDevice = deviceRegistry.objects()
        .mapNotNull { d -> d.string("id")?.let { id -> d.string("area_id")?.let { id to it } } }
        .toMap()
    val entities = entityRegistry.objects()
    val hidden = entities
        .filter { it.isSet("hidden_by") || it.isSet("disabled_by") }
        .mapNotNull { it.string("entity_id") }
        .toSet()
    val areaOfEntity = entities.mapNotNull { e ->
        val id = e.string("entity_id") ?: return@mapNotNull null
        val area = e.string("area_id") ?: e.string("device_id")?.let(areaOfDevice::get)
        area?.let { id to it }
    }.toMap()
    val lights = states.objects()
        .filter { it.string("entity_id")?.startsWith("light.") == true }
        .mapNotNull(Light::fromState)
        .filter { it.entityId !in hidden }
        .associateBy { it.entityId }
    return Home(lights, areas, areaOfEntity, hidden)
}

private fun kotlinx.serialization.json.JsonObject.isSet(key: String): Boolean =
    this[key].let { it != null && it != JsonNull }
