/*
 * Copyright (c) 2025 sTalk / Implica.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.call.impl.livekit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.livekit.android.room.participant.RemoteParticipant
import io.livekit.android.room.track.Track
import io.livekit.android.room.track.VideoTrack
import livekit.org.webrtc.EglBase

@Composable
fun VideoGrid(
    localVideoTrack: VideoTrack?,
    remoteParticipants: List<RemoteParticipant>,
    eglBase: EglBase,
    modifier: Modifier = Modifier,
) {
    val totalParticipants = remoteParticipants.size + 1 // +1 for local

    Box(modifier = modifier.background(Color.Black)) {
        when {
            totalParticipants <= 1 -> {
                // Solo: show local video fullscreen
                ParticipantVideoView(
                    videoTrack = localVideoTrack,
                    eglBase = eglBase,
                    mirror = true,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            totalParticipants == 2 -> {
                // 1:1 call: remote fullscreen, local in corner
                val remote = remoteParticipants.firstOrNull()
                val remoteVideo = remote?.getTrackPublication(Track.Source.CAMERA)
                    ?.track as? VideoTrack

                ParticipantVideoView(
                    videoTrack = remoteVideo,
                    eglBase = eglBase,
                    modifier = Modifier.fillMaxSize(),
                )

                // Local video in top-right corner
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .fillMaxWidth(0.3f)
                        .fillMaxHeight(0.25f)
                        .clip(RoundedCornerShape(12.dp)),
                ) {
                    ParticipantVideoView(
                        videoTrack = localVideoTrack,
                        eglBase = eglBase,
                        mirror = true,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            totalParticipants <= 4 -> {
                // 2x2 grid
                Column(modifier = Modifier.fillMaxSize()) {
                    val allTracks = buildVideoTrackList(localVideoTrack, remoteParticipants)
                    val rows = allTracks.chunked(2)
                    rows.forEach { row ->
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            row.forEach { (track, identity, isLocal) ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .padding(1.dp),
                                ) {
                                    ParticipantVideoView(
                                        videoTrack = track,
                                        eglBase = eglBase,
                                        mirror = isLocal,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                    // Name label
                                    Text(
                                        text = identity,
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .padding(4.dp)
                                            .background(
                                                Color.Black.copy(alpha = 0.5f),
                                                RoundedCornerShape(4.dp),
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            else -> {
                // 5+ participants: scrollable grid
                val allTracks = buildVideoTrackList(localVideoTrack, remoteParticipants)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(allTracks) { (track, identity, isLocal) ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(0.5f)
                                .padding(1.dp),
                        ) {
                            ParticipantVideoView(
                                videoTrack = track,
                                eglBase = eglBase,
                                mirror = isLocal,
                                modifier = Modifier.fillMaxSize(),
                            )
                            Text(
                                text = identity,
                                color = Color.White,
                                fontSize = 12.sp,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(4.dp)
                                    .background(
                                        Color.Black.copy(alpha = 0.5f),
                                        RoundedCornerShape(4.dp),
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class ParticipantTrackInfo(
    val videoTrack: VideoTrack?,
    val identity: String,
    val isLocal: Boolean,
)

private fun buildVideoTrackList(
    localVideoTrack: VideoTrack?,
    remoteParticipants: List<RemoteParticipant>,
): List<ParticipantTrackInfo> {
    val list = mutableListOf<ParticipantTrackInfo>()
    // Add local first
    list.add(ParticipantTrackInfo(localVideoTrack, "You", isLocal = true))
    // Add remotes
    remoteParticipants.forEach { participant ->
        val videoTrack = participant.getTrackPublication(Track.Source.CAMERA)
            ?.track as? VideoTrack
        list.add(
            ParticipantTrackInfo(
                videoTrack = videoTrack,
                identity = participant.identity?.value ?: "Unknown",
                isLocal = false,
            )
        )
    }
    return list
}
