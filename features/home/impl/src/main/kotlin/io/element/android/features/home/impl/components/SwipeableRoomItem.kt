/*
 * Copyright (c) 2025 sTalk / Implica.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.theme.components.Icon

/**
 * Wraps content with swipe-to-action behavior.
 * Swipe right (start→end): Mark as read
 * Swipe left (end→start): Toggle favorite
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableRoomItem(
    onMarkAsRead: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    onMarkAsRead()
                    false // Don't dismiss, snap back
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    onToggleFavorite()
                    false // Don't dismiss, snap back
                }
                SwipeToDismissBoxValue.Settled -> false
            }
        },
        positionalThreshold = { it * 0.3f },
    )

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        backgroundContent = {
            val direction = dismissState.dismissDirection

            val backgroundColor by animateColorAsState(
                targetValue = when (direction) {
                    SwipeToDismissBoxValue.StartToEnd -> ElementTheme.colors.bgSuccessSubtle
                    SwipeToDismissBoxValue.EndToStart -> ElementTheme.colors.bgInfoSubtle
                    SwipeToDismissBoxValue.Settled -> ElementTheme.colors.bgCanvasDefault
                },
                label = "swipe bg color",
            )

            val iconAlignment = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                else -> Alignment.CenterEnd
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(backgroundColor)
                    .padding(horizontal = 24.dp),
                contentAlignment = iconAlignment,
            ) {
                when (direction) {
                    SwipeToDismissBoxValue.StartToEnd -> {
                        Icon(
                            imageVector = CompoundIcons.CheckCircleSolid(),
                            contentDescription = "Mark as read",
                            tint = ElementTheme.colors.iconSuccessPrimary,
                        )
                    }
                    SwipeToDismissBoxValue.EndToStart -> {
                        Icon(
                            imageVector = CompoundIcons.Favourite(),
                            contentDescription = "Favorite",
                            tint = ElementTheme.colors.iconInfoPrimary,
                        )
                    }
                    SwipeToDismissBoxValue.Settled -> {}
                }
            }
        },
        content = { content() },
    )
}
