package app.planner.feature.week

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.planner.R
import app.planner.common.DateTexts
import app.planner.common.DayItemRow
import app.planner.common.EditorTarget
import app.planner.common.EditorTargetSaver
import app.planner.common.PickTaskSheet
import app.planner.common.SlotSaver
import app.planner.common.TaskEditorSheet
import app.planner.core.designsystem.component.DashedRule
import app.planner.core.designsystem.component.SectionLabel
import app.planner.core.designsystem.theme.PlannerTheme
import app.planner.core.domain.model.RecurrenceRule
import app.planner.core.domain.model.Slot
import app.planner.core.domain.model.Task
import app.planner.core.domain.recurrence.Occurrence
import java.time.LocalDate
import java.time.LocalTime

private val HOURS = 6..23

/** Почасовая шкала одного дня: видно «окна», тап по свободному часу — поставить туда задачу. */
@Composable
fun DayScreen(
    date: LocalDate,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    viewModel: WeekViewModel = hiltViewModel(),
) {
    // У экрана дня своя ViewModel: показываем неделю, в которую попадает этот день
    LaunchedEffect(date) { viewModel.showWeekOf(date) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pickSlot by rememberSaveable(stateSaver = SlotSaver) { mutableStateOf<Slot?>(null) }
    // Что открыто в редакторе: задача или правило повторения (с его повторением и паузой); null — закрыт
    var editing by rememberSaveable(stateSaver = EditorTargetSaver) {
        mutableStateOf<EditorTarget?>(
            null
        )
    }

    DayContent(
        date = date,
        state = state,
        contentPadding = contentPadding,
        onBack = onBack,
        onPickSlot = { pickSlot = it },
        onToggle = viewModel::toggle,
        onCompleteOccurrence = viewModel::completeOccurrence,
        // Тап по повторению — открыть его правило в том же редакторе, что и задачи
        onEditRule = { rule -> editing = EditorTarget.forRule(rule, state.today) },
        onEdit = { editing = EditorTarget(it) },
    )

    pickSlot?.let { slot ->
        PickTaskSheet(
            slot = slot,
            today = state.today,
            candidates = state.unscheduled,
            lists = state.lists,
            onDismiss = { pickSlot = null },
            onPick = { viewModel.schedule(it, slot); pickSlot = null },
            onCreate = { viewModel.createIn(slot, it); pickSlot = null },
        )
    }
    editing?.let { target ->
        // Открыто правило повторения (черновик: id = 0 и ruleId) — у него есть пауза
        val ruleId = target.task.ruleId.takeIf { target.task.isRuleDraft }
        TaskEditorSheet(
            initial = target.task,
            lists = state.lists,
            today = state.today,
            onDismiss = { editing = null },
            onSave = { task, newList, repeat ->
                viewModel.save(task, newList, repeat); editing = null
            },
            onDelete = { viewModel.delete(it); editing = null },
            initialRepeat = target.repeat,
            pause = target.pause,
            onPause = ruleId?.let { id ->
                { until ->
                    viewModel.pauseRule(id, until); editing = null
                }
            },
            onResume = ruleId?.let { id -> { viewModel.resumeRule(id); editing = null } },
            rulePauses = target.pauses,
        )
    }
}

/**
 * Рисующая часть почасовой шкалы дня: только разметка, без ViewModel и шторок.
 * Её показывает превью в Android Studio (см. `WeekPreviews.kt`).
 *
 * @param onPickSlot нажали на пустое место — «без времени» или конкретный час.
 */
@Composable
fun DayContent(
    date: LocalDate,
    state: WeekUiState,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onPickSlot: (Slot) -> Unit,
    onToggle: (Task) -> Unit,
    onCompleteOccurrence: (Occurrence) -> Unit,
    onEditRule: (RecurrenceRule) -> Unit,
    onEdit: (Task) -> Unit,
) {
    val items = state.itemsByDay[date].orEmpty()
    val untimed = items.filter { it.time == null }
    val byHour = items.filter { it.time != null }.groupBy { it.time!!.hour }
    val paper = PlannerTheme.paper
    // Открываем шкалу ближе к текущему часу, а не с шести утра
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = if (date == state.today) (LocalTime.now().hour - HOURS.first - 1).coerceAtLeast(
            0
        ) + 1 else 0,
    )

    Column(
        Modifier
            .fillMaxSize()
            .padding(contentPadding)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 8.dp, end = 16.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    stringResource(R.string.cd_back)
                )
            }
            Column {
                Text(DateTexts.weekday(date), style = MaterialTheme.typography.titleLarge)
                Text(
                    DateTexts.dayMonth(date),
                    color = paper.inkMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp)
        ) {
            item {
                SectionLabel(stringResource(R.string.day_no_time))
                if (untimed.isEmpty()) {
                    Text(
                        stringResource(R.string.week_add),
                        color = paper.inkFaint,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPickSlot(Slot(date)) }
                            .padding(vertical = 10.dp),
                    )
                }
                untimed.forEach { item ->
                    DayItemRow(
                        item,
                        state.lists,
                        onToggle,
                        onCompleteOccurrence,
                        onEdit,
                        onEditRule
                    )
                }
            }
            items(HOURS.toList(), key = { it }) { hour ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .clickable { onPickSlot(Slot(date, time = LocalTime.of(hour, 0))) },
                ) {
                    Text(
                        "%02d".format(hour),
                        color = paper.inkMuted,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier
                            .width(36.dp)
                            .padding(top = 4.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        DashedRule()
                        byHour[hour].orEmpty().forEach { item ->
                            DayItemRow(
                                item,
                                state.lists,
                                onToggle,
                                onCompleteOccurrence,
                                onEdit,
                                onEditRule
                            )
                        }
                    }
                }
            }
        }
    }
}
