/*
 * Copyright (c) 2025 sTalk / Implica.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.call.impl.recording

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.network.RetrofitFactory
import io.element.android.libraries.sessionstorage.api.SessionStore
import timber.log.Timber

private const val STALK_BASE_URL = "https://stalk.implica.ru/"

interface RecordingRepository {
    suspend fun startRecording(
        sessionId: String,
        roomId: String,
        livekitRoomName: String,
    ): Result<StartRecordingResponse>

    suspend fun stopRecording(
        sessionId: String,
        recordingId: String,
    ): Result<StopRecordingResponse>

    suspend fun getRecordings(
        sessionId: String,
        roomId: String,
    ): Result<RecordingsListResponse>
}

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultRecordingRepository @Inject constructor(
    private val retrofitFactory: RetrofitFactory,
    private val sessionStore: SessionStore,
) : RecordingRepository {
    private val api: RecordingApi by lazy {
        retrofitFactory.create(STALK_BASE_URL)
            .create(RecordingApi::class.java)
    }

    private suspend fun getAuthHeader(sessionId: String): String {
        val session = sessionStore.getSession(sessionId)
            ?: error("No session found for $sessionId")
        return "Bearer ${session.accessToken}"
    }

    override suspend fun startRecording(
        sessionId: String,
        roomId: String,
        livekitRoomName: String,
    ): Result<StartRecordingResponse> = runCatching {
        Timber.d("Starting recording for room $roomId")
        api.startRecording(
            authorization = getAuthHeader(sessionId),
            body = StartRecordingRequest(
                roomId = roomId,
                livekitRoomName = livekitRoomName,
            ),
        )
    }

    override suspend fun stopRecording(
        sessionId: String,
        recordingId: String,
    ): Result<StopRecordingResponse> = runCatching {
        Timber.d("Stopping recording $recordingId")
        api.stopRecording(
            authorization = getAuthHeader(sessionId),
            recordingId = recordingId,
        )
    }

    override suspend fun getRecordings(
        sessionId: String,
        roomId: String,
    ): Result<RecordingsListResponse> = runCatching {
        Timber.d("Getting recordings for room $roomId")
        api.getRecordings(
            authorization = getAuthHeader(sessionId),
            roomId = roomId,
        )
    }
}
