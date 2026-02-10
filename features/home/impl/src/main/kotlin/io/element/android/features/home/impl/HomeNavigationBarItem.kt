/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import io.element.android.compound.tokens.generated.CompoundIcons

enum class HomeNavigationBarItem(
    @StringRes
    val labelRes: Int,
) {
    Contacts(
        labelRes = R.string.screen_home_tab_contacts
    ),
    Calls(
        labelRes = R.string.screen_home_tab_calls
    ),
    Chats(
        labelRes = R.string.screen_home_tab_chats
    ),
    Apps(
        labelRes = R.string.screen_home_tab_apps
    ),
    Settings(
        labelRes = R.string.screen_home_tab_settings
    );

    @Composable
    fun icon(
        isSelected: Boolean,
    ) = when (this) {
        Contacts -> if (isSelected) CompoundIcons.UserProfileSolid() else CompoundIcons.UserProfile()
        Calls -> if (isSelected) CompoundIcons.VoiceCallSolid() else CompoundIcons.VoiceCall()
        Chats -> if (isSelected) CompoundIcons.ChatSolid() else CompoundIcons.Chat()
        Apps -> if (isSelected) CompoundIcons.ExtensionsSolid() else CompoundIcons.Extensions()
        Settings -> if (isSelected) CompoundIcons.SettingsSolid() else CompoundIcons.Settings()
    }

    companion object {
        fun from(index: Int): HomeNavigationBarItem {
            return entries.getOrElse(index) { Chats }
        }
    }
}
