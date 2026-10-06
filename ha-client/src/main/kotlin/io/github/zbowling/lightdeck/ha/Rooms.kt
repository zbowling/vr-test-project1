package io.github.zbowling.lightdeck.ha

data class Area(val id: String, val name: String)

data class Room(val area: Area?, val lights: List<Light>) {
    val name: String get() = area?.name ?: "Other lights"
    val anyOn: Boolean get() = lights.any { it.isOn }
}

/** Everything the app knows about the home's lights at one moment. */
data class Home(
    val lights: Map<String, Light> = emptyMap(),
    val areas: Map<String, Area> = emptyMap(),
    /** entity_id -> area_id, from the entity registry or the entity's device. */
    val areaOfEntity: Map<String, String> = emptyMap(),
    /** Entities hidden in Home Assistant, which the app doesn't show either. */
    val hidden: Set<String> = emptySet(),
) {
    fun withLight(entityId: String, light: Light?): Home = when {
        entityId in hidden -> this
        light == null -> copy(lights = lights - entityId)
        else -> copy(lights = lights + (entityId to light))
    }

    /** Rooms sorted by name with their lights sorted by name; unassigned lights come last. */
    fun rooms(): List<Room> = lights.values
        .groupBy { light -> areaOfEntity[light.entityId]?.let(areas::get) }
        .map { (area, lights) -> Room(area, lights.sortedBy { it.name.lowercase() }) }
        .sortedWith(compareBy<Room> { it.area == null }.thenBy { it.name.lowercase() })
}
