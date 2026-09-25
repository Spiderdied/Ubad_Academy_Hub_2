package com.ubad.academy.core

import android.icu.util.IslamicCalendar
import android.icu.util.ULocale
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

/** Hijri helpers — native port of hijriOf / ramadanInfo / upcomingFasts (Umm al-Qura, offline). */
object Hijri {
    data class HDate(val day: Int, val month: Int, val year: Int) // month is 1-based like the web

    fun of(date: LocalDate): HDate {
        val cal = IslamicCalendar(ULocale("ar@calendar=islamic-umalqura"))
        cal.calculationType = IslamicCalendar.CalculationType.ISLAMIC_UMALQURA
        cal.time = Date.from(date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant())
        return HDate(
            day = cal.get(IslamicCalendar.DAY_OF_MONTH),
            month = cal.get(IslamicCalendar.MONTH) + 1,
            year = cal.get(IslamicCalendar.YEAR),
        )
    }

    sealed interface Ramadan {
        val days: Int
        data class Before(override val days: Int) : Ramadan
        data class During(override val days: Int) : Ramadan
    }

    fun ramadanInfo(today: LocalDate = LocalDate.now()): Ramadan? {
        val h = of(today)
        if (h.month == 9) {
            for (i in 1..31) {
                val hh = of(today.plusDays(i.toLong()))
                if (hh.month == 10 && hh.day == 1) return Ramadan.During(i - 1)
            }
            return Ramadan.During(0)
        }
        for (i in 1..400) {
            val hh = of(today.plusDays(i.toLong()))
            if (hh.month == 9 && hh.day == 1) return Ramadan.Before(i)
        }
        return null
    }

    enum class FastKind { MONDAY, THURSDAY, WHITE }
    data class UpcomingFast(val date: LocalDate, val kind: FastKind, val hijriDay: Int)

    /** Next recommended voluntary fasts: Mon/Thu + the white days (13–15), max 6 entries in 60 days. */
    fun upcomingFasts(today: LocalDate = LocalDate.now()): List<UpcomingFast> {
        val list = mutableListOf<UpcomingFast>()
        var i = 1
        while (i <= 60 && list.size < 6) {
            val d = today.plusDays(i.toLong())
            val h = of(d)
            when (d.dayOfWeek) {
                DayOfWeek.MONDAY -> list += UpcomingFast(d, FastKind.MONDAY, h.day)
                DayOfWeek.THURSDAY -> list += UpcomingFast(d, FastKind.THURSDAY, h.day)
                else -> Unit
            }
            if (h.day in 13..15) list += UpcomingFast(d, FastKind.WHITE, h.day)
            i++
        }
        return list
    }

    fun isWhiteDay(date: LocalDate = LocalDate.now()) = of(date).day in 13..15
    fun isMonThu(date: LocalDate = LocalDate.now()) =
        date.dayOfWeek == DayOfWeek.MONDAY || date.dayOfWeek == DayOfWeek.THURSDAY
}
