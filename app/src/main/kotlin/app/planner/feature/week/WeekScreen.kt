package app.planner.feature.week

import android.content.ClipData
import android.content.ClipDescription
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.planner.R
import app.planner.common.DateTexts
import app.planner.common.DayItem
import app.planner.common.DayItemRow
import app.planner.common.EditorTarget
import app.planner.common.EditorTargetSaver
import app.planner.common.PickTaskSheet
import app.planner.common.SlotSaver
import app.planner.common.TaskEditorSheet
import app.planner.common.TaskSaver
import app.planner.core.designsystem.theme.PlannerTheme
import app.planner.core.designsystem.theme.SerifFamily
import app.planner.core.domain.model.RecurrenceRule
import app.planner.core.domain.model.Slot
import app.planner.core.domain.model.Task
import app.planner.core.domain.recurrence.Occurrence
import java.time.LocalDate

/**
 * Экран «Неделя» (вид «А» из плана): семь дней списком сверху вниз.
 *
 * - Тап по названию дня — почасовая шкала этого дня ([DayScreen]).
 * - «+» у дня — шторка «Новая | Из списков»: выбранная задача сразу встаёт на этот день.
 * - Кнопка с календариком в шапке — режим планирования: внизу появляется лоток
 *   с незавершённым и задачами из списков, их можно перетащить на день
 *   (или нажать на задачу, а потом на день — для тех, кому неудобно тянуть).
 * - Тап по диапазону дат в шапке возвращает к текущей неделе.
 */
@Composable
fun WeekScreen(
    onOpenDay: (LocalDate) -> Unit,
    contentPadding: PaddingValues,
    viewModel: WeekViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var planning by rememberSaveable { mutableStateOf(false) }
    var pickSlot by rememberSaveable(stateSaver = SlotSaver) { mutableStateOf<Slot?>(null) }
    // Что открыто в редакторе: задача или правило повторения (с его повторением и паузой); null — закрыт
    var editing by rememberSaveable(stateSaver = EditorTargetSaver) {
        mutableStateOf<EditorTarget?>(
            null
        )
    }
    // Для тех, кому неудобно перетаскивать: тап по задаче в лотке, потом тап по дню
    var selectedFromTray by rememberSaveable(stateSaver = TaskSaver) { mutableStateOf<Task?>(null) }

    WeekContent(
        state = state,
        planning = planning,
        selectedFromTray = selectedFromTray,
        contentPadding = contentPadding,
        onPrev = viewModel::previousWeek,
        onNext = viewModel::nextWeek,
        onThisWeek = viewModel::thisWeek,
        onTogglePlanning = { planning = !planning; selectedFromTray = null },
        onOpenDay = onOpenDay,
        onAdd = { day -> pickSlot = Slot(day) },
        onToggle = viewModel::toggle,
        onCompleteOccurrence = viewModel::completeOccurrence,
        // Тап по повторению — открыть его правило в том же редакторе, что и задачи
        onEditRule = { rule -> editing = EditorTarget.forRule(rule, state.today) },
        onEdit = { editing = EditorTarget(it) },
        onDrop = { taskId, day ->
            viewModel.dropOnDay(taskId, day)
            selectedFromTray = null
        },
        onSelectFromTray = { selectedFromTray = if (selectedFromTray?.id == it.id) null else it },
    )

    pickSlot?.let { slot ->
        PickTaskSheet(
            slot = slot,
            today = state.today,
            candidates = state.unscheduled,
            lists = state.lists,
            goalTitles = state.goalTitles,
            onDismiss = { pickSlot = null },
            onPick = { viewModel.schedule(it, slot); pickSlot = null },
            onCreate = { viewModel.createIn(slot, it); pickSlot = null },
        )
    }
    editing?.let { target ->
        TaskEditorSheet(
            target = target,
            lists = state.lists,
            today = state.today,
            onDismiss = { editing = null },
            onSave = viewModel::save,
            onDelete = viewModel::delete,
            onPause = viewModel::pauseRule,
            onResume = viewModel::resumeRule,
        )
    }
}

