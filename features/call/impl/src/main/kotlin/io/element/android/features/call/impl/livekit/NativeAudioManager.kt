/*
 * Copyright (c) 2025 sTalk / Implica.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.call.impl.livekit

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.getSystemService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/**
 * Manages audio routing for native LiveKit calls.
 * Handles speaker/earpiece/Bluetooth switching and proximity sensor.
 */
class NativeAudioManager(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val proximitySensorWakeLock by lazy {
        context.getSystemService<PowerManager>()
            ?.takeIf { it.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK) }
            ?.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "${context.packageName}:LiveKitCallWakeLock")
    }

    private val proximitySensorMutex = Mutex()

    private val wantedDeviceTypes = listOf(
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_USB_ACCESSORY,
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE,
    )

    private var isSpeakerOn = true
    private var hasRegisteredCallbacks = false

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            // Auto-switch to higher priority device if connected
            val validNew = addedDevices.orEmpty().filter { it.type in wantedDeviceTypes && it.isSink }
            if (validNew.isNotEmpty()) {
                val best = validNew.minByOrNull { wantedDeviceTypes.indexOf(it.type).let { i -> if (i == -1) Int.MAX_VALUE else i } }
                if (best != null) {
                    selectDevice(best)
                }
            }
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            // Fallback to speaker/earpiece
            applySpeakerState()
        }
    }

    fun onCallStarted() {
        Timber.d("NativeAudioManager: call started")
        audioManager.mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            AudioManager.MODE_IN_COMMUNICATION
        } else {
            AudioManager.MODE_NORMAL
        }
        applySpeakerState()
        audioManager.registerAudioDeviceCallback(audioDeviceCallback, null)
        hasRegisteredCallbacks = true
    }

    fun onCallStopped() {
        Timber.d("NativeAudioManager: call stopped")
        coroutineScope.launch {
            proximitySensorMutex.withLock {
                if (proximitySensorWakeLock?.isHeld == true) {
                    proximitySensorWakeLock?.release()
                }
            }
        }
        audioManager.mode = AudioManager.MODE_NORMAL
        if (hasRegisteredCallbacks) {
            audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
            }
            hasRegisteredCallbacks = false
        }
    }

    fun setSpeakerEnabled(enabled: Boolean) {
        isSpeakerOn = enabled
        applySpeakerState()
    }

    @Suppress("DEPRECATION")
    private fun applySpeakerState() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val devices = audioManager.availableCommunicationDevices
            val targetType = if (isSpeakerOn) AudioDeviceInfo.TYPE_BUILTIN_SPEAKER else AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
            val device = devices.firstOrNull { it.type == targetType }
            if (device != null) {
                audioManager.setCommunicationDevice(device)
            }
        } else {
            audioManager.isSpeakerphoneOn = isSpeakerOn
        }
        updateProximitySensor()
    }

    private fun selectDevice(device: AudioDeviceInfo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.setCommunicationDevice(device)
        }
        updateProximitySensor()
    }

    @Suppress("WakeLock", "WakeLockTimeout")
    private fun updateProximitySensor() {
        coroutineScope.launch {
            proximitySensorMutex.withLock {
                if (!isSpeakerOn && proximitySensorWakeLock?.isHeld == false) {
                    proximitySensorWakeLock?.acquire()
                } else if (isSpeakerOn && proximitySensorWakeLock?.isHeld == true) {
                    proximitySensorWakeLock?.release()
                }
            }
        }
    }
}
