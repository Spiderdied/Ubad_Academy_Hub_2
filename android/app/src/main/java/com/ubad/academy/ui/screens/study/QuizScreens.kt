package com.ubad.academy.ui.screens.study

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.ubad.academy.R
import com.ubad.academy.core.Feedback
import com.ubad.academy.core.Web
import com.ubad.academy.data.repository.StudyRepository
import com.ubad.academy.domain.model.Quiz
import com.ubad.academy.domain.model.QuizQuestion
import com.ubad.academy.ui.components.CollectMessages
import com.ubad.academy.ui.components.ConfirmDialog
import com.ubad.academy.ui.components.EmptyState
import com.ubad.academy.ui.components.LoadingState
import com.ubad.academy.ui.components.MessageQueue
import com.ubad.academy.ui.components.UbadScaffold
import com.ubad.academy.ui.navigation.Route
import com.ubad.academy.ui.theme.UbadThemeExt
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import kotlin.math.roundToInt

// ─────────────────────────── Quiz editor ───────────────────────────

/** Editable quiz document (web `doc`) + dirty flag; saved in SavedStateHandle as JSON. */
@Serializable
data class QuizDraft(val title: String, val questions: List<QuizQuestion>, val dirty: Boolean = false)

private fun emptyQuestion() = QuizQuestion("", listOf("", "", "", ""), 0)

@HiltViewModel
class QuizEditViewModel @Inject constructor(
    private val handle: SavedStateHandle,
    private val study: StudyRepository,
    private val feedback: Feedback,
) : ViewModel() {
    private val id = handle.toRoute<Route.QuizEdit>().id
    val isNew = id == null
    val messages = MessageQueue()
    private var existing: Quiz? = null

    private val _draft = MutableStateFlow(handle.get<String>("draft")?.let { runCatching { Json.decodeFromString<QuizDraft>(it) }.getOrNull() })
    val draft: StateFlow<QuizDraft?> = _draft

    init {
        viewModelScope.launch {
            existing = id?.let { study.quiz(it).first() }
            if (_draft.value == null) {
                val ex = existing
                set(if (ex != null) QuizDraft(ex.title, ex.questions.map { it.copy(options = it.options.toList()) }) else QuizDraft("", listOf(emptyQuestion())))
            }
        }
    }

    private fun set(d: QuizDraft) { _draft.value = d; handle["draft"] = Json.encodeToString(d) }
    private fun edit(f: (QuizDraft) -> QuizDraft) { _draft.value?.let { set(f(it).copy(dirty = true)) } }
    private fun editQ(i: Int, f: (QuizQuestion) -> QuizQuestion) = edit { d -> d.copy(questions = d.questions.mapIndexed { k, q -> if (k == i) f(q) else q }) }

    fun setTitle(v: String) = edit { it.copy(title = v) }
    fun setQuestion(i: Int, v: String) = editQ(i) { it.copy(q = v) }
    fun setOption(i: Int, j: Int, v: String) = editQ(i) { q -> q.copy(options = q.options.mapIndexed { k, o -> if (k == j) v else o }) }
    fun setCorrect(i: Int, j: Int) = editQ(i) { it.copy(correct = j) }
    fun addQuestion() { edit { it.copy(questions = it.questions + emptyQuestion()) }; feedback.click() }
    fun removeQuestion(i: Int) {
        val d = _draft.value ?: return
        if (d.questions.size == 1) { messages.send(R.string.study_minQ, error = true); return }
        edit { x -> x.copy(questions = x.questions.filterIndexed { k, _ -> k != i }) }
    }

    fun save(then: () -> Unit) = viewModelScope.launch {
        val d = _draft.value ?: return@launch
        val title = d.title.trim()
        if (title.isEmpty()) { messages.send(R.string.study_needTitle, error = true); return@launch }
        d.questions.forEachIndexed { i, q ->
            val filled = q.options.count { it.isNotBlank() }
            if (q.q.isBlank() || filled < 2 || q.options.getOrNull(q.correct).isNullOrBlank()) {
                messages.send(R.string.study_needQ, i + 1, error = true); return@launch
            }
        }
        val ex = existing
        study.saveQuiz(Quiz(ex?.id ?: Web.uid(), title, ex?.createdAt ?: System.currentTimeMillis(), d.questions))
        set(d.copy(dirty = false))
        feedback.toast(R.string.toast_saved)
        then()
    }
}

