/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.call.impl.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import im.vector.app.features.analytics.plan.MobileScreen
import io.element.android.compound.theme.ElementTheme
import io.element.android.features.call.api.CallType
import io.element.android.features.call.impl.data.WidgetMessage
import io.element.android.features.call.impl.recording.RecordingRepository
import io.element.android.features.call.impl.recording.RecordingState
import io.element.android.features.call.impl.utils.ActiveCallManager
import io.element.android.features.call.impl.utils.CallWidgetProvider
import io.element.android.features.call.impl.utils.WebViewWidgetMessageInterceptor
import io.element.android.features.call.impl.utils.WidgetMessageInterceptor
import io.element.android.features.call.impl.utils.WidgetMessageSerializer
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.architecture.runCatchingUpdatingState
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.designsystem.components.avatar.AvatarData
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.di.annotations.AppCoroutineScope
import io.element.android.libraries.matrix.api.MatrixClientProvider
import io.element.android.libraries.matrix.api.sync.SyncState
import io.element.android.libraries.matrix.api.widget.MatrixWidgetDriver
import io.element.android.libraries.network.useragent.UserAgentProvider
import io.element.android.services.analytics.api.ScreenTracker
import io.element.android.services.appnavstate.api.AppForegroundStateService
import io.element.android.services.toolbox.api.systemclock.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