/**
 * Рисующая часть «Недели»: только разметка, без ViewModel и шторок.
 * Режим планирования и выбранная в лотке задача приходят параметрами,
 * поэтому превью может показать и обычный вид, и режим планирования (см. `WeekPreviews.kt`).
 */
@Composable
fun WeekContent(
    state: WeekUiState,
    planning: Boolean,
    selectedFromTray: Task?,
    contentPadding: PaddingValues,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onThisWeek: () -> Unit,
    onTogglePlanning: () -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    onAdd: (LocalDate) -> Unit,
    onToggle: (Task) -> Unit,
    onCompleteOccurrence: (Occurrence) -> Unit,
    onEditRule: (RecurrenceRule) -> Unit,
    onEdit: (Task) -> Unit,
    onDrop: (taskId: Long, day: LocalDate) -> Unit,
    onSelectFromTray: (Task) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(contentPadding)
    ) {
        WeekHeader(
            weekStart = state.weekStart,
            planning = planning,
            onPrev = onPrev,
            onNext = onNext,
            onThisWeek = onThisWeek,
            onTogglePlanning = onTogglePlanning,
        )
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.weight(1f),
        ) {
            items(state.days, key = { it.toEpochDay() }) { day ->
                DayBlock(
                    day = day,
                    isToday = day == state.today,
                    items = state.itemsByDay[day].orEmpty(),
                    state = state,
                    planning = planning,
                    onOpenDay = { onOpenDay(day) },
                    onAdd = { onAdd(day) },
                    onToggle = onToggle,
                    onCompleteOccurrence = onCompleteOccurrence,
                    onEditRule = onEditRule,
                    onEdit = onEdit,
                    onDrop = { taskId -> onDrop(taskId, day) },
                    // Выбрали задачу в лотке — тап по дню ставит её туда
                    onTapInPlanning = selectedFromTray?.let { task -> { onDrop(task.id, day) } },
                )
            }
        }
        if (planning) {
            PlanningTray(
                overdue = state.overdue,
                unscheduled = state.unscheduled,
                selected = selectedFromTray,
                onSelect = onSelectFromTray,
            )
        }
    }
}

/** Шапка: «21 — 27 сентября», кнопка режима планирования и стрелки между неделями. */
@Composable
private fun WeekHeader(
    weekStart: LocalDate,
    planning: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onThisWeek: () -> Unit,
    onTogglePlanning: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 12.dp),
    ) {
        Text(
            DateTexts.weekRange(weekStart),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onThisWeek),
        )
        if (planning) {
            TextButton(onClick = onTogglePlanning) { Text(stringResource(R.string.action_done)) }
        } else {
            IconButton(onClick = onTogglePlanning) {
                Icon(Icons.Outlined.EditCalendar, stringResource(R.string.planning_title))
            }
        }
        IconButton(onClick = onPrev) {
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                stringResource(R.string.cd_prev_week)
            )
        }
        IconButton(onClick = onNext) {
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                stringResource(R.string.cd_next_week)
            )
        }
    }
    if (planning) {
        Text(
            stringResource(R.string.planning_hint),
            color = PlannerTheme.paper.inkMuted,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
    }
}

