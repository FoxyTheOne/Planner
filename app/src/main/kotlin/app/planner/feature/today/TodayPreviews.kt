package app.planner.feature.today

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import app.planner.core.designsystem.preview.PlannerScreenPreviews
import app.planner.core.designsystem.theme.PlannerTheme
import app.planner.core.domain.overdue.OverdueGroup
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
                DaySection.MORNING to listOf(callMom, doctor),
                DaySection.DAY to listOf(documents, englishChapter),
                DaySection.EVENING to listOf(watering),
                DaySection.ANYTIME to listOf(buyEpin),
            ),
            doneCount = 1,
            totalCount = 6,
            tomorrowCount = 3,
            tomorrowTimed = listOf(dentist),
            overdue = mapOf(OverdueGroup.YESTERDAY to listOf(forgotten)),
            showOverdueBanner = true,
            lists = lists,
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
    onOpenOverdue = {},
    onToggle = {},
    onEdit = {},
    onSwipeTomorrow = {},
    onSwipeSchedule = {},
    onAdd = {},
)
