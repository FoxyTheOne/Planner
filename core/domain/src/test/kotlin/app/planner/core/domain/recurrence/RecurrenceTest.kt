package app.planner.core.domain.recurrence

import app.planner.core.domain.model.Pause
import app.planner.core.domain.model.RecurrenceRule
import app.planner.core.domain.model.Reminder
import app.planner.core.domain.model.Repeat
import app.planner.core.domain.model.Task
import app.planner.core.domain.model.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalDate
import java.time.LocalTime
import java.time.MonthDay

/**
 * Тесты движка повторений. Даты настоящие: 5 октября 2026 — понедельник.
 * Удобно сверять с календарём.
 */
class RecurrenceTest {

    private val monday = LocalDate.of(2026, 10, 5)

    private fun rule(repeat: Repeat, start: LocalDate = monday, end: LocalDate? = null) =
        RecurrenceRule(title = "Тест", repeat = repeat, startDate = start, endDate = end)

    @Test
    fun `daily occurs every day of the week`() {
        val days = Recurrence.occurrences(rule(Repeat.Daily), monday, monday.plusDays(6)).map { it.date }
        assertEquals(7, days.size)
    }

    @Test
    fun `weekdays rule picks only chosen days`() {
        val r = rule(Repeat.OnWeekdays(setOf(MONDAY, WEDNESDAY, FRIDAY)))

        val days = Recurrence.occurrences(r, monday, monday.plusDays(6)).map { it.date }

        // пн 5, ср 7, пт 9 октября
        assertEquals(listOf(monday, monday.plusDays(2), monday.plusDays(4)), days)
    }

    @Test
    fun `every n days counts from the start date`() {
        // «Через день» с понедельника: пн, ср, пт, вс, вт…
        val r = rule(Repeat.EveryNDays(2))

        val days = Recurrence.occurrences(r, monday, monday.plusDays(8)).map { it.date }

        assertEquals(listOf(0L, 2, 4, 6, 8).map { monday.plusDays(it) }, days)
    }

    @Test
    fun `every n days works when the asked range starts later`() {
        // Правило началось давно — считать всё равно надо от его начала, а не от начала недели
        val r = rule(Repeat.EveryNDays(3), start = monday.minusDays(10))

        // От minusDays(10) каждые 3 дня: …, minusDays(1), plusDays(2), plusDays(5)
        val days = Recurrence.occurrences(r, monday, monday.plusDays(6)).map { it.date }

        assertEquals(listOf(monday.plusDays(2), monday.plusDays(5)), days)
    }

    @Test
    fun `nothing before start and after end`() {
        val r = rule(Repeat.Daily, start = monday.plusDays(2), end = monday.plusDays(4))

        assertFalse(Recurrence.occursOn(r, monday.plusDays(1))) // до начала
        assertTrue(Recurrence.occursOn(r, monday.plusDays(2))) // первый день — включительно
        assertTrue(Recurrence.occursOn(r, monday.plusDays(4))) // последний день — включительно
        assertFalse(Recurrence.occursOn(r, monday.plusDays(5))) // после конца
    }

    @Test
    fun `occurrencesOn picks only rules that fall on the day`() {
        val daily = rule(Repeat.Daily).copy(id = 1)
        val mondays = rule(Repeat.OnWeekdays(setOf(MONDAY))).copy(id = 2)
        val fridays = rule(Repeat.OnWeekdays(setOf(FRIDAY))).copy(id = 3)

        val onMonday = Recurrence.occurrencesOn(listOf(daily, mondays, fridays), monday)

        assertEquals(listOf(1L, 2L), onMonday.map { it.rule.id }) // пятничного в понедельник нет
    }

    @Test
    fun `completed day becomes a task and hides the virtual row`() {
        val pool = rule(Repeat.Daily).copy(id = 5, title = "Бассейн")
        val occurrence = Recurrence.occurrence(pool, monday)!!

        val task = Recurrence.toTask(occurrence, TaskStatus.DONE)

        assertEquals("Бассейн", task.title)
        assertEquals(5L, task.ruleId)
        assertEquals(monday, task.occurrenceDate)
        assertTrue(task.isDone)
        // В понедельник строки по правилу больше нет — вместо неё задача; во вторник правило снова выпадает
        assertTrue(Recurrence.occurrencesOn(listOf(pool), monday, instances = listOf(task)).isEmpty())
        assertEquals(1, Recurrence.occurrencesOn(listOf(pool), monday.plusDays(1), instances = listOf(task)).size)
    }

    @Test
    fun `week view skips only the completed day`() {
        val pool = rule(Repeat.OnWeekdays(setOf(MONDAY, WEDNESDAY, FRIDAY))).copy(id = 5)
        val doneOnWednesday = Recurrence.toTask(Recurrence.occurrence(pool, monday.plusDays(2))!!, TaskStatus.DONE)

        val week = Recurrence.occurrencesBetween(listOf(pool), monday, monday.plusDays(6), listOf(doneOnWednesday))

        // Среда уже отмечена — там будет задача; понедельник и пятница остаются строками по правилу
        assertEquals(listOf(monday, monday.plusDays(4)), week.map { it.date })
    }

