/*
 * Copyright (c) 2025 sTalk / Implica.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.contacts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.element.android.compound.theme.ElementTheme
import io.element.android.features.home.impl.components.StalkUnderlineFilter
import io.element.android.features.home.impl.model.RoomListRoomSummary
import io.element.android.features.home.impl.roomlist.RoomListContentState
import io.element.android.libraries.designsystem.components.avatar.Avatar
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.designsystem.components.avatar.AvatarType
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.matrix.api.core.RoomId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch

private enum class ContactsFilter(val displayName: String) {
    All("All"),
    Online("Online"),
}

private val ALPHABET = ('A'..'Z').toList()

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
    var selectedFilter by remember { mutableStateOf(ContactsFilter.All) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val allContacts: ImmutableList<RoomListRoomSummary> = remember(contentState) {
        when (contentState) {
            is RoomListContentState.Rooms -> contentState.summaries
                .filter { it.isDm && !it.isSpace && it.name != null }
                .sortedBy { it.name?.lowercase() }
                .toImmutableList()
            else -> emptyList<RoomListRoomSummary>().toImmutableList()
        }
    }

    // Online filter: show rooms with recent activity (hasNewContent) as proxy for online
    val contacts = remember(allContacts, selectedFilter) {
        when (selectedFilter) {
            ContactsFilter.All -> allContacts
            ContactsFilter.Online -> allContacts
                .filter { it.hasNewContent }
                .toImmutableList()
        }
    }

    // Build letter-to-index map for scrubber
    val letterIndexMap = remember(contacts) {
        val map = mutableMapOf<Char, Int>()
        contacts.forEachIndexed { index, contact ->
            val firstChar = contact.name?.firstOrNull()?.uppercaseChar() ?: '#'
            if (firstChar !in map) {
                map[firstChar] = index
            }
        }
        map
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Underline filters
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            ContactsFilter.entries.forEach { filter ->
                StalkUnderlineFilter(
                    text = filter.displayName,
                    isSelected = selectedFilter == filter,
                    onClick = { selectedFilter = filter },
                )
            }
        }

        if (contacts.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = if (selectedFilter == ContactsFilter.Online) "No online contacts" else "No contacts yet",
                    style = ElementTheme.typography.fontBodyLgRegular,
                    color = ElementTheme.colors.textSecondary,
                )
            }
        } else {
            Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(end = if (contacts.size > 20) 20.dp else 0.dp),
                ) {
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

                // Alphabetical scrubber (only when > 20 contacts)
                if (contacts.size > 20) {
                    AlphabetScrubber(
                        letterIndexMap = letterIndexMap,
                        onLetterSelected = { index ->
                            coroutineScope.launch {
                                listState.animateScrollToItem(index)
                            }
                        },
                        modifier = Modifier.align(Alignment.CenterEnd),
                    )
                }
            }
        }
    }
}

@Composable
private fun AlphabetScrubber(
    letterIndexMap: Map<Char, Int>,
    onLetterSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    var scrubberHeight by remember { mutableStateOf(0) }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(vertical = 4.dp, horizontal = 2.dp)
            .onSizeChanged { scrubberHeight = it.height }
            .pointerInput(letterIndexMap) {
                detectVerticalDragGestures { change, _ ->
                    val letterHeight = scrubberHeight.toFloat() / ALPHABET.size
                    val letterIndex = (change.position.y / letterHeight).toInt()
                        .coerceIn(0, ALPHABET.size - 1)
                    val letter = ALPHABET[letterIndex]
                    letterIndexMap[letter]?.let { contactIndex ->
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onLetterSelected(contactIndex)
                    }
                }
            },
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ALPHABET.forEach { letter ->
            val hasContacts = letter in letterIndexMap
            Text(
                text = letter.toString(),
                style = ElementTheme.typography.fontBodySmRegular.copy(
                    fontSize = 10.sp,
                ),
                color = if (hasContacts) {
                    ElementTheme.colors.textPrimary
                } else {
                    ElementTheme.colors.textDisabled
                },
                modifier = Modifier.clickable(enabled = hasContacts) {
                    letterIndexMap[letter]?.let { index ->
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onLetterSelected(index)
                    }
                },
            )
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
            avatarType = AvatarType.User,
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = contact.name.orEmpty(),
            style = ElementTheme.typography.fontBodyLgMedium,
            color = ElementTheme.colors.textPrimary,
        )
    }
}
