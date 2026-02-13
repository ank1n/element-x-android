/*
 * Copyright (c) 2025 sTalk / Implica.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.call.impl.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.call.impl.recording.RecordingState
import io.element.android.libraries.designsystem.components.avatar.Avatar
import io.element.android.libraries.designsystem.components.avatar.AvatarData
import io.element.android.libraries.designsystem.components.avatar.AvatarType

// sTalk: Native Compose overlay for Telegram-style call controls
@Composable
internal fun CallControlsOverlay(
    state: CallScreenState,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        // Top gradient + info
        TopCallInfo(
            participantName = state.participantName,
            callDurationSeconds = state.callDurationSeconds,
            avatarData = state.avatarData,
            isDm = state.isDm,
            isVideoEnabled = state.isVideoEnabled,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        // Bottom controls
        BottomCallControls(
            isMuted = state.isMuted,
            isVideoEnabled = state.isVideoEnabled,
            isSpeakerOn = state.isSpeakerOn,
            isHandRaised = state.isHandRaised,
            recordingState = state.recordingState,
            onToggleMute = { state.eventSink(CallScreenEvents.ToggleMute) },
            onToggleVideo = { state.eventSink(CallScreenEvents.ToggleVideo) },
            onToggleSpeaker = { state.eventSink(CallScreenEvents.ToggleSpeaker) },
            onToggleHandRaise = { state.eventSink(CallScreenEvents.ToggleHandRaise) },
            onToggleRecording = { state.eventSink(CallScreenEvents.ToggleRecording) },
            onHangup = { state.eventSink(CallScreenEvents.Hangup) },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun TopCallInfo(
    participantName: String,
    callDurationSeconds: Long,
    avatarData: AvatarData?,
    isDm: Boolean,
    isVideoEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.6f),
                        Color.Transparent,
                    ),
                )
            )
            .padding(top = 48.dp, bottom = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Show avatar when video is off
            if (!isVideoEnabled && avatarData != null) {
                Avatar(
                    avatarData = avatarData,
                    avatarType = if (isDm) AvatarType.User else AvatarType.Room(),
                    modifier = Modifier.size(120.dp),
                    forcedAvatarSize = 120.dp,
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            if (participantName.isNotEmpty()) {
                Text(
                    text = participantName,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            Text(
                text = formatDuration(callDurationSeconds),
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun BottomCallControls(
    isMuted: Boolean,
    isVideoEnabled: Boolean,
    isSpeakerOn: Boolean,
    isHandRaised: Boolean,
    recordingState: RecordingState,
    onToggleMute: () -> Unit,
    onToggleVideo: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onToggleHandRaise: () -> Unit,
    onToggleRecording: () -> Unit,
    onHangup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.6f),
                    ),
                )
            )
            .padding(bottom = 40.dp, top = 24.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Recording indicator
            RecordingIndicator(
                recordingState = recordingState,
                onToggleRecording = onToggleRecording,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Control buttons row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Mute button
                CallControlButton(
                    icon = if (isMuted) CompoundIcons.MicOff() else CompoundIcons.MicOn(),
                    contentDescription = if (isMuted) "Unmute" else "Mute",
                    isActive = isMuted,
                    onClick = onToggleMute,
                )

                // Speaker button
                CallControlButton(
                    icon = CompoundIcons.VoiceCall(),
                    contentDescription = if (isSpeakerOn) "Speaker off" else "Speaker on",
                    isActive = isSpeakerOn,
                    onClick = onToggleSpeaker,
                )

                // Video button
                CallControlButton(
                    icon = if (isVideoEnabled) CompoundIcons.VideoCall() else CompoundIcons.VideoCallOff(),
                    contentDescription = if (isVideoEnabled) "Video off" else "Video on",
                    isActive = !isVideoEnabled,
                    onClick = onToggleVideo,
                )

                // Hand raise button
                CallControlButton(
                    icon = CompoundIcons.RaisedHandSolid(),
                    contentDescription = if (isHandRaised) "Lower hand" else "Raise hand",
                    isActive = isHandRaised,
                    onClick = onToggleHandRaise,
                )

                // End call button (red)
                IconButton(
                    onClick = onHangup,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color.Red,
                        contentColor = Color.White,
                    ),
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(
                        imageVector = CompoundIcons.EndCall(),
                        contentDescription = "End call",
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CallControlButton(
    icon: ImageVector,
    contentDescription: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = if (isActive) Color.White.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.4f),
            contentColor = Color.White,
        ),
        modifier = modifier.size(52.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(26.dp),
        )
    }
}

@Composable
private fun RecordingIndicator(
    recordingState: RecordingState,
    onToggleRecording: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isRecording = recordingState is RecordingState.Recording
    val isStarting = recordingState is RecordingState.Starting

    if (!isRecording && !isStarting) {
        // Show small record button when idle
        IconButton(
            onClick = onToggleRecording,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = Color.Black.copy(alpha = 0.4f),
            ),
            modifier = modifier.size(40.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .background(color = Color.Red, shape = CircleShape)
            )
        }
        return
    }

    val pulseAlpha = if (isRecording) {
        val infiniteTransition = rememberInfiniteTransition(label = "rec pulse")
        val alpha by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 0.3f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 800),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "rec alpha",
        )
        alpha
    } else {
        1f
    }

    Row(
        modifier = modifier
            .alpha(pulseAlpha)
            .background(
                color = Color.Red.copy(alpha = 0.8f),
                shape = CircleShape,
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color = Color.White, shape = CircleShape)
        )
        Text(
            text = if (isStarting) "Starting..." else "REC",
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
        // Tap to stop recording
        if (isRecording) {
            IconButton(
                onClick = onToggleRecording,
                modifier = Modifier.size(24.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = Color.White,
                ),
            ) {
                Text("×", color = Color.White, fontSize = 16.sp)
            }
        }
    }
}

private fun formatDuration(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
