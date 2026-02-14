/*
 * Copyright (c) 2025 sTalk / Implica.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.apps

import android.annotation.SuppressLint
import androidx.compose.material3.ExperimentalMaterial3Api
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import timber.log.Timber
import io.element.android.features.home.impl.components.StalkUnderlineFilter
import io.element.android.libraries.designsystem.components.avatar.Avatar
import io.element.android.libraries.designsystem.components.avatar.AvatarData
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.designsystem.components.avatar.AvatarType
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.IconButton
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TopAppBar

@Composable
fun AppsView(
    state: AppsState,
    modifier: Modifier = Modifier,
) {
    if (state.selectedWidget != null) {
        Timber.d("AppsView: Opening widget WebView: ${state.selectedWidget.id} url=${state.selectedWidget.url}")
        WidgetWebView(
            widget = state.selectedWidget,
            onClose = { state.eventSink(AppsEvent.CloseWidget) },
            modifier = modifier,
        )
    } else {
        WidgetListView(
            state = state,
            modifier = modifier,
        )
    }
}

@Composable
private fun WidgetListView(
    state: AppsState,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        // Category filters
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(AppsCategory.entries) { category ->
                StalkUnderlineFilter(
                    text = category.displayName,
                    isSelected = state.selectedCategory == category,
                    onClick = { state.eventSink(AppsEvent.SelectCategory(category)) },
                )
            }
        }

        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            state.error != null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Failed to load apps",
                            style = ElementTheme.typography.fontBodyLgRegular,
                            color = ElementTheme.colors.textSecondary,
                        )
                        Text(
                            text = "Tap to retry",
                            style = ElementTheme.typography.fontBodySmRegular,
                            color = ElementTheme.colors.textActionAccent,
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .clickable { state.eventSink(AppsEvent.Refresh) },
                        )
                    }
                }
            }
            state.widgets.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No apps available",
                        style = ElementTheme.typography.fontBodyLgRegular,
                        color = ElementTheme.colors.textSecondary,
                    )
                }
            }
            else -> {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(
                        items = state.widgets,
                        key = { it.id },
                    ) { widget ->
                        WidgetListItem(
                            widget = widget,
                            onClick = {
                                Timber.d("AppsView: Widget clicked: ${widget.id} name=${widget.name}")
                                state.eventSink(AppsEvent.OpenWidget(widget))
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetListItem(
    widget: WidgetItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(
            avatarData = AvatarData(
                id = widget.id,
                name = widget.name,
                url = widget.icon?.let { if (it.startsWith("http")) it else "https://stalk.implica.ru$it" },
                size = AvatarSize.UserListItem,
            ),
            avatarType = AvatarType.Room(),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = widget.name,
                style = ElementTheme.typography.fontBodyLgMedium,
                color = ElementTheme.colors.textPrimary,
            )
            if (!widget.description.isNullOrEmpty()) {
                Text(
                    text = widget.description,
                    style = ElementTheme.typography.fontBodySmRegular,
                    color = ElementTheme.colors.textSecondary,
                )
            }
        }
        Icon(
            imageVector = CompoundIcons.ChevronRight(),
            contentDescription = null,
            tint = ElementTheme.colors.iconSecondary,
            modifier = Modifier.size(20.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WidgetWebView(
    widget: WidgetItem,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onClose)

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            titleStr = widget.name,
            navigationIcon = {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = CompoundIcons.Close(),
                        contentDescription = "Close",
                    )
                }
            },
        )
        WidgetWebViewContent(
            url = if (widget.url.startsWith("http")) widget.url else "https://stalk.implica.ru${widget.url}",
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WidgetWebViewContent(
    url: String,
    modifier: Modifier = Modifier,
) {
    Timber.d("WidgetWebViewContent: Loading URL: $url")
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, pageUrl: String?) {
                        Timber.d("WidgetWebView: Page loaded: $pageUrl")
                    }
                    override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                        Timber.e("WidgetWebView: Error loading $failingUrl: $errorCode $description")
                    }
                }
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                loadUrl(url)
            }
        },
        modifier = modifier,
    )
}
