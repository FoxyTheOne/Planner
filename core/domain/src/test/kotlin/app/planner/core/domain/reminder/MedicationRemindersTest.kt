package app.planner.core.domain.reminder

import app.planner.core.domain.medication.DoseKey
import app.planner.core.domain.model.DoseTaken
import app.planner.core.domain.model.Medication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Напоминания о приёмах: во время приёма, повторы через 10 минут, «Отложить», смахнутые и принятые. */
class MedicationRemindersTest {

    private val today = LocalDate.of(2026, 10, 15)
    private val morning = LocalTime.of(9, 0)
    private val evening = LocalTime.of(21, 0)

    private fun vitamin(repeat: Boolean = false) = Medication(
        id = 1, name = "Витамин D", times = listOf(morning, evening),
        startDate = today.minusDays(10), repeatReminder = repeat,
    )

    private fun at(hour: Int, minute: Int, date: LocalDate = today): LocalDateTime = date.atTime(hour, minute)

    @Test
    fun `rings exactly at the dose time`() {
        val alarm = MedicationReminders.alarmFor(vitamin(), emptyList(), at(8, 0))!!

        assertEquals(morning, alarm.first.time)
        assertEquals(at(9, 0), alarm.second)
    }

    @Test
    fun `taken dose is skipped`() {
        val taken = listOf(DoseTaken(1, today, morning))

        val alarm = MedicationReminders.alarmFor(vitamin(), taken, at(8, 0))!!

        assertEquals(at(21, 0), alarm.second)
    }

    @Test
    fun `no reminders when they are turned off`() {
        assertNull(MedicationReminders.alarmFor(vitamin().copy(remind = false), emptyList(), at(8, 0)))
    }

    @Test
    fun `without repeat an unanswered dose rings once`() {
        val alarm = MedicationReminders.alarmFor(vitamin(), emptyList(), at(9, 5))!!

        assertEquals(at(21, 0), alarm.second) // утренний уже прозвенел, дальше — вечерний
    }

    @Test
    fun `with repeat an unanswered dose rings three more times every 10 minutes`() {
        val vitamin = vitamin(repeat = true)

        assertEquals(at(9, 10), MedicationReminders.alarmFor(vitamin, emptyList(), at(9, 5))!!.second)
        assertEquals(at(9, 30), MedicationReminders.alarmFor(vitamin, emptyList(), at(9, 25))!!.second)
        // После третьего повтора — тишина до вечернего приёма
        assertEquals(at(21, 0), MedicationReminders.alarmFor(vitamin, emptyList(), at(9, 31))!!.second)
    }

    @Test
    fun `dismissed notification stops the repeats`() {
        val dismissed = setOf(DoseKey(1, today, morning))

        val alarm = MedicationReminders.alarmFor(vitamin(repeat = true), emptyList(), at(9, 5), dismissed = dismissed)!!

        assertEquals(at(21, 0), alarm.second)
    }

    @Test
    fun `snoozed dose rings at snooze time and repeats count from it`() {
        val snooze = Snooze(original = at(9, 0), until = at(9, 12), date = today)

        assertEquals(at(9, 12), MedicationReminders.alarmFor(vitamin(), emptyList(), at(9, 3), snooze)!!.second)
        assertEquals(at(9, 22), MedicationReminders.alarmFor(vitamin(repeat = true), emptyList(), at(9, 13), snooze)!!.second)
    }

    @Test
    fun `snooze of another dose does not move this one`() {
        // Отложили вчерашний вечерний — сегодняшний утренний звонит как обычно
        val snooze = Snooze(original = at(21, 0, today.minusDays(1)), until = at(21, 10, today.minusDays(1)), date = today.minusDays(1))

        assertEquals(at(9, 0), MedicationReminders.alarmFor(vitamin(), emptyList(), at(8, 0), snooze)!!.second)
    }

    @Test
    fun `late evening repeat goes past midnight`() {
        val late = vitamin(repeat = true).copy(times = listOf(LocalTime.of(23, 50)))

        val alarm = MedicationReminders.alarmFor(late, emptyList(), at(0, 5))!!

        assertEquals(today.minusDays(1), alarm.first.date) // это ещё вчерашний приём
        assertEquals(at(0, 10), alarm.second)
    }

    @Test
    fun `finished course has no reminders`() {
        val course = vitamin().copy(endDate = today.minusDays(1))

        assertNull(MedicationReminders.alarmFor(course, emptyList(), at(8, 0)))
    }

    @Test
    fun `dose is still due only if nothing happened to it`() {
        val key = DoseKey(1, today, morning)
        val vitamin = vitamin()

        assertTrue(MedicationReminders.stillDue(vitamin, key, emptyList(), emptySet()))
        assertFalse(MedicationReminders.stillDue(null, key, emptyList(), emptySet())) // лекарство удалили
        assertFalse(MedicationReminders.stillDue(vitamin, key, listOf(DoseTaken(1, today, morning)), emptySet()))
        assertFalse(MedicationReminders.stillDue(vitamin, key, emptyList(), setOf(key)))
        // Время приёма поменяли на 10:00 — о приёме в 9:00 больше не напоминаем
        assertFalse(MedicationReminders.stillDue(vitamin.copy(times = listOf(LocalTime.of(10, 0))), key, emptyList(), emptySet()))
    }

    @Test
    fun `snooze lives until its last repeat`() {
        val snooze = Snooze(original = at(9, 0), until = at(9, 10), date = today)

        assertTrue(MedicationReminders.snoozeAlive(vitamin(repeat = true), snooze, at(9, 35)))
        assertFalse(MedicationReminders.snoozeAlive(vitamin(repeat = true), snooze, at(9, 41)))
        assertFalse(MedicationReminders.snoozeAlive(vitamin(), snooze, at(9, 11)))
    }
}
