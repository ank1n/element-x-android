/*
 * Copyright (c) 2025 sTalk / Implica.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.apps

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

enum class AppsCategory(val apiValue: String?, val displayName: String) {
    All(apiValue = null, displayName = "All"),
    Tools(apiValue = "tools", displayName = "Tools"),
    Fun(apiValue = "fun", displayName = "Fun"),
    Productivity(apiValue = "productivity", displayName = "Productivity"),
    Other(apiValue = "other", displayName = "Other"),
}

data class AppsState(
    val widgets: ImmutableList<WidgetItem> = persistentListOf(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedCategory: AppsCategory = AppsCategory.All,
    val selectedWidget: WidgetItem? = null,
    val eventSink: (AppsEvent) -> Unit = {},
)

sealed interface AppsEvent {
    data class SelectCategory(val category: AppsCategory) : AppsEvent
    data class OpenWidget(val widget: WidgetItem) : AppsEvent
    data object CloseWidget : AppsEvent
    data object Refresh : AppsEvent
}
