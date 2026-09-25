package com.ubad.academy.ui.screens.study

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ubad.academy.R
import com.ubad.academy.core.External
import com.ubad.academy.domain.model.LinkKind
import com.ubad.academy.domain.model.SavedLink
import com.ubad.academy.ui.components.CollectMessages
import com.ubad.academy.ui.components.ConfirmDialog
import com.ubad.academy.ui.components.EmptyState
import com.ubad.academy.ui.components.LoadingState
import com.ubad.academy.ui.components.TextInputDialog
import com.ubad.academy.ui.components.UbadScaffold
import com.ubad.academy.ui.navigation.Route
import com.ubad.academy.ui.theme.UbadThemeExt

fun StudyTab.labelRes(): Int = when (this) {
    StudyTab.CARDS -> R.string.study_tabCards
    StudyTab.QUIZZES -> R.string.study_tabQuiz
    StudyTab.FOCUS -> R.string.focus_tab
    StudyTab.FORMS -> R.string.study_tabForms
    StudyTab.SUMMARIES -> R.string.study_tabSummaries
    StudyTab.SCHEDULE -> R.string.schedule_tab
}

fun StudyTab.icon(): ImageVector = when (this) {
    StudyTab.CARDS -> Icons.Outlined.Layers
    StudyTab.QUIZZES -> Icons.Outlined.CheckCircle
    StudyTab.FOCUS -> Icons.Outlined.Timer
    StudyTab.FORMS -> Icons.Outlined.Language
    StudyTab.SUMMARIES -> Icons.AutoMirrored.Outlined.MenuBook
    StudyTab.SCHEDULE -> Icons.Outlined.CalendarMonth
}

/** What the add button creates on each tab (hidden on Focus, like the web). */
private fun StudyTab.addLabel(): Int? = when (this) {
    StudyTab.CARDS -> R.string.study_newDeck
    StudyTab.QUIZZES -> R.string.study_newQuiz
    StudyTab.FOCUS -> null
    StudyTab.FORMS -> R.string.forms_newTest
    StudyTab.SUMMARIES -> R.string.sum_newBtn
    StudyTab.SCHEDULE -> R.string.schedule_add
}

@Composable
fun StudyScreen(onNavigate: (Route) -> Unit, viewModel: StudyViewModel = hiltViewModel()) {
    val tabKey by viewModel.tab.collectAsStateWithLifecycle()
    val tab = StudyTab.from(tabKey)
    val snackbar = remember { SnackbarHostState() }
    CollectMessages(viewModel.messages.flow, snackbar)

    var newDeck by rememberSaveable { mutableStateOf(false) }
    var linkEditor by remember { mutableStateOf<Pair<LinkKind, SavedLink?>?>(null) }
    var scheduleEditor by remember { mutableStateOf<ScheduleDraftRequest?>(null) }

    UbadScaffold(
        title = stringResource(R.string.nav_study), onBack = null, snackbarHostState = snackbar,
        floatingActionButton = {
            tab.addLabel()?.let { label ->
                ExtendedFloatingActionButton(
                    onClick = {
                        when (tab) {
                            StudyTab.CARDS -> newDeck = true
                            StudyTab.QUIZZES -> onNavigate(Route.QuizEdit())
                            StudyTab.FORMS -> linkEditor = LinkKind.FORMS to null
                            StudyTab.SUMMARIES -> linkEditor = LinkKind.SUMMARIES to null
                            StudyTab.SCHEDULE -> scheduleEditor = ScheduleDraftRequest()
                            StudyTab.FOCUS -> Unit
                        }
                    },
                    icon = { Icon(Icons.Outlined.Add, null) }, text = { Text(stringResource(label)) },
                )
            }
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(top = pad.calculateTopPadding())) {
            ScrollableTabRow(selectedTabIndex = tab.ordinal, edgePadding = 12.dp, containerColor = Color.Transparent) {
                StudyTab.entries.forEach { t ->
                    Tab(
                        selected = t == tab, onClick = { viewModel.selectTab(t) },
                        text = { Text(stringResource(t.labelRes()), maxLines = 1) }, icon = { Icon(t.icon(), null) },
                    )
                }
            }
            val bottom = pad.calculateBottomPadding() + 96.dp
            Box(Modifier.fillMaxSize()) {
                when (tab) {
                    StudyTab.CARDS -> DecksTab(viewModel, onNavigate, bottom) { newDeck = true }
                    StudyTab.QUIZZES -> QuizzesTab(viewModel, onNavigate, bottom)
                    StudyTab.FOCUS -> FocusTab(viewModel, pad.calculateBottomPadding() + 24.dp)
                    StudyTab.FORMS -> LinksTab(viewModel, LinkKind.FORMS, bottom) { linkEditor = LinkKind.FORMS to it }
                    StudyTab.SUMMARIES -> LinksTab(viewModel, LinkKind.SUMMARIES, bottom) { linkEditor = LinkKind.SUMMARIES to it }
                    StudyTab.SCHEDULE -> ScheduleTab(viewModel, bottom) { scheduleEditor = it }
                }
            }
        }
    }

    if (newDeck) TextInputDialog(
        title = stringResource(R.string.study_newDeck), label = stringResource(R.string.study_deckName), initial = "", maxLength = 80,
        onConfirm = { viewModel.createDeck(it); newDeck = false }, onDismiss = { newDeck = false },
    )
    linkEditor?.let { (kind, item) ->
        LinkDialog(kind, item, onSave = { t, u -> viewModel.saveLink(kind, item, t, u) { linkEditor = null } }, onDismiss = { linkEditor = null })
    }
    scheduleEditor?.let { req ->
        ScheduleDialog(req, onSave = { t, d, s, e -> viewModel.saveSchedule(req.entry, t, d, s, e); scheduleEditor = null }, onDismiss = { scheduleEditor = null })
    }
}

