package com.ubad.academy.ui.screens.study

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
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
import com.ubad.academy.domain.model.Deck
import com.ubad.academy.domain.model.Flashcard
import com.ubad.academy.ui.components.CollectMessages
import com.ubad.academy.ui.components.ConfirmDialog
import com.ubad.academy.ui.components.EmptyState
import com.ubad.academy.ui.components.LoadingState
import com.ubad.academy.ui.components.MessageQueue
import com.ubad.academy.ui.components.UbadScaffold
import com.ubad.academy.ui.navigation.Route
import com.ubad.academy.ui.theme.UbadThemeExt
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.roundToInt

/** Loaded-or-missing wrapper so "loading" (null) differs from "deck deleted". */
data class Loaded<T>(val value: T?)

private fun IntArray.shuffledInPlace(): IntArray = apply {
    for (i in size - 1 downTo 1) { val j = (Math.random() * (i + 1)).toInt(); val t = this[i]; this[i] = this[j]; this[j] = t }
}

// ─────────────────────────── Deck (study) ───────────────────────────

@HiltViewModel
class DeckViewModel @Inject constructor(
    private val handle: SavedStateHandle,
    private val study: StudyRepository,
    private val feedback: Feedback,
) : ViewModel() {
    val id = handle.toRoute<Route.Deck>().id
    val messages = MessageQueue()
    val deck: StateFlow<Loaded<Deck>?> = study.deck(id).map { Loaded(it) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // Web layer state `p.order` / pos / flipped (kept across rotation/process death).
    val order: StateFlow<IntArray?> = handle.getStateFlow("order", null)
    val pos: StateFlow<Int> = handle.getStateFlow("pos", 0)
    val flipped: StateFlow<Boolean> = handle.getStateFlow("flipped", false)

    /** Reset order to identity when card count changed (web `if(!p.order||p.order.length!==d.cards.length)`). */
    fun ensureOrder(n: Int) {
        if (order.value?.size != n) { handle["order"] = IntArray(n) { it }; if (pos.value >= n) handle["pos"] = 0 }
    }
    fun flip() { handle["flipped"] = !flipped.value }
    fun step(delta: Int) {
        val n = order.value?.size ?: return
        if (n == 0) return
        handle["pos"] = ((pos.value + delta) % n + n) % n; handle["flipped"] = false
    }
    fun shuffle() {
        val n = order.value?.size ?: return
        handle["order"] = (order.value ?: IntArray(n) { it }).copyOf().shuffledInPlace(); handle["pos"] = 0; handle["flipped"] = false
        feedback.click()
    }

    fun saveCard(d: Deck, existing: Flashcard?, front: String, back: String) = viewModelScope.launch {
        val f = front.trim().take(300); val b = back.trim().take(300)
        if (f.isEmpty() || b.isEmpty()) return@launch
        if (existing != null) {
            study.updateDeck(d.copy(cards = d.cards.map { if (it.id == existing.id) it.copy(front = f, back = b) else it }))
        } else {
            val cards = d.cards + Flashcard(Web.uid(), f, b)
            study.updateDeck(d.copy(cards = cards))
            handle["order"] = IntArray(cards.size) { it }; handle["pos"] = cards.size - 1; handle["flipped"] = false
        }
        messages.send(R.string.toast_saved)
    }
    fun deleteCard(d: Deck, index: Int) = viewModelScope.launch {
        val cards = d.cards.filterIndexed { i, _ -> i != index }
        study.updateDeck(d.copy(cards = cards))
        handle["order"] = IntArray(cards.size) { it }; handle["pos"] = 0; handle["flipped"] = false
    }
    fun deleteDeck(then: () -> Unit) = viewModelScope.launch { study.deleteDeck(id); feedback.toast(R.string.toast_deleted); then() }
}

@Composable
fun DeckScreen(onBack: () -> Unit, viewModel: DeckViewModel = hiltViewModel()) {
    val state by viewModel.deck.collectAsStateWithLifecycle()
    val order by viewModel.order.collectAsStateWithLifecycle()
    val pos by viewModel.pos.collectAsStateWithLifecycle()
    val flipped by viewModel.flipped.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    CollectMessages(viewModel.messages.flow, snackbar)
    val deck = state?.value
    var editing by remember { mutableStateOf<Pair<Boolean, Flashcard?>?>(null) } // (open, card)
    var deletingDeck by rememberSaveable { mutableStateOf(false) }
    var deletingCard by rememberSaveable { mutableStateOf<Int?>(null) }

    UbadScaffold(
        title = deck?.title ?: stringResource(R.string.study_tabCards), onBack = onBack, snackbarHostState = snackbar,
        actions = {
            if (deck != null) IconButton(onClick = { deletingDeck = true }) { Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.study_deleteDeck)) }
        },
    ) { pad ->
        when {
            state == null -> LoadingState(Modifier.padding(pad))
            deck == null -> EmptyState(Icons.Outlined.Layers, stringResource(R.string.study_noDecks), Modifier.padding(pad))
            else -> {
                LaunchedEffect(deck.cards.size) { viewModel.ensureOrder(deck.cards.size) }
                LazyColumn(
                    Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding() + 8.dp, bottom = pad.calculateBottomPadding() + 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    item {
                        val o = order
                        if (deck.cards.isEmpty() || o == null || o.size != deck.cards.size) {
                            EmptyState(Icons.Outlined.Layers, stringResource(R.string.study_noCards), hint = stringResource(R.string.study_addFirst))
                        } else {
                            val p = pos.coerceIn(0, o.size - 1)
                            val c = deck.cards[o[p]]
                            Column(Modifier.widthIn(max = 640.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                FlipCard(c.front, c.back, flipped, onFlip = viewModel::flip, onSwipe = viewModel::step)
                                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    IconButton(onClick = { viewModel.step(-1) }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, stringResource(R.string.study_prev)) }
                                    Text("${p + 1} / ${deck.cards.size}", style = MaterialTheme.typography.labelLarge, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                                    OutlinedButton(onClick = viewModel::flip) { Text(stringResource(R.string.study_flip)) }
                                    IconButton(onClick = viewModel::shuffle) { Icon(Icons.Outlined.Shuffle, stringResource(R.string.study_shuffle)) }
                                    IconButton(onClick = { viewModel.step(1) }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, stringResource(R.string.study_next)) }
                                }
                            }
                        }
                    }
                    item {
                        Row(Modifier.fillMaxWidth().padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${stringResource(R.string.study_tabCards)} · ${deck.cards.size}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Button(onClick = { editing = true to null }) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.padding(3.dp)); Text(stringResource(R.string.study_newCard)) }
                        }
                    }
                    itemsIndexed(deck.cards, key = { _, c -> c.id }) { i, c ->
                        StudyRow(Icons.Outlined.Layers, c.front, c.back, onClick = { editing = true to c }) {
                            IconButton(onClick = { editing = true to c }) { Icon(Icons.Outlined.Edit, stringResource(R.string.common_edit)) }
                            IconButton(onClick = { deletingCard = i }) { Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.common_delete)) }
                        }
                    }
                }
            }
        }
    }

    if (deck != null) {
        editing?.let { (_, card) ->
            CardDialog(card, onSave = { f, b -> viewModel.saveCard(deck, card, f, b); editing = null }, onDismiss = { editing = null })
        }
        deletingCard?.let { i ->
            ConfirmDialog(
                title = stringResource(R.string.common_delete), message = stringResource(R.string.common_confirmDelete),
                onConfirm = { deletingCard = null; viewModel.deleteCard(deck, i) }, onDismiss = { deletingCard = null },
            )
        }
        if (deletingDeck) ConfirmDialog(
            title = stringResource(R.string.study_deleteDeck), message = stringResource(R.string.study_deleteDeckMsg),
            onConfirm = { deletingDeck = false; viewModel.deleteDeck(onBack) }, onDismiss = { deletingDeck = false },
        )
    }
}

