/*
 * Copyright (c) 2025 sTalk / Implica.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.apps

import retrofit2.http.GET
import retrofit2.http.Header

internal interface AppsApi {
    @GET("apps-api/widgets")
    suspend fun getWidgets(
        @Header("Authorization") authorization: String,
    ): WidgetsResponse
}
