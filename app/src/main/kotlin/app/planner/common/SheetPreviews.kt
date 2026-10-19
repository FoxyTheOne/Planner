package app.planner.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.planner.core.designsystem.preview.PlannerPreviews
import app.planner.core.designsystem.theme.PlannerTheme
import app.planner.core.domain.model.PartOfDay
import app.planner.core.domain.model.Reminder
import app.planner.core.domain.model.Repeat
import app.planner.core.domain.model.Slot
import app.planner.core.domain.model.Task
import app.planner.core.domain.overdue.OverdueGroup
import app.planner.core.domain.recurrence.HabitHistory
import app.planner.core.domain.recurrence.Recurrence
import app.planner.feature.today.OverdueSheetBody
import app.planner.preview.SampleData
import java.time.LocalTime

/*
 * Превью содержимого шторок (без самой выезжающей рамки — её обычное превью не рисует).
 * Откройте файл в Android Studio и включите вид «Split».
 */

/** Фон как у шторки: чуть темнее бумаги, с отступом сверху. */
@Composable
private fun SheetSurface(content: @Composable () -> Unit) {
    PlannerTheme {
        Box(
            Modifier
                .background(PlannerTheme.paper.highlight)
                .padding(top = 16.dp)
        ) { content() }
    }
}

/** «Запланировать»: выбрано «Завтра», утро. */
@PlannerPreviews
@Composable
private fun ScheduleSheetPreview() = SheetSurface {
    ScheduleSheetBody(
        today = SampleData.today,
        initial = Slot(SampleData.today.plusDays(1), PartOfDay.MORNING),
        onConfirm = { _, _ -> },
        withReminder = true,
        initialReminder = Reminder.AtTime(LocalTime.of(9, 0)),
    )
}

/** Новая задача в списке «Книги». */
@PlannerPreviews
@Composable
private fun TaskEditorNewPreview() = SheetSurface {
    TaskEditorSheetBody(
        initial = Task(title = "", listId = SampleData.books.id),
        lists = SampleData.lists,
        today = SampleData.today,
        onSave = {},
        onDelete = null,
    )
}

/** Существующая задача с датой и заметкой: видна кнопка «Удалить». */
@PlannerPreviews
@Composable
private fun TaskEditorExistingPreview() = SheetSurface {
    TaskEditorSheetBody(
        initial = SampleData.documents.copy(note = "Паспорт, страховка, квитанции"),
        lists = SampleData.lists,
        today = SampleData.today,
        onSave = {},
        onDelete = {},
    )
}

/** Повторение, открытое для правки: бассейн по вторникам и четвергам. Внизу строка «↻ По дням: вт, чт». */
@PlannerPreviews
@Composable
private fun TaskEditorRepeatPreview() = SheetSurface {
    TaskEditorSheetBody(
        initial = Recurrence.draftFromRule(SampleData.poolRule),
        lists = SampleData.lists,
        today = SampleData.today,
        onSave = {},
        onDelete = {},
        initialRepeat = SampleData.poolRule.repeat,
    )
}

/**
 * Привычка «Вода», открытая долгим нажатием: переключатель включён, под ним статистика.
 * Отмечена через день, а неделю назад была пауза — в полоске видна «дырка».
 */
@PlannerPreviews
@Composable
private fun TaskEditorHabitPreview() = SheetSurface {
    val water = SampleData.habitRules[0]
    val today = SampleData.today
    val history = HabitHistory.of(water, emptyList(), today).let { full ->
        val paused = (7L..11L).map { today.minusDays(it) }.toSet()
        full.copy(
            planned = full.planned - paused, // минус для множества — «все, кроме этих»
            done = full.days.filterIndexed { i, day -> i % 2 == 0 && day !in paused }.toSet(),
        )
    }
    TaskEditorSheetBody(
        initial = Recurrence.draftFromRule(water),
        lists = SampleData.lists,
        today = today,
        onSave = {},
        onDelete = {},
        initialRepeat = water.repeat,
        initialHabit = true,
        habitHistory = history,
    )
}

/** Шторка «Повторять» с выбранными днями недели. */
@PlannerPreviews
@Composable
private fun RepeatSheetPreview() = SheetSurface {
    RepeatSheetBody(initial = SampleData.poolRule.repeat, today = SampleData.today, onDone = {})
}

/** Редактор цикла полива: 2 недели, открыта неделя 2 — четверг с подкормкой. */
@PlannerPreviews
@Composable
private fun CycleEditorPreview() = SheetSurface {
    CycleEditorSheetBody(
        initial = SampleData.wateringRule.repeat as Repeat.Cycle,
        today = SampleData.today,
        onDone = {})
}

/** Шторка «Пауза»: на неделю, на 2 недели, на месяц, до даты, пока не продолжу. */
@PlannerPreviews
@Composable
private fun PauseSheetPreview() = SheetSurface {
    PauseSheetBody(today = SampleData.today, onPause = {})
}

/** Окно «Добавить» из недели: завтрашний день, ниже — готовые задачи из списков. */
@PlannerPreviews
@Composable
private fun AddTaskSheetPreview() = SheetSurface {
    AddTaskSheetBody(
        title = slotText(Slot(SampleData.today.plusDays(1)), SampleData.today),
        candidates = listOf(
            SampleData.readDune,
            SampleData.buyBook,
            SampleData.printPhotos.copy(date = null)
        ),
        lists = SampleData.lists,
        onPick = {},
        onCreate = {},
        autoFocus = false,
    )
}

/** Разбор незавершённого: вчерашний хвост и задача недельной давности. */
@PlannerPreviews
@Composable
private fun OverdueSheetPreview() = SheetSurface {
    OverdueSheetBody(
        overdue = mapOf(
            OverdueGroup.YESTERDAY to listOf(SampleData.forgotten),
            OverdueGroup.THIS_WEEK to listOf(
                SampleData.documents.copy(
                    date = SampleData.today.minusDays(
                        4
                    )
                )
            ),
        ),
        today = SampleData.today,
        onSchedule = { _, _ -> },
        onReturnToList = {},
        onAllToToday = {},
        onAllBack = {},
        onNotNow = {},
    )
}
