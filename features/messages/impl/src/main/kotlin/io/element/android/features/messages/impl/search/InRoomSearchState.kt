/*
 * Copyright (c) 2025 sTalk / Implica.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.search

import io.element.android.libraries.matrix.api.core.UniqueId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

data class InRoomSearchState(
    val isActive: Boolean = false,
    val query: String = "",
    val matchingItemIds: ImmutableList<UniqueId> = persistentListOf(),
    val currentMatchIndex: Int = -1,
    val eventSink: (InRoomSearchEvent) -> Unit = {},
) {
    val totalMatches: Int get() = matchingItemIds.size
    val currentMatchDisplay: Int get() = if (currentMatchIndex >= 0) currentMatchIndex + 1 else 0
    val currentFocusedItemId: UniqueId? get() =
        if (currentMatchIndex in matchingItemIds.indices) matchingItemIds[currentMatchIndex] else null
}

sealed interface InRoomSearchEvent {
    data object ToggleSearch : InRoomSearchEvent
    data class QueryChanged(val query: String) : InRoomSearchEvent
    data object NextMatch : InRoomSearchEvent
    data object PreviousMatch : InRoomSearchEvent
}
