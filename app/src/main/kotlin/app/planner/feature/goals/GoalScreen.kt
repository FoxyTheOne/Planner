package app.planner.feature.goals

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.planner.R
import app.planner.common.DateTexts
import app.planner.common.GoalSaver
import app.planner.common.ScheduleSheet
import app.planner.common.TaskEditorSheet
import app.planner.common.TaskSaver
import app.planner.core.designsystem.component.DashedRule
import app.planner.core.designsystem.component.SectionLabel
import app.planner.core.designsystem.component.TaskRow
import app.planner.core.designsystem.theme.PlannerTheme
import app.planner.core.domain.goal.StepGroup
import app.planner.core.domain.model.Goal
import app.planner.core.domain.model.Task

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
    )

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
            initial = null,
            onDismiss = { scheduling = null },
            onConfirm = { viewModel.schedule(step, it); scheduling = null },
        )
    }
    if (addingStage) {
        StageDialog(
            onDismiss = { addingStage = false },
            onSave = { viewModel.addStage(it); addingStage = false },
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
            }
        }

        // Поле «Добавить шаг» стоит прямо в списке, и у нижних этапов клавиатура закрыла бы его.
        // imePadding — отступ снизу на высоту клавиатуры: список становится короче, и Compose
        // сам прокручивает его так, чтобы поле с курсором было видно над клавиатурой.
        // consumeWindowInsets(contentPadding) — «нижнюю панель мы уже учли», иначе к высоте
        // клавиатуры прибавилась бы ещё и высота панели вкладок, и между ними была бы дыра
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 12.dp, top = 4.dp, bottom = 32.dp),
            modifier = Modifier
                .consumeWindowInsets(contentPadding)
                .imePadding(),
        ) {
            item(key = "header") { GoalHeader(state) }

            if (state.loaded && state.steps.isEmpty()) {
                item(key = "hint") {
                    Text(
                        stringResource(R.string.goal_steps_hint),
                        color = paper.inkMuted,
                        modifier = Modifier.padding(top = 16.dp, end = 8.dp),
                    )
                }
            }

            state.groups.forEach { group ->
                stepGroup(
                    group,
                    onToggle,
                    onEditStep,
                    onSchedule,
                    onAddStep
                )
            }

            item(key = "add_stage") {
                AddRow(
                    stringResource(R.string.stage_add),
                    onClick = onAddStage,
                    Modifier.padding(top = 16.dp)
                )
            }
        }
    }
}

/** «Зачем» курсивом, строка срока и полоска прогресса. */
@Composable
private fun GoalHeader(state: GoalUiState) {
    val goal = state.goal ?: return
    Column(Modifier.padding(end = 8.dp)) {
        if (goal.why.isNotBlank()) {
            // «Зачем» — то, ради чего всё это. Курсивом, как заметка на полях
            Text(goal.why, color = PlannerTheme.paper.inkMuted, fontStyle = FontStyle.Italic)
        }
        state.overview?.let { overview ->
            DeadlineLine(goal, overview.deadline, state.today)
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
 * Один этап: подпись (у шагов без этапа её нет), шаги и строка «+ Добавить шаг».
 *
 * Это не @Composable, а функция-расширение LazyListScope — «кусок списка»: она добавляет в
 * LazyColumn несколько элементов (item, items). Так все шаги остаются отдельными элементами
 * одного списка и прокручиваются лениво, а не рисуются разом одной большой колонкой.
 */
private fun LazyListScope.stepGroup(
    group: StepGroup,
    onToggle: (Task) -> Unit,
    onEditStep: (Task) -> Unit,
    onSchedule: (Task) -> Unit,
    onAddStep: (title: String, stageId: Long?) -> Unit,
) {
    val stage = group.stage
    // Ключи с приставкой: у этапа и шага id могут совпасть (оба 1)
    if (stage != null) {
        item(key = "stage_${stage.id}") { SectionLabel(stage.title, Modifier.padding(top = 8.dp)) }
    }
    items(group.steps, key = { "step_${it.id}" }) { step ->
        Column(Modifier.animateItem()) {
            StepRow(step, onToggle, onEditStep, onSchedule)
            DashedRule()
        }
    }
    item(key = "add_step_${stage?.id}") { StepAddRow(onAdd = { onAddStep(it, stage?.id) }) }
}

/**
 * Строка шага — тот же [TaskRow], что в списках. Запланированный шаг серый и с плашкой даты;
 * у сделанного нет кнопки «Запланировать» — планировать уже нечего.
 */
@Composable
private fun StepRow(
    step: Task,
    onToggle: (Task) -> Unit,
    onEdit: (Task) -> Unit,
    onSchedule: (Task) -> Unit
) {
    TaskRow(
        title = step.title,
        done = step.isDone,
        onToggle = { onToggle(step) },
        onClick = { onEdit(step) },
        dimmed = step.isScheduled && !step.isDone,
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

/** Окно «Новый этап»: одно поле с названием. */
@Composable
private fun StageDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.stage_new)) },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                placeholder = { Text(stringResource(R.string.stage_name_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { if (title.isNotBlank()) onSave(title.trim()) }),
                modifier = Modifier.focusRequester(focus),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(title.trim()) }, enabled = title.isNotBlank()) {
                Text(stringResource(R.string.action_add))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
    LaunchedEffect(Unit) { focus.requestFocus() }
}
