package app.planner.core.data.db

import app.planner.core.domain.model.PartOfDay
import app.planner.core.domain.model.Pause
import app.planner.core.domain.model.RecurrenceRule
import app.planner.core.domain.model.Repeat
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalDate
import java.time.LocalTime

/**
 * Правило → строка таблицы → правило должно получиться тем же самым.
 * Если в toEntity/toDomain что-то забыть (новое поле, вариант Repeat), этот тест покраснеет.
 * Обычный JVM-тест: база не нужна, проверяется только перевод туда-обратно.
 */
class RecurrenceEntitiesTest {

    private val oct5 = LocalDate.of(2026, 10, 5)

    private fun base(repeat: Repeat) = RecurrenceRule(
        id = 7,
        title = "Полив",
        repeat = repeat,
        startDate = oct5,
        endDate = oct5.plusMonths(6),
        pauses = listOf(Pause(oct5.plusDays(10), oct5.plusDays(17)), Pause(oct5.plusDays(40))),
        time = LocalTime.of(9, 30),
        partOfDay = PartOfDay.MORNING,
        listId = 3,
        goalId = 2,
        position = 4, // место в ряду тоже должно пережить перевод
    )

    private fun assertRoundTrip(rule: RecurrenceRule) = assertEquals(rule, rule.toEntity().toDomain())

    @Test
    fun `daily survives the round trip`() = assertRoundTrip(base(Repeat.Daily))

    @Test
    fun `weekdays survive the round trip`() =
        assertRoundTrip(base(Repeat.OnWeekdays(setOf(MONDAY, WEDNESDAY, FRIDAY))))

    @Test
    fun `every n days survives the round trip`() = assertRoundTrip(base(Repeat.EveryNDays(3)))

    @Test
    fun `cycle survives the round trip`() = assertRoundTrip(
        base(
            Repeat.Cycle(
                weeks = listOf(
                    mapOf(WEDNESDAY to "фикус, монстера"),
                    mapOf(WEDNESDAY to "фикус", SATURDAY to "подкормка"),
                    emptyMap(), // неделя без дел — тоже бывает
                ),
                anchorWeekStart = oct5,
                anchorWeekNumber = 2,
            ),
        ),
    )

    @Test
    fun `sunday-start cycle survives the round trip`() = assertRoundTrip(
        base(
            Repeat.Cycle(
                weeks = listOf(mapOf(SUNDAY to "подкормка")),
                anchorWeekStart = oct5.minusDays(1), // воскресенье 4 октября
                weekStart = SUNDAY,
            ),
        ),
    )

    @Test
    fun `rule without optional fields survives the round trip`() = assertRoundTrip(
        RecurrenceRule(title = "Зарядка", repeat = Repeat.Daily, startDate = oct5),
    )

    @Test
    fun `habit survives the round trip`() = assertRoundTrip(base(Repeat.Daily).copy(isHabit = true))

    @Test
    fun `weekdays are stored as bits`() {
        // пн = 1, ср = 4, пт = 16 → 21
        val entity = base(Repeat.OnWeekdays(setOf(MONDAY, WEDNESDAY, FRIDAY))).toEntity()
        assertEquals(21, entity.weekdays)
        assertEquals("WEEKDAYS", entity.repeatType)
    }
}