/** Web `.flip3d` card: tap flips with a 3D turn; horizontal swipe steps (RTL-aware like the web). */
@Composable
fun FlipCard(front: String, back: String, flipped: Boolean, onFlip: () -> Unit, onSwipe: ((Int) -> Unit)? = null) {
    // System "remove animations" (animator scale 0) makes this instant automatically.
    val rot by animateFloatAsState(if (flipped) 180f else 0f, tween(450), label = "flip")
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val showBack = rot > 90f
    val flipLabel = stringResource(R.string.study_flip)
    Card(
        Modifier.fillMaxWidth().heightIn(min = 220.dp)
            .clickable(role = Role.Button, onClickLabel = flipLabel, onClick = onFlip)
            .then(if (onSwipe == null) Modifier else Modifier.pointerInput(rtl) {
                val threshold = 60.dp.toPx()
                var drag = 0f
                detectHorizontalDragGestures(
                    onDragStart = { drag = 0f },
                    onDragEnd = {
                        if (abs(drag) > threshold) {
                            val next = if (rtl) drag < -threshold else drag > threshold
                            onSwipe(if (next) 1 else -1)
                        }
                    },
                ) { _, dx -> drag += dx }
            })
            // Rotation last, so touch input above isn't mirrored while the back is showing.
            .graphicsLayer { rotationY = rot; cameraDistance = 12f * density },
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = if (showBack) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        border = BorderStroke(1.dp, UbadThemeExt.colors.line),
    ) {
        Box(Modifier.fillMaxWidth().heightIn(min = 220.dp).graphicsLayer { if (showBack) rotationY = 180f }.padding(24.dp), contentAlignment = Alignment.Center) {
            Text(
                if (showBack) back else front, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center,
                color = if (showBack) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** `openCardModal` */
@Composable
private fun CardDialog(card: Flashcard?, onSave: (String, String) -> Unit, onDismiss: () -> Unit) {
    var front by rememberSaveable { mutableStateOf(card?.front.orEmpty()) }
    var back by rememberSaveable { mutableStateOf(card?.back.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (card != null) R.string.study_editCard else R.string.study_newCard)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(front, { front = it.take(300) }, Modifier.fillMaxWidth(), minLines = 2,
                    label = { Text(stringResource(R.string.study_front)) }, placeholder = { Text(stringResource(R.string.study_frontPh)) })
                OutlinedTextField(back, { back = it.take(300) }, Modifier.fillMaxWidth(), minLines = 2,
                    label = { Text(stringResource(R.string.study_back)) }, placeholder = { Text(stringResource(R.string.study_backPh)) })
            }
        },
        confirmButton = { TextButton(enabled = front.isNotBlank() && back.isNotBlank(), onClick = { onSave(front, back) }) { Text(stringResource(R.string.common_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

// ─────────────────────────── Deck self-test ───────────────────────────

@HiltViewModel
class DeckTestViewModel @Inject constructor(
    private val handle: SavedStateHandle,
    study: StudyRepository,
    private val feedback: Feedback,
) : ViewModel() {
    private val id = handle.toRoute<Route.DeckTest>().id
    val deck: StateFlow<Loaded<Deck>?> = study.deck(id).map { Loaded(it) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val order: StateFlow<IntArray?> = handle.getStateFlow("order", null)
    val pos: StateFlow<Int> = handle.getStateFlow("pos", 0)
    val known: StateFlow<Int> = handle.getStateFlow("known", 0)
    val answered: StateFlow<Boolean> = handle.getStateFlow("answered", false)
    val flipped: StateFlow<Boolean> = handle.getStateFlow("flipped", false)
    val finished: StateFlow<Boolean> = handle.getStateFlow("finished", false)

    fun ensureOrder(n: Int) { if (order.value?.size != n) restart(n) }
    fun restart(n: Int) {
        handle["order"] = IntArray(n) { it }.shuffledInPlace()
        handle["pos"] = 0; handle["known"] = 0; handle["answered"] = false; handle["flipped"] = false; handle["finished"] = false
    }
    fun flip() { handle["flipped"] = !flipped.value }
    fun answer(isKnown: Boolean) {
        if (answered.value) return
        handle["answered"] = true
        if (isKnown) handle["known"] = known.value + 1
        feedback.click()
        viewModelScope.launch {
            delay(160)
            val n = order.value?.size ?: 0
            if (pos.value < n - 1) { handle["pos"] = pos.value + 1; handle["answered"] = false; handle["flipped"] = false }
            else handle["finished"] = true
        }
    }
}

@Composable
fun DeckTestScreen(onBack: () -> Unit, viewModel: DeckTestViewModel = hiltViewModel()) {
    val state by viewModel.deck.collectAsStateWithLifecycle()
    val order by viewModel.order.collectAsStateWithLifecycle()
    val pos by viewModel.pos.collectAsStateWithLifecycle()
    val known by viewModel.known.collectAsStateWithLifecycle()
    val answered by viewModel.answered.collectAsStateWithLifecycle()
    val flipped by viewModel.flipped.collectAsStateWithLifecycle()
    val finished by viewModel.finished.collectAsStateWithLifecycle()
    val deck = state?.value

    UbadScaffold(title = stringResource(R.string.study_selfTest), onBack = onBack) { pad ->
        when {
            state == null -> LoadingState(Modifier.padding(pad))
            deck == null || deck.cards.isEmpty() -> EmptyState(Icons.Outlined.Layers, stringResource(R.string.study_emptyTest), Modifier.padding(pad))
            else -> {
                LaunchedEffect(deck.cards.size) { viewModel.ensureOrder(deck.cards.size) }
                val o = order?.takeIf { it.size == deck.cards.size } ?: return@UbadScaffold
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(pad).padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Column(Modifier.widthIn(max = 640.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (finished) {
                            val score = (known.toDouble() / o.size * 100).roundToInt()
                            val msg = when { score <= 50 -> R.string.study_testLow; score < 80 -> R.string.study_testMid; else -> R.string.study_testHigh }
                            TestResult(stringResource(R.string.study_testResult), "$score%", stringResource(msg)) {
                                Button(onClick = { viewModel.restart(deck.cards.size) }) { Text(stringResource(R.string.study_retry)) }
                                OutlinedButton(onClick = onBack) { Text(stringResource(R.string.common_back)) }
                            }
                        } else {
                            val p = pos.coerceIn(0, o.size - 1)
                            val c = deck.cards[o[p]]
                            ProgressHeader(p.toFloat() / o.size, "${p + 1} / ${o.size}")
                            Spacer(Modifier.height(14.dp))
                            FlipCard(c.front, c.back, flipped, onFlip = viewModel::flip)
                            Text(stringResource(R.string.study_flip), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(onClick = { viewModel.answer(true) }, enabled = !answered,
                                    colors = ButtonDefaults.buttonColors(containerColor = UbadThemeExt.colors.ok)) { Text("🟢 ${stringResource(R.string.study_known)}") }
                                Button(onClick = { viewModel.answer(false) }, enabled = !answered,
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)) {
                                    Text("🔴 ${stringResource(R.string.study_unknown)}")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProgressHeader(fraction: Float, label: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.weight(1f))
        Text(label, style = MaterialTheme.typography.labelLarge, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Clip)
    }
}

@Composable
fun TestResult(eyebrow: String, score: String, message: String, actions: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), border = BorderStroke(1.dp, UbadThemeExt.colors.line),
    ) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(eyebrow, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(score, style = MaterialTheme.typography.displayMedium, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            Text(message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) { actions() }
        }
    }
}
