package app.planner.feature.week

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import app.planner.common.DateTexts
import app.planner.common.dayItems
import app.planner.core.designsystem.preview.PlannerScreenPreviews
import app.planner.core.designsystem.theme.PlannerTheme
import app.planner.core.domain.model.Task
import app.planner.core.domain.model.TaskStatus
import app.planner.core.domain.recurrence.Recurrence
import app.planner.preview.SampleData

/*
 * Превью «Недели» и почасовой шкалы дня на выдуманных данных.
 * Откройте файл в Android Studio и включите вид «Split».
 */

/** Неделя с задачами в разные дни и повторениями (бассейн, полив); сегодняшний день подсвечен. */
private fun sampleWeek(): WeekUiState = with(SampleData) {
    val start = DateTexts.startOfWeek(today)
    val days = (0L..6L).map(start::plusDays)
    val tasksByDay = mapOf(
        days[0] to listOf(printPhotos.copy(date = days[0], status = TaskStatus.DONE)),
        today to listOf(doctor, documents, englishChapter),
    )
    // Повторения считает тот же движок, что и в приложении
    val occurrences = Recurrence.occurrencesBetween(listOf(poolRule, wateringRule), days.first(), days.last())
        .groupBy { it.date }
    WeekUiState(
        today = today,
        weekStart = start,
        days = days,
        itemsByDay = days.associateWith { dayItems(tasksByDay[it].orEmpty(), occurrences[it].orEmpty()) },
        lists = lists,
        unscheduled = listOf(readDune, buyBook),
        overdue = listOf(forgotten),
    )
}

@PlannerScreenPreviews
@Composable
private fun WeekPreview() {
    PlannerTheme { WeekForPreview(planning = false) }
}

/** Режим планирования: внизу лоток, задача «Позвонить в ЖЭК» выбрана — её можно поставить тапом по дню. */
@PlannerScreenPreviews
@Composable
private fun WeekPlanningPreview() {
    PlannerTheme { WeekForPreview(planning = true, selected = SampleData.forgotten) }
}

/** Почасовая шкала сегодняшнего дня: задачи без времени сверху, врач в 10:00. */
@PlannerScreenPreviews
@Composable
private fun DayPreview() {
    PlannerTheme {
        DayContent(
            date = SampleData.today,
            state = sampleWeek(),
            contentPadding = PaddingValues(),
            onBack = {},
            onPickSlot = {},
            onToggle = {},
            onCompleteOccurrence = {},
            onEdit = {},
        )
    }
}

@Composable
private fun WeekForPreview(planning: Boolean, selected: Task? = null) = WeekContent(
    state = sampleWeek(),
    planning = planning,
    selectedFromTray = selected,
    contentPadding = PaddingValues(),
    onPrev = {},
    onNext = {},
    onThisWeek = {},
    onTogglePlanning = {},
    onOpenDay = {},
    onAdd = {},
    onToggle = {},
    onCompleteOccurrence = {},
    onEdit = {},
    onDrop = { _, _ -> },
    onSelectFromTray = {},
)