@Composable
fun QuizEditScreen(onBack: () -> Unit, viewModel: QuizEditViewModel = hiltViewModel()) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    CollectMessages(viewModel.messages.flow, snackbar)
    var askDiscard by rememberSaveable { mutableStateOf(false) }
    val dirty = draft?.dirty == true
    BackHandler(enabled = dirty) { askDiscard = true }

    UbadScaffold(
        title = stringResource(if (viewModel.isNew) R.string.study_newQuiz else R.string.study_editQuiz),
        onBack = { if (dirty) askDiscard = true else onBack() }, snackbarHostState = snackbar,
        actions = {
            if (draft != null) Button(onClick = { viewModel.save(onBack) }, modifier = Modifier.padding(end = 8.dp)) {
                Icon(Icons.Outlined.Check, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.common_save))
            }
        },
    ) { pad ->
        val d = draft ?: return@UbadScaffold LoadingState(Modifier.padding(pad))
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding() + 8.dp, bottom = pad.calculateBottomPadding() + 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                OutlinedTextField(d.title, { viewModel.setTitle(it.take(80)) }, Modifier.widthIn(max = 640.dp).fillMaxWidth(), singleLine = true,
                    label = { Text(stringResource(R.string.study_quizName)) })
            }
            itemsIndexed(d.questions) { i, q -> QuestionCard(i, q, viewModel) }
            item {
                OutlinedButton(onClick = viewModel::addQuestion, modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
                    Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.study_addQuestion))
                }
            }
        }
    }

    if (askDiscard) ConfirmDialog(
        title = stringResource(R.string.notes_discard), message = stringResource(R.string.notes_discardMsg),
        confirmLabel = stringResource(R.string.notes_discardBtn),
        onConfirm = { askDiscard = false; onBack() }, onDismiss = { askDiscard = false },
    )
}

@Composable
private fun QuestionCard(i: Int, q: QuizQuestion, vm: QuizEditViewModel) {
    Card(
        Modifier.widthIn(max = 640.dp).fillMaxWidth(), shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), border = BorderStroke(1.dp, UbadThemeExt.colors.line),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${stringResource(R.string.study_question)} ${i + 1}", style = MaterialTheme.typography.titleSmall, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                IconButton(onClick = { vm.removeQuestion(i) }) { Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.study_removeQ)) }
            }
            OutlinedTextField(q.q, { vm.setQuestion(i, it.take(400)) }, Modifier.fillMaxWidth(), minLines = 2,
                placeholder = { Text(stringResource(R.string.study_questionPh)) })
            val correctLabel = stringResource(R.string.study_correctOpt)
            q.options.forEachIndexed { j, o ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = q.correct == j, onClick = { vm.setCorrect(i, j) },
                        modifier = Modifier.semantics { contentDescription = correctLabel })
                    OutlinedTextField(o, { vm.setOption(i, j, it.take(160)) }, Modifier.weight(1f), singleLine = true,
                        placeholder = { Text(stringResource(R.string.study_option, j + 1)) })
                }
            }
        }
    }
}

// ─────────────────────────── Quiz play ───────────────────────────

