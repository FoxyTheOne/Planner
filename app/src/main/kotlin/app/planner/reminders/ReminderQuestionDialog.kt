package app.planner.reminders

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import app.planner.R
import app.planner.common.DateTexts
import app.planner.common.PlannerTimePickerDialog
import app.planner.core.domain.model.Reminder
import kotlinx.coroutines.flow.SharedFlow

/**
 * Окно «Задачу перенесли — что сделать с напоминанием?». Показывается, когда у задачи было
 * напоминание «в 9:00», а при переносе поменялось время дня (утро → вечер, новое точное время).
 * Когда именно спрашивать, решает домен (Reminders.askAfterMove), а кладёт вопрос TaskCommands.
 *
 * Варианты — каждый говорит прямо, что произойдёт:
 * - «Оставить в 9:00» — как было (так же, если окно просто закрыть);
 * - «За 30 минут до начала» — только если у задачи теперь есть точное время. Такое напоминание
 *   само переезжает вместе с задачей, и больше спрашивать про него не придётся;
 * - «Другое время…» — выбрать час в циферблате;
 * - «Убрать напоминание».
 *
 * Вопрос живёт в `remember`, а не в rememberSaveable: если повернуть экран, окно закроется,
 * а напоминание останется как было — ровно то же, что «закрыть окно». Ничего не теряется.
 */
@Composable
fun ReminderQuestionDialog(
    questions: SharedFlow<ReminderQuestion>,
    onAnswer: (taskId: Long, reminder: Reminder?) -> Unit,
) {
    var question by remember { mutableStateOf<ReminderQuestion?>(null) }
    var pickTime by remember { mutableStateOf(false) }
    // Ждём вопросы всё время, пока каркас приложения на экране (как SnackbarMessages)
    LaunchedEffect(Unit) { questions.collect { question = it } }

    val q = question ?: return
    fun answer(reminder: Reminder?) {
        onAnswer(q.taskId, reminder)
        question = null
        pickTime = false
    }
    val oldTime = DateTexts.time(q.reminderTime)

    if (pickTime) {
        PlannerTimePickerDialog(
            // Подсказка в циферблате: за полчаса до нового времени задачи, а если его нет — прежний час
            initial = q.taskTime?.minusMinutes(30) ?: q.reminderTime,
            onPick = { answer(Reminder.AtTime(it)) },
            onDismiss = { pickTime = false },
        )
        return
    }

    AlertDialog(
        // Закрыли окно, не выбрав, — напоминание остаётся как было
        onDismissRequest = { question = null },
        title = { Text(stringResource(R.string.reminder_question_title)) },
        text = { Text(stringResource(R.string.reminder_question_text, q.title, oldTime)) },
        confirmButton = {
            // Четыре варианта — столбиком, по правому краю, как обычные кнопки окна
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = { answer(Reminder.AtTime(q.reminderTime)) }) {
                    Text(stringResource(R.string.reminder_keep, oldTime))
                }
                if (q.taskTime != null) {
                    TextButton(onClick = { answer(Reminder.BeforeTime(30)) }) {
                        Text(stringResource(R.string.reminder_half_hour_before))
                    }
                }
                TextButton(onClick = { pickTime = true }) {
                    Text(stringResource(R.string.reminder_other_time))
                }
                TextButton(onClick = { answer(null) }) {
                    Text(stringResource(R.string.reminder_remove))
                }
            }
        },
    )
}
