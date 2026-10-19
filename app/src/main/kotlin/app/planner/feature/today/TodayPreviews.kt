package app.planner.feature.today

import app.planner.common.DayItem
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import app.planner.core.designsystem.preview.PlannerScreenPreviews
import app.planner.core.designsystem.theme.PlannerTheme
import app.planner.core.domain.model.Pause
import app.planner.core.domain.model.TaskStatus
import app.planner.core.domain.overdue.OverdueGroup
import app.planner.core.domain.recurrence.HabitDay
import app.planner.core.domain.recurrence.Recurrence
import app.planner.preview.SampleData

/*
 * Превью экрана «Сегодня». Откройте файл в Android Studio, вид «Split»:
 * справа появится экран на выдуманных данных (SampleData) в светлой и тёмной теме.
 * Нажатия в превью ничего не делают — поэтому все обработчики пустые `{}`.
 */

/** Обычный день: задачи по частям дня, баннер незавершённого и завтрашний приём у врача. */
@PlannerScreenPreviews
@Composable
private fun TodayPreview() {
    val state = with(SampleData) {
        TodayUiState(
            today = today,
            sections = mapOf(
                DaySection.MORNING to listOf(callMom, doctor).map(DayItem::TaskItem),
                DaySection.DAY to listOf(documents, englishChapter).map(DayItem::TaskItem),
                // Вечер: повторения (бассейн и полив по циклу) — строки с ↻
                DaySection.EVENING to listOf(poolRule, wateringRule)
                    .mapNotNull { Recurrence.occurrence(it, today) }
                    .map(DayItem::RepeatItem),
                DaySection.ANYTIME to listOf(DayItem.TaskItem(buyEpin)),
            ),
            doneCount = 1,
            totalCount = 6,
            tomorrowCount = 3,
            tomorrowTimed = listOf(dentist),
            overdue = mapOf(OverdueGroup.YESTERDAY to listOf(forgotten)),
            showOverdueBanner = true,
            lists = lists,
            // Внизу — повторение на паузе (отпуск на неделю)
            paused = listOf(poolRule.copy(pauses = listOf(Pause(today.minusDays(1), today.plusDays(5))))),
            // Привычки: «Вода» уже отмечена (у неё есть задача-отметка), остальные — нет
            habits = HabitDay.on(
                habitRules, today,
                instances = listOf(Recurrence.toTask(Recurrence.occurrence(habitRules[0], today)!!, TaskStatus.DONE)),
            ),
        )
    }
    PlannerTheme { TodayContentForPreview(state) }
}

/** Пустой день: так экран выглядит у человека, который только поставил приложение. */
@PlannerScreenPreviews
@Composable
private fun TodayEmptyPreview() {
    PlannerTheme { TodayContentForPreview(TodayUiState(today = SampleData.today)) }
}

@Composable
private fun TodayContentForPreview(state: TodayUiState) = TodayContent(
    state = state,
    contentPadding = PaddingValues(),
    onOpenSettings = {},
    onOpenHabits = {},
    onOpenOverdue = {},
    onToggle = {},
    onCompleteOccurrence = {},
    onToggleHabit = {},
    onEditRule = {},
    onEdit = {},
    onSwipeTomorrow = {},
    onSwipeSchedule = {},
    onAdd = {},
)
