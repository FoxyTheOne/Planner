package app.planner.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import app.planner.core.designsystem.theme.PlannerTheme
import app.planner.core.domain.model.Repeat
import app.planner.core.domain.model.Slot
import app.planner.core.domain.model.Task
import app.planner.core.domain.model.TaskList
import java.time.LocalDate

/**
 * Шторка создания и редактирования задачи.
 *
 * Сверху — в каком списке будет задача. По умолчанию выбран список, из которого открыли
 * шторку (или «Без списка»). В том же меню «Новый список»: можно завести список прямо сейчас,
 * не закрывая окно, — он создастся вместе с задачей.
 * Ниже — название, заметка и дата. Новая задача может сразу получить дату
 * (добавили из «Сегодня» или из слота недели) или остаться в списке без даты.
 *
 * Строка «↻» внизу — повторение. Выбрали «по средам» — при сохранении получится не задача,
 * а правило повторения (см. TaskCommands.save). Тот же редактор открывается и для правки
 * уже существующего повторения: тогда в [initial] черновик из правила (у него id = 0, а ruleId —
 * id правила), а в [initialRepeat] — как часто оно повторяется.
 *
 * @param onSave получает задачу (черновик), название нового списка, если его решили создать
 *   (иначе null), и повторение (null — «не повторять»).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditorSheet(
    initial: Task,
    lists: List<TaskList>,
    today: LocalDate,
    onDismiss: () -> Unit,
    onSave: (task: Task, newListName: String?, repeat: Repeat?) -> Unit,
    onDelete: ((Task) -> Unit)? = null,
    initialRepeat: Repeat? = null,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        TaskEditorSheetBody(
            initial = initial,
            lists = lists,
            today = today,
            onSave = onSave,
            onDelete = onDelete,
            initialRepeat = initialRepeat,
        )
    }
}

/**
 * Содержимое шторки без выезжающей рамки ModalBottomSheet.
 * Вынесено отдельно, чтобы его можно было показать в превью Android Studio:
 * сама шторка — всплывающее окно, а такие окна обычное превью не рисует.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditorSheetBody(
    initial: Task,
    lists: List<TaskList>,
    today: LocalDate,
    onSave: (task: Task, newListName: String?, repeat: Repeat?) -> Unit,
    onDelete: ((Task) -> Unit)? = null,
    initialRepeat: Repeat? = null,
) {
    // Новое — и не задача из базы (id = 0), и не правка уже существующего повторения (ruleId)
    val isNew = initial.id == 0L && initial.ruleId == null
    // Состояние шторки: то, что человек вводит, пока не нажал «Сохранить».
    // mutableStateOf — «коробка» со значением; когда в неё кладут новое, Compose перерисовывает тех, кто её читал.
    // remember — сохранить коробку между перерисовками (иначе при каждой букве она создавалась бы
    //   заново со значением initial.title, и текст в поле «откатывался» бы).
    // rememberSaveable — то же, что remember, но значение переживает и поворот экрана
    //   (Android пересоздаёт окно, и обычный remember всё бы забыл). Свои классы (Slot) он
    //   сохранять не умеет — для них указываем stateSaver из Savers.kt.
    // by — читать и писать `title` как обычную переменную, без `.value`.
    var title by rememberSaveable { mutableStateOf(initial.title) }
    var note by rememberSaveable { mutableStateOf(initial.note) }
    var listId by rememberSaveable { mutableStateOf(initial.listId) }
    // null — выбран существующий список; строка (даже пустая) — пользователь создаёт новый
    var newListName by rememberSaveable { mutableStateOf<String?>(null) }
    var slot by rememberSaveable(stateSaver = SlotSaver) {
        mutableStateOf(initial.date?.let {
            Slot(
                it,
                initial.partOfDay,
                initial.time
            )
        })
    }
    var showSchedule by rememberSaveable { mutableStateOf(false) }
    var repeat by rememberSaveable(stateSaver = RepeatSaver) { mutableStateOf(initialRepeat) }
    var showRepeat by rememberSaveable { mutableStateOf(false) }
    // FocusRequester — «пульт» для поля ввода: позволяет из кода поставить в него курсор.
    // Его сохранять не нужно (и нельзя): после поворота поле создаётся заново, и пульт — тоже
    val focus = remember { FocusRequester() }
    val newListFocus = remember { FocusRequester() }
    val creatingList = newListName != null
    var listMenuOpen by rememberSaveable { mutableStateOf(false) }

    // Сохранить можно, когда есть название задачи и, если создаётся список, — его название
    val canSave = title.isNotBlank() && newListName?.isNotBlank() != false

    fun save() {
        if (!canSave) return
        onSave(
            initial.copy(
                title = title.trim(),
                note = note.trim(),
                listId = if (newListName != null) null else listId,
                date = slot?.date,
                partOfDay = slot?.partOfDay,
                time = slot?.time,
            ),
            newListName?.trim(),
            repeat,
        )
    }

    Column(
        Modifier
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .imePadding()
            .padding(bottom = 16.dp),
    ) {
        // --- Список: первым делом видно, куда попадёт задача.
        // Выпадающее меню, а не ряд кнопок: так удобно и при 3 списках, и при 50 ---
        val selectedList = lists.firstOrNull { it.id == listId } ?: lists.firstOrNull { it.isInbox }
        Box {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { listMenuOpen = true }
                    .padding(vertical = 6.dp),
            ) {
                Icon(Icons.Outlined.Inbox, null, tint = PlannerTheme.paper.inkMuted)
                Text(
                    stringResource(R.string.task_list) + ":",
                    color = PlannerTheme.paper.inkMuted,
                    modifier = Modifier.padding(start = 12.dp, end = 8.dp),
                )
                Text(
                    text = if (creatingList) stringResource(R.string.list_new) else selectedList?.let {
                        listName(
                            it
                        )
                    }.orEmpty(),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.Outlined.ArrowDropDown,
                    stringResource(R.string.cd_choose_list),
                    tint = PlannerTheme.paper.inkMuted
                )
            }
            // DropdownMenu появляется поверх экрана под строкой; heightIn ограничивает высоту — дальше прокрутка
            DropdownMenu(
                expanded = listMenuOpen,
                onDismissRequest = { listMenuOpen = false },
                modifier = Modifier.heightIn(max = 360.dp),
            ) {
                lists.forEach { list ->
                    DropdownMenuItem(
                        text = { Text(listName(list)) },
                        leadingIcon = { ListColorDot(list) },
                        onClick = { listId = list.id; newListName = null; listMenuOpen = false },
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.list_new)) },
                    leadingIcon = { Icon(Icons.Outlined.Add, null) },
                    onClick = { newListName = newListName ?: ""; listMenuOpen = false },
                )
            }
        }
        if (newListName != null) {
            OutlinedTextField(
                value = newListName.orEmpty(),
                onValueChange = { newListName = it },
                label = { Text(stringResource(R.string.list_name_label)) },
                placeholder = { Text(stringResource(R.string.list_name_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .focusRequester(newListFocus),
            )
        }
        // Нажали «+ Новый список» — курсор сразу в поле названия списка,
        // иначе текст по привычке уйдёт в название задачи
        LaunchedEffect(creatingList) { if (creatingList) newListFocus.requestFocus() }

        // --- Сама задача ---
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            placeholder = { Text(stringResource(R.string.task_title_hint)) },
            textStyle = MaterialTheme.typography.titleMedium,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { save() }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
                .focusRequester(focus),
        )
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            placeholder = { Text(stringResource(R.string.task_note_hint)) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            minLines = 2,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )

        // --- Когда: тап открывает шторку «Запланировать» ---
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .clickable { showSchedule = true }
                .padding(vertical = 6.dp),
        ) {
            Icon(Icons.Outlined.CalendarMonth, null, tint = PlannerTheme.paper.inkMuted)
            // У повторения дата — это день, с которого оно начинается: «С сегодня · вечер»
            val current = slot
            val dateText = when {
                current != null && repeat != null ->
                    stringResource(
                        R.string.repeat_starting,
                        slotText(current, today).replaceFirstChar { it.lowercase() })

                current != null -> slotText(current, today)
                repeat != null -> stringResource(
                    R.string.repeat_starting,
                    stringResource(R.string.day_today_lower)
                )

                else -> stringResource(R.string.task_no_date)
            }
            Text(
                text = dateText,
                color = if (slot != null || repeat != null) PlannerTheme.paper.ink else PlannerTheme.paper.inkMuted,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .weight(1f),
            )
            if (slot != null) {
                IconButton(onClick = { slot = null }) {
                    Icon(
                        Icons.Outlined.Close,
                        stringResource(R.string.cd_clear_date)
                    )
                }
            }
        }

        // --- Повторять: тап открывает шторку «Повторять» ---
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showRepeat = true }
                .padding(vertical = 12.dp),
        ) {
            Icon(Icons.Outlined.Repeat, null, tint = PlannerTheme.paper.inkMuted)
            Text(
                text = repeatSummary(repeat),
                color = if (repeat != null) PlannerTheme.paper.ink else PlannerTheme.paper.inkMuted,
                modifier = Modifier.padding(start = 12.dp),
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp),
        ) {
            if (!isNew && onDelete != null) {
                TextButton(onClick = { onDelete(initial) }) {
                    Icon(Icons.Outlined.Delete, null)
                    Text(
                        stringResource(R.string.action_delete),
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
            }
            Row(Modifier.weight(1f)) {}
            Button(onClick = ::save, enabled = canSave) {
                Text(stringResource(if (isNew) R.string.action_add else R.string.action_save))
            }
        }
    }

    if (isNew) LaunchedEffect(Unit) { focus.requestFocus() }

    if (showSchedule) {
        ScheduleSheet(
            today = today,
            initial = slot,
            onDismiss = { showSchedule = false },
            onConfirm = { slot = it; showSchedule = false },
        )
    }
    if (showRepeat) {
        RepeatSheet(
            initial = repeat,
            onDismiss = { showRepeat = false },
            onDone = { repeat = it; showRepeat = false },
        )
    }
}

/** Цветная точка списка в меню; у «Без списка» — пустой кружок. */
@Composable
private fun ListColorDot(list: TaskList) {
    val paper = PlannerTheme.paper
    Box(
        Modifier
            .size(10.dp)
            .then(
                if (list.isInbox) Modifier.border(1.dp, paper.inkFaint, CircleShape)
                else Modifier.background(paper.label(list.color), CircleShape),
            ),
    )
}

/** Название списка для экрана: у «Без списка» — из ресурсов, чтобы менялось вместе с языком телефона. */
@Composable
fun listName(list: TaskList): String =
    if (list.isInbox) stringResource(R.string.list_inbox) else list.name

/** «Завтра · утро», «Пт 25, 25 сентября · 10:30» — дата задачи словами. */
@Composable
fun slotText(slot: Slot, today: LocalDate): String {
    val day = when (slot.date) {
        today -> stringResource(R.string.day_today)
        today.plusDays(1) -> stringResource(R.string.day_tomorrow)
        else -> "${DateTexts.shortDay(slot.date)}, ${DateTexts.dayMonth(slot.date)}"
    }
    val detail =
        slot.time?.let(DateTexts::time) ?: slot.partOfDay?.let { stringResource(it.labelRes()) }
    return listOfNotNull(day, detail).joinToString(" · ")
}
