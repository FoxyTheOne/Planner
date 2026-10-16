package app.planner.feature.goals

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.planner.R
import app.planner.common.DateTexts
import app.planner.common.EditorActionRow
import app.planner.common.PlannerDatePickerDialog
import app.planner.core.domain.model.Goal
import java.time.LocalDate

/**
 * Шторка «Новая цель» / «Цель»: название, «Зачем» и необязательный срок.
 *
 * Шаги здесь не добавляются: их удобнее записывать уже на экране цели, по одному,
 * когда цель есть и на неё можно смотреть. Здесь — только «шапка» цели.
 *
 * Строка срока — та же [EditorActionRow], что в редакторе задачи: пока срока нет, она
 * выглядит как действие «Добавить срок»; срок выбран — видно дату и ✕, чтобы его убрать.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalEditorSheet(
    initial: Goal,
    today: LocalDate,
    onDismiss: () -> Unit,
    onSave: (Goal) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        GoalEditorSheetBody(initial = initial, today = today, onSave = onSave)
    }
}

/** Содержимое шторки без выезжающей рамки — чтобы его можно было показать в превью. */
@Composable
fun GoalEditorSheetBody(initial: Goal, today: LocalDate, onSave: (Goal) -> Unit) {
    val isNew = initial.id == 0L
    var title by rememberSaveable { mutableStateOf(initial.title) }
    var why by rememberSaveable { mutableStateOf(initial.why) }
    var deadline by rememberSaveable { mutableStateOf(initial.deadline) }
    var pickDeadline by rememberSaveable { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    fun save() {
        if (title.isBlank()) return
        onSave(initial.copy(title = title.trim(), why = why.trim(), deadline = deadline))
    }

    Column(
        Modifier
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .imePadding()
            .padding(bottom = 16.dp),
    ) {
        Text(
            stringResource(if (isNew) R.string.goal_new else R.string.goal_edit),
            style = MaterialTheme.typography.titleLarge,
        )
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            placeholder = { Text(stringResource(R.string.goal_title_hint)) },
            textStyle = MaterialTheme.typography.titleMedium,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
                .focusRequester(focus),
        )
        OutlinedTextField(
            value = why,
            onValueChange = { why = it },
            placeholder = { Text(stringResource(R.string.goal_why_hint)) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            minLines = 2,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )
        EditorActionRow(
            icon = Icons.Outlined.Event,
            value = deadline?.let { stringResource(R.string.goal_deadline_until, DateTexts.dayMonthYear(it, today)) },
            action = stringResource(R.string.goal_deadline_add),
            onClick = { pickDeadline = true },
            onClear = { deadline = null },
            clearDescription = stringResource(R.string.cd_clear_deadline),
            modifier = Modifier.padding(top = 12.dp),
        )
        Row(Modifier.fillMaxWidth().padding(top = 20.dp)) {
            Row(Modifier.weight(1f)) {}
            Button(onClick = ::save, enabled = title.isNotBlank()) {
                Text(stringResource(if (isNew) R.string.action_add else R.string.action_save))
            }
        }
    }

    // Новая цель — сразу курсор в название, как в редакторе задачи
    if (isNew) LaunchedEffect(Unit) { focus.requestFocus() }

    if (pickDeadline) {
        PlannerDatePickerDialog(
            // Срока ещё нет — календарь открывается через месяц: «примерно когда?», а не «сегодня»
            initial = deadline ?: today.plusMonths(1),
            onPick = { deadline = it; pickDeadline = false },
            onDismiss = { pickDeadline = false },
        )
    }
}