    @Test
    fun `rule is built from the editor draft and back`() {
        val draft = Task(title = "Бассейн", date = monday.plusDays(2), partOfDay = null,
            time = LocalTime.of(18, 30), listId = 4)

        val rule = Recurrence.ruleFromDraft(draft, Repeat.OnWeekdays(setOf(WEDNESDAY)), today = monday)

        assertEquals(monday.plusDays(2), rule.startDate) // дата черновика — начало правила
        assertEquals(LocalTime.of(18, 30), rule.time)
        assertEquals(4L, rule.listId)

        // Правило сохранили (id = 9) и открыли снова — в редакторе то же самое
        val back = Recurrence.draftFromRule(rule.copy(id = 9))
        assertEquals(9L, back.ruleId)
        assertEquals(0L, back.id) // это черновик, а не задача из базы
        assertEquals(draft.title, back.title)
        assertEquals(draft.date, back.date)
    }

    @Test
    fun `editing a rule keeps its id and pauses`() {
        val existing = rule(Repeat.Daily).copy(id = 3, pauses = listOf(Pause(monday.plusDays(10))))
        val draft = Recurrence.draftFromRule(existing).copy(title = "Зарядка утром")

        val updated = Recurrence.ruleFromDraft(draft, Repeat.EveryNDays(2), today = monday, existing = existing)

        assertEquals(3L, updated.id)
        assertEquals(existing.pauses, updated.pauses)
        assertEquals("Зарядка утром", updated.title)
        assertEquals(Repeat.EveryNDays(2), updated.repeat)
    }

    @Test
    fun `editing a habit keeps its goal, turning habit off drops it`() {
        val duolingo = rule(Repeat.Daily).copy(id = 6, isHabit = true, goalId = 2)
        val draft = Recurrence.draftFromRule(duolingo).copy(title = "Duolingo, 15 минут")

        // Правка без смены «привычка / цель» — связь с целью на месте
        assertEquals(2L, Recurrence.ruleFromDraft(draft, Repeat.Daily, today = monday, existing = duolingo).goalId)
        // Выключили «Привычка» — это дело, а у дела связи с целью нет
        val plain = Recurrence.ruleFromDraft(draft, Repeat.Daily, today = monday, existing = duolingo, isHabit = false)
        assertEquals(null, plain.goalId)
    }

    @Test
    fun `editing a habit keeps it a habit`() {
        val water = rule(Repeat.Daily).copy(id = 4, isHabit = true)
        val draft = Recurrence.draftFromRule(water).copy(title = "Вода, 6 стаканов")

        val updated = Recurrence.ruleFromDraft(draft, Repeat.Daily, today = monday, existing = water)

        assertTrue(updated.isHabit)
    }

    @Test
    fun `habit switch in the editor decides`() {
        val draft = Task(title = "Зарядка")
        // Новое правило с включённым «Привычка»
        val habit = Recurrence.ruleFromDraft(draft, Repeat.Daily, today = monday, isHabit = true)
        assertTrue(habit.isHabit)
        // Переключатель выключили при правке — снова обычное дело
        val plain = Recurrence.ruleFromDraft(draft, Repeat.Daily, today = monday, existing = habit, isHabit = false)
        assertFalse(plain.isHabit)
    }

    @Test
    fun `draft without date starts the rule today`() {
        val rule = Recurrence.ruleFromDraft(Task(title = "Таблетки"), Repeat.Daily, today = monday)
        assertEquals(monday, rule.startDate)
    }

    @Test
    fun `moved day hides its own day but stays the same occurrence`() {
        val pool = rule(Repeat.OnWeekdays(setOf(MONDAY, WEDNESDAY))).copy(id = 5)
        // Понедельничный бассейн перенесли на вторник: задача на вторник, но день правила — понедельник
        val moved = Recurrence.toTask(Recurrence.occurrence(pool, monday)!!, TaskStatus.OPEN)
            .copy(date = monday.plusDays(1))

        val week = Recurrence.occurrencesBetween(listOf(pool), monday, monday.plusDays(6), listOf(moved))

        assertEquals(monday, moved.occurrenceDate)
        assertEquals(listOf(monday.plusDays(2)), week.map { it.date }) // по правилу осталась только среда
    }

