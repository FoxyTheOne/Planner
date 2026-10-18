package app.planner.feature.week

import app.planner.common.DayItem
import app.planner.core.domain.model.Task
import app.planner.core.domain.model.TaskList
import java.time.LocalDate

/** Всё, что нужно экрану «Неделя» (и экрану одного дня), одним объектом. */
data class WeekUiState(
    val today: LocalDate,
    val weekStart: LocalDate,
    val days: List<LocalDate> = emptyList(),
    /** Строки каждого дня: задачи и ещё не отмеченные дни повторений, по времени. */
    val itemsByDay: Map<LocalDate, List<DayItem>> = emptyMap(),
    val lists: List<TaskList> = emptyList(),
    /** Названия целей по id — подпись под шагом цели в дне недели и в шторке «Из списков». */
    val goalTitles: Map<Long, String> = emptyMap(),
    /** Кандидаты для шторки «Из списков» и лотка планирования. */
    val unscheduled: List<Task> = emptyList(),
    val overdue: List<Task> = emptyList(),
)