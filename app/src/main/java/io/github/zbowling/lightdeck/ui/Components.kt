package io.github.zbowling.lightdeck.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import metavrx.uiset.compose.Icon
import metavrx.uiset.compose.Text
import metavrx.uiset.compose.button.ButtonStyle
import metavrx.uiset.compose.button.LabelButton
import metavrx.uiset.compose.theme.UiSetTheme
import metavrx.uiset.compose.theme.icons.Icons

/**
 * Wraps an icon for UI Set icon slots. Icons are composable properties, so the
 * vector is read inside the returned lambda, not here.
 */
fun icon(vector: @Composable () -> ImageVector): @Composable () -> Unit = {
    Icon(imageVector = vector(), contentDescription = null, modifier = Modifier.size(24.dp))
}

/** Full-panel message with an optional action, for empty, loading and error states. */
@Composable
fun CenteredMessage(
    title: String,
    body: String? = null,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    val spacing = UiSetTheme.dimensions.spacing
    Column(
        modifier = Modifier.fillMaxSize().padding(spacing.twoXLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.large, Alignment.CenterVertically),
    ) {
        Text(title, style = UiSetTheme.typography.headline.copy(textAlign = TextAlign.Center))
        if (body != null) {
            Text(
                body,
                style = UiSetTheme.typography.body.copy(textAlign = TextAlign.Center),
                color = UiSetTheme.colorScheme.background.content.secondary,
                modifier = Modifier.widthIn(max = 520.dp),
            )
        }
        if (action != null) {
            LabelButton(label = action, onClick = onAction, style = ButtonStyle.Primary)
        }
    }
}

/**
 * Transient message shown inline at the top of the panel. UI Set has no snackbar,
 * and a banner inside the panel keeps the message where the user is looking.
 */
@Composable
fun MessageBanner(message: String, modifier: Modifier = Modifier) {
    val spacing = UiSetTheme.dimensions.spacing
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(UiSetTheme.shapes.card)
            .background(UiSetTheme.colorScheme.surfaceVariant.container.brush)
            .padding(horizontal = spacing.large, vertical = spacing.medium)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.medium),
    ) {
        Icon(
            imageVector = Icons.Regular.Warning,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = UiSetTheme.colorScheme.surfaceVariant.content.icon,
        )
        Text(
            message,
            style = UiSetTheme.typography.body,
            color = UiSetTheme.colorScheme.surfaceVariant.content.primary,
        )
    }
}
