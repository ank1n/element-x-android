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
import kotlinx.coroutines.flow.MutableSharedFlow
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

    init {
        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/", WebViewAssetLoader.AssetsPathHandler(webView.context))
            .build()

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)

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
                // sTalk: Inject CSS to hide Element Call native UI and make video full-screen
                injectCallOverlayCSS(view)
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

    // sTalk: Inject CSS to hide Element Call UI and make video full-screen (Telegram-style)
    private fun injectCallOverlayCSS(view: WebView) {
        view.evaluateJavascript(
            """
            (function() {
                var styleId = 'stalk-call-overlay-css';
                if (document.getElementById(styleId)) return;

                var css = '' +
                    '[class*="_header_"], [class*="_Header_"] { display: none !important; }' +
                    '[class*="_footer_"], [class*="_Footer_"] { display: none !important; }' +
                    '[class*="_toolbar_"], [class*="_Toolbar_"] { display: none !important; }' +
                    '[class*="_controls_"], [class*="_Controls_"] { display: none !important; }' +
                    '[class*="_bar_"]:not([class*="_sidebar"]) { display: none !important; }' +
                    '[class*="_lobby_"], [class*="_Lobby_"] { display: none !important; }' +
                    '[class*="_logo_"], [class*="_Logo_"] { display: none !important; }' +
                    '[class*="_invite_"], [class*="_Invite_"] { display: none !important; }' +
                    '[class*="_hangup_"], [class*="_Hangup_"] { display: none !important; }' +
                    '[class*="_button-row"], [class*="_ButtonRow"] { display: none !important; }' +
                    'header, footer, nav { display: none !important; }' +
                    'body { background: #000 !important; margin: 0 !important; padding: 0 !important; overflow: hidden !important; }' +
                    'video { object-fit: cover !important; width: 100% !important; height: 100% !important; }' +
                    '[class*="_avatar_"], [class*="_Avatar_"] { display: none !important; }' +
                    '[class*="_no-video"], [class*="_noVideo"] { background: #000 !important; }';

                var style = document.createElement('style');
                style.id = styleId;
                style.textContent = css;
                document.head.appendChild(style);

                // MutationObserver to re-apply CSS if React re-renders and removes our style
                var observer = new MutationObserver(function() {
                    if (!document.getElementById(styleId)) {
                        var s = document.createElement('style');
                        s.id = styleId;
                        s.textContent = css;
                        document.head.appendChild(s);
                    }
                });
                observer.observe(document.documentElement, { childList: true, subtree: true });

                // Delayed re-application for async React rendering
                [500, 1500, 3000].forEach(function(delay) {
                    setTimeout(function() {
                        if (!document.getElementById(styleId)) {
                            var s = document.createElement('style');
                            s.id = styleId;
                            s.textContent = css;
                            document.head.appendChild(s);
                        }
                    }, delay);
                });
            })();
            """.trimIndent(),
            null,
        )
    }

    // sTalk: Inject JS bridge for native call controls ↔ WebView state sync
    fun injectControlsBridge(view: WebView) {
        view.evaluateJavascript(
            """
            (function() {
                if (window._stalkControlsBridgeInit) return;
                window._stalkControlsBridgeInit = true;

                // Listen for mute state changes from Element Call
                var origAudioMute = null;
                function hookAudioTrack() {
                    var tracks = document.querySelectorAll('audio, video');
                    tracks.forEach(function(el) {
                        if (el.srcObject) {
                            el.srcObject.getAudioTracks().forEach(function(track) {
                                if (!track._stalkHooked) {
                                    track._stalkHooked = true;
                                    var origEnabled = Object.getOwnPropertyDescriptor(
                                        MediaStreamTrack.prototype, 'enabled'
                                    );
                                    // We'll detect changes via polling instead
                                }
                            });
                        }
                    });
                }

                // Poll for control state changes and report to native
                var lastMuteState = null;
                var lastVideoState = null;
                setInterval(function() {
                    try {
                        // Check mute buttons
                        var muteBtn = document.querySelector('[class*="_mute"], [aria-label*="Mute"], [aria-label*="mute"], [data-testid*="mute"]');
                        var isMuted = muteBtn ? (muteBtn.getAttribute('aria-pressed') === 'true' || muteBtn.classList.toString().indexOf('active') >= 0) : false;

                        var videoBtn = document.querySelector('[class*="_video"], [aria-label*="Video"], [aria-label*="camera"], [data-testid*="video"]');
                        var isVideoOff = videoBtn ? (videoBtn.getAttribute('aria-pressed') === 'true' || videoBtn.classList.toString().indexOf('active') >= 0) : false;

                        if (isMuted !== lastMuteState) {
                            lastMuteState = isMuted;
                            if (window.stalkCallControls) window.stalkCallControls.onMuteChanged(isMuted);
                        }
                        if (isVideoOff !== lastVideoState) {
                            lastVideoState = isVideoOff;
                            if (window.stalkCallControls) window.stalkCallControls.onVideoChanged(!isVideoOff);
                        }
                    } catch(e) {}
                }, 500);
            })();
            """.trimIndent(),
            null,
        )
    }

    // sTalk: Toggle mute in the WebView
    fun toggleMuteInWebView() {
        webView.evaluateJavascript(
            """
            (function() {
                var btn = document.querySelector('[class*="_mute"], [aria-label*="Mute"], [aria-label*="mute"], [data-testid*="mute"]');
                if (btn) btn.click();
            })();
            """.trimIndent(),
            null,
        )
    }

    // sTalk: Toggle video in the WebView
    fun toggleVideoInWebView() {
        webView.evaluateJavascript(
            """
            (function() {
                var btn = document.querySelector('[class*="_video"], [aria-label*="Video"], [aria-label*="camera"], [data-testid*="video"]');
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
