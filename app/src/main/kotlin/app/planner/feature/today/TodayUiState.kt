package app.planner.feature.today

import app.planner.core.domain.model.Task
import app.planner.core.domain.model.TaskList
import app.planner.core.domain.overdue.OverdueGroup
import app.planner.core.domain.recurrence.Occurrence
import java.time.LocalDate
import java.time.LocalTime

/*
 * Состояние экрана «Сегодня» — то, что ViewModel готовит, а экран рисует.
 * Здесь только данные, без логики загрузки: её делает TodayViewModel.
 */

/** Раздел экрана «Сегодня»: утро, день, вечер или «в любое время». */
enum class DaySection { MORNING, DAY, EVENING, ANYTIME }

/**
 * Строка в разделе «Сегодня»: обычная задача или повторение (полив, бассейн).
 *
 * Это снова `sealed interface`, как Repeat: вариантов ровно два, и у каждого свои данные.
 * Экран делает `when (item)` и для задачи рисует строку с чекбоксом, а для повторения — с ↻.
 */
sealed interface TodayItem {
    /** Ключ для LazyColumn. У задачи и правила id могут совпасть (оба 1), поэтому с приставкой. */
    val key: String

    /** Время, по которому строка встаёт в разделе; null — без времени, после тех, у кого оно есть. */
    val time: LocalTime?

    /** Сделанное опускается вниз раздела. */
    val isDone: Boolean

    data class TaskItem(val task: Task) : TodayItem {
        override val key get() = "task_${task.id}"
        override val time get() = task.time
        override val isDone get() = task.isDone
    }

    data class RepeatItem(val occurrence: Occurrence) : TodayItem {
        override val key get() = "rule_${occurrence.rule.id}"
        override val time get() = occurrence.rule.time

        // Строка по правилу всегда «не сделано»: отмеченный день становится задачей (TaskItem)
        override val isDone get() = false
    }
}

/**
 * Всё, что нужно экрану «Сегодня», одним неизменяемым объектом.
 * Экран просто рисует это состояние; когда данные меняются — ViewModel присылает новый объект.
 */
data class TodayUiState(
    val today: LocalDate,
    val sections: Map<DaySection, List<TodayItem>> = emptyMap(),
    val doneCount: Int = 0,
    val totalCount: Int = 0,
    val tomorrowCount: Int = 0,
    /** События завтра со временем — их важно увидеть заранее (приём у врача). */
    val tomorrowTimed: List<Task> = emptyList(),
    val overdue: Map<OverdueGroup, List<Task>> = emptyMap(),
    val showOverdueBanner: Boolean = false,
    val lists: List<TaskList> = emptyList(),
) {
    val overdueCount: Int get() = overdue.values.sumOf { it.size }
    val isEmpty: Boolean get() = sections.values.all { it.isEmpty() }
}