/**
 * Один день недели: заголовок («Ср 23») и его задачи. Сегодняшний день подсвечен.
 *
 * В режиме планирования день становится «целью» перетаскивания (drag and drop):
 * [DragAndDropTarget] получает событие, когда над днём отпускают задачу из лотка.
 * В перетаскиваемом объекте лежит id задачи в виде текста (ClipData).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DayBlock(
    day: LocalDate,
    isToday: Boolean,
    items: List<DayItem>,
    state: WeekUiState,
    planning: Boolean,
    onOpenDay: () -> Unit,
    onAdd: () -> Unit,
    onToggle: (Task) -> Unit,
    onCompleteOccurrence: (Occurrence) -> Unit,
    onEditRule: (RecurrenceRule) -> Unit,
    onEdit: (Task) -> Unit,
    onDrop: (Long) -> Unit,
    onTapInPlanning: (() -> Unit)?,
) {
    val paper = PlannerTheme.paper
    var dragOver by remember { mutableStateOf(false) }
    val dropTarget = remember(day) {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                dragOver = false
                val id = event.toAndroidDragEvent().clipData?.getItemAt(0)?.text?.toString()
                    ?.toLongOrNull()
                    ?: return false
                onDrop(id)
                return true
            }

            override fun onEntered(event: DragAndDropEvent) {
                dragOver = true
            }

            override fun onExited(event: DragAndDropEvent) {
                dragOver = false
            }

            override fun onEnded(event: DragAndDropEvent) {
                dragOver = false
            }
        }
    }
    val shape = RoundedCornerShape(10.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .then(
                when {
                    dragOver -> Modifier
                        .border(1.5.dp, paper.timeAccent, shape)
                        .background(paper.highlight, shape)

                    isToday -> Modifier.background(paper.highlight, shape)
                    else -> Modifier
                },
            )
            .then(
                if (planning) {
                    Modifier.dragAndDropTarget(
                        shouldStartDragAndDrop = {
                            it.mimeTypes().contains(ClipDescription.MIMETYPE_TEXT_PLAIN)
                        },
                        target = dropTarget,
                    )
                } else Modifier,
            )
            .then(if (onTapInPlanning != null) Modifier.clickable(onClick = onTapInPlanning) else Modifier)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = DateTexts.shortDay(day) + if (isToday) " · " + stringResource(R.string.day_today_lower) else "",
                fontFamily = SerifFamily,
                color = if (isToday) paper.timeAccent else paper.inkMuted,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onOpenDay)
                    .padding(vertical = 6.dp),
            )
            if (!planning) {
                IconButton(onClick = onAdd) {
                    Icon(
                        Icons.Outlined.Add,
                        stringResource(R.string.cd_add_to_day, DateTexts.shortDay(day)),
                        tint = paper.inkMuted
                    )
                }
            }
        }
        if (items.isEmpty() && planning) {
            Text(
                stringResource(R.string.week_free),
                color = paper.inkFaint,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        items.forEach { item ->
            DayItemRow(
                item,
                state.lists,
                state.goalTitles,
                onToggle,
                onCompleteOccurrence,
                onEdit,
                onEditRule
            )
        }
    }
}

/**
 * Лоток режима планирования: вкладки «Незавершённое» и «Из списков».
 * Задачу можно зажать и перетащить на день (dragAndDropSource начинает перетаскивание
 * по долгому нажатию) или просто нажать, чтобы выбрать, а потом нажать на день.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun PlanningTray(
    overdue: List<Task>,
    unscheduled: List<Task>,
    selected: Task?,
    onSelect: (Task) -> Unit,
) {
    val paper = PlannerTheme.paper
    var tab by rememberSaveable { mutableIntStateOf(if (overdue.isNotEmpty()) 0 else 1) }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 2.dp) {
        Column {
            PrimaryTabRow(
                selectedTabIndex = tab,
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Tab(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    text = { Text(stringResource(R.string.planning_tray_overdue, overdue.size)) })
                Tab(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    text = { Text(stringResource(R.string.pick_tab_lists)) })
            }
            val items = if (tab == 0) overdue else unscheduled
            if (items.isEmpty()) {
                Text(
                    stringResource(R.string.planning_tray_empty),
                    color = paper.inkMuted,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .heightIn(max = 180.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp),
                ) {
                    items.forEach { task ->
                        val isSelected = selected?.id == task.id
                        val shape = RoundedCornerShape(16.dp)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primaryContainer else paper.paper,
                                    shape
                                )
                                .border(
                                    0.5.dp,
                                    if (isSelected) paper.timeAccent else MaterialTheme.colorScheme.outline,
                                    shape
                                )
                                // Долгое нажатие начинает перетаскивание, обычный тап — выбор
                                .dragAndDropSource { _ ->
                                    DragAndDropTransferData(
                                        ClipData.newPlainText(
                                            "task",
                                            task.id.toString()
                                        )
                                    )
                                }
                                .clickable { onSelect(task) }
                                .semantics { contentDescription = task.title }
                                .padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                        ) {
                            Icon(Icons.Outlined.DragIndicator, null, tint = paper.inkFaint)
                            Text(
                                task.title,
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}
