package com.ubad.academy.ui.screens.hub

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ubad.academy.R
import com.ubad.academy.core.Dates
import com.ubad.academy.core.Hijri
import com.ubad.academy.core.Web
import com.ubad.academy.core.currentLocale
import com.ubad.academy.ui.navigation.HubSection
import com.ubad.academy.ui.navigation.Route
import com.ubad.academy.ui.theme.UbadThemeExt
import java.time.LocalDate

@Composable
fun HubScreen(
    onNavigate: (Route) -> Unit,
    viewModel: HubViewModel = hiltViewModel(),
) {
    val ramadan by viewModel.ramadan.collectAsStateWithLifecycle()
    val locale = currentLocale()
    val today = remember { LocalDate.now() }
    val colors = UbadThemeExt.colors

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 164.dp),
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.ubad_logo), stringResource(R.string.cd_logo), Modifier.size(40.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.brand_name), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    Text(
                        stringResource(R.string.brand_sub), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall,
                        letterSpacing = 0.3.em, color = MaterialTheme.colorScheme.outline,
                    )
                }
                IconButton(onClick = { onNavigate(Route.Search) }) {
                    Icon(Icons.Outlined.Search, stringResource(R.string.common_search))
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.padding(top = 18.dp, bottom = 6.dp)) {
                Text(
                    Dates.long(today, locale), style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.outline,
                )
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.hub_head), style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = { onNavigate(Route.Calendar(Web.today())) },
                        label = { Text(Dates.shortChip(today, locale)) },
                        leadingIcon = { Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(AssistChipDefaults.IconSize)) },
                    )
                    ramadan?.let { r ->
                        AssistChip(
                            onClick = { onNavigate(Route.Islam) },
                            label = { Text(stringResource(R.string.islam_ramadan) + " " + r.days) },
                            leadingIcon = { Icon(Icons.Outlined.DarkMode, null, Modifier.size(AssistChipDefaults.IconSize)) },
                        )
                    }
                }
            }
        }
        itemsIndexed(HubSection.entries, key = { _, s -> s.name }) { i, section ->
            HubCard(section, i, accent = colors.accent(i)) { onNavigate(section.route) }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                stringResource(R.string.hub_foot), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline, modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun HubCard(section: HubSection, index: Int, accent: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    // Staggered entrance (web: `.hub-card.pre` → cards rise into place one by one).
    val anim = remember { Animatable(0f) }
    LaunchedEffect(Unit) { anim.animateTo(1f, tween(durationMillis = 420, delayMillis = 45 * index)) }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().graphicsLayer {
            alpha = anim.value
            translationY = (1f - anim.value) * 36f
        },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = androidx.compose.foundation.BorderStroke(1.dp, UbadThemeExt.colors.line),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(MaterialTheme.shapes.medium).background(accent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(section.icon, null, tint = accent) }
                Spacer(Modifier.weight(1f))
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                    tint = MaterialTheme.colorScheme.outline,
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(stringResource(section.title), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                stringResource(section.subtitle), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
