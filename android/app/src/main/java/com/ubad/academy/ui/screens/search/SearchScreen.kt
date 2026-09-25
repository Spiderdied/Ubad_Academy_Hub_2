package com.ubad.academy.ui.screens.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ubad.academy.R
import com.ubad.academy.core.Dates
import com.ubad.academy.core.External
import com.ubad.academy.core.Web
import com.ubad.academy.core.currentLocale
import com.ubad.academy.data.repository.CourseRepository
import com.ubad.academy.data.repository.NoteRepository
import com.ubad.academy.data.repository.PlannerRepository
import com.ubad.academy.data.repository.StudyRepository
import com.ubad.academy.domain.model.LinkKind
import com.ubad.academy.ui.components.EmptyState
import com.ubad.academy.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One result row; [target] is either an in-app route or an external link to open. */
data class SearchHit(val key: String, val icon: Group, val title: String, val sub: String, val route: Route? = null, val link: Pair<LinkKind, String>? = null, val url: String = "")

enum class Group(val label: Int) {
    NOTES(R.string.search_notes), COURSES(R.string.search_courses), EVENTS(R.string.search_events), DECKS(R.string.search_decks),
    QUIZZES(R.string.search_quizzes), FORMS(R.string.search_forms), SUMMARIES(R.string.search_summaries),
}

/** Plain data the results are built from; kept separate so the matching is testable. */
data class SearchSources(
    val notes: List<com.ubad.academy.domain.model.Note>,
    val courses: List<com.ubad.academy.domain.model.Course>,
    val events: List<com.ubad.academy.domain.model.CalendarEvent>,
    val decks: List<com.ubad.academy.domain.model.Deck>,
    val quizzes: List<com.ubad.academy.domain.model.Quiz>,
    val forms: List<com.ubad.academy.domain.model.SavedLink>,
    val summaries: List<com.ubad.academy.domain.model.SavedLink>,
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    notes: NoteRepository,
    courses: CourseRepository,
    planner: PlannerRepository,
    private val study: StudyRepository,
) : ViewModel() {
    private val _q = MutableStateFlow("")
    val q = _q.asStateFlow()
    fun setQuery(v: String) { _q.value = v }

    private val sources = combine(
        notes.notes, courses.courses, planner.events, study.decks, study.quizzes,
    ) { n, c, e, d, z -> arrayOf<Any>(n, c, e, d, z) }
        .combine(combine(study.links(LinkKind.FORMS), study.links(LinkKind.SUMMARIES)) { f, s -> f to s }) { a, (f, s) ->
            @Suppress("UNCHECKED_CAST")
            SearchSources(
                a[0] as List<com.ubad.academy.domain.model.Note>, a[1] as List<com.ubad.academy.domain.model.Course>,
                a[2] as List<com.ubad.academy.domain.model.CalendarEvent>, a[3] as List<com.ubad.academy.domain.model.Deck>,
                a[4] as List<com.ubad.academy.domain.model.Quiz>, f, s,
            )
        }

    val results: StateFlow<SearchSources?> = sources.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun markOpened(kind: LinkKind, id: String) = viewModelScope.launch { study.markOpened(kind, id) }
}

/** Web `buildResults(q)` — same fields, groups and per-group limits. */
object SearchMatcher {
    data class Labels(val untitled: String, val cards: String, val questions: String, val formsBadge: String, val sumBadge: String, val eventDate: (String) -> String)

