/*
 * Copyright (c) 2025 sTalk / Implica.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.call.impl.livekit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import io.livekit.android.room.track.VideoTrack
import livekit.org.webrtc.EglBase
import livekit.org.webrtc.RendererCommon
import livekit.org.webrtc.SurfaceViewRenderer

@Composable
fun ParticipantVideoView(
    videoTrack: VideoTrack?,
    eglBase: EglBase,
    mirror: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var currentTrack by remember { mutableStateOf<VideoTrack?>(null) }

    AndroidView(
        factory = { ctx ->
            SurfaceViewRenderer(ctx).apply {
                init(eglBase.eglBaseContext, null)
                setMirror(mirror)
                setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                setEnableHardwareScaler(true)
            }
        },
        update = { renderer ->
            if (currentTrack != videoTrack) {
                currentTrack?.removeRenderer(renderer)
                videoTrack?.addRenderer(renderer)
                currentTrack = videoTrack
            }
        },
        onRelease = { renderer ->
            currentTrack?.removeRenderer(renderer)
            renderer.release()
        },
        modifier = modifier,
    )
}
