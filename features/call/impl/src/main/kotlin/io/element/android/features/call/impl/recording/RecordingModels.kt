/*
 * Copyright (c) 2025 sTalk / Implica.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.call.impl.recording

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StartRecordingRequest(
    @SerialName("matrixRoomId")
    val roomId: String,
    @SerialName("roomName")
    val livekitRoomName: String,
)

@Serializable
data class StartRecordingResponse(
    @SerialName("recording_id")
    val recordingId: String,
    @SerialName("status")
    val status: String,
)

@Serializable
data class StopRecordingResponse(
    @SerialName("recording_id")
    val recordingId: String,
    @SerialName("status")
    val status: String,
    @SerialName("duration_seconds")
    val durationSeconds: Long,
)

@Serializable
data class RecordingItem(
    @SerialName("id")
    val id: String,
    @SerialName("room_id")
    val roomId: String,
    @SerialName("started_at")
    val startedAt: String,
    @SerialName("duration_seconds")
    val durationSeconds: Long,
    @SerialName("file_url")
    val fileUrl: String,
)

@Serializable
data class RecordingsListResponse(
    @SerialName("recordings")
    val recordings: List<RecordingItem>,
)

@Serializable
data class ActiveRecordingResponse(
    @SerialName("recording_id")
    val recordingId: String? = null,
    @SerialName("active")
    val active: Boolean,
)
