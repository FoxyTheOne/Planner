package app.planner.feature.today

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.planner.R
import app.planner.common.DateTexts
import app.planner.common.ScheduleSheet
import app.planner.common.TaskEditorSheet
import app.planner.common.TaskSaver
import app.planner.core.designsystem.component.DashedRule
import app.planner.core.designsystem.component.RepeatRow
import app.planner.core.designsystem.component.SectionLabel
import app.planner.core.designsystem.component.TaskRow
import app.planner.core.designsystem.theme.PlannerTheme
import app.planner.core.domain.model.Slot
import app.planner.core.domain.model.Task
import app.planner.core.domain.model.TaskList
import kotlinx.coroutines.launch

/**
 * Экран «Сегодня» — главный экран приложения.
 *
 * Сверху день недели и прогресс («2 из 5»), баннер незавершённого, затем задачи по частям дня:
 * утро, день, вечер, «в любое время». Внизу — превью завтрашнего дня (важно увидеть
 * заранее, например, приём у врача). Кнопка «+» добавляет задачу на сегодня.
 *
 * Жесты: свайп влево — перенести на завтра, свайп вправо — выбрать другую дату.
 * Тап по задаче — открыть редактор, тап по квадратику — отметить выполненной.
 *
 * @param contentPadding отступы от нижней панели навигации и системных полос (приходят из Scaffold).
 */
@Composable
fun TodayScreen(
    onOpenSettings: () -> Unit,
    contentPadding: PaddingValues,
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editing by rememberSaveable(stateSaver = TaskSaver) { mutableStateOf<Task?>(null) }
    var scheduling by rememberSaveable(stateSaver = TaskSaver) { mutableStateOf<Task?>(null) }
    var showOverdue by rememberSaveable { mutableStateOf(false) }

    TodayContent(
        state = state,
        contentPadding = contentPadding,
        onOpenSettings = onOpenSettings,
        onOpenOverdue = { showOverdue = true },
        onToggle = viewModel::toggle,
        onEdit = { editing = it },
        onSwipeTomorrow = viewModel::moveToTomorrow,
        onSwipeSchedule = { scheduling = it },
        onAdd = { editing = Task(title = "", date = state.today) },
    )

    editing?.let { task ->
        TaskEditorSheet(
            initial = task,
            lists = state.lists,
            today = state.today,
            onDismiss = { editing = null },
            onSave = { task, newList -> viewModel.save(task, newList); editing = null },
            onDelete = { viewModel.delete(it); editing = null },
        )
    }
    scheduling?.let { task ->
        ScheduleSheet(
            today = state.today,
            initial = task.date?.let { Slot(it, task.partOfDay, task.time) },
            onDismiss = { scheduling = null },
            onConfirm = { viewModel.schedule(task, it); scheduling = null },
        )
    }
    if (showOverdue) {
        OverdueSheet(
            overdue = state.overdue,
            today = state.today,
            onDismiss = { showOverdue = false },
            onSchedule = viewModel::schedule,
            onReturnToList = viewModel::returnToList,
            onAllToToday = { viewModel.allOverdueToToday(); showOverdue = false },
            onAllBack = { viewModel.allOverdueBackToLists(); showOverdue = false },
            onNotNow = { viewModel.hideOverdueBannerForToday(); showOverdue = false },
        )
    }
}

/**
 * Рисующая часть экрана «Сегодня»: получает готовое состояние и сообщает о нажатиях наверх.
 *
 * Здесь нет ViewModel и шторок — только разметка. Поэтому этот экран можно показать
 * в превью Android Studio на выдуманных данных (см. `TodayPreviews.kt`), не запуская приложение.
 * Такое разделение называется «поднятие состояния» (state hoisting).
 */
