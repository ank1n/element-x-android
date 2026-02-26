/*
 * Copyright (c) 2025 sTalk / Implica.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.call.impl.recording

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

internal interface RecordingApi {
    @POST("recording-api/start")
    suspend fun startRecording(
        @Header("Authorization") authorization: String,
        @Body body: StartRecordingRequest,
    ): StartRecordingResponse

    @POST("recording-api/stop/{recordingId}")
    suspend fun stopRecording(
        @Header("Authorization") authorization: String,
        @Path("recordingId") recordingId: String,
    ): StopRecordingResponse

    @GET("recording-api/recordings")
    suspend fun getRecordings(
        @Header("Authorization") authorization: String,
        @Query("room_id") roomId: String,
    ): RecordingsListResponse

    @GET("recording-api/active")
    suspend fun getActiveRecording(
        @Header("Authorization") authorization: String,
        @Query("matrixRoomId") matrixRoomId: String,
    ): ActiveRecordingResponse
}