@AssistedInject
class CallScreenPresenter(
    @Assisted private val callType: CallType,
    @Assisted private val navigator: CallScreenNavigator,
    private val callWidgetProvider: CallWidgetProvider,
    userAgentProvider: UserAgentProvider,
    private val clock: SystemClock,
    private val dispatchers: CoroutineDispatchers,
    private val matrixClientsProvider: MatrixClientProvider,
    private val screenTracker: ScreenTracker,
    private val activeCallManager: ActiveCallManager,
    private val languageTagProvider: LanguageTagProvider,
    private val appForegroundStateService: AppForegroundStateService,
    @AppCoroutineScope
    private val appCoroutineScope: CoroutineScope,
    private val widgetMessageSerializer: WidgetMessageSerializer,
    private val recordingRepository: RecordingRepository,
) : Presenter<CallScreenState> {
    @AssistedFactory
    interface Factory {
        fun create(callType: CallType, navigator: CallScreenNavigator): CallScreenPresenter
    }

    private val isInWidgetMode = callType is CallType.RoomCall
    private val userAgent = userAgentProvider.provide()

    @Composable
    override fun present(): CallScreenState {
        val coroutineScope = rememberCoroutineScope()
        val urlState = remember { mutableStateOf<AsyncData<String>>(AsyncData.Uninitialized) }
        val callWidgetDriver = remember { mutableStateOf<MatrixWidgetDriver?>(null) }
        val messageInterceptor = remember { mutableStateOf<WidgetMessageInterceptor?>(null) }
        var isWidgetLoaded by rememberSaveable { mutableStateOf(false) }
        var ignoreWebViewError by rememberSaveable { mutableStateOf(false) }
        var webViewError by remember { mutableStateOf<String?>(null) }
        var recordingState by remember { mutableStateOf<RecordingState>(RecordingState.Idle) }
        // sTalk: Native call controls state
        var isMuted by remember { mutableStateOf(false) }
        var isVideoEnabled by remember { mutableStateOf(true) }
        var isSpeakerOn by remember { mutableStateOf(true) }
        var isHandRaised by remember { mutableStateOf(false) }
        var callDurationSeconds by remember { mutableStateOf(0L) }
        var callStartTimeMillis by remember { mutableStateOf(0L) }
        var participantName by remember { mutableStateOf("") }
        var avatarData by remember { mutableStateOf<AvatarData?>(null) }
        var isDm by remember { mutableStateOf(false) }
        val languageTag = languageTagProvider.provideLanguageTag()
        val theme = if (ElementTheme.isLightTheme) "light" else "dark"

        DisposableEffect(Unit) {
            coroutineScope.launch {
                // Sets the call as joined
                activeCallManager.joinedCall(callType)
                fetchRoomCallUrl(
                    inputs = callType,
                    urlState = urlState,
                    callWidgetDriver = callWidgetDriver,
                    languageTag = languageTag,
                    theme = theme,
                )
            }
            onDispose {
                appCoroutineScope.launch { activeCallManager.hungUpCall(callType) }
            }
        }

        // sTalk: Load room info (name + avatar) for call overlay
        LaunchedEffect(callType) {
            if (callType is CallType.RoomCall) {
                val client = matrixClientsProvider.getOrNull(callType.sessionId)
                if (client != null) {
                    client.getRoomInfoFlow(callType.roomId).collect { optional ->
                        val roomInfo = optional.orElse(null) ?: return@collect
                        isDm = roomInfo.isDirect
                        if (roomInfo.isDirect && roomInfo.heroes.isNotEmpty()) {
                            val hero = roomInfo.heroes.first()
                            participantName = hero.displayName ?: hero.userId.value
                            avatarData = AvatarData(
                                id = hero.userId.value,
                                name = hero.displayName,
                                url = hero.avatarUrl,
                                size = AvatarSize.IncomingCall,
                            )
                        } else {
                            participantName = roomInfo.name ?: ""
                            avatarData = AvatarData(
                                id = roomInfo.id.value,
                                name = roomInfo.name,
                                url = roomInfo.avatarUrl,
                                size = AvatarSize.IncomingCall,
                            )
                        }
                    }
                }
            }
        }

        when (callType) {
            is CallType.ExternalUrl -> {
                // No analytics yet for external calls
            }
            is CallType.RoomCall -> {
                screenTracker.TrackScreen(screen = MobileScreen.ScreenName.RoomCall)
            }
        }

        // sTalk: Call duration timer
        LaunchedEffect(isWidgetLoaded) {
            if (isWidgetLoaded) {
                callStartTimeMillis = clock.epochMillis()
                while (true) {
                    delay(1.seconds)
                    callDurationSeconds = (clock.epochMillis() - callStartTimeMillis) / 1000
                }
            }
        }

        // sTalk: Check for active recording when call starts
        LaunchedEffect(isWidgetLoaded) {
            if (isWidgetLoaded && callType is CallType.RoomCall) {
                launch(dispatchers.io) {
                    recordingRepository.getActiveRecording(
                        sessionId = callType.sessionId.value,
                        roomId = callType.roomId.value,
                    ).onSuccess { response ->
                        if (response.active && response.recordingId != null) {
                            recordingState = RecordingState.Recording(
                                recordingId = response.recordingId,
                                startedAtMillis = clock.epochMillis(),
                            )
                            Timber.d("Restored active recording: ${response.recordingId}")
                        }
                    }.onFailure { error ->
                        Timber.w(error, "Failed to check active recording (non-fatal)")
                    }
                }
            }
        }

        HandleMatrixClientSyncState()

        callWidgetDriver.value?.let { driver ->
            LaunchedEffect(Unit) {
                driver.incomingMessages
                    .onEach {
                        // Relay message to the WebView
                        messageInterceptor.value?.sendMessage(it)
                    }
                    .launchIn(this)

                driver.run()
            }
        }

        messageInterceptor.value?.let { interceptor ->
            LaunchedEffect(Unit) {
                interceptor.interceptedMessages
                    .onEach {
                        // We are receiving messages from the WebView, consider that the application is loaded
                        ignoreWebViewError = true
                        // Relay message to Widget Driver
                        callWidgetDriver.value?.send(it)

                        val parsedMessage = parseMessage(it)
                        if (parsedMessage?.direction == WidgetMessage.Direction.FromWidget) {
                            if (parsedMessage.action == WidgetMessage.Action.Close) {
                                close(callWidgetDriver.value, navigator)
                            } else if (parsedMessage.action == WidgetMessage.Action.ContentLoaded) {
                                isWidgetLoaded = true
                            }
                        }
                    }
                    .launchIn(this)
            }

            if (callType is CallType.RoomCall) {
                // Note: For external calls isWidgetLoaded will always be false
                LaunchedEffect(Unit) {
                    // Wait for the call to be joined, if it takes too long, we display an error
                    delay(10.seconds)

                    if (!isWidgetLoaded) {
                        Timber.w("The call took too long to load. Displaying an error before exiting.")

                        // This will display a simple 'Sorry, an error occurred' dialog and force the user to exit the call
                        webViewError = ""
                    }
                }
            }
        }

        fun handleEvent(event: CallScreenEvents) {
            when (event) {
                is CallScreenEvents.Hangup -> {
                    val widgetId = callWidgetDriver.value?.id
                    val interceptor = messageInterceptor.value
                    if (widgetId != null && interceptor != null && isWidgetLoaded) {
                        // If the call was joined, we need to hang up first. Then the UI will be dismissed automatically.
                        sendHangupMessage(widgetId, interceptor)
                        isWidgetLoaded = false

                        coroutineScope.launch {
                            // Wait for a couple of seconds to receive the hangup message
                            // If we don't get it in time, we close the screen anyway
                            delay(2.seconds)
                            close(callWidgetDriver.value, navigator)
                        }
                    } else {
                        coroutineScope.launch {
                            close(callWidgetDriver.value, navigator)
                        }
                    }
                }
                is CallScreenEvents.SetupMessageChannels -> {
                    messageInterceptor.value = event.widgetMessageInterceptor
                }
                is CallScreenEvents.OnWebViewError -> {
                    if (!ignoreWebViewError) {
                        webViewError = event.description.orEmpty()
                    }
                    // Else ignore the error, give a chance the Element Call to recover by itself.
                }
                is CallScreenEvents.ToggleRecording -> {
                    coroutineScope.launch(dispatchers.io) {
                        toggleRecording(
                            currentState = recordingState,
                            onStateChange = { recordingState = it },
                        )
                    }
                }
                // sTalk: Native call control events
                is CallScreenEvents.ToggleMute -> {
                    val interceptor = messageInterceptor.value
                    if (interceptor is WebViewWidgetMessageInterceptor) {
                        interceptor.toggleMuteInWebView()
                    }
                    isMuted = !isMuted
                }
                is CallScreenEvents.ToggleVideo -> {
                    val interceptor = messageInterceptor.value
                    if (interceptor is WebViewWidgetMessageInterceptor) {
                        interceptor.toggleVideoInWebView()
                    }
                    isVideoEnabled = !isVideoEnabled
                }
                is CallScreenEvents.ToggleSpeaker -> {
                    isSpeakerOn = !isSpeakerOn
                    // Speaker toggle is handled natively via WebViewAudioManager
                }
                is CallScreenEvents.OnMuteStateChanged -> {
                    isMuted = event.isMuted
                }
                is CallScreenEvents.OnVideoStateChanged -> {
                    isVideoEnabled = event.isVideoEnabled
                }
                is CallScreenEvents.ToggleHandRaise -> {
                    val interceptor = messageInterceptor.value
                    if (interceptor is WebViewWidgetMessageInterceptor) {
                        interceptor.toggleHandRaiseInWebView()
                    }
                    isHandRaised = !isHandRaised
                }
                is CallScreenEvents.OnHandRaiseStateChanged -> {
                    isHandRaised = event.isHandRaised
                }
            }
        }

        return CallScreenState(
            urlState = urlState.value,
            webViewError = webViewError,
            userAgent = userAgent,
            isCallActive = isWidgetLoaded,
            isInWidgetMode = isInWidgetMode,
            recordingState = recordingState,
            isMuted = isMuted,
            isVideoEnabled = isVideoEnabled,
            isSpeakerOn = isSpeakerOn,
            isHandRaised = isHandRaised,
            participantName = participantName,
            avatarData = avatarData,
            isDm = isDm,
            callDurationSeconds = callDurationSeconds,
            eventSink = ::handleEvent,
        )
    }

    private suspend fun fetchRoomCallUrl(
        inputs: CallType,
        urlState: MutableState<AsyncData<String>>,
        callWidgetDriver: MutableState<MatrixWidgetDriver?>,
        languageTag: String?,
        theme: String?,
    ) {
        urlState.runCatchingUpdatingState {
            when (inputs) {
                is CallType.ExternalUrl -> {
                    inputs.url
                }
                is CallType.RoomCall -> {
                    val result = callWidgetProvider.getWidget(
                        sessionId = inputs.sessionId,
                        roomId = inputs.roomId,
                        clientId = UUID.randomUUID().toString(),
                        languageTag = languageTag,
                        theme = theme,
                    ).getOrThrow()
                    callWidgetDriver.value = result.driver
                    Timber.d("Call widget driver initialized for sessionId: ${inputs.sessionId}, roomId: ${inputs.roomId}")
                    result.url
                }
            }
        }
    }

    @Composable
    private fun HandleMatrixClientSyncState() {
        val coroutineScope = rememberCoroutineScope()
        DisposableEffect(Unit) {
            val roomCallType = callType as? CallType.RoomCall ?: return@DisposableEffect onDispose {}
            val client = matrixClientsProvider.getOrNull(roomCallType.sessionId) ?: return@DisposableEffect onDispose {
                Timber.w("No MatrixClient found for sessionId, can't send call notification: ${roomCallType.sessionId}")
            }
            coroutineScope.launch {
                Timber.d("Observing sync state in-call for sessionId: ${roomCallType.sessionId}")
                client.syncService.syncState
                    .collect { state ->
                        if (state != SyncState.Running) {
                            appForegroundStateService.updateIsInCallState(true)
                        }
                    }
            }
            onDispose {
                Timber.d("Stopped observing sync state in-call for sessionId: ${roomCallType.sessionId}")
                // Make sure we mark the call as ended in the app state
                appForegroundStateService.updateIsInCallState(false)
            }
        }
    }

    private fun parseMessage(message: String): WidgetMessage? {
        return widgetMessageSerializer.deserialize(message).getOrNull()
    }

    private fun sendHangupMessage(widgetId: String, messageInterceptor: WidgetMessageInterceptor) {
        val message = WidgetMessage(
            direction = WidgetMessage.Direction.ToWidget,
            widgetId = widgetId,
            requestId = "widgetapi-${clock.epochMillis()}",
            action = WidgetMessage.Action.HangUp,
            data = null,
        )
        messageInterceptor.sendMessage(widgetMessageSerializer.serialize(message))
    }

    private suspend fun toggleRecording(
        currentState: RecordingState,
        onStateChange: (RecordingState) -> Unit,
    ) {
        val roomCall = callType as? CallType.RoomCall ?: return
        when (currentState) {
            is RecordingState.Idle, is RecordingState.Error -> {
                onStateChange(RecordingState.Starting)
                val result = recordingRepository.startRecording(
                    sessionId = roomCall.sessionId.value,
                    roomId = roomCall.roomId.value,
                    livekitRoomName = "livekit_${roomCall.roomId.value}",
                )
                result.fold(
                    onSuccess = { response ->
                        onStateChange(
                            RecordingState.Recording(
                                recordingId = response.recordingId,
                                startedAtMillis = clock.epochMillis(),
                            )
                        )
                        Timber.d("Recording started: ${response.recordingId}")
                    },
                    onFailure = { error ->
                        Timber.e(error, "Failed to start recording")
                        onStateChange(RecordingState.Error(error.message ?: "Failed to start recording"))
                    },
                )
            }
            is RecordingState.Recording -> {
                val result = recordingRepository.stopRecording(
                    sessionId = roomCall.sessionId.value,
                    recordingId = currentState.recordingId,
                )
                result.fold(
                    onSuccess = { response ->
                        Timber.d("Recording stopped: ${response.recordingId}, duration: ${response.durationSeconds}s")
                        onStateChange(RecordingState.Idle)
                    },
                    onFailure = { error ->
                        Timber.e(error, "Failed to stop recording")
                        onStateChange(RecordingState.Error(error.message ?: "Failed to stop recording"))
                    },
                )
            }
            is RecordingState.Starting -> {
                // Ignore toggle while starting
            }
        }
    }

    private fun CoroutineScope.close(widgetDriver: MatrixWidgetDriver?, navigator: CallScreenNavigator) = launch(dispatchers.io) {
        navigator.close()
        widgetDriver?.close()
    }
}
