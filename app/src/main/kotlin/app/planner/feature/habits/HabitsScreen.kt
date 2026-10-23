package app.planner.feature.habits

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.planner.R
import app.planner.common.DateTexts
import app.planner.common.EditorTarget
import app.planner.common.EditorTargetSaver
import app.planner.common.HabitHistoryView
import app.planner.common.MedicationEditorSheet
import app.planner.common.MedicationRow
import app.planner.common.MedicationTarget
import app.planner.common.MedicationTargetSaver
import app.planner.common.RuleRow
import app.planner.common.TaskEditorSheet
import app.planner.common.pausedText
import app.planner.common.ruleScheduleText
import app.planner.core.designsystem.component.DashedRule
import app.planner.core.designsystem.component.SectionLabel
import app.planner.core.designsystem.theme.PlannerTheme
import app.planner.core.domain.model.Medication
import app.planner.core.domain.model.Repeat
import app.planner.core.domain.model.Task
import app.planner.core.domain.recurrence.Recurrence
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.time.LocalDate

/**
 * Экран «Привычки» — все привычки в одном месте. Открывается кнопкой «Все привычки» в конце
 * ряда «таблеток» на «Сегодня».
 *
 * Зачем он: на «Сегодня» видны только сегодняшние привычки. Привычка, которая начнётся завтра,
 * или та, что на паузе, оттуда не видна — и поправить её было негде. Здесь видно всё:
 * как часто, как идёт (мягкая статистика за 30 дней), когда начнётся, до какого дня пауза.
 * Тап — редактор привычки; «+ Привычка» — новая.
 * Порядок — как у «таблеток» на «Сегодня»: подержать привычку и перетащить.
 *
 * Привычки живут здесь, а не в списках: списки — это темы («Дом», «Работа»), а привычки —
 * отдельный вид вещей, их не планируют и не закрывают. Второй раздел — лекарства: они тоже повторяются
 * и не «закрываются», и их тоже удобно видеть все сразу — какие идут, какие на перерыве, какие закончились.
 */
@Composable
fun HabitsScreen(
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    viewModel: HabitsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Что открыто в редакторе: привычка (правило) или новая; null — закрыт
    var editing by rememberSaveable(stateSaver = EditorTargetSaver) {
        mutableStateOf<EditorTarget?>(
            null
        )
    }
    // Какое лекарство открыто в редакторе (id; 0 — новое); null — закрыт
    var editingMedication by rememberSaveable(stateSaver = MedicationTargetSaver) {
        mutableStateOf<MedicationTarget?>(null)
    }

    HabitsContent(
        state = state,
        contentPadding = contentPadding,
        onBack = onBack,
        onOpen = { item -> editing = EditorTarget.forRule(item.rule, state.today) },
        // Новая привычка: каждый день с сегодня, переключатель «Привычка» уже включён
        onAdd = {
            editing = EditorTarget(
                task = Task(title = "", date = state.today),
                repeat = Repeat.Daily,
                isHabit = true,
            )
        },
        onReorder = viewModel::reorder,
        onOpenMedication = { editingMedication = MedicationTarget(it.id) },
        onAddMedication = { editingMedication = MedicationTarget(id = 0) },
    )

    editingMedication?.let { target ->
        val item = state.medications.firstOrNull { it.medication.id == target.id }
        // key(target): открыли другое лекарство — поля редактора начинаются заново
        key(target) {
            MedicationEditorSheet(
                medication = item?.medication,
                today = state.today,
                history = item?.history,
                onDismiss = { editingMedication = null },
                onSave = viewModel::saveMedication,
                onDelete = viewModel::deleteMedication,
                onPause = viewModel::pauseMedication,
                onResume = viewModel::resumeMedication,
            )
        }
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
            goals = state.goals,
            // Статистика — у уже сохранённой привычки (у новой её ещё нет)
            habitHistory = state.habits.firstOrNull { it.rule.id == target.task.ruleId }?.history,
        )
    }
}

