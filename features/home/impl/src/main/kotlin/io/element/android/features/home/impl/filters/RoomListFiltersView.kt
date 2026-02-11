/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.filters

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import io.element.android.features.home.impl.components.StalkUnderlineFilter
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight

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
            StalkUnderlineFilter(
                text = stringResource(id = filterWithSelection.filter.stringResource),
                isSelected = filterWithSelection.isSelected,
                onClick = { onToggleFilter(filterWithSelection.filter) },
            )
        }
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
