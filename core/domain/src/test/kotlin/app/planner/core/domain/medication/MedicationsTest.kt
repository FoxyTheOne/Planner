package app.planner.core.domain.medication

import app.planner.core.domain.model.DoseTaken
import app.planner.core.domain.model.Medication
import app.planner.core.domain.model.Pause
import app.planner.core.domain.model.Repeat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.THURSDAY
import java.time.LocalDate
import java.time.LocalTime
import java.time.MonthDay

/** Лекарства: приёмы на день, курс, перерывы и история «28 из 30». */
class MedicationsTest {

    /** Четверг. */
    private val today = LocalDate.of(2026, 10, 15)
    private val morning = LocalTime.of(9, 0)
    private val evening = LocalTime.of(21, 0)

    private fun vitaminD(
        times: List<LocalTime> = listOf(morning, evening),
        start: LocalDate = today.minusMonths(2),
    ) = Medication(id = 1, name = "Витамин D", dose = "1 капсула", times = times, startDate = start)

    private fun taken(medication: Medication, date: LocalDate, time: LocalTime) =
        DoseTaken(medication.id, date, time)

    @Test
    fun `doses of the day go by time and then by list order`() {
        val vitamin = vitaminD().copy(position = 1)
        val magnesium = Medication(
            id = 2, name = "Магний", times = listOf(morning), startDate = today, position = 0,
        )

        val doses = Medications.dosesOn(listOf(vitamin, magnesium), today, emptyList())

        // В 9:00 — сначала магний: он выше в списке лекарств
        assertEquals(
            listOf("Магний" to morning, "Витамин D" to morning, "Витамин D" to evening),
            doses.map { it.medication.name to it.time },
        )
    }

    @Test
    fun `each dose is marked on its own`() {
        val vitamin = vitaminD()

        val doses = Medications.doses(vitamin, today, listOf(taken(vitamin, today, morning)))

        assertTrue(doses[0].isTaken) // утренний принят
        assertFalse(doses[1].isTaken) // вечерний — ещё нет
    }

    @Test
    fun `marks follow the time when a dose is added`() {
        val vitamin = vitaminD()
        val marks = listOf(taken(vitamin, today, evening))

        // Добавили приём в 14:00 — отметка вечернего не «съехала» на новый, второй по счёту
        val doses = Medications.doses(vitamin.copy(times = listOf(morning, LocalTime.of(14, 0), evening)), today, marks)

        assertEquals(listOf(false, false, true), doses.map { it.isTaken })
    }

    @Test
    fun `dose times are sorted and without repeats`() {
        val vitamin = vitaminD(times = listOf(evening, morning, evening))

        assertEquals(listOf(morning, evening), vitamin.doseTimes)
    }

    @Test
    fun `only scheduled days have doses`() {
        val vitamin = vitaminD().copy(repeat = Repeat.OnWeekdays(setOf(MONDAY, THURSDAY)))

        assertEquals(2, Medications.doses(vitamin, today, emptyList()).size) // четверг
        assertTrue(Medications.doses(vitamin, today.plusDays(1), emptyList()).isEmpty()) // пятница
    }

    @Test
    fun `course of 10 days ends on the tenth day`() {
        val end = Medications.courseEnd(today, 10)
        val course = vitaminD(start = today).copy(endDate = end)

        assertEquals(today.plusDays(9), end)
        assertTrue(Medications.takesOn(course, end))
        assertFalse(Medications.takesOn(course, end.plusDays(1)))
        assertEquals(10, Medications.daysLeft(course, today))
        assertEquals(1, Medications.daysLeft(course, end))
        assertEquals(0, Medications.daysLeft(course, end.plusDays(1)))
        assertTrue(Medications.isFinished(course, end.plusDays(1)))
        assertFalse(Medications.isFinished(course, end))
    }

    @Test
    fun `endless course has no days left and never finishes`() {
        val vitamin = vitaminD()

        assertNull(Medications.daysLeft(vitamin, today))
        assertFalse(Medications.isFinished(vitamin, today.plusYears(5)))
    }

    @Test
    fun `course that has not started counts its whole length`() {
        val later = vitaminD(start = today.plusDays(3)).copy(endDate = today.plusDays(7))

        assertTrue(Medications.isUpcoming(later, today))
        assertEquals(5, Medications.daysLeft(later, today))
    }

    @Test
    fun `no doses during a break, and resume brings them back today`() {
        val vitamin = Medications.pause(vitaminD(), today.minusDays(2), until = null)

        assertTrue(Medications.doses(vitamin, today, emptyList()).isEmpty())
        assertTrue(Medications.activePause(vitamin, today) != null)

        val resumed = Medications.resume(vitamin, today)

        assertEquals(2, Medications.doses(resumed, today, emptyList()).size)
        assertEquals(listOf(Pause(today.minusDays(2), today.minusDays(1))), resumed.pauses)
    }

    @Test
    fun `history counts doses that already came and how many were taken`() {
        val vitamin = vitaminD(start = today.minusDays(2)) // три дня: позавчера, вчера, сегодня
        val marks = listOf(
            taken(vitamin, today.minusDays(2), morning),
            taken(vitamin, today.minusDays(2), evening),
            taken(vitamin, today.minusDays(1), morning),
            taken(vitamin, today, morning),
        )

        // Сейчас обед: вечерний приём сегодня ещё не наступил — в счёт не идёт
        val history = Medications.history(vitamin, marks, today.atTime(13, 0))

        assertEquals(5, history.planned)
        assertEquals(4, history.taken)
        assertEquals(today.minusDays(2), history.from) // курс моложе 30 дней — период с его начала
        assertTrue(history.startedRecently)
    }

    @Test
    fun `dose taken early counts in history`() {
        val vitamin = vitaminD(start = today)

        // Вечерний отметили заранее, в обед
        val history = Medications.history(vitamin, listOf(taken(vitamin, today, evening)), today.atTime(13, 0))

        assertEquals(2, history.planned) // утренний (уже прошёл) и вечерний (отмечен)
        assertEquals(1, history.taken)
    }

    @Test
    fun `history skips break days and looks back 30 days at most`() {
        val vitamin = Medications.pause(vitaminD(times = listOf(morning)), today.minusDays(9), today.minusDays(5))

        val history = Medications.history(vitamin, emptyList(), today.atTime(23, 0))

        assertEquals(today.minusDays(29), history.from)
        assertFalse(history.startedRecently)
        assertEquals(30 - 5, history.planned) // пять дней перерыва — приёмов не было
        assertEquals(0, history.taken)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `medication needs at least one dose time`() {
        vitaminD(times = emptyList())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `medication cannot repeat once a year`() {
        vitaminD().copy(repeat = Repeat.Yearly(MonthDay.of(10, 15)))
    }
}
