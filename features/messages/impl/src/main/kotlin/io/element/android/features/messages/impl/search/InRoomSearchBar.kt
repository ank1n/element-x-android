/*
 * Copyright (c) 2025 sTalk / Implica.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Text

/**
 * Inline search bar for searching messages within a room.
 * Shown at the top of the chat screen with query input, result counter, and navigation buttons.
 */
@Composable
fun InRoomSearchBar(
    state: InRoomSearchState,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(state.isActive) {
        if (state.isActive) {
            focusRequester.requestFocus()
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(ElementTheme.colors.bgCanvasDefault)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Search icon
        Icon(
            imageVector = CompoundIcons.Search(),
            contentDescription = null,
            tint = ElementTheme.colors.iconSecondary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))

        // Search input
        BasicTextField(
            value = state.query,
            onValueChange = { state.eventSink(InRoomSearchEvent.QueryChanged(it)) },
            singleLine = true,
            textStyle = ElementTheme.typography.fontBodyMdRegular.copy(
                color = ElementTheme.colors.textPrimary,
            ),
            cursorBrush = SolidColor(ElementTheme.colors.iconPrimary),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
            decorationBox = { innerTextField ->
                if (state.query.isEmpty()) {
                    Text(
                        text = "Search messages...",
                        style = ElementTheme.typography.fontBodyMdRegular,
                        color = ElementTheme.colors.textSecondary,
                    )
                }
                innerTextField()
            },
        )

        // Result counter
        if (state.query.isNotEmpty()) {
            Text(
                text = "${state.currentMatchDisplay}/${state.totalMatches}",
                style = ElementTheme.typography.fontBodySmRegular,
                color = ElementTheme.colors.textSecondary,
                modifier = Modifier.padding(horizontal = 4.dp),
            )

            // Up button (previous match)
            IconButton(
                onClick = { state.eventSink(InRoomSearchEvent.PreviousMatch) },
                enabled = state.totalMatches > 0,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = CompoundIcons.ChevronUp(),
                    contentDescription = "Previous result",
                    tint = if (state.totalMatches > 0) {
                        ElementTheme.colors.iconPrimary
                    } else {
                        ElementTheme.colors.iconDisabled
                    },
                    modifier = Modifier.size(20.dp),
                )
            }

            // Down button (next match)
            IconButton(
                onClick = { state.eventSink(InRoomSearchEvent.NextMatch) },
                enabled = state.totalMatches > 0,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = CompoundIcons.ChevronDown(),
                    contentDescription = "Next result",
                    tint = if (state.totalMatches > 0) {
                        ElementTheme.colors.iconPrimary
                    } else {
                        ElementTheme.colors.iconDisabled
                    },
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        // Close button
        IconButton(
            onClick = { state.eventSink(InRoomSearchEvent.ToggleSearch) },
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                imageVector = CompoundIcons.Close(),
                contentDescription = "Close search",
                tint = ElementTheme.colors.iconSecondary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
