/*
 * Copyright (c) 2025 sTalk / Implica.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.call.impl.livekit

import android.content.Context
import io.livekit.android.ConnectOptions
import io.livekit.android.LiveKit
import io.livekit.android.LiveKitOverrides
import io.livekit.android.RoomOptions
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.Room
import io.livekit.android.room.participant.RemoteParticipant
import io.livekit.android.room.track.LocalVideoTrack
import io.livekit.android.room.track.Track
import io.livekit.android.room.track.VideoTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import livekit.org.webrtc.EglBase
import timber.log.Timber

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    FAILED,
}

class LiveKitCallManager(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
) {
    private var room: Room? = null

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _localVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val localVideoTrack: StateFlow<VideoTrack?> = _localVideoTrack.asStateFlow()

    private val _remoteParticipants = MutableStateFlow<List<RemoteParticipant>>(emptyList())
    val remoteParticipants: StateFlow<List<RemoteParticipant>> = _remoteParticipants.asStateFlow()

    private val _roomName = MutableStateFlow<String?>(null)
    val roomName: StateFlow<String?> = _roomName.asStateFlow()

    private val nativeAudioManager = NativeAudioManager(context, coroutineScope)

    val eglBase: EglBase by lazy { EglBase.create() }

    suspend fun connect(credentials: LiveKitCredentials) {
        if (_connectionState.value == ConnectionState.CONNECTED || _connectionState.value == ConnectionState.CONNECTING) {
            Timber.d("LiveKit: already connected or connecting, skipping")
            return
        }

        _connectionState.value = ConnectionState.CONNECTING
        _roomName.value = credentials.roomName
        Timber.d("LiveKit: connecting to ${credentials.url}, room=${credentials.roomName}")

        try {
            val newRoom = LiveKit.create(
                appContext = context.applicationContext,
                options = RoomOptions(
                    adaptiveStream = true,
                    dynacast = true,
                ),
                overrides = LiveKitOverrides(
                    eglBase = eglBase,
                ),
            )

            room = newRoom

            // Collect room events
            coroutineScope.launch {
                newRoom.events.collect { event ->
                    handleRoomEvent(event)
                }
            }

            newRoom.connect(
                url = credentials.url,
                token = credentials.token,
                options = ConnectOptions(
                    autoSubscribe = true,
                ),
            )

            // Enable camera and microphone after connecting
            val localParticipant = newRoom.localParticipant
            localParticipant.setCameraEnabled(true)
            localParticipant.setMicrophoneEnabled(true)

            // Get local video track
            updateLocalVideoTrack()

            // Update remote participants
            updateRemoteParticipants()

            nativeAudioManager.onCallStarted()

            _connectionState.value = ConnectionState.CONNECTED
            Timber.d("LiveKit: connected successfully")
        } catch (e: Exception) {
            Timber.e(e, "LiveKit: connection failed")
            _connectionState.value = ConnectionState.FAILED
        }
    }

    fun disconnect() {
        Timber.d("LiveKit: disconnecting")
        nativeAudioManager.onCallStopped()
        room?.disconnect()
        room?.release()
        room = null
        _connectionState.value = ConnectionState.DISCONNECTED
        _localVideoTrack.value = null
        _remoteParticipants.value = emptyList()
        _roomName.value = null
    }

    fun setMicrophoneEnabled(enabled: Boolean) {
        coroutineScope.launch {
            try {
                room?.localParticipant?.setMicrophoneEnabled(enabled)
                Timber.d("LiveKit: microphone enabled=$enabled")
            } catch (e: Exception) {
                Timber.e(e, "LiveKit: failed to set microphone")
            }
        }
    }

    fun setCameraEnabled(enabled: Boolean) {
        coroutineScope.launch {
            try {
                room?.localParticipant?.setCameraEnabled(enabled)
                updateLocalVideoTrack()
                Timber.d("LiveKit: camera enabled=$enabled")
            } catch (e: Exception) {
                Timber.e(e, "LiveKit: failed to set camera")
            }
        }
    }

    fun setSpeakerEnabled(enabled: Boolean) {
        nativeAudioManager.setSpeakerEnabled(enabled)
    }

    private fun handleRoomEvent(event: RoomEvent) {
        when (event) {
            is RoomEvent.TrackSubscribed -> {
                Timber.d("LiveKit: track subscribed: ${event.track.kind}, participant=${event.participant.identity}")
                updateRemoteParticipants()
            }
            is RoomEvent.TrackUnsubscribed -> {
                Timber.d("LiveKit: track unsubscribed: ${event.track.kind}")
                updateRemoteParticipants()
            }
            is RoomEvent.ParticipantConnected -> {
                Timber.d("LiveKit: participant connected: ${event.participant.identity}")
                updateRemoteParticipants()
            }
            is RoomEvent.ParticipantDisconnected -> {
                Timber.d("LiveKit: participant disconnected: ${event.participant.identity}")
                updateRemoteParticipants()
            }
            is RoomEvent.TrackMuted -> {
                updateRemoteParticipants()
            }
            is RoomEvent.TrackUnmuted -> {
                updateRemoteParticipants()
            }
            is RoomEvent.Disconnected -> {
                Timber.d("LiveKit: room disconnected")
                _connectionState.value = ConnectionState.DISCONNECTED
            }
            is RoomEvent.Reconnecting -> {
                Timber.d("LiveKit: reconnecting")
                _connectionState.value = ConnectionState.CONNECTING
            }
            is RoomEvent.Reconnected -> {
                Timber.d("LiveKit: reconnected")
                _connectionState.value = ConnectionState.CONNECTED
            }
            else -> {}
        }
    }

    private fun updateLocalVideoTrack() {
        val localParticipant = room?.localParticipant ?: return
        val videoTrack = localParticipant.getTrackPublication(Track.Source.CAMERA)
            ?.track as? LocalVideoTrack
        _localVideoTrack.value = videoTrack
    }

    private fun updateRemoteParticipants() {
        val currentRoom = room ?: return
        _remoteParticipants.value = currentRoom.remoteParticipants.values.toList()
    }
}
