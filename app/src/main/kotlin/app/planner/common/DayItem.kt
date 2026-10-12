package app.planner.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.planner.R
import app.planner.core.designsystem.component.TaskRow
import app.planner.core.designsystem.theme.PlannerTheme
import app.planner.core.domain.model.RecurrenceRule
import app.planner.core.domain.model.Task
import app.planner.core.domain.model.TaskList
import app.planner.core.domain.model.TaskStatus
import app.planner.core.domain.recurrence.Occurrence
import app.planner.core.domain.recurrence.Recurrence
import java.time.LocalTime

/**
 * Строка дня: обычная задача или день повторения (полив, бассейн).
 * Одна и та же для «Сегодня», «Недели» и почасового «Дня».
 *
 * Это снова `sealed interface`, как Repeat: вариантов ровно два, и у каждого свои данные.
 * Экран делает `when (item)`: задачу можно отметить, открыть, смахнуть; день повторения — отметить.
 */
sealed interface DayItem {
    /** Ключ для LazyColumn. У задачи и правила id могут совпасть (оба 1), поэтому с приставкой. */
    val key: String

    /** Время, по которому строка встаёт в дне; null — без времени, после тех, у кого оно есть. */
    val time: LocalTime?

    /** Сделанное опускается вниз раздела. */
    val isDone: Boolean

    data class TaskItem(val task: Task) : DayItem {
        override val key get() = "task_${task.id}"
        override val time get() = task.time
        override val isDone get() = task.isDone
    }

    /** День повторения, ещё не отмеченный: строится по правилу, в базе его нет. */
    data class RepeatItem(val occurrence: Occurrence) : DayItem {
        override val key get() = "rule_${occurrence.rule.id}"
        override val time get() = occurrence.rule.time

        // Строка по правилу всегда «не сделано»: отмеченный день становится задачей (TaskItem)
        override val isDone get() = false
    }
}

/**
 * Строка как задача — для действий, которые умеют работать только с задачами (перенос, «Запланировать»).
 * У дня повторения это черновик: задача с id = 0, ruleId и occurrenceDate (см. Recurrence.toTask).
 * Сохранят его — и день повторения станет настоящей задачей на новой дате.
 */
fun DayItem.asTask(): Task = when (this) {
    is DayItem.TaskItem -> task
    is DayItem.RepeatItem -> Recurrence.toTask(occurrence, TaskStatus.OPEN)
}

/**
 * Задачи и дни повторений одного дня — в одну кучу, по времени (без времени — в конце).
 * sortedBy «устойчивый»: у кого время одинаковое, остаются в том порядке, в каком пришли из базы.
 */
fun dayItems(tasks: List<Task>, occurrences: List<Occurrence>): List<DayItem> =
    (tasks.map { DayItem.TaskItem(it) } + occurrences.map { DayItem.RepeatItem(it) })
        .sortedWith(compareBy(nullsLast()) { it.time })

/**
 * Строка дня для «Недели» и «Дня»: задача или день повторения — оба через [TaskRow].
 * У дня повторения справа значок ↻, у цикла под названием — дела этого дня.
 * («Сегодня» рисует задачи сам — со свайпами, а дни повторений — этой же функцией.)
 *
 * @param onEditRule тап по неотмеченному дню повторения — открыть само правило в редакторе.
 */
@Composable
fun DayItemRow(
    item: DayItem,
    lists: List<TaskList>,
    onToggle: (Task) -> Unit,
    onCompleteOccurrence: (Occurrence) -> Unit,
    onEdit: (Task) -> Unit,
    onEditRule: (RecurrenceRule) -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.cd_mark_done)
    when (item) {
        is DayItem.TaskItem -> {
            val task = item.task
            TaskRow(
                title = task.title,
                done = task.isDone,
                onToggle = { onToggle(task) },
                onClick = { onEdit(task) },
                modifier = modifier,
                time = task.time?.let(DateTexts::time),
                // У дня повторения — значок ↻ и дела этого дня (они лежат в заметке),
                // а у перенесённого ещё и «вместо ср 7»: видно, откуда он взялся
                subtitle = if (task.isOccurrence) occurrenceSubtitle(task) else null,
                repeating = task.isOccurrence,
                labelColor = labelColorOf(task.listId, lists),
                toggleDescription = description,
            )
        }
        is DayItem.RepeatItem -> {
            val rule = item.occurrence.rule
            TaskRow(
                title = rule.title,
                done = false,
                onToggle = { onCompleteOccurrence(item.occurrence) },
                onClick = { onEditRule(rule) },
                modifier = modifier,
                time = rule.time?.let(DateTexts::time),
                subtitle = item.occurrence.details,
                repeating = true,
                labelColor = labelColorOf(rule.listId, lists),
                toggleDescription = description,
            )
        }
    }
}

/**
 * Вторая строка дня повторения: дела цикла и, если день перенесли, — с какого дня.
 * «фикус · вместо ср 7». Если в среду и четверг по правилу бассейн, а средний перенесли на четверг,
 * в четверг будет две строки — и подпись объясняет, почему.
 */
@Composable
private fun occurrenceSubtitle(task: Task): String? {
    val from = task.occurrenceDate
    val moved = if (from != null && from != task.date) {
        stringResource(R.string.occurrence_moved_from, DateTexts.shortDay(from).lowercase())
    } else null
    return listOfNotNull(task.note.ifEmpty { null }, moved).joinToString(" · ").ifEmpty { null }
}

/** Цвет точки-метки по id списка (у задач и у правил повторения). У «Без списка» точки нет. */
@Composable
fun labelColorOf(listId: Long?, lists: List<TaskList>) =
    lists.firstOrNull { it.id == listId && !it.isInbox }?.let { PlannerTheme.paper.label(it.color) }
