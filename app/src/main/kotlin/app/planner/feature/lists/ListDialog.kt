package app.planner.feature.lists

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.planner.R
import app.planner.core.designsystem.theme.PlannerTheme
import app.planner.core.domain.list.ListNameProblem
import app.planner.core.domain.model.ListColor
import app.planner.core.domain.model.TaskList

/**
 * Диалог создания и редактирования списка: название, цвет и (для существующего) удаление.
 * Используется и в оглавлении, и внутри списка (карандаш в заголовке).
 *
 * @param initial список для редактирования; у нового списка id = 0.
 * @param checkName проверка названия: null — можно, иначе причина, почему нельзя (её даёт ViewModel).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ListDialog(
    initial: TaskList,
    checkName: (String) -> ListNameProblem?,
    onDismiss: () -> Unit,
    onSave: (TaskList) -> Unit,
    onDelete: (TaskList) -> Unit,
) {
    val isNew = initial.id == 0L
    var name by rememberSaveable { mutableStateOf(initial.name) }
    var color by rememberSaveable { mutableStateOf(initial.color) }
    // Проверяем при каждой перерисовке, то есть после каждой введённой буквы
    val problem = checkName(name)
    // Какую подсказку показать под полем (null — никакую)
    val problemText = when (problem) {
        ListNameProblem.INBOX -> R.string.list_name_is_inbox
        ListNameProblem.DUPLICATE -> R.string.list_name_taken
        null -> null
    }
    val paper = PlannerTheme.paper
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (isNew) R.string.list_new else R.string.list_edit)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    // label — подпись над полем: сразу видно, что вводится название списка, а не задача
                    label = { Text(stringResource(R.string.list_name_label)) },
                    placeholder = { Text(stringResource(R.string.list_name_hint)) },
                    singleLine = true,
                    // isError красит поле в цвет ошибки, supportingText — подпись под полем
                    isError = problem != null,
                    supportingText = problemText?.let { textId -> { Text(stringResource(textId)) } },
                )
                Text(
                    stringResource(R.string.list_color),
                    color = paper.inkMuted,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ListColor.entries.forEach { c ->
                        val description = stringResource(R.string.cd_list_color)
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(32.dp)
                                .background(paper.label(c), CircleShape)
                                .clickable { color = c }
                                .semantics { contentDescription = description },
                        ) {
                            if (c == color) Icon(
                                Icons.Outlined.Check,
                                null,
                                tint = paper.paper,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                if (!isNew) {
                    TextButton(
                        onClick = { onDelete(initial) },
                        modifier = Modifier.padding(top = 12.dp)
                    ) {
                        Text(
                            stringResource(R.string.list_delete),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Text(
                        stringResource(R.string.list_delete_hint),
                        color = paper.inkMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(initial.copy(name = name.trim(), color = color)) },
                enabled = name.isNotBlank() && problem == null
            ) {
                Text(stringResource(if (isNew) R.string.action_create else R.string.action_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