/**
 * Рисующая часть экрана: только разметка, без ViewModel и шторок —
 * её можно показать в превью Android Studio.
 */
@Composable
fun HabitsContent(
    state: HabitsUiState,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpen: (HabitItem) -> Unit,
    onAdd: () -> Unit,
    onReorder: (ids: List<Long>) -> Unit,
    onOpenMedication: (Medication) -> Unit,
    onAddMedication: () -> Unit,
) {
    val paper = PlannerTheme.paper

    // ПЕРЕТАСКИВАНИЕ (библиотека Reorderable, как у шагов цели).
    // Пока привычку тащат, порядок меняется только здесь, в памяти экрана; в базу он записывается
    // один раз — когда привычку отпустили (onReorder). remember(state.habits): пришли новые данные
    // из базы (добавили привычку, переименовали) — порядок берётся заново из них
    var habits by remember(state.habits) { mutableStateOf(state.habits) }
    val lazyListState = rememberLazyListState()
    // Лёгкая вибрация: «взяли», «сдвинулась на строку», «отпустили»
    val haptic = LocalHapticFeedback.current
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        // from / to — элементы LazyColumn; их key — id правил. На подсказку (key — строка) не встаём
        val fromIndex = habits.indexOfFirst { it.rule.id == from.key }
        val toIndex = habits.indexOfFirst { it.rule.id == to.key }
        if (fromIndex >= 0 && toIndex >= 0) {
            habits = habits.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
            haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        }
    }

    fun commit(newOrder: List<HabitItem>) {
        habits = newOrder
        onReorder(newOrder.map { it.rule.id })
    }

    /** Сдвинуть привычку на одно место (для TalkBack: «Выше» / «Ниже»). */
    fun moved(index: Int, by: Int): List<HabitItem> {
        val target = index + by
        if (target !in habits.indices) return habits
        return habits.toMutableList().apply { add(target, removeAt(index)) }
    }

    Box(
        Modifier
            .fillMaxSize()
            .padding(contentPadding)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.cd_back))
                }
                // Есть лекарства — они тоже здесь, и заголовок говорит об этом
                Text(
                    stringResource(
                        if (state.medications.isEmpty()) R.string.habits_title else R.string.habits_and_medications,
                    ),
                    style = MaterialTheme.typography.titleLarge,
                )
            }

            LazyColumn(
                state = lazyListState,
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 12.dp,
                    top = 4.dp,
                    bottom = 96.dp
                ),
            ) {
                if (state.loaded && habits.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            stringResource(R.string.habits_empty),
                            color = paper.inkMuted,
                            modifier = Modifier.padding(top = 16.dp, end = 8.dp),
                        )
                    }
                }
                // Подсказка, как переставлять, — когда есть что переставлять
                if (habits.size > 1) {
                    item(key = "hint") {
                        Text(
                            stringResource(R.string.habits_reorder_hint),
                            color = paper.inkMuted,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp, end = 8.dp),
                        )
                    }
                }
                itemsIndexed(habits, key = { _, item -> item.rule.id }) { index, item ->
                    ReorderableItem(reorderState, key = item.rule.id) { isDragging ->
                        // Взятая привычка чуть приподнимается: тень и бумажный фон под ней
                        val elevation by animateDpAsState(if (isDragging) 6.dp else 0.dp)
                        Surface(shadowElevation = elevation, color = paper.paper) {
                            Column(
                                // longPressDraggableHandle — тащить после долгого нажатия в любом месте
                                // строки. Обычный тап по-прежнему открывает привычку
                                Modifier.longPressDraggableHandle(
                                    onDragStarted = {
                                        haptic.performHapticFeedback(
                                            HapticFeedbackType.LongPress
                                        )
                                    },
                                    onDragStopped = {
                                        haptic.performHapticFeedback(HapticFeedbackType.GestureEnd)
                                        commit(habits)
                                    },
                                ),
                            ) {
                                HabitRow(
                                    item,
                                    state.today,
                                    onClick = { onOpen(item) },
                                    onMoveUp = { commit(moved(index, -1)) },
                                    onMoveDown = { commit(moved(index, +1)) },
                                )
                                DashedRule()
                            }
                        }
                    }
                }

                // --- Лекарства: второй раздел. Ключи — строки «med_…», чтобы не совпасть с id привычек
                // (по ним работает перетаскивание: лекарства в нём не участвуют) ---
                item(key = "meds_label") {
                    SectionLabel(
                        stringResource(R.string.medications_section),
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
                if (state.loaded && state.medications.isEmpty()) {
                    item(key = "meds_empty") {
                        Text(
                            stringResource(R.string.medications_empty),
                            color = paper.inkMuted,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                }
                items(state.activeMedications, key = { "med_${it.medication.id}" }) { item ->
                    MedicationRow(
                        item.medication,
                        item.history,
                        state.today,
                        onClick = { onOpenMedication(item.medication) })
                    DashedRule()
                }
                // «+ Лекарство» — под списком лекарств, как продолжение раздела
                item(key = "meds_add") {
                    TextButton(onClick = onAddMedication, modifier = Modifier.padding(top = 4.dp)) {
                        Icon(Icons.Outlined.Add, null, Modifier.padding(end = 6.dp))
                        Text(stringResource(R.string.medication_add))
                    }
                }
                // Закончившиеся курсы — в конце, бледнее: вдруг курс повторят, тогда достаточно продлить
                if (state.finishedMedications.isNotEmpty()) {
                    item(key = "meds_finished") {
                        SectionLabel(
                            stringResource(R.string.medications_finished),
                            modifier = Modifier.padding(top = 16.dp),
                        )
                    }
                    items(state.finishedMedications, key = { "med_${it.medication.id}" }) { item ->
                        MedicationRow(
                            item.medication,
                            item.history,
                            state.today,
                            onClick = { onOpenMedication(item.medication) })
                        DashedRule()
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = onAdd,
            icon = { Icon(Icons.Outlined.Add, null) },
            text = { Text(stringResource(R.string.habit_switch)) },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
        )
    }
}

/**
 * Одна привычка — общая строка повторения (RuleRow): название, как часто («Каждый день · утро»)
 * и как идёт. «Как идёт» зависит от того, что с ней сейчас:
 * - на паузе — «На паузе до 12 октября» (и значок паузы);
 * - ещё не началась — «С 4 октября»;
 * - идёт — мягкая статистика: «За 30 дней — 18 раз» и полоска квадратиков.
 */
@Composable
private fun HabitRow(
    item: HabitItem,
    today: LocalDate,
    onClick: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    val rule = item.rule
    val pause = Recurrence.activePause(rule, today)
    val upLabel = stringResource(R.string.action_move_up)
    val downLabel = stringResource(R.string.action_move_down)
    RuleRow(
        rule = rule,
        today = today,
        subtitle = ruleScheduleText(rule),
        onClick = onClick,
        // Для TalkBack — «Выше» и «Ниже» в меню действий: тащить пальцем, не видя экрана, неудобно.
        // true — «выполнено»: так TalkBack понимает, что нажатие сработало
        modifier = Modifier.semantics {
            customActions = listOf(
                CustomAccessibilityAction(upLabel) { onMoveUp(); true },
                CustomAccessibilityAction(downLabel) { onMoveDown(); true },
            )
        },
    ) {
        when {
            pause != null -> StatusText(pausedText(pause))
            rule.startDate > today -> StatusText(
                stringResource(R.string.repeat_starting, DateTexts.dayMonth(rule.startDate)),
            )

            else -> HabitHistoryView(item.history, Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun StatusText(text: String) {
    Text(
        text,
        color = PlannerTheme.paper.inkMuted,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(top = 4.dp),
    )
}
