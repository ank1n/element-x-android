/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.call.impl.ui

import io.element.android.features.call.impl.utils.WidgetMessageInterceptor

sealed interface CallScreenEvents {
    data object Hangup : CallScreenEvents
    data class SetupMessageChannels(val widgetMessageInterceptor: WidgetMessageInterceptor) : CallScreenEvents
    data class OnWebViewError(val description: String?) : CallScreenEvents
    data object ToggleRecording : CallScreenEvents
    // sTalk: Native call control events
    data object ToggleMute : CallScreenEvents
    data object ToggleVideo : CallScreenEvents
    data object ToggleSpeaker : CallScreenEvents
    data object ToggleHandRaise : CallScreenEvents
    data class OnHandRaiseStateChanged(val isHandRaised: Boolean) : CallScreenEvents
}
