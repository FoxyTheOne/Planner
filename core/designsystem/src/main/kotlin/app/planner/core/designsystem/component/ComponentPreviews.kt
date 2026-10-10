package app.planner.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.planner.core.designsystem.preview.PlannerPreviews
import app.planner.core.designsystem.theme.PlannerTheme
import app.planner.core.domain.model.ListColor

/*
 * Превью общих компонентов. Откройте файл в Android Studio и включите вид «Split» —
 * справа появятся строки задач, чипы и подписи в светлой и тёмной теме.
 * Функции private: они нужны только Studio, из кода приложения их не вызывают.
 */

/** Обёртка для превью: тема и фон бумаги, чтобы компонент выглядел как в приложении. */
@Composable
private fun PreviewSurface(content: @Composable () -> Unit) {
    PlannerTheme {
        Column(Modifier
            .background(PlannerTheme.paper.paper)
            .padding(16.dp)) { content() }
    }
}

@PlannerPreviews
@Composable
private fun TaskRowPreview() = PreviewSurface {
    SectionLabel("утро")
    TaskRow(title = "Позвонить маме", done = true, onToggle = {})
    DashedRule()
    TaskRow(title = "Врач, каб. 214", done = false, onToggle = {}, time = "10:30")
    DashedRule()
    SectionLabel("день")
    TaskRow(
        title = "Разобрать документы",
        done = false,
        onToggle = {},
        labelColor = PlannerTheme.paper.label(ListColor.TERRACOTTA),
    )
    DashedRule()
    TaskRow(
        title = "Очень длинное название задачи, которое не помещается в одну строку и переносится",
        done = false,
        onToggle = {},
        labelColor = PlannerTheme.paper.label(ListColor.DUSTY_BLUE),
    )
}

/** Дни повторений: бассейн (ещё не отмечен) и полив по циклу с делами этой недели (уже отмечен). */
@PlannerPreviews
@Composable
private fun RepeatRowPreview() = PreviewSurface {
    TaskRow(title = "Бассейн", done = false, onToggle = {}, time = "18:30", repeating = true)
    DashedRule()
    TaskRow(
        title = "Полив",
        done = true,
        onToggle = {},
        subtitle = "фикус, монстера",
        repeating = true,
        labelColor = PlannerTheme.paper.label(ListColor.SAGE),
    )
}

/** Как задача выглядит в списке, когда она уже запланирована: серая, с датой и кнопкой справа. */
@PlannerPreviews
@Composable
private fun ScheduledInListPreview() = PreviewSurface {
    TaskRow(
        title = "Распечатать фото",
        done = false,
        onToggle = {},
        dimmed = true,
        trailingBadge = "Пт 25",
        trailing = { Icon(Icons.Outlined.CalendarMonth, null, tint = PlannerTheme.paper.inkMuted) },
    )
}

@PlannerPreviews
@Composable
private fun ChipsPreview() = PreviewSurface {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PaperChip("Сегодня", selected = false, onClick = {})
        PaperChip("Завтра", selected = true, onClick = {})
        PaperChip("Выходные", selected = false, onClick = {})
    }
}
