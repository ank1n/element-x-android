/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.call.impl.ui

import io.element.android.features.call.impl.livekit.LiveKitCallManager
import io.element.android.features.call.impl.recording.RecordingState
import io.livekit.android.room.participant.RemoteParticipant
import io.livekit.android.room.track.VideoTrack
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.designsystem.components.avatar.AvatarData

data class CallScreenState(
    val urlState: AsyncData<String>,
    val webViewError: String?,
    val userAgent: String,
    val isCallActive: Boolean,
    val isInWidgetMode: Boolean,
    val recordingState: RecordingState = RecordingState.Idle,
    // sTalk: Native call controls state
    val isMuted: Boolean = false,
    val isVideoEnabled: Boolean = true,
    val isSpeakerOn: Boolean = true,
    val isHandRaised: Boolean = false,
    val participantName: String = "",
    val avatarData: AvatarData? = null,
    val isDm: Boolean = false,
    val callDurationSeconds: Long = 0L,
    // sTalk: Native LiveKit state
    val isLiveKitConnected: Boolean = false,
    val localVideoTrack: VideoTrack? = null,
    val remoteParticipants: List<RemoteParticipant> = emptyList(),
    val liveKitCallManager: LiveKitCallManager? = null,
    val eventSink: (CallScreenEvents) -> Unit,
)
