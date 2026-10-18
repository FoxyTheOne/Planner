package app.planner.common

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import app.planner.core.domain.model.Goal
import app.planner.core.domain.model.PartOfDay
import app.planner.core.domain.model.Pause
import app.planner.core.domain.model.Reminder
import app.planner.core.domain.model.Repeat
import app.planner.core.domain.model.Task
import app.planner.core.domain.model.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek.WEDNESDAY
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * «Сохранятели» для поворота экрана: объект → простые значения → обратно тот же объект.
 *
 * Зачем тест: Saver пишется руками, поле за полем, и новое поле легко забыть — тогда после
 * поворота экрана оно молча возвращается к значению по умолчанию (так уже было с goalId и
 * с половиной напоминания). Здесь все поля заполнены НЕ значениями по умолчанию: забыли поле
 * в save или restore — объект вернётся другим, и тест упадёт.
 */
class SaversTest {

    /** Прогнать значение через Saver туда и обратно, как это делает rememberSaveable. */
    private fun <T> roundTrip(saver: Saver<T, Any>, value: T): T? {
        // SaverScope отвечает «можно ли это положить в Bundle»; в тесте Bundle нет — разрешаем всё
        val scope = SaverScope { true }
        val saved = with(saver) { scope.save(value) } ?: return null
        return saver.restore(saved)
    }

    private val day = LocalDate.of(2026, 10, 19)

    private val fullTask = Task(
        id = 7, title = "Купить плитку", note = "Леруа, ряд 12", date = day, time = LocalTime.of(10, 30),
        partOfDay = PartOfDay.MORNING, listId = 3, status = TaskStatus.DONE, ruleId = 4,
        occurrenceDate = day.minusDays(1), goalId = 2, stageId = 5, position = 6,
        reminder = Reminder.BeforeTime(30), isSample = true,
        createdAt = Instant.ofEpochSecond(1_000), doneAt = Instant.ofEpochSecond(2_000),
    )

    @Test
    fun `task survives with every field`() {
        assertEquals(fullTask, roundTrip(TaskSaver, fullTask))
        // Второй вид напоминания — тоже
        val atNine = fullTask.copy(reminder = Reminder.AtTime(LocalTime.of(9, 0)))
        assertEquals(atNine, roundTrip(TaskSaver, atNine))
    }

    @Test
    fun `reminder survives, both kinds`() {
        assertEquals(Reminder.BeforeTime(30), roundTrip(ReminderSaver, Reminder.BeforeTime(30)))
        assertEquals(Reminder.AtTime(LocalTime.of(9, 0)), roundTrip(ReminderSaver, Reminder.AtTime(LocalTime.of(9, 0))))
    }

    @Test
    fun `goal survives with every field`() {
        val goal = Goal(
            id = 1, title = "Ремонт", why = "Уют", note = "Плитка 30×60", deadline = day,
            position = 2, createdAt = Instant.ofEpochSecond(500), achievedOn = day.plusDays(3),
        )
        assertEquals(goal, roundTrip(GoalSaver, goal))
    }

    @Test
    fun `editor target survives with every field`() {
        val target = EditorTarget(
            task = fullTask,
            repeat = Repeat.OnWeekdays(setOf(WEDNESDAY)),
            pause = Pause(day, day.plusDays(7)),
            pauses = listOf(Pause(day.minusDays(30), day.minusDays(20)), Pause(day, day.plusDays(7))),
            isHabit = true,
            habitGoalId = 2,
        )
        assertEquals(target, roundTrip(EditorTargetSaver, target))
    }
}
