package app.planner.feature.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.planner.R
import app.planner.common.DateTexts
import app.planner.core.designsystem.theme.PlannerTheme
import app.planner.core.domain.goal.DeadlineState
import app.planner.core.domain.goal.GoalProgress
import app.planner.core.domain.model.Goal
import java.time.LocalDate

/*
 * Кусочки, общие для карточки цели (экран «Цели») и экрана одной цели: строка срока и полоска прогресса.
 * internal — видны во всём модуле app, но это не «общие компоненты» дизайн-системы: они знают про цели.
 */

/**
 * Строка срока. Мягкое напоминание:
 * - впереди — «до 31 декабря · ещё 2 месяца» серым; когда осталось ≤ 14 дней — цветом кнопок, но не красным;
 * - прошёл — нейтральное «срок был 31 декабря», без «просрочено на 23 дня»;
 * - срока нет — строки нет совсем.
 */
@Composable
internal fun DeadlineLine(goal: Goal, state: DeadlineState, today: LocalDate) {
    val deadline = goal.deadline ?: return
    val date = DateTexts.dayMonthYear(deadline, today)
    val paper = PlannerTheme.paper
    val (text, color) = when (state) {
        is DeadlineState.Ahead -> stringResource(
            R.string.deadline_ahead,
            date,
            remainingText(state.daysLeft)
        ) to
                if (state.isSoon) MaterialTheme.colorScheme.primary else paper.inkMuted

        DeadlineState.Passed -> stringResource(R.string.deadline_passed, date) to paper.inkMuted
        DeadlineState.None -> return
    }
    Text(
        text,
        color = color,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(top = 2.dp)
    )
}

/**
 * «сегодня», «ещё 10 дней», «ещё 2 месяца». До месяца — днями (так точнее и понятнее),
 * дальше — месяцами: «ещё 74 дня» никто не переводит в голове в «два с половиной месяца».
 */
@Composable
private fun remainingText(daysLeft: Int): String = when {
    daysLeft == 0 -> stringResource(R.string.deadline_today)
    daysLeft < 31 -> pluralStringResource(R.plurals.deadline_days_left, daysLeft, daysLeft)
    else -> (daysLeft / 30).let { pluralStringResource(R.plurals.deadline_months_left, it, it) }
}

/**
 * Тонкая полоска прогресса. Своя, а не LinearProgressIndicator из Material: у той свои отступы
 * и «точка» на конце, а здесь нужна простая линия в бумажном стиле.
 * fillMaxWidth(доля) — закрашенная часть занимает такую долю ширины полоски.
 */
@Composable
internal fun ProgressBar(progress: GoalProgress, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(2.dp)
    Box(
        modifier
            .height(4.dp)
            .background(PlannerTheme.paper.rule, shape)
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.fraction)
                .height(4.dp)
                .background(MaterialTheme.colorScheme.primary, shape)
        )
    }
}
