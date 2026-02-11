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
    @SerialName("room_id")
    val roomId: String,
    @SerialName("room_name")
    val roomName: String,
    @SerialName("widget_id")
    val widgetId: String,
    @SerialName("name")
    val name: String,
    @SerialName("url")
    val url: String,
    @SerialName("type")
    val type: String,
    @SerialName("category")
    val category: String? = null,
)

@Serializable
data class WidgetsResponse(
    @SerialName("widgets")
    val widgets: List<WidgetItem>,
)
