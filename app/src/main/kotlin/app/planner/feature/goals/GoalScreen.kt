package app.planner.feature.goals

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.planner.R
import app.planner.common.DateTexts
import app.planner.common.EditorTarget
import app.planner.common.EditorTargetSaver
import app.planner.common.GoalSaver
import app.planner.common.PlannerDatePickerDialog
import app.planner.common.RuleRow
import app.planner.common.ScheduleSheet
import app.planner.common.TaskEditorSheet
import app.planner.common.TaskSaver
import app.planner.common.habitHistoryText
import app.planner.common.notePreview
import app.planner.common.pausedText
import app.planner.core.designsystem.component.DashedRule
import app.planner.core.designsystem.component.PaperChip
import app.planner.core.designsystem.component.SectionLabel
import app.planner.core.designsystem.component.TaskRow
import app.planner.core.designsystem.theme.PlannerTheme
import app.planner.core.domain.goal.DeadlineState
import app.planner.core.domain.goal.StepGroup
import app.planner.core.domain.model.Goal
import app.planner.core.domain.model.GoalStage
import app.planner.core.domain.model.RecurrenceRule
import app.planner.core.domain.model.Repeat
import app.planner.core.domain.model.Slot
import app.planner.core.domain.model.Task
import app.planner.core.domain.recurrence.Recurrence
import app.planner.core.domain.reminder.Reminders
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * Экран одной цели: «Зачем», срок и прогресс, а ниже — шаги по этапам.
 *
 * Шаг — обычная задача: квадратик отмечает, тап открывает редактор (тот же, что у задач,
 * только без списка и повторения), значок календаря — «Запланировать». Запланированный шаг
 * становится серым с датой: он уже в плане дня, но по-прежнему виден в своей цели.
 *
 * Шаги добавляются прямо в своём этапе — строкой «+ Добавить шаг» под ним. После Enter
 * поле остаётся открытым: так удобно записать подряд пять шагов, не открывая ничего заново.
 *
 * @param onBack вернуться к списку целей (и если цель удалили, пока экран был открыт).
 */
