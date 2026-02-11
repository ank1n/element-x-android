/*
 * Copyright (c) 2025 sTalk / Implica.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.apps

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.network.RetrofitFactory
import io.element.android.libraries.sessionstorage.api.SessionStore
import timber.log.Timber

private const val STALK_BASE_URL = "https://stalk.implica.ru/"

interface AppsRepository {
    suspend fun getWidgets(category: String? = null): Result<List<WidgetItem>>
}

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultAppsRepository @Inject constructor(
    private val retrofitFactory: RetrofitFactory,
    private val sessionStore: SessionStore,
) : AppsRepository {
    private val api: AppsApi by lazy {
        retrofitFactory.create(STALK_BASE_URL)
            .create(AppsApi::class.java)
    }

    private suspend fun getAuthHeader(): String {
        val session = sessionStore.getLatestSession()
            ?: error("No active session found")
        return "Bearer ${session.accessToken}"
    }

    override suspend fun getWidgets(category: String?): Result<List<WidgetItem>> = runCatching {
        Timber.d("Fetching widgets, category=$category")
        api.getWidgets(
            authorization = getAuthHeader(),
            category = category,
        ).widgets
    }
}
