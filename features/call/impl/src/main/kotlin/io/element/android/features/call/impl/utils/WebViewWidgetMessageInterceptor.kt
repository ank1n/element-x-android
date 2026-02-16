/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.call.impl.utils

import android.graphics.Bitmap
import android.net.http.SslError
import android.webkit.JavascriptInterface
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.net.toUri
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import io.element.android.features.call.impl.BuildConfig
import io.element.android.features.call.impl.livekit.LiveKitCredentials
import io.element.android.features.call.impl.livekit.LiveKitJwtDecoder
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import timber.log.Timber

class WebViewWidgetMessageInterceptor(
    private val webView: WebView,
    private val onUrlLoaded: (String) -> Unit,
    private val onError: (String?) -> Unit,
) : WidgetMessageInterceptor {
    companion object {
        // We call both the WebMessageListener and the JavascriptInterface objects in JS with this
        // 'listenerName' so they can both receive the data from the WebView when
        // `${LISTENER_NAME}.postMessage(...)` is called
        const val LISTENER_NAME = "elementX"
    }

    // It's important to have extra capacity here to make sure we don't drop any messages
    override val interceptedMessages = MutableSharedFlow<String>(extraBufferCapacity = 10)

    // sTalk: LiveKit credentials flow — emitted when WebSocket URL + token are intercepted
    private val _livekitCredentials = MutableSharedFlow<LiveKitCredentials>(extraBufferCapacity = 1)
    val livekitCredentials: SharedFlow<LiveKitCredentials> = _livekitCredentials.asSharedFlow()

    init {
        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/", WebViewAssetLoader.AssetsPathHandler(webView.context))
            .build()

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)

                // sTalk: Hide all WebView content — native LiveKit SDK handles video rendering
                view.evaluateJavascript(
                    """
                        (function() {
                            var s = document.createElement('style');
                            s.textContent = 'body, body * { visibility:hidden!important; height:0!important; width:0!important; overflow:hidden!important; }';
                            (document.head || document.documentElement).appendChild(s);
                        })();
                    """.trimIndent(),
                    null
                )

                // sTalk: Suppress getUserMedia so WebView doesn't capture camera/microphone
                view.evaluateJavascript(
                    """
                        (function() {
                            if (window._stalkMediaSuppressed) return;
                            window._stalkMediaSuppressed = true;
                            if (navigator.mediaDevices) {
                                navigator.mediaDevices.getUserMedia = function() {
                                    console.log('[sTalk] getUserMedia suppressed — native SDK handles media');
                                    return Promise.reject(new DOMException('Media handled by native SDK', 'NotAllowedError'));
                                };
                            }
                        })();
                    """.trimIndent(),
                    null
                )

                // Due to https://github.com/element-hq/element-x-android/issues/4097
                // we need to supply a logging implementation that correctly includes
                // objects in log lines.
                view.evaluateJavascript(
                    """
                        function logFn(consoleLogFn, ...args) {
                            consoleLogFn(
                                args.map(
                                    a => typeof a === "string" ? a : JSON.stringify(a)
                                ).join(' ')
                            );
                        };
                        globalThis.console.debug = logFn.bind(null, console.debug);
                        globalThis.console.log = logFn.bind(null, console.log);
                        globalThis.console.info = logFn.bind(null, console.info);
                        globalThis.console.warn = logFn.bind(null, console.warn);
                        globalThis.console.error = logFn.bind(null, console.error);
                    """.trimIndent(),
                    null
                )

                // sTalk: Hook WebSocket to intercept LiveKit signaling URL + access_token
                view.evaluateJavascript(
                    """
                        (function() {
                            if (window._stalkWSHooked) return;
                            window._stalkWSHooked = true;
                            var OrigWS = window.WebSocket;
                            window.WebSocket = function(url, protocols) {
                                var urlStr = String(url);
                                var isLiveKit = urlStr.indexOf('livekit') >= 0 || urlStr.indexOf('/rtc') >= 0;
                                if (isLiveKit) {
                                    console.log('[sTalk-ws] LiveKit WebSocket URL: ' + urlStr.substring(0, 200));
                                    // Extract access_token from URL
                                    try {
                                        var urlObj = new URL(urlStr);
                                        var token = urlObj.searchParams.get('access_token');
                                        if (token && window.stalkLiveKit) {
                                            var wsUrl = urlObj.protocol.replace('ws', 'http') + '//' + urlObj.host;
                                            console.log('[sTalk-ws] Credentials intercepted, sending to native');
                                            window.stalkLiveKit.onCredentialsIntercepted(wsUrl, token);
                                        }
                                    } catch(e) {
                                        console.log('[sTalk-ws] Failed to extract credentials: ' + e.message);
                                    }
                                }
                                var ws;
                                if (protocols !== undefined) {
                                    ws = new OrigWS(urlStr, protocols);
                                } else {
                                    ws = new OrigWS(urlStr);
                                }
                                return ws;
                            };
                            window.WebSocket.prototype = OrigWS.prototype;
                            window.WebSocket.CONNECTING = OrigWS.CONNECTING;
                            window.WebSocket.OPEN = OrigWS.OPEN;
                            window.WebSocket.CLOSING = OrigWS.CLOSING;
                            window.WebSocket.CLOSED = OrigWS.CLOSED;
                        })();
                    """.trimIndent(),
                    null
                )

                // We inject this JS code when the page starts loading to attach a message listener to the window.
                // This listener will receive both messages:
                // - EC widget API -> Element X (message.data.api == "fromWidget")
                // - Element X -> EC widget API (message.data.api == "toWidget"), we should ignore these
                view.evaluateJavascript(
                    """
                        window.addEventListener('message', function(event) {
                            let message = {data: event.data, origin: event.origin}
                            if (message.data.response && message.data.api == "toWidget"
                                || !message.data.response && message.data.api == "fromWidget") {
                                let json = JSON.stringify(event.data) 
                                ${"console.log('message sent: ' + json);".takeIf { BuildConfig.DEBUG }}
                                $LISTENER_NAME.postMessage(json);
                            } else {
                                ${"console.log('message received (ignored): ' + JSON.stringify(event.data));".takeIf { BuildConfig.DEBUG }}
                            }
                        });
                    """.trimIndent(),
                    null
                )
            }

            override fun onPageFinished(view: WebView, url: String) {
                onUrlLoaded(url)
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                // No network for instance, transmit the error
                Timber.e("onReceivedError error: ${error?.errorCode} ${error?.description}")

                // Only propagate the error if it happens while loading the current page
                if (view?.url == request?.url.toString()) {
                    onError(error?.description.toString())
                }

                super.onReceivedError(view, request, error)
            }

            override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
                Timber.e("onReceivedHttpError error: ${errorResponse?.statusCode} ${errorResponse?.reasonPhrase}")

                // Only propagate the error if it happens while loading the current page
                if (view?.url == request?.url.toString()) {
                    onError(errorResponse?.statusCode.toString())
                }

                super.onReceivedHttpError(view, request, errorResponse)
            }

            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                Timber.e("onReceivedSslError error: ${error?.primaryError}")

                // Only propagate the error if it happens while loading the current page
                if (view?.url == error?.url.toString()) {
                    onError(error?.toString())
                }

                super.onReceivedSslError(view, handler, error)
            }

            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest): WebResourceResponse? {
                return assetLoader.shouldInterceptRequest(request.url)
            }

            @Suppress("OVERRIDE_DEPRECATION")
            override fun shouldInterceptRequest(view: WebView?, url: String): WebResourceResponse? {
                return assetLoader.shouldInterceptRequest(url.toUri())
            }
        }

        // Create a WebMessageListener, which will receive messages from the WebView and reply to them
        val webMessageListener = WebViewCompat.WebMessageListener { _, message, _, _, _ ->
            onMessageReceived(message.data)
        }

        // Use WebMessageListener if supported, otherwise use JavascriptInterface
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.addWebMessageListener(
                webView,
                LISTENER_NAME,
                setOf("*"),
                webMessageListener
            )
        } else {
            webView.addJavascriptInterface(object {
                @JavascriptInterface
                fun postMessage(json: String?) {
                    onMessageReceived(json)
                }
            }, LISTENER_NAME)
        }
    }

    override fun sendMessage(message: String) {
        webView.evaluateJavascript("postMessage($message, '*')", null)
    }

    private fun onMessageReceived(json: String?) {
        // Here is where we would handle the messages from the WebView, passing them to the Rust SDK
        json?.let { interceptedMessages.tryEmit(it) }
    }

    // sTalk: Called from JS when LiveKit WebSocket credentials are intercepted
    fun onLiveKitCredentials(url: String, token: String) {
        val roomName = LiveKitJwtDecoder.decodeRoomName(token)
        if (roomName == null) {
            Timber.w("Failed to decode room name from LiveKit JWT")
            return
        }
        // Convert HTTP(S) URL back to WSS for LiveKit SDK
        val wsUrl = url.replace("http://", "ws://").replace("https://", "wss://")
        val credentials = LiveKitCredentials(
            url = wsUrl,
            token = token,
            roomName = roomName,
        )
        Timber.d("LiveKit credentials intercepted: url=$wsUrl, room=$roomName")
        _livekitCredentials.tryEmit(credentials)
    }

    // sTalk: Toggle hand raise in the WebView
    fun toggleHandRaiseInWebView() {
        webView.evaluateJavascript(
            """
            (function() {
                var btn = document.querySelector('[aria-label*="Hand"], [aria-label*="hand"], [data-testid*="hand"], [class*="_hand"], [class*="Hand"]');
                if (btn) btn.click();
            })();
            """.trimIndent(),
            null,
        )
    }

    // sTalk: Trigger hangup in the WebView
    fun hangupInWebView() {
        webView.evaluateJavascript(
            """
            (function() {
                var btn = document.querySelector('[class*="_hangup"], [aria-label*="Hang up"], [aria-label*="hangup"], [data-testid*="hangup"]');
                if (btn) btn.click();
            })();
            """.trimIndent(),
            null,
        )
    }
}
