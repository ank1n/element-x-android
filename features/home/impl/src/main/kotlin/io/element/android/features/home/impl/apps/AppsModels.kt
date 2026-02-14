/*
 * Copyright (c) 2025 sTalk / Implica.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.apps

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WidgetItem(
    @SerialName("id")
    val id: String,
    @SerialName("name")
    val name: String,
    @SerialName("description")
    val description: String? = null,
    @SerialName("icon")
    val icon: String? = null,
    @SerialName("url")
    val url: String,
    @SerialName("type")
    val type: String,
    @SerialName("enabled")
    val enabled: Boolean = true,
)

@Serializable
data class WidgetsResponse(
    @SerialName("widgets")
    val widgets: List<WidgetItem>,
    @SerialName("total")
    val total: Int = 0,
)
