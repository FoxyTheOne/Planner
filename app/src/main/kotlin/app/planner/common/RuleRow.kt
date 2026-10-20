package app.planner.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.planner.core.designsystem.theme.PlannerTheme
import app.planner.core.domain.model.PartOfDay
import app.planner.core.domain.model.RecurrenceRule
import app.planner.core.domain.recurrence.Recurrence
import app.planner.core.domain.reminder.Reminders
import java.time.LocalDate
import java.time.LocalTime

/**
 * Строка самого повторения или привычки (а не одного её дня): значок, название, подпись,
 * а справа — колокольчик, если о днях повторения напоминают. Без галочки: отмечают дни
 * повторения на «Сегодня» и в «Неделе», а здесь — само расписание. Тап — редактор правила.
 *
 * Одна на все экраны: раздел «повторяется» в списке, экран «Привычки», привычки цели,
 * приостановленные повторения на «Сегодня». Раньше у каждого экрана была своя почти
 * одинаковая строка — четыре копии, которые легко было бы поправить в одном месте и забыть в других.
 *
 * Значок говорит, что это: ↻ — повторение, 🧘 — привычка, ⏸ — сейчас на паузе.
 *
 * @param subtitle подпись под названием — у каждого экрана своя: «Каждый день · вечер»,
 *   «За 30 дней — 18 раз», «На паузе до 12 октября».
 * @param faded бледнее обычного — так на «Сегодня» показаны приостановленные повторения.
 * @param extra что ещё показать под подписью (у привычки — полоска статистики за 30 дней).
 */
@Composable
fun RuleRow(
    rule: RecurrenceRule,
    today: LocalDate,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    faded: Boolean = false,
    extra: @Composable () -> Unit = {},
) {
    val paper = PlannerTheme.paper
    val paused = Recurrence.activePause(rule, today) != null
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
    ) {
        // Та же ширина 40dp, что у чекбокса в строке задачи — названия стоят ровно
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            Icon(
                when {
                    paused -> Icons.Outlined.PauseCircle
                    rule.isHabit -> Icons.Outlined.SelfImprovement
                    else -> Icons.Outlined.Repeat
                },
                null,
                tint = if (faded) paper.inkFaint else paper.inkMuted,
            )
        }
        Column(
            Modifier
                .padding(start = 10.dp, end = 8.dp)
                .weight(1f)
        ) {
            Text(
                rule.title,
                color = if (faded) paper.inkMuted else paper.ink,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                subtitle,
                color = if (faded) paper.inkFaint else paper.inkMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
            extra()
        }
        // Колокольчик — как у задач: о днях этого повторения напомнят. На паузе — не напомнят
        if (!paused && Reminders.hasReminder(rule)) {
            Icon(
                Icons.Outlined.NotificationsNone,
                null,
                tint = paper.inkMuted,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .size(16.dp),
            )
        }
    }
}

/** «Каждый день · утро», «Каждый год, 15 октября · 18:00» — как часто и когда повторяется правило. */
@Composable
fun ruleScheduleText(rule: RecurrenceRule): String =
    listOfNotNull(
        repeatSummary(rule.repeat),
        timeOfDayText(rule.time, rule.partOfDay)
    ).joinToString(" · ")

/** «10:30» или «утро»; null — в любое время. Точное время важнее части дня: оно и показывается. */
@Composable
fun timeOfDayText(time: LocalTime?, partOfDay: PartOfDay?): String? =
    time?.let(DateTexts::time) ?: partOfDay?.let { stringResource(it.labelRes()) }