@Composable
fun GoalScreen(
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    viewModel: GoalViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editingGoal by rememberSaveable(stateSaver = GoalSaver) { mutableStateOf<Goal?>(null) }
    var editingStep by rememberSaveable(stateSaver = TaskSaver) { mutableStateOf<Task?>(null) }
    var scheduling by rememberSaveable(stateSaver = TaskSaver) { mutableStateOf<Task?>(null) }
    var addingStage by rememberSaveable { mutableStateOf(false) }
    // Какой этап открыт в окне — по id: число rememberSaveable хранит сам, без отдельного Saver
    var editingStageId by rememberSaveable { mutableStateOf<Long?>(null) }
    var pickingDeadline by rememberSaveable { mutableStateOf(false) }
    // Привычка цели в редакторе — тот же EditorTarget, что в «Сегодня»; null — редактор закрыт
    var editingHabit by rememberSaveable(stateSaver = EditorTargetSaver) {
        mutableStateOf<EditorTarget?>(
            null
        )
    }

    // Цели больше нет (удалили) — возвращаемся к списку целей, как экран списка
    val goal = state.goal
    if (state.loaded && goal == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    GoalContent(
        state = state,
        contentPadding = contentPadding,
        onBack = onBack,
        onEditGoal = { editingGoal = it },
        onToggle = viewModel::toggle,
        onEditStep = { editingStep = it },
        onSchedule = { scheduling = it },
        onAddStep = viewModel::addStep,
        onAddStage = { addingStage = true },
        onEditStage = { editingStageId = it.id },
        onReorder = viewModel::reorder,
        onOpenHabit = { editingHabit = EditorTarget.forRule(it, state.today) },
        // Новая привычка этой цели: «каждый день», переключатель «Привычка» включён, цель выбрана —
        // остаётся написать название
        onNewHabit = {
            editingHabit = EditorTarget(
                task = Task(title = ""),
                repeat = Repeat.Daily,
                isHabit = true,
                habitGoalId = state.goal?.id,
            )
        },
        // when по sealed interface: компилятор проверит, что разобраны все действия с целью
        onGoalAction = { action ->
            when (action) {
                GoalAction.Achieve -> viewModel.achieve()
                GoalAction.Reopen -> viewModel.reopen()
                GoalAction.NewDeadline -> pickingDeadline = true
                GoalAction.NoDeadline -> viewModel.setDeadline(null)
                GoalAction.Delete -> viewModel.deleteGoal()
            }
        },
    )

    editingHabit?.let { target ->
        TaskEditorSheet(
            target = target,
            lists = state.lists,
            today = state.today,
            onDismiss = { editingHabit = null },
            onSave = viewModel::saveHabit,
            onDelete = viewModel::deleteHabit,
            onPause = viewModel::pauseHabit,
            onResume = viewModel::resumeHabit,
            habitHistory = state.habits.firstOrNull { it.rule.id == target.task.ruleId }?.history,
            // Здесь можно связать только с этой целью (или отвязать); перевязать к другой — из «Сегодня»
            goals = listOfNotNull(state.goal),
        )
    }

    if (pickingDeadline) {
        PlannerDatePickerDialog(
            // Новый срок обычно позже старого — календарь открывается через месяц от сегодня
            initial = state.today.plusMonths(1),
            onPick = { viewModel.setDeadline(it); pickingDeadline = false },
            onDismiss = { pickingDeadline = false },
        )
    }

    editingGoal?.let { g ->
        GoalEditorSheet(
            initial = g,
            today = state.today,
            onDismiss = { editingGoal = null },
            onSave = { viewModel.saveGoal(it); editingGoal = null },
        )
    }
    editingStep?.let { step ->
        TaskEditorSheet(
            initial = step,
            // Список у шага не выбирают (строки списка в редакторе для шага нет) — списки не нужны
            lists = emptyList(),
            today = state.today,
            onDismiss = { editingStep = null },
            onSave = { viewModel.saveStep(it); editingStep = null },
            onDelete = { viewModel.deleteStep(it); editingStep = null },
        )
    }
    scheduling?.let { step ->
        ScheduleSheet(
            today = state.today,
            // Уже запланированный шаг — шторка открывается на его дне и времени, а не «с нуля»
            initial = step.date?.let { Slot(it, step.partOfDay, step.time) },
            onDismiss = { scheduling = null },
            onConfirm = { slot, reminder ->
                viewModel.schedule(step, slot, reminder); scheduling = null
            },
            withReminder = true,
            initialReminder = step.reminder,
        )
    }
    if (addingStage) {
        StageDialog(
            onDismiss = { addingStage = false },
            onSave = { viewModel.addStage(it); addingStage = false },
        )
    }
    // Окно этапа ищет сам этап по id в свежих данных: переименовали — в окне новое название,
    // этапа больше нет — firstOrNull вернёт null, и окно просто не покажется
    state.stages.firstOrNull { it.id == editingStageId }?.let { stage ->
        StageDialog(
            initial = stage.title,
            onDismiss = { editingStageId = null },
            onSave = { viewModel.renameStage(stage.copy(title = it)); editingStageId = null },
            onDelete = { viewModel.deleteStage(stage); editingStageId = null },
        )
    }
}

/** Рисующая часть экрана цели: только разметка, без ViewModel и шторок (для превью, см. `GoalsPreviews.kt`). */
@Composable
fun GoalContent(
    state: GoalUiState,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onEditGoal: (Goal) -> Unit,
    onToggle: (Task) -> Unit,
    onEditStep: (Task) -> Unit,
    onSchedule: (Task) -> Unit,
    onAddStep: (title: String, stageId: Long?) -> Unit,
    onAddStage: () -> Unit,
    onEditStage: (GoalStage) -> Unit,
    onReorder: (List<StepGroup>) -> Unit,
    onOpenHabit: (RecurrenceRule) -> Unit,
    onNewHabit: () -> Unit,
    onGoalAction: (GoalAction) -> Unit,
) {
    val paper = PlannerTheme.paper
    val goal = state.goal
    Column(
        Modifier
            .fillMaxSize()
            .padding(contentPadding)
    ) {
        // Шапка как у экрана списка: назад, название, карандаш
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, end = 8.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.cd_back))
            }
            Text(
                goal?.title.orEmpty(),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            if (goal != null) {
                IconButton(onClick = { onEditGoal(goal) }) {
                    Icon(
                        Icons.Outlined.Edit,
                        stringResource(R.string.cd_edit_goal),
                        tint = paper.inkMuted
                    )
                }
                GoalMenu(achieved = goal.isAchieved, onGoalAction = onGoalAction)
            }
        }

        // Поле «Добавить шаг» стоит прямо в списке, и у нижних этапов клавиатура закрыла бы его.
        // imePadding — отступ снизу на высоту клавиатуры: список становится короче, и Compose
        // сам прокручивает его так, чтобы поле с курсором было видно над клавиатурой.
        // consumeWindowInsets(contentPadding) — «нижнюю панель мы уже учли», иначе к высоте
        // клавиатуры прибавилась бы ещё и высота панели вкладок, и между ними была бы дыра
        // ПЕРЕТАСКИВАНИЕ ШАГОВ (библиотека Reorderable)
        // rows — строки экрана (см. GoalRows.kt). Пока шаг тащат, порядок меняется только здесь,
        // в памяти экрана; в базу он записывается один раз — когда шаг отпустили (onReorder).
        // remember(state.groups): пришли новые данные из базы — строки собираются заново из них
        var rows by remember(state.groups) { mutableStateOf(goalRows(state.groups)) }
        val lazyListState = rememberLazyListState()
        // Лёгкая вибрация: «взяли», «сдвинулся на строку», «отпустили» — пальцем чувствуешь, что происходит
        val haptic = LocalHapticFeedback.current
        val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
            // from / to — элементы LazyColumn; их key — те же ключи, что у строк (GoalRow.key)
            rows = rows.moved(fromKey = from.key as String, toKey = to.key as String)
            haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        }

        fun commit(newRows: List<GoalRow>) {
            rows = newRows
            onReorder(stepGroupsOf(newRows))
        }

        LazyColumn(
            state = lazyListState,
            contentPadding = PaddingValues(start = 20.dp, end = 12.dp, top = 4.dp, bottom = 32.dp),
            modifier = Modifier
                .consumeWindowInsets(contentPadding)
                .imePadding(),
        ) {
            item(key = "header") { GoalHeader(state, onGoalAction) }

            if (state.loaded && state.steps.isEmpty()) {
                item(key = "hint") {
                    Text(
                        stringResource(R.string.goal_steps_hint),
                        color = paper.inkMuted,
                        modifier = Modifier.padding(top = 16.dp, end = 8.dp),
                    )
                }
            }

            // Каждая строка — ReorderableItem: шаги тащат, а мимо заголовков и «+ Добавить шаг»
            // их можно протащить (поэтому эти строки тоже «участвуют», хотя сами не двигаются)
            items(rows, key = { it.key }) { row ->
                ReorderableItem(reorderState, key = row.key) { isDragging ->
                    when (row) {
                        is GoalRow.StageHeader -> StageHeaderRow(
                            row.stage,
                            onClick = { onEditStage(row.stage) })

                        is GoalRow.AddStep -> StepAddRow(onAdd = { onAddStep(it, row.stageId) })
                        is GoalRow.Step -> {
                            // Взятый шаг чуть приподнимается: тень и бумажный фон под ним
                            val elevation by animateDpAsState(if (isDragging) 6.dp else 0.dp)
                            Surface(shadowElevation = elevation, color = paper.paper) {
                                Column(
                                    // longPressDraggableHandle — тащить после долгого нажатия в любом
                                    // месте строки. Обычный тап по-прежнему открывает шаг, квадратик — отмечает
                                    Modifier.longPressDraggableHandle(
                                        onDragStarted = {
                                            haptic.performHapticFeedback(
                                                HapticFeedbackType.LongPress
                                            )
                                        },
                                        onDragStopped = {
                                            haptic.performHapticFeedback(HapticFeedbackType.GestureEnd)
                                            commit(rows)
                                        },
                                    ),
                                ) {
                                    StepRow(
                                        step = row.step,
                                        onToggle = onToggle,
                                        onEdit = onEditStep,
                                        onSchedule = onSchedule,
                                        onMoveUp = { commit(rows.movedUp(row.key)) },
                                        onMoveDown = { commit(rows.movedDown(row.key)) },
                                    )
                                    DashedRule()
                                }
                            }
                        }
                    }
                }
            }

            item(key = "add_stage") {
                AddRow(
                    stringResource(R.string.stage_add),
                    onClick = onAddStage,
                    Modifier.padding(top = 16.dp)
                )
            }

            // --- Привычки, которые помогают цели. Ниже шагов и отдельно от них: в процент не входят ---
            item(key = "habits_label") {
                SectionLabel(
                    stringResource(R.string.section_goal_habits),
                    Modifier.padding(top = 16.dp)
                )
            }
            items(state.habits, key = { "habit_${it.rule.id}" }) { habit ->
                // Привычка цели: под названием — мягкая статистика «за 30 дней — 18 раз» (или пауза).
                // Отмечают её по-прежнему «таблеткой» на «Сегодня» — здесь только посмотреть, как она идёт
                RuleRow(
                    rule = habit.rule,
                    today = state.today,
                    subtitle = Recurrence.activePause(habit.rule, state.today)
                        ?.let { pausedText(it) }
                        ?: habitHistoryText(habit.history),
                    onClick = { onOpenHabit(habit.rule) },
                )
            }
            item(key = "add_habit") {
                AddRow(
                    stringResource(R.string.habit_new),
                    onClick = onNewHabit
                )
            }

            // --- Заметки — в самом низу: это не план, а то, что под рукой (ссылки, мерки, мысли).
            // Пишут их в шторке цели (карандаш в шапке); тап по заметкам открывает её же ---
            if (goal != null) {
                item(key = "notes_label") {
                    SectionLabel(
                        stringResource(R.string.section_goal_notes),
                        Modifier.padding(top = 16.dp)
                    )
                }
                item(key = "notes") {
                    if (goal.note.isBlank()) {
                        AddRow(
                            stringResource(R.string.goal_note_add),
                            onClick = { onEditGoal(goal) })
                    } else {
                        Text(
                            goal.note,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onEditGoal(goal) }
                                .padding(top = 4.dp, bottom = 8.dp, end = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * «Зачем» курсивом, строка срока и полоска прогресса. Ниже — то, о чём цель мягко «спрашивает»:
 * - цель достигнута — «достигнута 18 октября» и «Вернуть в работу»;
 * - срок прошёл — «срок был …» и три выхода: новый срок, без срока, достигнута. Ничего не блокирует
 *   и не торопит: можно не выбирать вовсе, строка просто остаётся;
 * - все шаги сделаны — «Все шаги сделаны — цель достигнута?» Сама цель в архив не уходит:
 *   может, человек хочет добавить ещё шаги. Решает он.
 */
@Composable
private fun GoalHeader(state: GoalUiState, onGoalAction: (GoalAction) -> Unit) {
    val goal = state.goal ?: return
    val paper = PlannerTheme.paper
    Column(Modifier.padding(end = 8.dp)) {
        if (goal.why.isNotBlank()) {
            // «Зачем» — то, ради чего всё это. Курсивом, как заметка на полях
            Text(goal.why, color = paper.inkMuted, fontStyle = FontStyle.Italic)
        }
        val achievedOn = goal.achievedOn
        if (achievedOn != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(
                        R.string.goal_achieved_on,
                        DateTexts.dayMonthYear(achievedOn, state.today)
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { onGoalAction(GoalAction.Reopen) }) {
                    Text(stringResource(R.string.goal_reopen))
                }
            }
        }
        state.overview?.let { overview ->
            DeadlineLine(goal, overview.deadline, state.today)
            val allDone = overview.progress.total > 0 && overview.nextStep == null
            when {
                goal.isAchieved -> Unit
                overview.deadline == DeadlineState.Passed -> GoalChoices(
                    stringResource(R.string.deadline_new) to { onGoalAction(GoalAction.NewDeadline) },
                    stringResource(R.string.deadline_none) to { onGoalAction(GoalAction.NoDeadline) },
                    stringResource(R.string.goal_mark_achieved) to { onGoalAction(GoalAction.Achieve) },
                )

                allDone -> {
                    Text(
                        stringResource(R.string.goal_all_steps_done_ask),
                        color = paper.inkMuted,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    GoalChoices(stringResource(R.string.goal_mark_achieved) to {
                        onGoalAction(
                            GoalAction.Achieve
                        )
                    })
                }
            }
            val progress = overview.progress
            if (progress.total > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 12.dp)
                ) {
                    ProgressBar(progress, Modifier.weight(1f))
                    Text(
                        stringResource(R.string.goal_progress, progress.done, progress.total),
                        color = PlannerTheme.paper.inkMuted,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
        }
    }
}

/**
 * Ряд «таблеток»-вариантов: «Новый срок · Без срока · Достигнута». Те же PaperChip, что в шторке
 * «Запланировать». `vararg` — сколько угодно пар «подпись → действие» через запятую.
 * FlowRow переносит таблетки на новую строку, если на узком экране они не помещаются в одну.
 */
@Composable
private fun GoalChoices(vararg choices: Pair<String, () -> Unit>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 8.dp),
    ) {
        // (text, action) — «разобрать пару»: первое значение в text, второе в action
        choices.forEach { (text, action) -> PaperChip(text, selected = false, onClick = action) }
    }
}

/**
 * Меню «⋮» в шапке цели: отметить достигнутой (или вернуть в работу) и удалить.
 * Удаление — без окна «Вы уверены?»: вместо него Snackbar с «Отменить», как везде в приложении.
 */
@Composable
private fun GoalMenu(achieved: Boolean, onGoalAction: (GoalAction) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(
                Icons.Outlined.MoreVert,
                stringResource(R.string.cd_goal_menu),
                tint = PlannerTheme.paper.inkMuted
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(if (achieved) R.string.goal_reopen else R.string.goal_mark_achieved)) },
                onClick = {
                    open = false
                    onGoalAction(if (achieved) GoalAction.Reopen else GoalAction.Achieve)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.goal_delete)) },
                leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                onClick = { open = false; onGoalAction(GoalAction.Delete) },
            )
        }
    }
}

