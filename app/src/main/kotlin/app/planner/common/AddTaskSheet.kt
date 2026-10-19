package app.planner.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.planner.R
import app.planner.core.designsystem.component.DashedRule
import app.planner.core.designsystem.component.SectionLabel
import app.planner.core.domain.model.Task
import app.planner.core.domain.model.TaskList

/**
 * Окно «Добавить» — одно на все экраны: «Сегодня», «Неделя», «День», «Списки».
 *
 * Раньше на каждом экране было своё: на «Сегодня» «+» открывал сразу полный редактор,
 * в «Неделе» — шторку с вкладками «Новая / Из списков», в «Списках» — снова редактор.
 * Научиться приходилось трижды. Теперь окно одно, а экран решает только, КУДА добавлять:
 * на какой день (и час) или в какой список — это видно в заголовке ([title]).
 *
 * Одно поле делает две вещи:
 * - «+» (или «Готово» на клавиатуре) — создать новую задачу с этим названием;
 * - пока печатаешь, ниже остаются только подходящие задачи из списков и шаги целей
 *   («плит» → «Купить плитку»). Нажала на готовую — она встаёт в этот день.
 * «Заметка, повтор, напоминание…» — полный редактор с уже набранным названием.
 *
 * Шаги целей без даты собраны под заголовками своих целей, после списков: запланировать шаг
 * на неделю — как раз то, ради чего цели разбивают на шаги.
 *
 * @param title куда добавляем: «Сегодня», «Вт, 29 сентября · утро», «Книги».
 * @param candidates готовые задачи, которые можно поставить в этот день; пусто — блока «Или взять
 *   из списков» нет (например, в самих списках брать из списков незачем).
 * @param goalTitles названия целей по id — для заголовков над шагами.
 * @param onPick нажали на готовую задачу из [candidates].
 * @param onCreate «+»: новая задача с этим названием.
 * @param onMore полный редактор с уже набранным названием (может быть и пустым).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskSheet(
    title: String,
    candidates: List<Task>,
    lists: List<TaskList>,
    goalTitles: Map<Long, String>,
    onDismiss: () -> Unit,
    onPick: (Task) -> Unit,
    onCreate: (String) -> Unit,
    onMore: (String) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        AddTaskSheetBody(
            title = title,
            candidates = candidates,
            lists = lists,
            goalTitles = goalTitles,
            onPick = onPick,
            onCreate = onCreate,
            onMore = onMore,
        )
    }
}

/**
 * Содержимое шторки без выезжающей рамки ModalBottomSheet.
 * Вынесено отдельно, чтобы его можно было показать в превью Android Studio:
 * сама шторка — всплывающее окно, а такие окна обычное превью не рисует.
 *
 * @param autoFocus поставить курсор в поле сразу (в приложении — да: нажали «+», значит, хотят писать;
 *   в превью — нет, там клавиатуры не бывает).
 */
@Composable
fun AddTaskSheetBody(
    title: String,
    candidates: List<Task>,
    lists: List<TaskList>,
    goalTitles: Map<Long, String> = emptyMap(),
    onPick: (Task) -> Unit,
    onCreate: (String) -> Unit,
    onMore: (String) -> Unit = {},
    autoFocus: Boolean = true,
) {
    var text by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    fun create() {
        if (text.isNotBlank()) onCreate(text.trim())
    }

    Column(
        Modifier
            .navigationBarsPadding()
            .imePadding()
            .padding(bottom = 16.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 20.dp, end = 12.dp, top = 12.dp),
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(stringResource(R.string.task_title_hint)) },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { create() }),
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focus),
            )
            FilledIconButton(
                onClick = ::create,
                enabled = text.isNotBlank(),
                modifier = Modifier.padding(start = 8.dp),
            ) { Icon(Icons.Outlined.Add, stringResource(R.string.action_add)) }
        }
        TextButton(
            onClick = { onMore(text.trim()) },
            modifier = Modifier.padding(start = 8.dp),
        ) { Text(stringResource(R.string.pick_more)) }

        // То же поле — и поиск: пока печатаешь, остаются только подходящие готовые задачи.
        // Ничего не подошло — блок просто прячется: значит, это новая задача, жми «+»
        val query = text.trim()
        val filtered =
            candidates.filter { query.isEmpty() || it.title.contains(query, ignoreCase = true) }
        if (filtered.isNotEmpty()) {
            SectionLabel(
                stringResource(R.string.pick_from_lists),
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .padding(top = 8.dp),
            )
            CandidateList(filtered, lists, goalTitles, onPick)
        }
    }

    if (autoFocus) LaunchedEffect(Unit) { focus.requestFocus() }
}

/**
 * Готовые задачи по группам: задачи — под своим списком, шаги целей — под своей целью
 * (у шага списка нет: без этого он попал бы под «Без списка»). Ключ группы — строка,
 * чтобы id списка и id цели не путались: «list_3» и «goal_3» — разные группы.
 */
@Composable
private fun CandidateList(
    tasks: List<Task>,
    lists: List<TaskList>,
    goalTitles: Map<Long, String>,
    onPick: (Task) -> Unit,
) {
    val groups = tasks
        .groupBy { task -> task.goalId?.let { "goal_$it" } ?: "list_${task.listId}" }
        .entries
        .sortedBy { it.key.startsWith("goal_") } // сначала списки, потом цели
    val listsById = lists.associateBy { it.id }
    LazyColumn(
        Modifier
            .heightIn(max = 420.dp)
            .padding(horizontal = 20.dp)
    ) {
        groups.forEach { (key, groupTasks) ->
            item(key = "h$key") {
                val first = groupTasks.first()
                val goalTitle = first.goalId?.let(goalTitles::get)
                val list = first.listId?.let(listsById::get)
                SectionLabel(
                    when {
                        goalTitle != null -> stringResource(R.string.pick_goal_section, goalTitle)
                        list != null -> listName(list)
                        else -> stringResource(R.string.list_inbox)
                    },
                )
            }
            items(groupTasks, key = { it.id }) { task ->
                Column {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(task) }
                            .padding(vertical = 12.dp),
                    ) { Text(task.title, style = MaterialTheme.typography.bodyLarge) }
                    DashedRule()
                }
            }
        }
    }
}