@Composable
fun StudyRow(
    icon: ImageVector,
    title: String,
    sub: String,
    onClick: (() -> Unit)?,
    titleExtra: @Composable () -> Unit = {},
    actions: @Composable () -> Unit,
) {
    Card(
        onClick = onClick ?: {}, enabled = onClick != null,
        modifier = Modifier.widthIn(max = 840.dp).fillMaxWidth(), shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer, disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, UbadThemeExt.colors.line),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        titleExtra()
                    }
                    Text(sub, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            actions()
        }
    }
}

@Composable
private fun <T> ListOrState(
    items: List<T>?,
    bottom: androidx.compose.ui.unit.Dp,
    key: (T) -> String,
    empty: @Composable () -> Unit,
    row: @Composable (T) -> Unit,
) {
    when {
        items == null -> LoadingState()
        items.isEmpty() -> empty()
        else -> LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = bottom),
            verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally,
        ) { items(items, key = key) { row(it) } }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DecksTab(vm: StudyViewModel, onNavigate: (Route) -> Unit, bottom: androidx.compose.ui.unit.Dp, onNew: () -> Unit) {
    val decks by vm.decks.collectAsStateWithLifecycle()
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    ListOrState(decks, bottom, { it.id }, empty = {
        EmptyState(Icons.Outlined.Layers, stringResource(R.string.study_noDecks), hint = stringResource(R.string.study_noDecksHint),
            action = stringResource(R.string.study_newDeck), onAction = onNew)
    }) { d ->
        StudyRow(Icons.Outlined.Layers, d.title, "${d.cards.size} ${stringResource(R.string.study_cardsLc)}", onClick = { onNavigate(Route.Deck(d.id)) }) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                OutlinedButton(onClick = { onNavigate(Route.Deck(d.id)) }) { Text(stringResource(R.string.study_study)) }
                Button(onClick = { onNavigate(Route.DeckTest(d.id)) }) { Icon(Icons.Outlined.CheckCircle, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.study_selfTest)) }
                IconButton(onClick = { deleting = d.id }) { Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.common_delete)) }
            }
        }
    }
    deleting?.let { id ->
        ConfirmDialog(stringResource(R.string.study_deleteDeck), stringResource(R.string.study_deleteDeckMsg),
            onConfirm = { vm.deleteDeck(id); deleting = null }, onDismiss = { deleting = null })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuizzesTab(vm: StudyViewModel, onNavigate: (Route) -> Unit, bottom: androidx.compose.ui.unit.Dp) {
    val quizzes by vm.quizzes.collectAsStateWithLifecycle()
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    ListOrState(quizzes, bottom, { it.id }, empty = {
        EmptyState(Icons.Outlined.CheckCircle, stringResource(R.string.study_noQuizzes), hint = stringResource(R.string.study_noQuizzesHint),
            action = stringResource(R.string.study_newQuiz), onAction = { onNavigate(Route.QuizEdit()) })
    }) { z ->
        StudyRow(Icons.Outlined.CheckCircle, z.title, "${z.questions.size} ${stringResource(R.string.study_questionsLc)}", onClick = { onNavigate(Route.QuizEdit(z.id)) }) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                Button(onClick = { onNavigate(Route.QuizPlay(z.id)) }) { Icon(Icons.Outlined.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.study_start)) }
                IconButton(onClick = { onNavigate(Route.QuizEdit(z.id)) }) { Icon(Icons.Outlined.Edit, stringResource(R.string.common_edit)) }
                IconButton(onClick = { deleting = z.id }) { Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.common_delete)) }
            }
        }
    }
    deleting?.let { id ->
        ConfirmDialog(stringResource(R.string.common_delete), stringResource(R.string.common_confirmDelete),
            onConfirm = { vm.deleteQuiz(id); deleting = null }, onDismiss = { deleting = null })
    }
}

