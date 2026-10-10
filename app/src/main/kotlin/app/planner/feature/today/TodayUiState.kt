package app.planner.feature.today

import app.planner.common.DayItem
import app.planner.core.domain.model.Task
import app.planner.core.domain.model.TaskList
import app.planner.core.domain.overdue.OverdueGroup
import java.time.LocalDate

/*
 * Состояние экрана «Сегодня» — то, что ViewModel готовит, а экран рисует.
 * Здесь только данные, без логики загрузки: её делает TodayViewModel.
 */

/** Раздел экрана «Сегодня»: утро, день, вечер или «в любое время». */
enum class DaySection { MORNING, DAY, EVENING, ANYTIME }

/**
 * Всё, что нужно экрану «Сегодня», одним неизменяемым объектом.
 * Экран просто рисует это состояние; когда данные меняются — ViewModel присылает новый объект.
 */
data class TodayUiState(
    val today: LocalDate,
    val sections: Map<DaySection, List<DayItem>> = emptyMap(),
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