/**
 * Строка шага — тот же [TaskRow], что в списках. Запланированный шаг серый и с плашкой даты;
 * у сделанного нет кнопки «Запланировать» — планировать уже нечего.
 *
 * Для TalkBack у строки два своих действия — «Выше» и «Ниже» (customActions): перетаскивать
 * пальцем, не видя экрана, неудобно, а так шаг двигают из меню действий TalkBack.
 */
@Composable
private fun StepRow(
    step: Task,
    onToggle: (Task) -> Unit,
    onEdit: (Task) -> Unit,
    onSchedule: (Task) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    val upLabel = stringResource(R.string.action_move_up)
    val downLabel = stringResource(R.string.action_move_down)
    TaskRow(
        title = step.title,
        done = step.isDone,
        onToggle = { onToggle(step) },
        onClick = { onEdit(step) },
        subtitle = step.notePreview,
        // Действие возвращает true — «выполнено»: так TalkBack понимает, что нажатие сработало
        modifier = Modifier.semantics {
            customActions = listOf(
                CustomAccessibilityAction(upLabel) { onMoveUp(); true },
                CustomAccessibilityAction(downLabel) { onMoveDown(); true },
            )
        },
        dimmed = step.isScheduled && !step.isDone,
        reminder = Reminders.hasReminder(step),
        trailingBadge = step.date?.takeIf { !step.isDone }?.let(DateTexts::shortDay),
        toggleDescription = stringResource(R.string.cd_mark_done),
        trailing = if (step.isDone) null else ({
            IconButton(onClick = { onSchedule(step) }) {
                Icon(
                    Icons.Outlined.CalendarMonth,
                    stringResource(R.string.schedule_title),
                    tint = PlannerTheme.paper.inkMuted,
                )
            }
        }),
    )
}

