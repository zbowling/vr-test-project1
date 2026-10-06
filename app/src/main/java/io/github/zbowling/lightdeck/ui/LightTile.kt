package io.github.zbowling.lightdeck.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.zbowling.lightdeck.ha.Light
import io.github.zbowling.lightdeck.ha.Rgb
import kotlin.math.roundToInt
import metavrx.uiset.compose.Icon
import metavrx.uiset.compose.Text
import metavrx.uiset.compose.card.SecondaryCard
import metavrx.uiset.compose.control.Switch
import metavrx.uiset.compose.slider.Slider
import metavrx.uiset.compose.theme.LocalContentColors
import metavrx.uiset.compose.theme.UiSetTheme
import metavrx.uiset.compose.theme.icons.Icons

private val ColorPresets = listOf(
    "Warm white" to Rgb(255, 183, 76),
    "Cool white" to Rgb(240, 236, 228),
    "Red" to Rgb(255, 40, 30),
    "Orange" to Rgb(255, 128, 0),
    "Green" to Rgb(40, 220, 80),
    "Blue" to Rgb(30, 110, 255),
    "Purple" to Rgb(150, 60, 255),
    "Pink" to Rgb(255, 60, 160),
)

private fun Rgb.toColor() = Color(r, g, b)

/**
 * One light, laid out for Look and Pinch:
 * - The header row is the single toggle target. Its Switch only displays state
 *   (null callback), so the system sees one interactable element, and the row is
 *   clipped to the card shape so the system hover highlight matches it.
 * - The slider and each color swatch are separate, clearly spaced targets of at
 *   least 48dp. Nothing depends on hover.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LightTile(
    light: Light,
    onToggle: () -> Unit,
    onBrightness: (Int) -> Unit,
    onColor: (Rgb) -> Unit,
) {
    val spacing = UiSetTheme.dimensions.spacing
    SecondaryCard(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (light.isAvailable) 1f else 0.5f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .clip(UiSetTheme.shapes.card)
                .toggleable(
                    value = light.isOn,
                    enabled = light.isAvailable,
                    role = Role.Switch,
                    onValueChange = { onToggle() },
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.medium),
        ) {
            Icon(
                imageVector = if (light.isOn) Icons.Regular.BrightnessOn else Icons.Regular.BrightnessOff,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = if (light.isOn) {
                    light.rgb?.toColor() ?: UiSetTheme.colorScheme.accent.content.icon
                } else {
                    LocalContentColors.current.icon
                },
            )
            Column(Modifier.weight(1f)) {
                Text(light.name, style = UiSetTheme.typography.title)
                Text(
                    stateLabel(light),
                    style = UiSetTheme.typography.bodySmall,
                    color = LocalContentColors.current.secondary,
                )
            }
            Switch(
                checked = light.isOn,
                onCheckedChange = null,
                contentDescription = null,
                enabled = light.isAvailable,
            )
        }

        if (light.isAvailable && light.supportsBrightness) {
            // Follow the drag locally and commit once, when it ends. Keep the value
            // until Home Assistant reports the light's new state.
            var dragged by remember(light.entityId) { mutableStateOf<Float?>(null) }
            LaunchedEffect(light) { dragged = null }
            Slider(
                value = dragged ?: ((light.brightnessPct ?: 0) / 100f),
                onValueChange = { dragged = it },
                onValueChangeFinished = { dragged?.let { onBrightness((it * 100).roundToInt()) } },
                contentDescription = "${light.name} brightness",
                startIcon = icon { Icons.Regular.BrightnessLow },
                endIcon = icon { Icons.Regular.BrightnessOn },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (light.isAvailable && light.supportsColor) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.medium),
                verticalArrangement = Arrangement.spacedBy(spacing.medium),
            ) {
                ColorPresets.forEach { (name, preset) ->
                    // clip -> background -> clickable: the circle is both the visual and
                    // the hover shape; 48dp is the minimum gaze target.
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(preset.toColor())
                            .clickable(onClickLabel = "Set ${light.name} to $name") { onColor(preset) }
                            .semantics { contentDescription = name },
                    )
                }
            }
        }
    }
}

private fun stateLabel(light: Light): String = when {
    !light.isAvailable -> "Unavailable"
    light.isOn && light.brightnessPct != null -> "On · ${light.brightnessPct}%"
    light.isOn -> "On"
    else -> "Off"
}
