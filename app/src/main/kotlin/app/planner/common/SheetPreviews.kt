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
import app.planner.core.domain.model.Repeat
import app.planner.core.domain.model.Slot
import app.planner.core.domain.model.Task
import app.planner.core.domain.overdue.OverdueGroup
import app.planner.core.domain.recurrence.Recurrence
import app.planner.feature.today.OverdueSheetBody
import app.planner.preview.SampleData

/*
 * Превью содержимого шторок (без самой выезжающей рамки — её обычное превью не рисует).
 * Откройте файл в Android Studio и включите вид «Split».
 */

/** Фон как у шторки: чуть темнее бумаги, с отступом сверху. */
@Composable
private fun SheetSurface(content: @Composable () -> Unit) {
    PlannerTheme {
        Box(Modifier
            .background(PlannerTheme.paper.highlight)
            .padding(top = 16.dp)) { content() }
    }
}

/** «Запланировать»: выбрано «Завтра», утро. */
@PlannerPreviews
@Composable
private fun ScheduleSheetPreview() = SheetSurface {
    ScheduleSheetBody(
        today = SampleData.today,
        initial = Slot(SampleData.today.plusDays(1), PartOfDay.MORNING),
        onConfirm = {},
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
        onSave = { _, _, _ -> },
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
        onSave = { _, _, _ -> },
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
        onSave = { _, _, _ -> },
        onDelete = {},
        initialRepeat = SampleData.poolRule.repeat,
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

/** Шторка из недели: свободный слот в пятницу, вкладка «Из списков». */
@PlannerPreviews
@Composable
private fun PickTaskSheetPreview() = SheetSurface {
    PickTaskSheetBody(
        slot = Slot(SampleData.today.plusDays(1)),
        today = SampleData.today,
        candidates = listOf(
            SampleData.readDune,
            SampleData.buyBook,
            SampleData.printPhotos.copy(date = null)
        ),
        lists = SampleData.lists,
        onPick = {},
        onCreate = {},
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
