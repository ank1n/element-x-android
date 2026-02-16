/*
 * Copyright (c) 2025 sTalk / Implica.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.call.impl.livekit

import io.element.android.features.call.impl.utils.PipController

/**
 * PiP controller for native LiveKit video rendering.
 * Video renderers (SurfaceViewRenderer) continue working in PiP mode automatically.
 */
class LiveKitPipController(
    private val manager: LiveKitCallManager,
) : PipController {
    override suspend fun canEnterPip(): Boolean {
        return manager.connectionState.value == ConnectionState.CONNECTED
    }

    override fun enterPip() {
        // No-op: SurfaceViewRenderer continues rendering in PiP mode
    }

    override fun exitPip() {
        // No-op: SurfaceViewRenderer continues rendering after exiting PiP
    }
}
