/*
 * Copyright (c) 2025 sTalk / Implica.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.calls

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.home.impl.components.StalkUnderlineFilter
import io.element.android.features.home.impl.model.RoomListRoomSummary
import io.element.android.features.home.impl.roomlist.RoomListContentState
import io.element.android.libraries.designsystem.components.avatar.Avatar
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.designsystem.components.avatar.AvatarType
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.matrix.api.core.RoomId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

private enum class CallsFilter(val displayName: String) {
    All("All"),
    Missed("Missed"),
    Outgoing("Outgoing"),
    Incoming("Incoming"),
}

/**
 * Calls view showing rooms with active or recent calls.
 * Active calls are shown at the top with a green indicator.
 */
@Composable
fun CallsView(
    contentState: RoomListContentState,
    onRoomClick: (RoomId) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedFilter by remember { mutableStateOf(CallsFilter.All) }

    val activeCalls: ImmutableList<RoomListRoomSummary> = remember(contentState) {
        when (contentState) {
            is RoomListContentState.Rooms -> contentState.summaries
                .filter { it.hasRoomCall }
                .toImmutableList()
            else -> emptyList<RoomListRoomSummary>().toImmutableList()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Underline filters
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            CallsFilter.entries.forEach { filter ->
                StalkUnderlineFilter(
                    text = filter.displayName,
                    isSelected = selectedFilter == filter,
                    onClick = { selectedFilter = filter },
                )
            }
        }

        if (activeCalls.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = CompoundIcons.VoiceCall(),
                    contentDescription = null,
                    tint = ElementTheme.colors.iconSecondary,
                    modifier = Modifier.size(48.dp),
                )
                Spacer(modifier = Modifier.size(16.dp))
                Text(
                    text = "No calls yet",
                    style = ElementTheme.typography.fontBodyLgRegular,
                    color = ElementTheme.colors.textSecondary,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(
                    items = activeCalls,
                    key = { it.roomId.value },
                ) { room ->
                    CallItem(
                        room = room,
                        onClick = { onRoomClick(room.roomId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CallItem(
    room: RoomListRoomSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(
            avatarData = room.avatarData.copy(size = AvatarSize.UserListItem),
            avatarType = AvatarType.User,
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = room.name.orEmpty(),
                style = ElementTheme.typography.fontBodyLgMedium,
                color = ElementTheme.colors.textPrimary,
            )
            Text(
                text = "Active call",
                style = ElementTheme.typography.fontBodySmRegular,
                color = ElementTheme.colors.textSuccessPrimary,
            )
        }
        Icon(
            imageVector = CompoundIcons.VoiceCallSolid(),
            contentDescription = null,
            tint = ElementTheme.colors.iconSuccessPrimary,
            modifier = Modifier.size(24.dp),
        )
    }
}
