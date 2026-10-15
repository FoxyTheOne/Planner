package app.planner.feature.lists

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.planner.R
import app.planner.common.TaskEditorSheet
import app.planner.common.TaskListSaver
import app.planner.common.TaskSaver
import app.planner.common.listName
import app.planner.core.designsystem.component.DashedRule
import app.planner.core.designsystem.component.SectionLabel
import app.planner.core.designsystem.theme.PlannerTheme
import app.planner.core.domain.list.nextListColor
import app.planner.core.domain.model.Task
import app.planner.core.domain.model.TaskList

/**
 * Оглавление списков — первый уровень вкладки «Списки».
 *
 * Все списки столбиком со счётчиком открытых задач, как оглавление в блокноте:
 * так удобно и при 3 списках, и при 50. Сверху поиск по названиям списков и задач.
 * «Без списка» показывается внизу и только когда в нём что-то есть.
 *
 * Сразу после запуска приложения экран сам открывает последний список, в котором
 * человек был в прошлый раз (см. [ListsOverviewViewModel.listToRestore]).
 *
 * @param onOpenList открыть список (переход на экран [ListDetailScreen]).
 */
@Composable
fun ListsOverviewScreen(
    onOpenList: (Long) -> Unit,
    contentPadding: PaddingValues,
    viewModel: ListsOverviewViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editingList by rememberSaveable(stateSaver = TaskListSaver) { mutableStateOf<TaskList?>(null) }
    var newTask by rememberSaveable(stateSaver = TaskSaver) { mutableStateOf<Task?>(null) }

    // LaunchedEffect(Unit) выполняется один раз, когда экран появляется
    LaunchedEffect(Unit) {
        viewModel.listToRestore()?.let(onOpenList)
    }

    ListsOverviewContent(
        state = state,
        contentPadding = contentPadding,
        onQueryChange = viewModel::setQuery,
        onOpenList = onOpenList,
        onEditList = { editingList = it },
        onNewList = { editingList = TaskList(name = "", color = nextListColor(state.allLists)) },
        onAddTask = { newTask = Task(title = "") },
    )

    newTask?.let { task ->
        TaskEditorSheet(
            initial = task,
            lists = state.allLists,
            today = state.today,
            onDismiss = { newTask = null },
            onSave = { viewModel.saveTask(it); newTask = null },
        )
    }
    editingList?.let { list ->
        ListDialog(
            initial = list,
            checkName = { viewModel.checkListName(it, list.id) },
            onDismiss = { editingList = null },
            // Новый список сразу открываем — чтобы можно было начать его заполнять
            onSave = {
                viewModel.saveList(it) { id -> if (list.id == 0L) onOpenList(id) }; editingList =
                null
            },
            onDelete = { viewModel.deleteList(it); editingList = null },
        )
    }
}

/**
 * Рисующая часть оглавления: только разметка, без ViewModel и диалогов.
 * Её показывает превью в Android Studio (см. `ListsPreviews.kt`).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ListsOverviewContent(
    state: ListsOverviewUiState,
    contentPadding: PaddingValues,
    onQueryChange: (String) -> Unit,
    onOpenList: (Long) -> Unit,
    onEditList: (TaskList) -> Unit,
    onNewList: () -> Unit,
    onAddTask: () -> Unit,
) {
    val paper = PlannerTheme.paper
    Box(
        Modifier
            .fillMaxSize()
            .padding(contentPadding)
    ) {
        LazyColumn(
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 16.dp,
                bottom = 96.dp
            )
        ) {
            item {
                Text(
                    stringResource(R.string.nav_lists),
                    style = MaterialTheme.typography.headlineMedium
                )
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    trailingIcon = {
                        if (state.query.isNotEmpty()) {
                            IconButton(onClick = { onQueryChange("") }) {
                                Icon(Icons.Outlined.Close, stringResource(R.string.cd_clear_search))
                            }
                        }
                    },
                    placeholder = { Text(stringResource(R.string.lists_search)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 4.dp),
                )
            }

            if (state.query.isBlank()) {
                // --- Обычный вид: оглавление ---
                items(state.rows, key = { it.list.id }) { row ->
                    ListRowItem(
                        row = row,
                        onClick = { onOpenList(row.list.id) },
                        // Долгое нажатие — быстрый путь к переименованию, основной — карандаш внутри списка
                        onLongClick = { if (!row.list.isInbox) onEditList(row.list) },
                    )
                }
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onNewList)
                            .padding(vertical = 14.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Add,
                            null,
                            tint = paper.inkMuted,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            stringResource(R.string.list_new),
                            color = paper.inkMuted,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }
                }
            } else {
                // --- Поиск: сначала подходящие списки, потом задачи с названием их списка ---
                if (state.foundLists.isEmpty() && state.foundTasks.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.pick_nothing_found),
                            color = paper.inkMuted,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    }
                }
                if (state.foundLists.isNotEmpty()) {
                    item { SectionLabel(stringResource(R.string.nav_lists)) }
                    items(state.foundLists, key = { "l${it.list.id}" }) { row ->
                        ListRowItem(
                            row = row,
                            onClick = { onOpenList(row.list.id) },
                            onLongClick = {})
                    }
                }
                if (state.foundTasks.isNotEmpty()) {
                    item { SectionLabel(stringResource(R.string.lists_found_tasks)) }
                    items(state.foundTasks, key = { "t${it.id}" }) { task ->
                        val list = state.allLists.firstOrNull { it.id == task.listId }
                        FoundTaskItem(
                            task = task,
                            listTitle = list?.let { listName(it) }.orEmpty(),
                            // Тап по найденной задаче открывает её список
                            onClick = { task.listId?.let(onOpenList) },
                        )
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = onAddTask,
            icon = { Icon(Icons.Outlined.Add, null) },
            text = { Text(stringResource(R.string.action_new_task)) },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
        )
    }
}

/** Строка оглавления: цветная точка, название, справа — сколько открытых задач. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ListRowItem(row: ListRow, onClick: () -> Unit, onLongClick: () -> Unit) {
    val paper = PlannerTheme.paper
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(vertical = 14.dp),
        ) {
            ListDot(if (row.list.isInbox) null else paper.label(row.list.color))
            Text(
                listName(row.list),
                style = MaterialTheme.typography.bodyLarge,
                color = if (row.list.isInbox) paper.inkMuted else paper.ink,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .weight(1f),
            )
            if (row.openCount > 0) {
                Text(
                    row.openCount.toString(),
                    color = paper.inkMuted,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        DashedRule()
    }
}

/** Найденная задача: название и, мелко справа, в каком она списке. */
@Composable
private fun FoundTaskItem(task: Task, listTitle: String, onClick: () -> Unit) {
    val paper = PlannerTheme.paper
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 12.dp),
        ) {
            Text(
                task.title,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Text(listTitle, color = paper.inkMuted, style = MaterialTheme.typography.labelMedium)
        }
        DashedRule()
    }
}

/** Точка цвета списка; у «Без списка» — пустой кружок. */
@Composable
fun ListDot(color: Color?, size: Int = 10) {
    val paper = PlannerTheme.paper
    Box(
        Modifier
            .size(size.dp)
            .then(
                if (color != null) Modifier.background(color, CircleShape)
                else Modifier.border(1.dp, paper.inkFaint, CircleShape),
            ),
    )
}
