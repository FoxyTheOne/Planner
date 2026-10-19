package app.planner.feature.today

import app.planner.common.DayItem
import app.planner.core.domain.model.Goal
import app.planner.core.domain.model.RecurrenceRule
import app.planner.core.domain.model.Task
import app.planner.core.domain.model.TaskList
import app.planner.core.domain.overdue.OverdueGroup
import app.planner.core.domain.recurrence.HabitDay
import app.planner.core.domain.recurrence.HabitHistory
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
    /** Все цели — для подписи под шагом цели и для «Помогает цели» в редакторе привычки. */
    val goals: List<Goal> = emptyList(),
    /** Задачи без даты из списков и шаги целей — для окна «Добавить»: можно взять готовую на сегодня. */
    val unscheduled: List<Task> = emptyList(),
    /** Повторения, которые сегодня на паузе: их не видно в разделах, но открыть, чтобы продолжить, нужно. */
    val paused: List<RecurrenceRule> = emptyList(),
    /**
     * Привычки на сегодня — «таблетки» над планом. В разделы утро/день/вечер они не попадают
     * и в счёт «2 из 5» не входят: это не дела, которые надо «закрыть», а то, что хочется делать.
     */
    val habits: List<HabitDay> = emptyList(),
    /** Мягкая статистика каждой привычки (по id правила): показывается в редакторе привычки. */
    val habitHistory: Map<Long, HabitHistory> = emptyMap(),
    /** Все правила повторений по id — из дня повторения можно открыть его правило («Изменить все дни»). */
    val rules: Map<Long, RecurrenceRule> = emptyMap(),
) {
    /** Названия целей по id — подпись под шагом цели («Купить плитку» → «шаг к цели «Ремонт в ванной»»). */
    val goalTitles: Map<Long, String> get() = goals.associate { it.id to it.title }

    val overdueCount: Int get() = overdue.values.sumOf { it.size }
    val isEmpty: Boolean get() = sections.values.all { it.isEmpty() }
}