@Composable
fun TodayContent(
    state: TodayUiState,
    contentPadding: PaddingValues,
    onOpenSettings: () -> Unit,
    onOpenOverdue: () -> Unit,
    onToggle: (Task) -> Unit,
    onEdit: (Task) -> Unit,
    onSwipeTomorrow: (Task) -> Unit,
    onSwipeSchedule: (Task) -> Unit,
    onAdd: () -> Unit,
) {
    Box(Modifier
        .fillMaxSize()
        .padding(contentPadding)) {
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 96.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item { TodayHeader(state, onOpenSettings) }

            if (state.showOverdueBanner) {
                item { OverdueBanner(count = state.overdueCount, onClick = onOpenOverdue) }
            }

            if (state.isEmpty) {
                item {
                    Text(
                        stringResource(R.string.today_empty),
                        color = PlannerTheme.paper.inkMuted,
                        modifier = Modifier.padding(top = 32.dp),
                    )
                }
            }

            DaySection.entries.forEach { section ->
                val sectionItems = state.sections[section].orEmpty()
                if (sectionItems.isNotEmpty()) {
                    item(key = "s_$section") { SectionLabel(stringResource(section.labelRes())) }
                    items(sectionItems, key = { it.key }) { item ->
                        // when по sealed interface: задача — строка с чекбоксом и свайпами, повторение — строка с ↻
                        when (item) {
                            is TodayItem.TaskItem -> {
                                val task = item.task
                                SwipeableTaskRow(
                                    task = task,
                                    lists = state.lists,
                                    onToggle = { onToggle(task) },
                                    onClick = { onEdit(task) },
                                    onSwipeTomorrow = { onSwipeTomorrow(task) },
                                    onSwipeSchedule = { onSwipeSchedule(task) },
                                    modifier = Modifier.animateItem(),
                                )
                            }

                            is TodayItem.RepeatItem -> {
                                val rule = item.occurrence.rule
                                Column(Modifier.animateItem()) {
                                    RepeatRow(
                                        title = rule.title,
                                        details = item.occurrence.details,
                                        time = rule.time?.let(DateTexts::time),
                                        labelColor = labelColorOf(rule.listId, state.lists),
                                    )
                                    DashedRule()
                                }
                            }
                        }
                    }
                }
            }

            item { TomorrowPreview(state) }
        }

        FloatingActionButton(
            onClick = onAdd,
            containerColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
        ) { Icon(Icons.Outlined.Add, stringResource(R.string.cd_add_task)) }
    }
}

/** Заголовок: «Четверг», ниже «24 сентября · 1 из 5», справа шестерёнка настроек. */
@Composable
private fun TodayHeader(state: TodayUiState, onOpenSettings: () -> Unit) {
    Row(verticalAlignment = Alignment.Top) {
        Column(Modifier
            .weight(1f)
            .padding(top = 8.dp)) {
            Text(DateTexts.weekday(state.today), style = MaterialTheme.typography.headlineMedium)
            val progress = if (state.totalCount > 0) {
                " · " + stringResource(R.string.today_progress, state.doneCount, state.totalCount)
            } else ""
            Text(
                DateTexts.dayMonth(state.today) + progress,
                style = MaterialTheme.typography.bodyMedium,
                color = PlannerTheme.paper.inkMuted,
            )
        }
        IconButton(onClick = onOpenSettings) {
            Icon(
                Icons.Outlined.Settings,
                stringResource(R.string.cd_settings)
            )
        }
    }
}