/**
 * «+ Добавить шаг», а по нажатию — поле ввода на том же месте.
 * Enter добавляет шаг и очищает поле, но не закрывает его: следующий шаг можно писать сразу.
 * Закрыть — ✕ или Enter в пустом поле.
 *
 * rememberSaveable — поле не закроется и текст не пропадёт при повороте экрана.
 */
@Composable
private fun StepAddRow(onAdd: (String) -> Unit) {
    var adding by rememberSaveable { mutableStateOf(false) }
    var text by rememberSaveable { mutableStateOf("") }
    if (!adding) {
        AddRow(stringResource(R.string.step_add), onClick = { adding = true })
        return
    }
    val focus = remember { FocusRequester() }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        placeholder = { Text(stringResource(R.string.step_hint)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = {
            if (text.isBlank()) adding = false else {
                onAdd(text.trim())
                text = ""
            }
        }),
        trailingIcon = {
            IconButton(onClick = { adding = false; text = "" }) {
                Icon(Icons.Outlined.Close, stringResource(R.string.action_done))
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, end = 8.dp)
            .focusRequester(focus),
    )
    // Открыли поле — курсор сразу в нём
    LaunchedEffect(Unit) { focus.requestFocus() }
}

/** Строка-действие «+ …» серым — как «+ Новый список» в оглавлении списков. */
@Composable
private fun AddRow(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val paper = PlannerTheme.paper
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
    ) {
        // Та же ширина, что у квадратика в строке шага (40dp) — тексты стоят ровно
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Add, null, tint = paper.inkMuted, modifier = Modifier.size(20.dp))
        }
        Text(text, color = paper.inkMuted, modifier = Modifier.padding(start = 10.dp))
    }
}

