/*
 * Copyright (c) 2025 sTalk / Implica.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.components.avatar.Avatar
import io.element.android.libraries.designsystem.components.avatar.AvatarData
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.designsystem.theme.components.HorizontalDivider
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.matrix.api.user.MatrixUser

/**
 * Settings view showing user profile and grouped settings sections.
 * TZ #16: Profile editing buttons, settings sections with headers.
 */
@Composable
fun SettingsView(
    matrixUser: MatrixUser,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        // Profile header
        ProfileHeader(
            matrixUser = matrixUser,
            onEditProfile = onOpenSettings,
        )

        HorizontalDivider()

        // Account section
        SettingsSection(title = "Account") {
            SettingsItem(
                label = "Profile",
                subtitle = matrixUser.displayName ?: matrixUser.userId.value,
                onClick = onOpenSettings,
            )
            SettingsItem(
                label = "User ID",
                subtitle = matrixUser.userId.value,
            )
        }

        // Privacy section
        SettingsSection(title = "Privacy") {
            SettingsItem(
                label = "Encryption",
                subtitle = "End-to-end encryption enabled",
                onClick = onOpenSettings,
            )
            SettingsItem(
                label = "Backup",
                subtitle = "Key backup",
                onClick = onOpenSettings,
            )
        }

        // Notifications section
        SettingsSection(title = "Notifications") {
            SettingsItem(
                label = "Notification settings",
                subtitle = "Sound, vibration, preview",
                onClick = onOpenSettings,
            )
        }

        // Appearance section
        SettingsSection(title = "Appearance") {
            SettingsItem(
                label = "Theme",
                subtitle = "System default",
                onClick = onOpenSettings,
            )
        }

        // About section
        SettingsSection(title = "About") {
            SettingsItem(
                label = "Settings",
                subtitle = "Advanced settings and more",
                onClick = onOpenSettings,
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun ProfileHeader(
    matrixUser: MatrixUser,
    onEditProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onEditProfile)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Avatar(
            avatarData = AvatarData(
                id = matrixUser.userId.value,
                name = matrixUser.displayName,
                url = matrixUser.avatarUrl,
                size = AvatarSize.RoomHeader,
            ),
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = matrixUser.displayName ?: "Unknown",
            style = ElementTheme.typography.fontHeadingMdBold,
            color = ElementTheme.colors.textPrimary,
        )
        Text(
            text = matrixUser.userId.value,
            style = ElementTheme.typography.fontBodySmRegular,
            color = ElementTheme.colors.textSecondary,
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = ElementTheme.typography.fontBodySmMedium,
            color = ElementTheme.colors.textActionAccent,
        )
        content()
    }
}

@Composable
private fun SettingsItem(
    label: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = ElementTheme.typography.fontBodyLgRegular,
                color = ElementTheme.colors.textPrimary,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = ElementTheme.typography.fontBodySmRegular,
                    color = ElementTheme.colors.textSecondary,
                )
            }
        }
        if (onClick != null) {
            Icon(
                imageVector = CompoundIcons.ChevronRight(),
                contentDescription = null,
                tint = ElementTheme.colors.iconSecondary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