/** Google Forms tests and Summaries share one list (web `LINK_CFG`); links open in a Custom Tab. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LinksTab(vm: StudyViewModel, kind: LinkKind, bottom: androidx.compose.ui.unit.Dp, onEdit: (SavedLink?) -> Unit) {
    val list by (if (kind == LinkKind.FORMS) vm.forms else vm.summaries).collectAsStateWithLifecycle()
    val context = LocalContext.current
    val toolbar = MaterialTheme.colorScheme.surface
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    val forms = kind == LinkKind.FORMS
    val icon = if (forms) Icons.Outlined.Language else Icons.AutoMirrored.Outlined.MenuBook
    ListOrState(list, bottom, { it.id }, empty = {
        EmptyState(icon, stringResource(if (forms) R.string.forms_empty else R.string.sum_empty),
            hint = stringResource(if (forms) R.string.forms_emptyHint else R.string.sum_emptyHint),
            action = stringResource(if (forms) R.string.forms_newTest else R.string.sum_newBtn), onAction = { onEdit(null) })
    }) { item ->
        val open = { vm.markOpened(kind, item.id); External.openInTab(context, item.url, toolbar) }
        StudyRow(
            icon, item.title, stringResource(if (forms) R.string.forms_badge else R.string.sum_badge), onClick = open,
            titleExtra = { if (item.pinned) Icon(Icons.Filled.PushPin, null, Modifier.padding(start = 6.dp), tint = UbadThemeExt.colors.ok) },
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 8.dp)) {
                Button(onClick = open) { Text(stringResource(if (forms) R.string.forms_startTest else R.string.sum_open)) }
                IconButton(onClick = { vm.togglePin(kind, item.id) }) {
                    val label = if (item.pinned) (if (forms) R.string.forms_unpin else R.string.sum_unpin) else (if (forms) R.string.forms_pin else R.string.sum_pin)
                    Icon(if (item.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin, stringResource(label))
                }
                IconButton(onClick = { onEdit(item) }) { Icon(Icons.Outlined.Edit, stringResource(R.string.common_edit)) }
                IconButton(onClick = { deleting = item.id }) { Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.common_delete)) }
            }
        }
    }
    deleting?.let { id ->
        ConfirmDialog(stringResource(R.string.common_delete), stringResource(R.string.common_confirmDelete),
            onConfirm = { vm.deleteLink(kind, id); deleting = null }, onDismiss = { deleting = null })
    }
}

/** `openLinkModal(kind, item)` */
@Composable
private fun LinkDialog(kind: LinkKind, item: SavedLink?, onSave: (String, String) -> Unit, onDismiss: () -> Unit) {
    val forms = kind == LinkKind.FORMS
    var title by rememberSaveable { mutableStateOf(item?.title.orEmpty()) }
    var url by rememberSaveable { mutableStateOf(item?.url.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (item != null) (if (forms) R.string.forms_edit else R.string.sum_edit) else (if (forms) R.string.forms_newTest else R.string.sum_newBtn)))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it.take(120) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(if (forms) R.string.forms_testName else R.string.sum_name)) },
                    placeholder = { Text(stringResource(if (forms) R.string.forms_testNamePh else R.string.sum_namePh)) })
                OutlinedTextField(url, { url = it.take(2000) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(if (forms) R.string.forms_url else R.string.sum_url)) },
                    placeholder = { Text(stringResource(if (forms) R.string.forms_urlPh else R.string.sum_urlPh)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
            }
        },
        confirmButton = { TextButton(onClick = { onSave(title, url) }) { Text(stringResource(R.string.common_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}
