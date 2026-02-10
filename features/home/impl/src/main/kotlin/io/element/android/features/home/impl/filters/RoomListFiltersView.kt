/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.filters

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Text

/**
 * Telegram-style underline filters for room list.
 * Replaces Material 3 FilterChip with underline tab indicators.
 */
@Composable
fun RoomListFiltersView(
    state: RoomListFiltersState,
    modifier: Modifier = Modifier
) {
    fun onToggleFilter(filter: RoomListFilter) {
        state.eventSink(RoomListFiltersEvent.ToggleFilter(filter))
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        state.filterSelectionStates.forEach { filterWithSelection ->
            UnderlineFilterItem(
                text = stringResource(id = filterWithSelection.filter.stringResource),
                selected = filterWithSelection.isSelected,
                onClick = { onToggleFilter(filterWithSelection.filter) },
            )
        }
    }
}

@Composable
private fun UnderlineFilterItem(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val textColor by animateColorAsState(
        targetValue = if (selected) {
            ElementTheme.colors.textPrimary
        } else {
            ElementTheme.colors.textSecondary
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "filter text color",
    )

    val underlineColor by animateColorAsState(
        targetValue = if (selected) {
            ElementTheme.colors.iconPrimary
        } else {
            Color.Transparent
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "filter underline color",
    )

    val underlineWidth by animateDpAsState(
        targetValue = if (selected) 40.dp else 0.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "filter underline width",
    )

    Column(
        modifier = modifier
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            )
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = text,
            style = ElementTheme.typography.fontBodyMdMedium,
            color = textColor,
        )
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .width(underlineWidth)
                .height(2.dp)
                .background(underlineColor)
        )
    }
}

@PreviewsDayNight
@Composable
internal fun RoomListFiltersViewPreview(@PreviewParameter(RoomListFiltersStateProvider::class) state: RoomListFiltersState) = ElementPreview {
    RoomListFiltersView(
        modifier = Modifier.padding(vertical = 4.dp),
        state = state,
    )
}
