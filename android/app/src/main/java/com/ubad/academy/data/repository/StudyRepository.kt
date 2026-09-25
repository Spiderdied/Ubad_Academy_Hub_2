package com.ubad.academy.data.repository

import com.ubad.academy.core.Web
import com.ubad.academy.data.local.db.UbadDatabase
import com.ubad.academy.data.local.db.toDomain
import com.ubad.academy.data.local.db.toEntity
import com.ubad.academy.domain.model.Deck
import com.ubad.academy.domain.model.Flashcard
import com.ubad.academy.domain.model.LinkKind
import com.ubad.academy.domain.model.Quiz
import com.ubad.academy.domain.model.QuizQuestion
import com.ubad.academy.domain.model.SavedLink
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Flashcard decks, quizzes, Google-Form tests and Summary links. */
@Singleton
class StudyRepository @Inject constructor(private val db: UbadDatabase) {
    private val dao get() = db.study()

    val decks: Flow<List<Deck>> = dao.observeDecks().map { l -> l.map { it.toDomain() } }
    val quizzes: Flow<List<Quiz>> = dao.observeQuizzes().map { l -> l.map { it.toDomain() } }
    fun links(kind: LinkKind): Flow<List<SavedLink>> = dao.observeLinks(kind.key).map { l -> l.map { it.toDomain() } }

    fun deck(id: String): Flow<Deck?> = decks.map { l -> l.firstOrNull { it.id == id } }
    fun quiz(id: String): Flow<Quiz?> = quizzes.map { l -> l.firstOrNull { it.id == id } }

    // ── decks ──
    suspend fun createDeck(title: String): String {
        val d = Deck(Web.uid(), title.trim().take(80), System.currentTimeMillis())
        dao.upsertDeck(d.toEntity(dao.nextDeckPos()))
        return d.id
    }

    suspend fun updateDeck(deck: Deck) {
        val pos = dao.decks().firstOrNull { it.id == deck.id }?.position ?: dao.nextDeckPos()
        val clean = deck.copy(
            title = deck.title.trim().take(80),
            cards = deck.cards.map { it.copy(front = it.front.take(300), back = it.back.take(300)) },
        )
        dao.upsertDeck(clean.toEntity(pos))
    }

    suspend fun addCard(deck: Deck, front: String, back: String) =
        updateDeck(deck.copy(cards = deck.cards + Flashcard(Web.uid(), front.trim(), back.trim())))

    suspend fun deleteDeck(id: String) = dao.deleteDeck(id)

    // ── quizzes ──
    /** Saves with the same validity filter the web normalizer applies on load. */
    suspend fun saveQuiz(quiz: Quiz): String {
        val clean = quiz.copy(
            title = quiz.title.trim().take(80),
            questions = quiz.questions.map { q ->
                QuizQuestion(q.q.trim().take(400), q.options.take(4).map { it.trim().take(160) }, q.correct.coerceIn(0, 3))
            }.filter { isValid(it) },
        )
        val pos = dao.quizzes().firstOrNull { it.id == quiz.id }?.position ?: dao.nextQuizPos()
        dao.upsertQuiz(clean.toEntity(pos))
        return clean.id
    }

    suspend fun deleteQuiz(id: String) = dao.deleteQuiz(id)

    // ── links (forms / summaries) ──
    /** openLinkModal save. Returns false when the URL is not a valid http(s) link. */
    suspend fun saveLink(kind: LinkKind, existing: SavedLink?, title: String, rawUrl: String): Boolean {
        val url = Web.safeHttpUrl(rawUrl) ?: return false
        val t = title.trim().take(120)
        val all = dao.links(kind.key)
        if (existing != null) {
            val pos = all.firstOrNull { it.id == existing.id }?.position ?: dao.nextLinkPos(kind.key)
            dao.upsertLink(existing.copy(title = t, url = url).toEntity(kind, pos))
        } else {
            dao.upsertLink(SavedLink(Web.uid(), t, url, System.currentTimeMillis()).toEntity(kind, dao.nextLinkPos(kind.key)))
        }
        return true
    }

    suspend fun togglePin(kind: LinkKind, id: String) = mutateLink(kind, id) { it.copy(pinned = !it.pinned) }
    suspend fun markOpened(kind: LinkKind, id: String) = mutateLink(kind, id) { it.copy(lastOpened = System.currentTimeMillis()) }
    suspend fun deleteLink(kind: LinkKind, id: String) = dao.deleteLink(kind.key, id)

    private suspend fun mutateLink(kind: LinkKind, id: String, f: (SavedLink) -> SavedLink) {
        val e = dao.links(kind.key).firstOrNull { it.id == id } ?: return
        dao.upsertLink(f(e.toDomain()).toEntity(kind, e.position))
    }

    companion object {
        fun isValid(q: QuizQuestion) =
            q.q.isNotEmpty() && q.options.count { it.isNotEmpty() } >= 2 && q.options.getOrNull(q.correct).orEmpty().isNotEmpty()

        /** Web list order: pinned first, then newest. */
        fun sortLinks(l: List<SavedLink>) = l.sortedWith(compareByDescending<SavedLink> { it.pinned }.thenByDescending { it.createdAt })
    }
}