    fun build(q: String, s: SearchSources, l: Labels): List<SearchHit> {
        val n = q.trim().lowercase()
        if (n.isEmpty()) return emptyList()
        val out = mutableListOf<SearchHit>()
        s.notes.filter { (it.title + " " + it.body + " " + it.tags.joinToString(" ")).lowercase().contains(n) }.take(5)
            .forEach { out += SearchHit("n" + it.id, Group.NOTES, it.title.ifEmpty { l.untitled }, it.body.take(60), Route.NoteEditor(it.id)) }
        s.courses.filter { (it.name + " " + it.code + " " + it.instructor).lowercase().contains(n) }.take(5)
            .forEach { out += SearchHit("c" + it.id, Group.COURSES, it.name, listOf(it.code, it.instructor).filter(String::isNotEmpty).joinToString(" · "), Route.CourseDetail(it.id)) }
        s.events.filter { (it.title + " " + it.desc).lowercase().contains(n) }.take(5)
            .forEach { out += SearchHit("e" + it.id, Group.EVENTS, it.title, l.eventDate(it.date), Route.Calendar(it.date)) }
        s.decks.filter { d -> (d.title + " " + d.cards.joinToString(" ") { it.front + " " + it.back }).lowercase().contains(n) }.take(4)
            .forEach { out += SearchHit("d" + it.id, Group.DECKS, it.title, "${it.cards.size} ${l.cards}", Route.Deck(it.id)) }
        s.quizzes.filter { z -> (z.title + " " + z.questions.joinToString(" ") { it.q }).lowercase().contains(n) }.take(4)
            .forEach { out += SearchHit("z" + it.id, Group.QUIZZES, it.title, "${it.questions.size} ${l.questions}", Route.QuizPlay(it.id)) }
        s.forms.filter { it.title.lowercase().contains(n) }.take(4)
            .forEach { out += SearchHit("f" + it.id, Group.FORMS, it.title, l.formsBadge, link = LinkKind.FORMS to it.id, url = it.url) }
        s.summaries.filter { it.title.lowercase().contains(n) }.take(4)
            .forEach { out += SearchHit("s" + it.id, Group.SUMMARIES, it.title, l.sumBadge, link = LinkKind.SUMMARIES to it.id, url = it.url) }
        return out
    }
}

private fun Group.icon(): ImageVector = when (this) {
    Group.NOTES -> Icons.Outlined.Description
    Group.COURSES -> Icons.AutoMirrored.Outlined.MenuBook
    Group.EVENTS -> Icons.Outlined.CalendarMonth
    Group.DECKS -> Icons.Outlined.Layers
    Group.QUIZZES -> Icons.Outlined.CheckCircle
    Group.FORMS -> Icons.Outlined.Language
    Group.SUMMARIES -> Icons.AutoMirrored.Outlined.MenuBook
}

/** Global search (web `openSearch` overlay) as a native full-screen layer. */
@Composable
fun SearchScreen(onNavigate: (Route) -> Unit, onBack: () -> Unit, viewModel: SearchViewModel = hiltViewModel()) {
    val q by viewModel.q.collectAsStateWithLifecycle()
    val sources by viewModel.results.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val locale = currentLocale()
    val toolbar = MaterialTheme.colorScheme.surface
    val labels = SearchMatcher.Labels(
        untitled = stringResource(R.string.notes_untitled), cards = stringResource(R.string.study_cardsLc),
        questions = stringResource(R.string.study_questionsLc), formsBadge = stringResource(R.string.forms_badge),
        sumBadge = stringResource(R.string.sum_badge),
        eventDate = { d -> if (Web.isYmd(d)) Dates.dayMonth(Web.parseYmd(d), locale) else d },
    )
    val hits = remember(q, sources, locale) { sources?.let { SearchMatcher.build(q, it, labels) }.orEmpty() }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    fun open(h: SearchHit) {
        h.route?.let { onNavigate(it); return }
        h.link?.let { (kind, id) -> viewModel.markOpened(kind, id); External.openInTab(context, h.url, toolbar) }
    }

    Box(Modifier.fillMaxSize().statusBarsPadding().imePadding(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(Modifier.widthIn(max = 840.dp).fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                OutlinedTextField(
                    value = q, onValueChange = viewModel::setQuery, singleLine = true,
                    placeholder = { Text(stringResource(R.string.search_ph)) },
                    leadingIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back)) } },
                    trailingIcon = {
                        if (q.isNotEmpty()) IconButton(onClick = { viewModel.setQuery("") }) { Icon(Icons.Outlined.Clear, stringResource(R.string.common_close)) }
                        else Icon(Icons.Outlined.Search, null)
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { hits.firstOrNull()?.let(::open) }),
                    modifier = Modifier.fillMaxWidth().padding(12.dp).focusRequester(focus),
                )
            }
            if (q.isNotBlank() && sources != null && hits.isEmpty()) item {
                EmptyState(Icons.Outlined.Search, stringResource(R.string.search_none, q.trim()), compact = true)
            }
            var last: Group? = null
            hits.forEach { h ->
                if (h.icon != last) {
                    last = h.icon
                    item(key = "g" + h.icon.name) {
                        Text(
                            stringResource(h.icon.label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp).semantics { heading() },
                        )
                    }
                }
                item(key = h.key) {
                    ListItem(
                        headlineContent = { Text(h.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = if (h.sub.isNotEmpty()) ({ Text(h.sub, maxLines = 1, overflow = TextOverflow.Ellipsis) }) else null,
                        leadingContent = { Icon(h.icon.icon(), null) },
                        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { open(h) },
                    )
                }
            }
        }
    }
}
