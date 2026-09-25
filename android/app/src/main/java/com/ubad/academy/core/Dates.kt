package com.ubad.academy.core

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import com.ubad.academy.R
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Locale-aware date formatting (the web used Intl.DateTimeFormat with the UI language). */
object Dates {
    fun long(d: LocalDate, locale: Locale): String =
        d.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", locale))

    fun weekdayDayMonth(d: LocalDate, locale: Locale): String =
        d.format(DateTimeFormatter.ofPattern("EEEE d MMMM", locale))

    fun shortChip(d: LocalDate, locale: Locale): String =
        d.format(DateTimeFormatter.ofPattern("EEE d MMM", locale))

    fun dayMonth(d: LocalDate, locale: Locale): String =
        d.format(DateTimeFormatter.ofPattern("d MMM", locale))

    fun dayMonthYear(d: LocalDate, locale: Locale): String =
        d.format(DateTimeFormatter.ofPattern("d MMM yyyy", locale))

    fun monthYear(d: LocalDate, locale: Locale): String =
        d.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))

    fun monthShort(d: LocalDate, locale: Locale): String =
        d.month.getDisplayName(TextStyle.SHORT, locale)

    fun weekdayShort(d: LocalDate, locale: Locale): String =
        d.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)

    fun fromEpoch(ms: Long): LocalDateTime =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault())

    fun parseIso(iso: String?): LocalDateTime? = runCatching {
        java.time.OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()
    }.getOrNull()

    /** `hijriDateStr()` — e.g. «الخميس، 3 ربيع الآخر 1448 هـ». */
    fun hijriLong(context: Context, d: LocalDate, locale: Locale): String {
        val h = Hijri.of(d)
        val months = context.resources.getStringArray(R.array.hijri_months)
        val weekday = d.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
        return context.getString(R.string.hijri_format, weekday, h.day.toString(), months[h.month - 1], h.year.toString())
    }

    /** `greetKey()` */
    fun greetingKey(hour: Int): Int = when (hour) {
        in 5..11 -> R.string.dash_greet_morning
        in 12..16 -> R.string.dash_greet_afternoon
        in 17..21 -> R.string.dash_greet_evening
        else -> R.string.dash_greet_night
    }
}

@Composable
@ReadOnlyComposable
fun currentLocale(): Locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
