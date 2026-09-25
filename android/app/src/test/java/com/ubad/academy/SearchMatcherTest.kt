package com.ubad.academy

import com.ubad.academy.domain.model.CalendarEvent
import com.ubad.academy.domain.model.Course
import com.ubad.academy.domain.model.Deck
import com.ubad.academy.domain.model.Flashcard
import com.ubad.academy.domain.model.Note
import com.ubad.academy.domain.model.SavedLink
import com.ubad.academy.ui.navigation.Route
import com.ubad.academy.ui.screens.search.Group
import com.ubad.academy.ui.screens.search.SearchMatcher
import com.ubad.academy.ui.screens.search.SearchSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchMatcherTest {
    private val labels = SearchMatcher.Labels("Untitled", "cards", "questions", "Test", "Summary") { it }

    private val sources = SearchSources(
        notes = (1..7).map { Note("n$it", title = if (it == 1) "" else "Physics $it", body = "about physics", createdAt = 0, updatedAt = 0) },
        courses = listOf(Course("c1", "Calculus", code = "MATH101", instructor = "Dr. Physics", createdAt = 0)),
        events = listOf(CalendarEvent("e1", "Exam", date = "2026-10-01", desc = "physics exam", createdAt = 0)),
        decks = listOf(Deck("d1", "Vocab", 0, listOf(Flashcard("f", "physics", "فيزياء")))),
        quizzes = emptyList(),
        forms = listOf(SavedLink("l1", "Physics form", "https://forms.gle/x", 0)),
        summaries = emptyList(),
    )

    @Test fun emptyQueryGivesNothing() = assertTrue(SearchMatcher.build("  ", sources, labels).isEmpty())

    @Test fun groupsLimitsAndTargetsMatchWeb() {
        val hits = SearchMatcher.build("PHYSICS", sources, labels)
        assertEquals(5, hits.count { it.icon == Group.NOTES })
        assertEquals("Untitled", hits.first().title)
        assertEquals(Route.CourseDetail("c1"), hits.single { it.icon == Group.COURSES }.route)
        assertEquals(Route.Calendar("2026-10-01"), hits.single { it.icon == Group.EVENTS }.route)
        assertEquals("1 cards", hits.single { it.icon == Group.DECKS }.sub)
        assertEquals("https://forms.gle/x", hits.single { it.icon == Group.FORMS }.url)
        // Group order follows the web.
        assertEquals(listOf(Group.NOTES, Group.COURSES, Group.EVENTS, Group.DECKS, Group.FORMS), hits.map { it.icon }.distinct())
    }

    @Test fun arabicMatches() {
        assertEquals(1, SearchMatcher.build("فيزياء", sources, labels).size)
    }
}