    @Test
    fun `renamed rule updates a moved day but keeps its new date`() {
        val watering = rule(Repeat.Cycle(listOf(mapOf(MONDAY to "фикус")), anchorWeekStart = monday)).copy(id = 5, title = "Полив")
        // Понедельничный полив перенесли на среду, 19:00
        val moved = Recurrence.toTask(Recurrence.occurrence(watering, monday)!!, TaskStatus.OPEN)
            .copy(id = 40, date = monday.plusDays(2), time = LocalTime.of(19, 0))

        // Правило переименовали, перенесли в другой список и поменяли дела в таблице
        val changed = watering.copy(
            title = "Полив цветов",
            listId = 3,
            repeat = Repeat.Cycle(listOf(mapOf(MONDAY to "фикус, монстера")), anchorWeekStart = monday),
        )
        val synced = Recurrence.syncInstance(moved, changed)

        assertEquals("Полив цветов", synced.title)
        assertEquals(3L, synced.listId)
        assertEquals("фикус, монстера", synced.note)
        assertEquals(monday.plusDays(2), synced.date) // перенос остался
        assertEquals(LocalTime.of(19, 0), synced.time)
        assertEquals(40L, synced.id) // та же задача, а не новая
    }

    @Test
    fun `cycle details go to the task note`() {
        val watering = rule(Repeat.Cycle(listOf(mapOf(MONDAY to "фикус")), anchorWeekStart = monday))

        val task = Recurrence.toTask(Recurrence.occurrence(watering, monday)!!, TaskStatus.DONE)

        assertEquals("фикус", task.note)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `weekdays rule without days is not allowed`() {
        // require в init не даст создать «по дням недели» без единого дня
        Repeat.OnWeekdays(emptySet())
    }
    @Test
    fun `yearly occurs once a year on its day`() {
        val oct15 = LocalDate.of(2026, 10, 15)
        val birthday = RecurrenceRule(title = "День рождения мамы", repeat = Repeat.Yearly(MonthDay.from(oct15)), startDate = oct15)
        val dates = Recurrence.occurrences(birthday, oct15.minusDays(1), oct15.plusYears(2)).map { it.date }
        assertEquals(listOf(oct15, oct15.plusYears(1), oct15.plusYears(2)), dates)
    }

    @Test
    fun `february 29 falls on the 28th in a common year`() {
        val feb29 = LocalDate.of(2028, 2, 29)
        val rule = RecurrenceRule(title = "Годовщина", repeat = Repeat.Yearly(MonthDay.from(feb29)), startDate = feb29)
        assertTrue(Recurrence.occursOn(rule, LocalDate.of(2029, 2, 28)))
        assertFalse(Recurrence.occursOn(rule, LocalDate.of(2029, 3, 1)))
        assertTrue(Recurrence.occursOn(rule, LocalDate.of(2032, 2, 29))) // високосный — снова 29-е
        assertFalse(Recurrence.occursOn(rule, LocalDate.of(2032, 2, 28)))
    }
    // --- Подготовка к дню «раз в год» ---

    private val oct15 = LocalDate.of(2026, 10, 15)
    private val birthday = RecurrenceRule(
        id = 9, title = "День рождения мамы", repeat = Repeat.Yearly(MonthDay.from(oct15)),
        startDate = oct15.minusYears(5), prepDays = 14, listId = 2,
        reminder = Reminder.AtTime(LocalTime.of(10, 0)),
    )

    @Test
    fun `prep task appears when preparation starts`() {
        // За 15 дней — ещё рано
        assertTrue(Recurrence.prepTasksDue(listOf(birthday), emptyList(), oct15.minusDays(15)).isEmpty())
        // За 14 дней — пора: обычная задача на сегодня, помнит, к какому дню готовит
        val prep = Recurrence.prepTasksDue(listOf(birthday), emptyList(), oct15.minusDays(14)).single()
        assertEquals(oct15.minusDays(14), prep.date)
        assertEquals(oct15, prep.prepFor)
        assertEquals(9L, prep.prepRuleId)
        assertEquals(2L, prep.listId)
        assertEquals(Reminder.AtTime(LocalTime.of(10, 0)), prep.reminder)
    }

    @Test
    fun `prep task is made once, and late if the app was not opened`() {
        val made = Task(title = "День рождения мамы", date = oct15.minusDays(14), prepRuleId = 9, prepFor = oct15)
        // Уже есть (даже сделанная или пропущенная) — второй не будет
        assertTrue(Recurrence.prepTasksDue(listOf(birthday), listOf(made), oct15.minusDays(3)).isEmpty())
        // Приложение не открывали — задача появится в тот день, когда откроют, на сегодня
        assertEquals(oct15.minusDays(3), Recurrence.prepTasksDue(listOf(birthday), emptyList(), oct15.minusDays(3)).single().date)
        // День прошёл — готовимся уже к следующему году, но до этого ещё далеко
        assertTrue(Recurrence.prepTasksDue(listOf(birthday), listOf(made), oct15.plusDays(1)).isEmpty())
    }

    @Test
    fun `no prep without days, and timed reminder does not carry over`() {
        assertTrue(Recurrence.prepTasksDue(listOf(birthday.copy(prepDays = null)), emptyList(), oct15.minusDays(1)).isEmpty())
        // «За 30 минут до начала» у подготовки не от чего считать — без напоминания
        val timed = birthday.copy(time = LocalTime.of(18, 0), reminder = Reminder.BeforeTime(30))
        assertNull(Recurrence.prepTasksDue(listOf(timed), emptyList(), oct15.minusDays(1)).single().reminder)
    }
}
