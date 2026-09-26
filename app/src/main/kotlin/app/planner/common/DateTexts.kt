package app.planner.common

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

/** Даты по-человечески с учётом языка системы. */
object DateTexts {
    private val locale: Locale get() = Locale.getDefault()

    /** «Четверг» */
    fun weekday(date: LocalDate): String =
        date.dayOfWeek.getDisplayName(TextStyle.FULL_STANDALONE, locale).replaceFirstChar { it.titlecase(locale) }

    /** «Чт» */
    fun weekdayShort(day: DayOfWeek): String =
        day.getDisplayName(TextStyle.SHORT_STANDALONE, locale).replaceFirstChar { it.titlecase(locale) }

    /** «24 сентября» */
    fun dayMonth(date: LocalDate): String = DateTimeFormatter.ofPattern("d MMMM", locale).format(date)

    /** «Чт 24» */
    fun shortDay(date: LocalDate): String = "${weekdayShort(date.dayOfWeek)} ${date.dayOfMonth}"

    /** «21 — 27 сентября» или «28 сент. — 4 окт.» */
    fun weekRange(start: LocalDate): String {
        val end = start.plusDays(6)
        return if (start.month == end.month) {
            "${start.dayOfMonth} — ${dayMonth(end)}"
        } else {
            val f = DateTimeFormatter.ofPattern("d MMM", locale)
            "${f.format(start)} — ${f.format(end)}"
        }
    }

    fun time(time: LocalTime): String = DateTimeFormatter.ofPattern("HH:mm", locale).format(time)

    fun startOfWeek(date: LocalDate): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(WeekFields.of(locale).firstDayOfWeek))

    /** Ближайшая суббота (если сегодня выходной — сегодня). */
    fun nearestWeekend(today: LocalDate): LocalDate =
        if (today.dayOfWeek == DayOfWeek.SATURDAY || today.dayOfWeek == DayOfWeek.SUNDAY) today
        else today.with(TemporalAdjusters.next(DayOfWeek.SATURDAY))

    fun nextWeekStart(today: LocalDate): LocalDate = startOfWeek(today).plusWeeks(1)
}
