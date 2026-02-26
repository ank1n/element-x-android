/*
 * Copyright (c) 2025 sTalk / Implica.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.call.impl.livekit

import android.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber

object LiveKitJwtDecoder {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Decodes a LiveKit JWT token and extracts the room name from the `video.room` claim.
     *
     * @param token The JWT token string
     * @return The room name, or null if decoding fails
     */
    fun decodeRoomName(token: String): String? {
        return try {
            val parts = token.split(".")
            if (parts.size != 3) {
                Timber.w("Invalid JWT format: expected 3 parts, got ${parts.size}")
                return null
            }
            val payloadJson = String(Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP))
            val payload = json.parseToJsonElement(payloadJson).jsonObject
            val videoObj = payload["video"]?.jsonObject
            val roomName = videoObj?.get("room")?.jsonPrimitive?.content
            if (roomName == null) {
                Timber.w("JWT payload does not contain video.room claim")
            }
            roomName
        } catch (e: Exception) {
            Timber.e(e, "Failed to decode JWT token")
            null
        }
    }

    /**
     * Decodes a LiveKit JWT token and extracts the participant identity from the `sub` claim.
     *
     * @param token The JWT token string
     * @return The participant identity, or null if decoding fails
     */
    fun decodeIdentity(token: String): String? {
        return try {
            val parts = token.split(".")
            if (parts.size != 3) return null
            val payloadJson = String(Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP))
            val payload = json.parseToJsonElement(payloadJson).jsonObject
            payload["sub"]?.jsonPrimitive?.content
        } catch (e: Exception) {
            Timber.e(e, "Failed to decode JWT identity")
            null
        }
    }
}
