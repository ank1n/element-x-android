/*
 * Copyright (c) 2025 sTalk / Implica.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.login

import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.TimeUnit

class KeycloakLoginException(message: String) : Exception(message)

@Inject
class KeycloakDirectLoginService {

    suspend fun login(authorizationUrl: String, username: String, password: String): String {
        return withContext(Dispatchers.IO) {
            val cookieJar = InMemoryCookieJar()
            val client = OkHttpClient.Builder()
                .cookieJar(cookieJar)
                .followRedirects(false)
                .followSslRedirects(false)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()

            try {
                // Step 1: Follow redirects from authorization URL to Keycloak login page
                val loginPageResult = followRedirectsToHtml(client, authorizationUrl)

                // Step 2: Parse the Keycloak login form
                val formAction = parseFormAction(loginPageResult.body)
                    ?: throw KeycloakLoginException("Не удалось найти форму логина")
                val hiddenFields = parseHiddenFields(loginPageResult.body)

                // Resolve action URL relative to current page URL
                val actionUrl = resolveUrl(loginPageResult.finalUrl, formAction)

                // Step 3: POST credentials
                val formBodyBuilder = FormBody.Builder()
                    .add("username", username)
                    .add("password", password)
                for ((name, value) in hiddenFields) {
                    formBodyBuilder.add(name, value)
                }

                val postRequest = Request.Builder()
                    .url(actionUrl)
                    .post(formBodyBuilder.build())
                    .header("User-Agent", USER_AGENT)
                    .header("Referer", loginPageResult.finalUrl)
                    .header("Origin", loginPageResult.finalUrl.toHttpUrlOrNull()?.let {
                        "${it.scheme}://${it.host}"
                    } ?: loginPageResult.finalUrl)
                    .build()

                val postResponse = client.newCall(postRequest).execute()

                // Step 4: Follow redirects until we get the callback URL
                followRedirectsOrGetError(client, postResponse)
            } finally {
                client.dispatcher.executorService.shutdown()
                client.connectionPool.evictAll()
            }
        }
    }

    private data class PageResult(val finalUrl: String, val body: String)

    private fun followRedirectsToHtml(client: OkHttpClient, startUrl: String): PageResult {
        var url = startUrl
        var maxRedirects = 15
        while (maxRedirects-- > 0) {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()
            val response = client.newCall(request).execute()
            val location = response.header("Location")
            if (response.isRedirect && location != null) {
                response.close()
                url = resolveUrl(url, location)
                continue
            }
            val body = response.body.string()
            return PageResult(url, body)
        }
        throw IOException("Too many redirects")
    }

    private fun followRedirectsOrGetError(client: OkHttpClient, initialResponse: Response): String {
        var response = initialResponse
        var maxRedirects = 20
        while (maxRedirects-- > 0) {
            val currentUrl = response.request.url.toString()
            val location = response.header("Location")

            if (location != null) {
                // Check if this is the callback URL (custom scheme, not HTTP)
                if (!location.startsWith("http://") && !location.startsWith("https://") && !location.startsWith("/")) {
                    response.close()
                    return location
                }

                val resolvedUrl = resolveUrl(currentUrl, location)
                response.close()

                val request = Request.Builder()
                    .url(resolvedUrl)
                    .header("User-Agent", USER_AGENT)
                    .build()
                response = client.newCall(request).execute()
                continue
            }

            // No redirect — read the body
            val responseBody = response.use { it.body.string() }

            // Check if this is a MAS consent page — auto-approve it
            if (currentUrl.contains("/consent/") || responseBody.contains("layout-container consent")) {
                Timber.d("Auto-approving MAS consent page")
                val csrfToken = parseCsrfToken(responseBody)

                val consentFormBody = FormBody.Builder()
                if (csrfToken != null) {
                    consentFormBody.add("csrf", csrfToken)
                }

                val consentRequest = Request.Builder()
                    .url(currentUrl)
                    .post(consentFormBody.build())
                    .header("User-Agent", USER_AGENT)
                    .header("Referer", currentUrl)
                    .build()
                response = client.newCall(consentRequest).execute()
                continue
            }

            // Not a consent page — check for Keycloak errors
            val errorMessage = parseKeycloakError(responseBody)
            throw KeycloakLoginException(errorMessage ?: "Ошибка авторизации")
        }
        throw IOException("Too many redirects")
    }

    private fun parseCsrfToken(html: String): String? {
        val pattern = Regex("""<input[^>]*name\s*=\s*["']csrf["'][^>]*value\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        pattern.find(html)?.let { return decodeHtmlEntities(it.groupValues[1]) }
        val pattern2 = Regex("""<input[^>]*value\s*=\s*["']([^"']+)["'][^>]*name\s*=\s*["']csrf["']""", RegexOption.IGNORE_CASE)
        pattern2.find(html)?.let { return decodeHtmlEntities(it.groupValues[1]) }
        return null
    }

    private fun parseFormAction(html: String): String? {
        val kcFormPattern = Regex("""<form[^>]*id\s*=\s*["']kc-form-login["'][^>]*action\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        kcFormPattern.find(html)?.let { return decodeHtmlEntities(it.groupValues[1]) }

        val kcFormPattern2 = Regex("""<form[^>]*action\s*=\s*["']([^"']+)["'][^>]*id\s*=\s*["']kc-form-login["']""", RegexOption.IGNORE_CASE)
        kcFormPattern2.find(html)?.let { return decodeHtmlEntities(it.groupValues[1]) }

        val anyFormPattern = Regex("""<form[^>]*action\s*=\s*["']([^"']*authenticate[^"']*)["']""", RegexOption.IGNORE_CASE)
        anyFormPattern.find(html)?.let { return decodeHtmlEntities(it.groupValues[1]) }

        return null
    }

    private fun parseHiddenFields(html: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val pattern = Regex("""<input[^>]*type\s*=\s*["']hidden["'][^>]*>""", RegexOption.IGNORE_CASE)
        for (match in pattern.findAll(html)) {
            val tag = match.value
            val name = Regex("""name\s*=\s*["']([^"']+)["']""").find(tag)?.groupValues?.get(1) ?: continue
            val value = Regex("""value\s*=\s*["']([^"']*)["']""").find(tag)?.groupValues?.get(1) ?: ""
            result[name] = decodeHtmlEntities(value)
        }
        return result
    }

    private fun parseKeycloakError(html: String): String? {
        val pattern = Regex("""<span[^>]*class\s*=\s*["'][^"']*kc-feedback-text[^"']*["'][^>]*>(.*?)</span>""", RegexOption.IGNORE_CASE)
        pattern.find(html)?.let {
            return it.groupValues[1].trim().replace(Regex("<[^>]+>"), "")
        }

        val alertPattern = Regex("""<div[^>]*class\s*=\s*["'][^"']*alert-error[^"']*["'][^>]*>(.*?)</div>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        alertPattern.find(html)?.let {
            return it.groupValues[1].trim().replace(Regex("<[^>]+>"), "").trim()
        }

        return null
    }

    private fun decodeHtmlEntities(text: String): String {
        return text
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&#x27;", "'")
    }

    private fun resolveUrl(baseUrl: String, relativeUrl: String): String {
        if (relativeUrl.startsWith("http://") || relativeUrl.startsWith("https://")) {
            return relativeUrl
        }
        val base = baseUrl.toHttpUrlOrNull() ?: return relativeUrl
        return base.resolve(relativeUrl)?.toString() ?: relativeUrl
    }

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36"
    }
}

private class InMemoryCookieJar : CookieJar {
    private val cookies = mutableListOf<Cookie>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        this.cookies.addAll(cookies)
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        return cookies.filter { it.matches(url) }
    }
}
