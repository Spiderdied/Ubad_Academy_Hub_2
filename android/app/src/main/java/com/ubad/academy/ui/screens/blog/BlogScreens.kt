package com.ubad.academy.ui.screens.blog

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.ubad.academy.R
import com.ubad.academy.core.Dates
import com.ubad.academy.core.External
import com.ubad.academy.core.currentLocale
import com.ubad.academy.domain.model.BlogPost
import com.ubad.academy.ui.components.EmptyState
import com.ubad.academy.ui.components.ErrorState
import com.ubad.academy.ui.components.LoadingState
import com.ubad.academy.ui.components.UbadScaffold
import com.ubad.academy.ui.navigation.Route
import com.ubad.academy.ui.theme.UbadThemeExt
import java.time.format.DateTimeFormatter

private fun postDate(p: BlogPost) = Dates.parseIso(p.published ?: p.updated)

@Composable
fun BlogScreen(onNavigate: (Route) -> Unit, onBack: () -> Unit, viewModel: BlogViewModel = hiltViewModel()) {
    val visible by viewModel.visible.collectAsStateWithLifecycle()
    val total by viewModel.total.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val next by viewModel.nextPageToken.collectAsStateWithLifecycle()
    val locale = currentLocale()

    UbadScaffold(
        title = stringResource(R.string.blog_title), onBack = onBack,
        actions = {
            TextButton(onClick = viewModel::refresh, enabled = !status.loading) {
                Icon(Icons.Outlined.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.blog_refresh))
            }
        },
    ) { pad ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(300.dp), modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding() + 8.dp, bottom = pad.calculateBottomPadding() + 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Card(
                    shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    border = BorderStroke(1.dp, UbadThemeExt.colors.line),
                ) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.blog_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.blog_subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        // Web `#blog-status`: offline notice when showing the cache, else the count.
                        val offline = status.error && total > 0
                        val statusText = when { offline -> stringResource(R.string.blog_offline); visible != null && total > 0 -> total.toString(); else -> "" }
                        if (statusText.isNotEmpty()) Text(statusText, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelMedium,
                            color = if (offline) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.widthIn(max = 160.dp))
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                OutlinedTextField(
                    query, viewModel::setQuery, Modifier.fillMaxWidth(), singleLine = true,
                    leadingIcon = { Icon(Icons.Outlined.Search, null) }, placeholder = { Text(stringResource(R.string.blog_search)) },
                )
            }
            val list = visible
            when {
                list == null || (status.loading && total == 0) -> item(span = { GridItemSpan(maxLineSpan) }) { LoadingState(label = stringResource(R.string.blog_loading)) }
                list.isEmpty() && status.error && total == 0 -> item(span = { GridItemSpan(maxLineSpan) }) {
                    ErrorState(stringResource(R.string.blog_error), offline = true, onRetry = viewModel::refresh)
                }
                list.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) { EmptyState(Icons.AutoMirrored.Outlined.Article, stringResource(R.string.blog_empty)) }
                else -> {
                    items(list, key = { it.post.id }) { c ->
                        PostCard(c, locale) { onNavigate(Route.BlogPost(c.post.id)) }
                    }
                    if (next != null && query.isBlank()) item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                            if (status.loading) CircularProgressIndicator()
                            else OutlinedButton(onClick = viewModel::loadMore) { Text(stringResource(R.string.blog_loadMore)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PostCard(c: BlogCard, locale: java.util.Locale, onOpen: () -> Unit) {
    val title = c.title.ifEmpty { stringResource(R.string.blog_untitled) }
    Card(
        onClick = onOpen, shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), border = BorderStroke(1.dp, UbadThemeExt.colors.line),
    ) {
        val img = c.post.imageUrl
        if (img != null) AsyncImage(img, null, Modifier.fillMaxWidth().aspectRatio(16f / 9f), contentScale = ContentScale.Crop)
        else Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Outlined.Article, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.outline)
        }
        Column(Modifier.padding(14.dp)) {
            postDate(c.post)?.let {
                Text(it.format(DateTimeFormatter.ofPattern("d MMM yyyy", locale)), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
            Text(c.excerpt.ifEmpty { stringResource(R.string.blog_noImage) }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
            Button(onClick = onOpen, modifier = Modifier.padding(top = 10.dp)) {
                Text(stringResource(R.string.blog_read)); Spacer(Modifier.width(4.dp)); Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun BlogPostScreen(onBack: () -> Unit, viewModel: BlogPostViewModel = hiltViewModel()) {
    val article by viewModel.article.collectAsStateWithLifecycle()
    val missing by viewModel.missing.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val toolbar = MaterialTheme.colorScheme.surface
    val locale = currentLocale()
    val a = article
    val title = a?.title?.ifEmpty { null } ?: stringResource(if (a == null) R.string.blog_title else R.string.blog_untitled)

    UbadScaffold(title = title, onBack = onBack) { pad ->
        when {
            missing -> EmptyState(Icons.AutoMirrored.Outlined.Article, stringResource(R.string.blog_empty), Modifier.padding(pad))
            a == null -> LoadingState(Modifier.padding(pad))
            else -> Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(pad).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Card(
                    Modifier.widthIn(max = 760.dp).fillMaxWidth(), shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), border = BorderStroke(1.dp, UbadThemeExt.colors.line),
                ) {
                    a.post.imageUrl?.let { AsyncImage(it, null, Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth) }
                    Column(Modifier.padding(18.dp)) {
                        postDate(a.post)?.let {
                            Text(it.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", locale)), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(a.title.ifEmpty { stringResource(R.string.blog_untitled) }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp, bottom = 8.dp))
                        BlogBlocks(a.blocks)
                        a.post.url?.let { url ->
                            OutlinedButton(onClick = { External.openInTab(context, url, toolbar) }, modifier = Modifier.padding(top = 16.dp)) {
                                Text(stringResource(R.string.blog_original)); Spacer(Modifier.width(6.dp)); Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
