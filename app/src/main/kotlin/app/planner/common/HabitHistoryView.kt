package app.planner.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import app.planner.R
import app.planner.core.designsystem.theme.PlannerTheme
import app.planner.core.domain.recurrence.HabitHistory

/**
 * Мягкая статистика привычки: строка «За 30 дней — 18 раз» и полоска квадратиков, день за днём.
 *
 * Квадратик дня:
 * - закрашен — привычку отметили;
 * - только рамка — в этот день она выпадала, но отметки нет;
 * - пусто — в этот день её не было (пауза или не её день недели). Пауза видна «дыркой» в полоске.
 *
 * Чего здесь нарочно нет: «из 26», процентов и серий — почему, см. [HabitHistory].
 *
 * Полоску TalkBack пропускает (`clearAndSetSemantics {}`): 30 квадратиков подряд вслух —
 * это шум. Главное он прочитает в строке над ней.
 */
@Composable
fun HabitHistoryView(history: HabitHistory, modifier: Modifier = Modifier) {
    val paper = PlannerTheme.paper
    val accent = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outline
    Column(modifier) {
        Text(
            habitHistoryText(history),
            color = paper.inkMuted,
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .clearAndSetSemantics {},
        ) {
            val shape = RoundedCornerShape(2.dp)
            // Всегда 30 мест, даже если привычке три дня: квадратики одного размера, новые — справа.
            // Дни до начала привычки — пустые места слева
            repeat(HabitHistory.DAYS - history.days.size) { Box(Modifier.weight(1f)) }
            history.days.forEach { day ->
                Box(
                    Modifier
                        .weight(1f)
                        .aspectRatio(1f) // высота = ширине: квадрат
                        .then(
                            when (day) {
                                in history.done -> Modifier.background(accent, shape)
                                in history.planned -> Modifier.border(1.dp, outline, shape)
                                else -> Modifier
                            },
                        ),
                )
            }
        }
    }
}

/** «За 30 дней — 18 раз», «С 10 октября — 2 раза» или «Отметок пока нет». */
@Composable
private fun habitHistoryText(history: HabitHistory): String {
    val count = history.doneCount
    return when {
        count == 0 -> stringResource(R.string.habit_stats_none)
        // У plurals два «числа»: первое выбирает форму слова (раз / раза), остальные — то, что подставить в текст
        history.startedRecently ->
            pluralStringResource(
                R.plurals.habit_stats_since,
                count,
                DateTexts.dayMonth(history.from),
                count
            )

        else -> pluralStringResource(R.plurals.habit_stats_month, count, count)
    }
}
