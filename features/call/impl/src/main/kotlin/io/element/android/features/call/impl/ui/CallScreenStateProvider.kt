/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.call.impl.ui

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.element.android.features.call.impl.recording.RecordingState
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.designsystem.components.avatar.AvatarData

open class CallScreenStateProvider : PreviewParameterProvider<CallScreenState> {
    override val values: Sequence<CallScreenState>
        get() = sequenceOf(
            aCallScreenState(),
            aCallScreenState(urlState = AsyncData.Loading()),
            aCallScreenState(urlState = AsyncData.Failure(Exception("An error occurred"))),
            aCallScreenState(webViewError = "Error details from WebView"),
        )
}

internal fun aCallScreenState(
    urlState: AsyncData<String> = AsyncData.Success("https://call.element.io/some-actual-call?with=parameters"),
    webViewError: String? = null,
    userAgent: String = "",
    isCallActive: Boolean = true,
    isInWidgetMode: Boolean = false,
    recordingState: RecordingState = RecordingState.Idle,
    isMuted: Boolean = false,
    isVideoEnabled: Boolean = true,
    isSpeakerOn: Boolean = true,
    isHandRaised: Boolean = false,
    participantName: String = "",
    avatarData: AvatarData? = null,
    isDm: Boolean = false,
    callDurationSeconds: Long = 0L,
    eventSink: (CallScreenEvents) -> Unit = {},
): CallScreenState {
    return CallScreenState(
        urlState = urlState,
        webViewError = webViewError,
        userAgent = userAgent,
        isCallActive = isCallActive,
        isInWidgetMode = isInWidgetMode,
        recordingState = recordingState,
        isMuted = isMuted,
        isVideoEnabled = isVideoEnabled,
        isSpeakerOn = isSpeakerOn,
        isHandRaised = isHandRaised,
        participantName = participantName,
        avatarData = avatarData,
        isDm = isDm,
        callDurationSeconds = callDurationSeconds,
        eventSink = eventSink,
    )
}
