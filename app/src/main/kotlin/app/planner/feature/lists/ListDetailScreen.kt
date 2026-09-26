package app.planner.feature.lists

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.planner.R
import app.planner.common.DateTexts
import app.planner.common.ScheduleSheet
import app.planner.common.TaskEditorSheet
import app.planner.common.listName
import app.planner.core.designsystem.component.DashedRule
import app.planner.core.designsystem.component.TaskRow
import app.planner.core.designsystem.theme.PlannerTheme
import app.planner.core.domain.model.Task
import app.planner.core.domain.model.TaskList

/**
 * Экран одного списка — второй уровень вкладки «Списки».
 *
 * Сверху стрелка назад (к оглавлению), цвет и название списка, карандаш —
 * переименовать, сменить цвет или удалить. Ниже задачи: запланированные остаются
 * здесь серыми, с датой, и уходят, только когда сделаны. У каждой задачи справа
 * календарик — быстро запланировать. «+ Задача» кладёт новую задачу в этот список.
 */
@Composable
fun ListDetailScreen(
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    viewModel: ListDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Task?>(null) }
    var scheduling by remember { mutableStateOf<Task?>(null) }
    var editingList by remember { mutableStateOf<TaskList?>(null) }
    val paper = PlannerTheme.paper

    // Список удалили (здесь или через оглавление) — возвращаемся к оглавлению
    val list = state.list
    if (state.loaded && list == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    Box(Modifier.fillMaxSize().padding(contentPadding)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 8.dp, end = 8.dp)) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.cd_back)) }
                if (list != null) {
                    ListDot(if (list.isInbox) null else paper.label(list.color))
                    Text(
                        listName(list),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(start = 10.dp).weight(1f),
                    )
                    // «Без списка» служебный — его нельзя переименовать или удалить
                    if (!list.isInbox) {
                        IconButton(onClick = { editingList = list }) {
                            Icon(Icons.Outlined.Edit, stringResource(R.string.cd_edit_list), tint = paper.inkMuted)
                        }
                    }
                }
            }

            LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 12.dp, top = 4.dp, bottom = 96.dp)) {
                if (state.loaded && state.tasks.isEmpty()) {
                    item {
                        Text(stringResource(R.string.list_empty), color = paper.inkMuted, modifier = Modifier.padding(top = 16.dp, end = 8.dp))
                    }
                }
                items(state.tasks, key = { it.id }) { task ->
                    // animateItem — плавное перемещение строки, когда задача меняет место в списке
                    Column(Modifier.animateItem()) {
                        TaskRow(
                            title = task.title,
                            done = task.isDone,
                            onToggle = { viewModel.toggle(task) },
                            onClick = { editing = task },
                            dimmed = task.isScheduled,
                            trailingBadge = task.date?.let(DateTexts::shortDay),
                            toggleDescription = stringResource(R.string.cd_mark_done),
                            trailing = {
                                IconButton(onClick = { scheduling = task }) {
                                    Icon(Icons.Outlined.CalendarMonth, stringResource(R.string.schedule_title), tint = paper.inkMuted)
                                }
                            },
                        )
                        DashedRule()
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = { editing = Task(title = "", listId = list?.id) },
            icon = { Icon(Icons.Outlined.Add, null) },
            text = { Text(stringResource(R.string.action_new_task)) },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        )
    }

    // Шторки и диалоги рисуются поверх экрана, только когда соответствующая переменная не null
    editing?.let { task ->
        TaskEditorSheet(
            initial = task,
            lists = state.allLists,
            today = state.today,
            onDismiss = { editing = null },
            onSave = { t, newList -> viewModel.saveTask(t, newList); editing = null },
            onDelete = { viewModel.delete(it); editing = null },
        )
    }
    scheduling?.let { task ->
        ScheduleSheet(
            today = state.today,
            initial = null,
            onDismiss = { scheduling = null },
            onConfirm = { viewModel.schedule(task, it); scheduling = null },
        )
    }
    editingList?.let { l ->
        ListDialog(
            initial = l,
            onDismiss = { editingList = null },
            onSave = { viewModel.saveList(it); editingList = null },
            onDelete = { viewModel.deleteList(it); editingList = null },
        )
    }
}
