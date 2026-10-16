package app.planner.core.domain.overdue

import app.planner.core.domain.model.Task
import app.planner.core.domain.model.TaskStatus
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Группы в шторке «Незавершённое»: чем давнее хвост, тем ниже он в списке. */
enum class OverdueGroup { YESTERDAY, THIS_WEEK, OLDER }

/**
 * Правила «Незавершённого» (хвостов): что считается хвостом, как хвосты группируются
 * и когда задача тихо возвращается в свой список.
 *
 * Это чистая логика без Android и базы данных, поэтому её легко проверить
 * обычными unit-тестами (см. `OverdueTest`).
 *
 * `object` в Kotlin — класс, у которого есть ровно один экземпляр; его функции вызываются
 * прямо по имени: `Overdue.isOverdue(task, today)`.
 */
object Overdue {

    /** Открытая задача с датой в прошлом. События с точным временем не копятся — они уходят в историю. */
    fun isOverdue(task: Task, today: LocalDate): Boolean =
        task.status == TaskStatus.OPEN &&
                task.date != null &&
                task.date < today &&
                !task.isTimedEvent

    /** В какую группу шторки попадает хвост — по тому, сколько дней прошло с его даты. */
    fun group(task: Task, today: LocalDate): OverdueGroup {
        val days = ChronoUnit.DAYS.between(task.date, today)
        return when {
            days <= 1 -> OverdueGroup.YESTERDAY
            days < 7 -> OverdueGroup.THIS_WEEK
            else -> OverdueGroup.OLDER
        }
    }

    /** Хвосты, разложенные по группам; внутри группы — сначала самые свежие. */
    fun grouped(tasks: List<Task>, today: LocalDate): Map<OverdueGroup, List<Task>> =
        tasks.filter { isOverdue(it, today) }
            .sortedByDescending { it.date }
            .groupBy { group(it, today) }

    /**
     * Задача висит в хвостах дольше [returnAfterDays] — пора вернуть её в список без даты.
     * Считаем от даты задачи: если человек сам её переносил, отсчёт начинается заново —
     * её не забыли, ею занимаются.
     */
    fun shouldReturnToList(task: Task, today: LocalDate, returnAfterDays: Int): Boolean =
        isOverdue(task, today) &&
                ChronoUnit.DAYS.between(
                    task.date,
                    today
                ) > returnAfterDays

    /**
     * Та же задача, но без даты и времени — в своём списке, а если списка нет — во «Входящих».
     * Шаг цели возвращается в свою цель: списка у него нет и не должно появиться.
     */
    fun returnedToList(task: Task, inboxId: Long): Task =
        task.copy(
            date = null,
            time = null,
            partOfDay = null,
            listId = if (task.isGoalStep) null else task.listId ?: inboxId,
        )
}
