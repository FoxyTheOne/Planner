package app.planner.feature.week

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import app.planner.common.DateTexts
import app.planner.core.designsystem.preview.PlannerScreenPreviews
import app.planner.core.designsystem.theme.PlannerTheme
import app.planner.core.domain.model.Task
import app.planner.core.domain.model.TaskStatus
import app.planner.preview.SampleData
import java.time.LocalTime

/*
 * Превью «Недели» и почасовой шкалы дня на выдуманных данных.
 * Откройте файл в Android Studio и включите вид «Split».
 */

/** Неделя с задачами в разные дни; сегодняшний день подсвечен. */
private fun sampleWeek(): WeekUiState = with(SampleData) {
    val start = DateTexts.startOfWeek(today)
    val days = (0L..6L).map(start::plusDays)
    WeekUiState(
        today = today,
        weekStart = start,
        days = days,
        tasksByDay = mapOf(
            days[0] to listOf(printPhotos.copy(date = days[0], status = TaskStatus.DONE)),
            days[2] to listOf(
                watering.copy(date = days[2]),
                Task(id = 30, title = "Бассейн", date = days[2], time = LocalTime.of(16, 30), listId = home.id),
            ),
            today to listOf(doctor, documents, englishChapter),
            days[5] to listOf(Task(id = 31, title = "Полив · вода + эпин", date = days[5], listId = flowers.id)),
        ),
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
    onEdit = {},
    onDrop = { _, _ -> },
    onSelectFromTray = {},
)
