/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.call.impl.ui

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.element.android.features.call.impl.livekit.LiveKitCallManager
import io.element.android.features.call.impl.livekit.LiveKitPipController
import io.element.android.features.call.impl.livekit.VideoGrid
import io.element.android.features.call.impl.pip.PictureInPictureEvents
import io.element.android.features.call.impl.pip.PictureInPictureState
import io.element.android.features.call.impl.pip.aPictureInPictureState
import io.element.android.features.call.impl.utils.WebViewWidgetMessageInterceptor
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.designsystem.components.ProgressDialog
import io.element.android.libraries.designsystem.components.dialogs.ErrorDialog
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Scaffold
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.ui.strings.CommonStrings
import timber.log.Timber

typealias RequestPermissionCallback = (Array<String>) -> Unit

interface CallScreenNavigator {
    fun close()
}

@Composable
internal fun CallScreenView(
    state: CallScreenState,
    pipState: PictureInPictureState,
    onConsoleMessage: (ConsoleMessage) -> Unit,
    requestPermissions: (Array<String>, RequestPermissionCallback) -> Unit,
    modifier: Modifier = Modifier,
) {
    val liveKitCallManager = state.liveKitCallManager
    fun handleBack() {
        if (pipState.supportPip) {
            pipState.eventSink.invoke(PictureInPictureEvents.EnterPictureInPicture)
        } else {
            state.eventSink(CallScreenEvents.Hangup)
        }
    }

    Scaffold(
        modifier = modifier,
    ) { padding ->
        BackHandler {
            handleBack()
        }
        if (state.webViewError != null) {
            ErrorDialog(
                content = buildString {
                    append(stringResource(CommonStrings.error_unknown))
                    state.webViewError.takeIf { it.isNotEmpty() }?.let { append("\n\n").append(it) }
                },
                onSubmit = { state.eventSink(CallScreenEvents.Hangup) },
            )
        } else {
            Box(modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding)
                .fillMaxSize()
            ) {
                // 1. Native video grid (sTalk: LiveKit SDK rendering)
                if (state.isLiveKitConnected && liveKitCallManager != null) {
                    VideoGrid(
                        localVideoTrack = state.localVideoTrack,
                        remoteParticipants = state.remoteParticipants,
                        eglBase = liveKitCallManager.eglBase,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                // 2. Invisible WebView for signaling only (1x1px)
                CallWebView(
                    modifier = Modifier
                        .size(1.dp)
                        .alpha(0f),
                    url = state.urlState,
                    userAgent = state.userAgent,
                    onPermissionsRequest = { request ->
                        // Grant all permissions — actual media is handled by native SDK
                        request.grant(request.resources)
                    },
                    onConsoleMessage = onConsoleMessage,
                    onCreateWebView = { webView ->
                        webView.addBackHandler(onBackPressed = ::handleBack)
                        var interceptorRef: WebViewWidgetMessageInterceptor? = null
                        // sTalk: Register JS interface for LiveKit credential interception
                        webView.addJavascriptInterface(object {
                            @Suppress("unused")
                            @JavascriptInterface
                            fun onCredentialsIntercepted(url: String, token: String) {
                                interceptorRef?.onLiveKitCredentials(url, token)
                            }
                        }, "stalkLiveKit")
                        val interceptor = WebViewWidgetMessageInterceptor(
                            webView = webView,
                            onUrlLoaded = { url ->
                                webView.evaluateJavascript("controls.onBackButtonPressed = () => { backHandler.onBackPressed() }", null)
                                Timber.d("URL $url is loaded (signaling-only WebView)")
                            },
                            onError = { state.eventSink(CallScreenEvents.OnWebViewError(it)) },
                        )
                        interceptorRef = interceptor
                        state.eventSink(CallScreenEvents.SetupMessageChannels(interceptor))
                        // sTalk: Use LiveKit PiP controller instead of WebView PiP
                        if (liveKitCallManager != null) {
                            val pipController = LiveKitPipController(liveKitCallManager)
                            pipState.eventSink(PictureInPictureEvents.SetPipController(pipController))
                        }
                    },
                    onDestroyWebView = {
                        // No-op: audio managed by NativeAudioManager inside LiveKitCallManager
                    }
                )

                // 3. Native call controls overlay (unchanged)
                if (state.isCallActive && state.isInWidgetMode) {
                    CallControlsOverlay(
                        state = state,
                    )
                }
            }
            when (state.urlState) {
                AsyncData.Uninitialized,
                is AsyncData.Loading ->
                    ProgressDialog(text = stringResource(id = CommonStrings.common_please_wait))
                is AsyncData.Failure -> {
                    Timber.e(state.urlState.error, "WebView failed to load URL: ${state.urlState.error.message}")
                    ErrorDialog(
                        content = state.urlState.error.message.orEmpty(),
                        onSubmit = { state.eventSink(CallScreenEvents.Hangup) },
                    )
                }
                is AsyncData.Success -> Unit
            }
        }
    }
}

@Composable
private fun CallWebView(
    url: AsyncData<String>,
    userAgent: String,
    onPermissionsRequest: (PermissionRequest) -> Unit,
    onConsoleMessage: (ConsoleMessage) -> Unit,
    onCreateWebView: (WebView) -> Unit,
    onDestroyWebView: (WebView) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (LocalInspectionMode.current) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("WebView - can't be previewed")
        }
    } else {
        AndroidView(
            modifier = modifier,
            factory = { context ->
                WebView(context).apply {
                    onCreateWebView(this)
                    setup(
                        userAgent = userAgent,
                        onPermissionsRequested = onPermissionsRequest,
                        onConsoleMessage = onConsoleMessage,
                    )
                }
            },
            update = { webView ->
                if (url is AsyncData.Success && webView.url != url.data) {
                    webView.loadUrl(url.data)
                }
            },
            onRelease = { webView ->
                onDestroyWebView(webView)
                webView.destroy()
            }
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun WebView.setup(
    userAgent: String,
    onPermissionsRequested: (PermissionRequest) -> Unit,
    onConsoleMessage: (ConsoleMessage) -> Unit,
) {
    // sTalk: 1x1px — WebView is used for signaling only, not rendering
    layoutParams = ViewGroup.LayoutParams(1, 1)

    with(settings) {
        javaScriptEnabled = true
        allowContentAccess = true
        allowFileAccess = true
        domStorageEnabled = true
        mediaPlaybackRequiresUserGesture = false
        @Suppress("DEPRECATION")
        databaseEnabled = true
        loadsImagesAutomatically = true
        userAgentString = userAgent
    }

    webChromeClient = object : WebChromeClient() {
        override fun onPermissionRequest(request: PermissionRequest) {
            onPermissionsRequested(request)
        }

        override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
            onConsoleMessage(consoleMessage)
            return true
        }
    }
}

private fun WebView.addBackHandler(onBackPressed: () -> Unit) {
    addJavascriptInterface(
        object {
            @Suppress("unused")
            @JavascriptInterface
            fun onBackPressed() = onBackPressed()
        },
        "backHandler"
    )
}

@PreviewsDayNight
@Composable
internal fun CallScreenViewPreview(
    @PreviewParameter(CallScreenStateProvider::class) state: CallScreenState,
) = ElementPreview {
    CallScreenView(
        state = state,
        pipState = aPictureInPictureState(),
        requestPermissions = { _, _ -> },
        onConsoleMessage = {},
    )
}