/** Одна строка «Незавершённое: N · Разобрать». Не растёт, сколько бы хвостов ни накопилось. */
@Composable
private fun OverdueBanner(count: Int, onClick: () -> Unit) {
    val paper = PlannerTheme.paper
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(top = 12.dp)
            .fillMaxWidth()
            .background(paper.warningBg, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            pluralStringResource(R.plurals.overdue_banner, count, count),
            color = paper.warningInk,
            modifier = Modifier.weight(1f),
        )
        Text(
            stringResource(R.string.overdue_sort),
            color = paper.warningInk,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

/**
 * Строка задачи, которую можно смахнуть: влево — на завтра, вправо — выбрать дату.
 * Под строкой при смахивании видна подсказка, что произойдёт.
 *
 * SwipeToDismissBox — стандартный компонент «смахни, чтобы убрать». Мы используем его
 * только как жест: сами строку не убираем — после переноса задача исчезнет из «Сегодня»,
 * когда база пришлёт обновлённый список.
 */
@Composable
fun SwipeableTaskRow(
    task: Task,
    lists: List<TaskList>,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    onSwipeTomorrow: () -> Unit,
    onSwipeSchedule: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val paper = PlannerTheme.paper
    val state = rememberSwipeToDismissBoxState()
    val scope = rememberCoroutineScope()
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        // Сделанное не переносим — свайп только у открытых задач
        enableDismissFromStartToEnd = !task.isDone,
        enableDismissFromEndToStart = !task.isDone,
        onDismiss = { value ->
            // Строку не убираем сами: задача уедет из списка, когда обновится база
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> onSwipeTomorrow()
                SwipeToDismissBoxValue.StartToEnd -> onSwipeSchedule()
                SwipeToDismissBoxValue.Settled -> Unit
            }
            scope.launch { state.reset() }
        },
        backgroundContent = {
            val toTomorrow = state.dismissDirection == SwipeToDismissBoxValue.EndToStart
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (toTomorrow) Arrangement.End else Arrangement.Start,
                modifier = Modifier
                    .fillMaxSize()
                    .background(paper.highlight)
                    .padding(horizontal = 16.dp),
            ) {
                Icon(
                    if (toTomorrow) Icons.AutoMirrored.Outlined.ArrowForward else Icons.Outlined.CalendarMonth,
                    null,
                    tint = paper.inkMuted,
                )
                Text(
                    stringResource(if (toTomorrow) R.string.day_tomorrow else R.string.schedule_title),
                    color = paper.inkMuted,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        },
    ) {
        Column(Modifier.background(paper.paper)) {
            TaskRow(
                title = task.title,
                done = task.isDone,
                onToggle = onToggle,
                onClick = onClick,
                time = task.time?.let(DateTexts::time),
                labelColor = labelColorOf(task, lists),
                toggleDescription = stringResource(R.string.cd_mark_done),
            )
            DashedRule()
        }
    }
}

/** Цвет точки-метки задачи — цвет её списка. У «Входящих» точки нет. */
@Composable
fun labelColorOf(task: Task, lists: List<TaskList>) = labelColorOf(task.listId, lists)

/** Цвет точки-метки по id списка (у задач и у правил повторения). */
@Composable
fun labelColorOf(listId: Long?, lists: List<TaskList>) =
    lists.firstOrNull { it.id == listId && !it.isInbox }?.let { PlannerTheme.paper.label(it.color) }

/** «→ Завтра: 3 задачи» и отдельно события со временем — чтобы вечером не пропустить утренний приём. */
@Composable
private fun TomorrowPreview(state: TodayUiState) {
    if (state.tomorrowCount == 0) return
    val paper = PlannerTheme.paper
    Column(Modifier.padding(top = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = paper.inkMuted)
            Text(
                pluralStringResource(
                    R.plurals.tomorrow_preview,
                    state.tomorrowCount,
                    state.tomorrowCount
                ),
                color = paper.inkMuted,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        state.tomorrowTimed.forEach { task ->
            Text(
                "${DateTexts.time(task.time!!)}  ${task.title}",
                color = paper.ink,
                modifier = Modifier.padding(start = 32.dp, top = 4.dp),
            )
        }
    }
}

/** Строка-подпись раздела из ресурсов (на языке телефона). */
fun DaySection.labelRes(): Int = when (this) {
    DaySection.MORNING -> R.string.part_morning
    DaySection.DAY -> R.string.part_day
    DaySection.EVENING -> R.string.part_evening
    DaySection.ANYTIME -> R.string.part_anytime
}