/**
 * Заголовок этапа. Тап открывает окно этапа — переименовать или удалить.
 * Это тот же [SectionLabel], что «утро» / «вечер» в «Сегодня», только нажимаемый.
 */
@Composable
private fun StageHeaderRow(stage: GoalStage, onClick: () -> Unit) {
    SectionLabel(
        stage.title,
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(top = 8.dp),
    )
}

/**
 * Окно этапа: одно поле с названием.
 * - Новый этап ([initial] пустой, [onDelete] = null) — «Новый этап» и кнопка «Добавить».
 * - Существующий — «Этап», «Сохранить» и «Удалить этап». Шаги удалённого этапа остаются в цели.
 */
@Composable
private fun StageDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    initial: String = "",
    onDelete: (() -> Unit)? = null,
) {
    val isNew = onDelete == null
    var title by rememberSaveable { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (isNew) R.string.stage_new else R.string.stage_edit)) },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = { Text(stringResource(R.string.stage_name_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = {
                        if (title.isNotBlank()) onSave(
                            title.trim()
                        )
                    }),
                    modifier = Modifier.focusRequester(focus),
                )
                if (onDelete != null) {
                    TextButton(onClick = onDelete, modifier = Modifier.padding(top = 8.dp)) {
                        Icon(Icons.Outlined.Delete, null)
                        Text(
                            stringResource(R.string.stage_delete),
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(title.trim()) }, enabled = title.isNotBlank()) {
                Text(stringResource(if (isNew) R.string.action_add else R.string.action_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
    // Новый этап — сразу курсор в поле; у существующего не открываем клавиатуру зря (может, его удаляют)
    if (isNew) LaunchedEffect(Unit) { focus.requestFocus() }
}