@HiltViewModel
class QuizPlayViewModel @Inject constructor(
    private val handle: SavedStateHandle,
    study: StudyRepository,
    private val feedback: Feedback,
) : ViewModel() {
    private val id = handle.toRoute<Route.QuizPlay>().id
    val quiz: StateFlow<Loaded<Quiz>?> = study.quiz(id).map { Loaded(it) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val index: StateFlow<Int> = handle.getStateFlow("i", 0)
    val answers: StateFlow<IntArray?> = handle.getStateFlow("answers", null)
    val finished: StateFlow<Boolean> = handle.getStateFlow("finished", false)

    fun ensure(n: Int) { if (answers.value?.size != n) retry(n) }
    fun choose(j: Int) { val a = answers.value?.copyOf() ?: return; a[index.value] = j; handle["answers"] = a; feedback.click() }
    fun next(q: Quiz) {
        val a = answers.value ?: return
        if (a[index.value] < 0) return
        if (index.value + 1 < q.questions.size) handle["i"] = index.value + 1
        else {
            handle["finished"] = true
            val correct = q.questions.indices.count { a[it] == q.questions[it].correct }
            if ((correct.toDouble() / q.questions.size * 100).roundToInt() >= 80) feedback.celebrate()
        }
    }
    fun retry(n: Int) { handle["i"] = 0; handle["answers"] = IntArray(n) { -1 }; handle["finished"] = false }
}

@Composable
fun QuizPlayScreen(onBack: () -> Unit, viewModel: QuizPlayViewModel = hiltViewModel()) {
    val state by viewModel.quiz.collectAsStateWithLifecycle()
    val index by viewModel.index.collectAsStateWithLifecycle()
    val answersState by viewModel.answers.collectAsStateWithLifecycle()
    val finished by viewModel.finished.collectAsStateWithLifecycle()
    val quiz = state?.value

    UbadScaffold(title = quiz?.title ?: stringResource(R.string.study_tabQuiz), onBack = onBack) { pad ->
        when {
            state == null -> LoadingState(Modifier.padding(pad))
            quiz == null || quiz.questions.isEmpty() -> EmptyState(Icons.Outlined.CheckCircle, stringResource(R.string.study_noQuizzes), Modifier.padding(pad))
            else -> {
                LaunchedEffect(quiz.questions.size) { viewModel.ensure(quiz.questions.size) }
                val answers = answersState?.takeIf { it.size == quiz.questions.size } ?: return@UbadScaffold
                val n = quiz.questions.size
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding() + 8.dp, bottom = pad.calculateBottomPadding() + 32.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (!finished) {
                        val i = index.coerceIn(0, n - 1)
                        val q = quiz.questions[i]
                        item { Box(Modifier.widthIn(max = 640.dp)) { ProgressHeader(i.toFloat() / n, stringResource(R.string.study_qOf, i + 1, n)) } }
                        item {
                            Card(Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(top = 4.dp), shape = MaterialTheme.shapes.large,
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), border = BorderStroke(1.dp, UbadThemeExt.colors.line)) {
                                Text(q.q, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(18.dp))
                            }
                        }
                        q.options.forEachIndexed { j, o ->
                            if (o.isNotEmpty()) item(key = "o$i-$j") { OptionRow(('A' + j).toString(), o, answers[i] == j) { viewModel.choose(j) } }
                        }
                        item {
                            Row(Modifier.widthIn(max = 640.dp).fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                Button(onClick = { viewModel.next(quiz) }, enabled = answers[i] >= 0) {
                                    Text(stringResource(if (i == n - 1) R.string.study_finish else R.string.study_nextQ))
                                    Spacer(Modifier.width(6.dp)); Icon(Icons.AutoMirrored.Outlined.ArrowForward, null)
                                }
                            }
                        }
                    } else {
                        val correct = quiz.questions.indices.count { answers[it] == quiz.questions[it].correct }
                        val pct = (correct.toDouble() / n * 100).roundToInt()
                        item {
                            Card(Modifier.widthIn(max = 640.dp).fillMaxWidth(), shape = MaterialTheme.shapes.large,
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("$pct%", style = MaterialTheme.typography.displayMedium, fontFamily = FontFamily.Monospace)
                                    Text("${stringResource(R.string.study_score)} · $correct / $n", style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                        item { Text(stringResource(R.string.study_review), style = MaterialTheme.typography.titleMedium, modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(top = 10.dp)) }
                        itemsIndexed(quiz.questions) { k, q -> ReviewCard(q, answers[k]) }
                        item {
                            Row(Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
                                OutlinedButton(onClick = onBack) { Text(stringResource(R.string.common_done)) }
                                Button(onClick = { viewModel.retry(n) }) { Icon(Icons.Outlined.Refresh, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.study_retry)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionRow(key: String, text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        Modifier.widthIn(max = 640.dp).fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else UbadThemeExt.colors.line),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.size(30.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(key, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun ReviewCard(q: QuizQuestion, answer: Int) {
    val ok = answer == q.correct
    val colors = UbadThemeExt.colors
    val tone = if (ok) colors.ok else colors.danger
    Card(
        Modifier.widthIn(max = 640.dp).fillMaxWidth(), shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), border = BorderStroke(1.dp, tone.copy(alpha = 0.6f)),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(q.q, style = MaterialTheme.typography.titleSmall)
            AnswerLine(stringResource(R.string.study_your), q.options.getOrNull(answer)?.takeIf { it.isNotEmpty() } ?: "—", tone)
            if (!ok) AnswerLine(stringResource(R.string.study_correctAns), q.options.getOrNull(q.correct).orEmpty(), colors.ok)
        }
    }
}

@Composable
private fun AnswerLine(label: String, value: String, tone: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$label: ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(shape = MaterialTheme.shapes.small, color = tone.copy(alpha = 0.18f)) {
            Text(value, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
        }
    }
}

