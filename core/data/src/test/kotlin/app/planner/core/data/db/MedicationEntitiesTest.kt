package app.planner.core.data.db

import app.planner.core.domain.model.DoseTaken
import app.planner.core.domain.model.FoodRelation
import app.planner.core.domain.model.Medication
import app.planner.core.domain.model.Pause
import app.planner.core.domain.model.Repeat
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.THURSDAY
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * Лекарство → строка таблицы → лекарство должно получиться тем же самым (как RecurrenceEntitiesTest
 * для правил). Забудем новое поле в toEntity/toDomain — тест покраснеет.
 */
class MedicationEntitiesTest {

    private val oct5 = LocalDate.of(2026, 10, 5)

    private fun base(repeat: Repeat) = Medication(
        id = 4,
        name = "Витамин D",
        dose = "1 капсула",
        food = FoodRelation.AFTER,
        times = listOf(LocalTime.of(9, 0), LocalTime.of(21, 30)),
        repeat = repeat,
        startDate = oct5,
        endDate = oct5.plusDays(29),
        pauses = listOf(Pause(oct5.plusDays(10), oct5.plusDays(12)), Pause(oct5.plusDays(20))),
        remind = false,
        repeatReminder = true,
        note = "запивать водой",
        position = 2,
    )

    private fun assertRoundTrip(medication: Medication) =
        assertEquals(medication, medication.toEntity().toDomain())

    @Test
    fun `daily survives the round trip`() = assertRoundTrip(base(Repeat.Daily))

    @Test
    fun `weekdays survive the round trip`() =
        assertRoundTrip(base(Repeat.OnWeekdays(setOf(MONDAY, THURSDAY))))

    @Test
    fun `every n days survives the round trip`() = assertRoundTrip(base(Repeat.EveryNDays(3)))

    @Test
    fun `no food relation, no end and no pauses survive too`() =
        assertRoundTrip(base(Repeat.Daily).copy(food = null, endDate = null, pauses = emptyList()))

    @Test
    fun `times are stored sorted as seconds of day`() {
        val medication = base(Repeat.Daily).copy(times = listOf(LocalTime.of(21, 0), LocalTime.of(9, 0)))

        // 9:00 — 32400 секунд от полуночи, 21:00 — 75600
        assertEquals("32400,75600", medication.toEntity().times)
    }

    @Test
    fun `dose taken survives the round trip`() {
        val taken = DoseTaken(4, oct5, LocalTime.of(9, 0), Instant.parse("2026-10-05T06:03:00Z"))

        assertEquals(taken, taken.toEntity().toDomain())
    }
}
