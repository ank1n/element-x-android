/*
 * Copyright (c) 2025 sTalk / Implica.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.contacts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.features.home.impl.model.RoomListRoomSummary
import io.element.android.features.home.impl.roomlist.RoomListContentState
import io.element.android.libraries.designsystem.components.avatar.Avatar
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.matrix.api.core.RoomId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * Contacts view showing DM rooms filtered to only real people.
 * Filters: isDm = true, excludes empty rooms and spaces.
 * Per TZ #23: only show rooms with 2+ active members.
 */
@Composable
fun ContactsView(
    contentState: RoomListContentState,
    onContactClick: (RoomId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val contacts: ImmutableList<RoomListRoomSummary> = remember(contentState) {
        when (contentState) {
            is RoomListContentState.Rooms -> contentState.summaries
                .filter { it.isDm && !it.isSpace && it.name != null }
                .sortedBy { it.name?.lowercase() }
                .toImmutableList()
            else -> emptyList<RoomListRoomSummary>().toImmutableList()
        }
    }

    if (contacts.isEmpty()) {
        Column(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "No contacts yet",
                style = ElementTheme.typography.fontBodyLgRegular,
                color = ElementTheme.colors.textSecondary,
            )
        }
    } else {
        LazyColumn(modifier = modifier.fillMaxSize()) {
            items(
                items = contacts,
                key = { it.roomId.value },
            ) { contact ->
                ContactItem(
                    contact = contact,
                    onClick = { onContactClick(contact.roomId) },
                )
            }
        }
    }
}

@Composable
private fun ContactItem(
    contact: RoomListRoomSummary,
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
            avatarData = contact.avatarData.copy(size = AvatarSize.UserListItem),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = contact.name.orEmpty(),
            style = ElementTheme.typography.fontBodyLgMedium,
            color = ElementTheme.colors.textPrimary,
        )
    }
}
