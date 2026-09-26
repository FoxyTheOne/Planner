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
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.planner.R
import app.planner.core.designsystem.component.DashedRule
import app.planner.core.designsystem.component.SectionLabel
import app.planner.core.designsystem.theme.PlannerTheme
import app.planner.core.domain.model.Slot
import app.planner.core.domain.model.Task
import app.planner.core.domain.model.TaskList
import java.time.LocalDate

/**
 * Шторка из недели: место в плане уже выбрано, осталось выбрать, чем его занять —
 * новой задачей или готовой из списков. Одним тапом задача встаёт в слот.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickTaskSheet(
    slot: Slot,
    today: LocalDate,
    candidates: List<Task>,
    lists: List<TaskList>,
    onDismiss: () -> Unit,
    onPick: (Task) -> Unit,
    onCreate: (String) -> Unit,
) {
    var tab by remember { mutableIntStateOf(if (candidates.isEmpty()) 0 else 1) }
    var newTitle by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = 16.dp),
        ) {
            Text(
                slotText(slot, today),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            PrimaryTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.padding(top = 8.dp)) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.pick_tab_new)) })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.pick_tab_lists)) })
            }

            if (tab == 0) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        placeholder = { Text(stringResource(R.string.task_title_hint)) },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { if (newTitle.isNotBlank()) onCreate(newTitle.trim()) }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = { onCreate(newTitle.trim()) },
                        enabled = newTitle.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    ) { Text(stringResource(R.string.action_add)) }
                }
            } else {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    placeholder = { Text(stringResource(R.string.pick_search)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                )
                val filtered = candidates.filter { query.isBlank() || it.title.contains(query.trim(), ignoreCase = true) }
                if (filtered.isEmpty()) {
                    Text(
                        stringResource(if (candidates.isEmpty()) R.string.pick_empty else R.string.pick_nothing_found),
                        color = PlannerTheme.paper.inkMuted,
                        modifier = Modifier.padding(20.dp),
                    )
                } else {
                    val byList = filtered.groupBy { it.listId }
                    val listsById = lists.associateBy { it.id }
                    LazyColumn(Modifier.heightIn(max = 420.dp).padding(horizontal = 20.dp)) {
                        byList.forEach { (listId, tasks) ->
                            item(key = "h$listId") {
                                val list = listId?.let(listsById::get)
                                SectionLabel(list?.let { listName(it) } ?: stringResource(R.string.list_inbox))
                            }
                            items(tasks, key = { it.id }) { task ->
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
            }
        }
    }
}
