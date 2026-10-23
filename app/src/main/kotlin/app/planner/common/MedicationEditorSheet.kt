package app.planner.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.planner.R
import app.planner.core.designsystem.component.PaperChip
import app.planner.core.designsystem.theme.PlannerTheme
import app.planner.core.domain.medication.MedicationHistory
import app.planner.core.domain.medication.Medications
import app.planner.core.domain.model.FoodRelation
import app.planner.core.domain.model.Medication
import app.planner.core.domain.model.Repeat
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/**
 * Какое лекарство открыто в редакторе: [id] уже сохранённого или 0 — новое.
 * [name] — название нового, если его уже набрали в окне «Добавить» («Витамин D» → «Лекарство»).
 */
data class MedicationTarget(val id: Long, val name: String = "")

/** Готовые варианты курса в меню: неделя, 10 дней, две недели, месяц. */
private val COURSE_DAYS = listOf(7, 10, 14, 30)

/**
 * Шторка «Лекарство»: новое ([medication] = null) или правка существующего.
 *
 * @param newName название нового лекарства (уже набранное в окне «Добавить»).
 * @param history как принимали последние 30 дней — у уже сохранённого лекарства.
 * @param onSave сохранить; шторка закроется сама.
 * @param onPause перерыв с сегодня по until (null — пока не нажмут «Продолжить»), [onResume] — продолжить.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationEditorSheet(
    medication: Medication?,
    today: LocalDate,
    onDismiss: () -> Unit,
    onSave: (Medication) -> Unit,
    onDelete: (Medication) -> Unit,
    onPause: (medicationId: Long, until: LocalDate?) -> Unit,
    onResume: (medicationId: Long) -> Unit,
    newName: String = "",
    history: MedicationHistory? = null,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        MedicationEditorBody(
            initial = medication,
            newName = newName,
            today = today,
            history = history,
            onSave = { onSave(it); onDismiss() },
            onDelete = medication?.let { med -> { onDelete(med); onDismiss() } },
            onPause = medication?.let { med -> { until -> onPause(med.id, until); onDismiss() } },
            onResume = medication?.let { med -> { onResume(med.id); onDismiss() } },
        )
    }
}

/**
 * Содержимое шторки «Лекарство» — без рамки ModalBottomSheet, чтобы его можно было показать в превью.
 *
 * Сверху вниз: что (название, доза, как с едой) → когда (времена приёмов, по каким дням, с какого дня,
 * как долго) → напоминания → заметка. Так же читается и сама подпись на «Сегодня»:
 * «9:00 · Витамин D — 1 капсула · после еды».
 *
 * @param onDelete / [onPause] / [onResume] — null у нового лекарства: удалять и прерывать ещё нечего.
 * @param autoFocus поставить курсор в название сразу (у нового лекарства в приложении; в превью — нет).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MedicationEditorBody(
    initial: Medication?,
    today: LocalDate,
    onSave: (Medication) -> Unit,
    newName: String = "",
    history: MedicationHistory? = null,
    onDelete: (() -> Unit)? = null,
    onPause: ((until: LocalDate?) -> Unit)? = null,
    onResume: (() -> Unit)? = null,
    autoFocus: Boolean = initial == null,
) {
    // Всё, что вводят, — в rememberSaveable: переживёт поворот экрана (см. Savers.kt).
    // LocalDate, LocalTime и enum Bundle хранит сам; списку времён нужен TimesSaver
    var name by rememberSaveable { mutableStateOf(initial?.name ?: newName) }
    var dose by rememberSaveable { mutableStateOf(initial?.dose.orEmpty()) }
    var food by rememberSaveable { mutableStateOf(initial?.food) }
    var times by rememberSaveable(stateSaver = TimesSaver) {
        // Новое лекарство — один приём утром: самый частый случай, поменять легко
        mutableStateOf(initial?.doseTimes ?: listOf(LocalTime.of(9, 0)))
    }
    var repeat by rememberSaveable(stateSaver = RepeatSaver) {
        mutableStateOf<Repeat?>(initial?.repeat ?: Repeat.Daily)
    }
    var start by rememberSaveable { mutableStateOf(initial?.startDate ?: today) }
    var end by rememberSaveable { mutableStateOf(initial?.endDate) }
    var remind by rememberSaveable { mutableStateOf(initial?.remind ?: true) }
    var repeatReminder by rememberSaveable { mutableStateOf(initial?.repeatReminder ?: false) }
    var note by rememberSaveable { mutableStateOf(initial?.note.orEmpty()) }

    // Открытые окна: какое время меняем (номер в times; -1 — добавляем новое), шторки и меню
    var editingTime by rememberSaveable { mutableStateOf<Int?>(null) }
    var showRepeat by rememberSaveable { mutableStateOf(false) }
    var courseMenu by remember { mutableStateOf(false) }
    var pickEnd by rememberSaveable { mutableStateOf(false) }
    var showPause by rememberSaveable { mutableStateOf(false) }

    val focus = remember { FocusRequester() }
    val paper = PlannerTheme.paper
    val canSave = name.isNotBlank() && times.isNotEmpty()

    fun save() {
        if (!canSave) return
        onSave(
            Medication(
                id = initial?.id ?: 0,
                name = name.trim(),
                dose = dose.trim(),
                food = food,
                times = times,
                repeat = repeat ?: Repeat.Daily,
                startDate = start,
                endDate = end,
                pauses = initial?.pauses.orEmpty(),
                remind = remind,
                // Повтор напоминания имеет смысл, только когда напоминают
                repeatReminder = remind && repeatReminder,
                note = note.trim(),
                position = initial?.position ?: 0,
            ),
        )
    }

    Column(
        Modifier
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .imePadding()
            // Полей много: на маленьком экране шторку можно прокрутить
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp),
    ) {
        // --- Что ---
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            placeholder = { Text(stringResource(R.string.medication_name_hint)) },
            textStyle = MaterialTheme.typography.titleMedium,
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focus),
        )
        OutlinedTextField(
            value = dose,
            onValueChange = { dose = it },
            placeholder = { Text(stringResource(R.string.medication_dose_hint)) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )

        // Как с едой: «неважно» — тоже выбор (null), он и стоит первым
        Row(Modifier.padding(top = 12.dp)) {
            Icon(
                Icons.Outlined.Restaurant,
                null,
                tint = paper.inkMuted,
                modifier = Modifier.padding(top = 6.dp)
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(start = 12.dp),
            ) {
                PaperChip(stringResource(R.string.food_any), food == null, { food = null })
                FoodRelation.entries.forEach { option ->
                    PaperChip(foodChipText(option), food == option, { food = option })
                }
            }
        }

        // --- Когда: времена приёмов. Нажали на время — поменять, ✕ — убрать (последнее убрать нельзя) ---
        Row(Modifier.padding(top = 12.dp)) {
            Icon(
                Icons.Outlined.Schedule,
                null,
                tint = paper.inkMuted,
                modifier = Modifier.padding(top = 14.dp)
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(start = 12.dp),
            ) {
                val removeDescription = stringResource(R.string.cd_remove_time)
                times.forEachIndexed { index, time ->
                    InputChip(
                        selected = false,
                        onClick = { editingTime = index },
                        label = { Text(DateTexts.time(time)) },
                        trailingIcon = if (times.size > 1) {
                            {
                                Icon(
                                    Icons.Outlined.Close,
                                    removeDescription,
                                    modifier = Modifier
                                        .size(InputChipDefaults.IconSize)
                                        .clickable { times = times - time },
                                )
                            }
                        } else null,
                    )
                }
                AssistChip(
                    onClick = { editingTime = -1 },
                    label = { Text(stringResource(R.string.medication_time_add)) },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.Add,
                            null,
                            Modifier.size(InputChipDefaults.IconSize)
                        )
                    },
                )
            }
        }

        EditorActionRow(
            icon = Icons.Outlined.Repeat,
            value = repeatSummary(repeat ?: Repeat.Daily),
            action = "",
            onClick = { showRepeat = true },
            onClear = null,
            clearDescription = "",
            modifier = Modifier.padding(top = 4.dp),
        )
        DayRow(
            value = startText(start, today),
            date = start,
            today = today,
            onPick = { day ->
                // Курс «переезжает» вместе с началом: было «10 дней с 5-го» — осталось 10 дней
                end = end?.let { day.plusDays(ChronoUnit.DAYS.between(start, it)) }
                start = day
            },
            onClear = null,
        )

        // Курс: бессрочно или до какого дня. Меню — готовые длины и «Выбрать дату»
        Box {
            EditorActionRow(
                icon = Icons.Outlined.EventAvailable,
                value = courseValue(start, end),
                action = "",
                onClick = { courseMenu = true },
                onClear = if (end != null) ({ end = null }) else null,
                clearDescription = stringResource(R.string.cd_clear_course),
            )
            DropdownMenu(expanded = courseMenu, onDismissRequest = { courseMenu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.medication_course_endless)) },
                    onClick = { courseMenu = false; end = null },
                )
                COURSE_DAYS.forEach { days ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                pluralStringResource(
                                    R.plurals.medication_course_days,
                                    days,
                                    days
                                )
                            )
                        },
                        onClick = { courseMenu = false; end = Medications.courseEnd(start, days) },
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.medication_course_until_date)) },
                    onClick = { courseMenu = false; pickEnd = true },
                )
            }
        }

        // --- Напоминания ---
        SwitchRow(
            icon = Icons.Outlined.NotificationsNone,
            title = stringResource(R.string.medication_remind),
            hint = stringResource(R.string.medication_remind_hint),
            checked = remind,
            onCheckedChange = { remind = it },
        )
        if (remind) {
            SwitchRow(
                icon = Icons.Outlined.NotificationsActive,
                title = stringResource(R.string.medication_repeat_reminder),
                hint = stringResource(R.string.medication_repeat_reminder_hint),
                checked = repeatReminder,
                onCheckedChange = { repeatReminder = it },
            )
        }

        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            placeholder = { Text(stringResource(R.string.medication_note_hint)) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            minLines = 2,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )

        // Как принимали — у сохранённого лекарства, если приёмы уже были
        history?.let { medicationHistoryText(it) }?.let {
            Text(
                it,
                color = paper.inkMuted,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        // --- Перерыв: только у сохранённого лекарства ---
        if (onPause != null && initial != null) {
            val pause = Medications.activePause(initial, today)
            if (pause == null) {
                EditorActionRow(
                    icon = Icons.Outlined.PauseCircle,
                    value = null,
                    action = stringResource(R.string.medication_pause),
                    onClick = { showPause = true },
                    onClear = null,
                    clearDescription = "",
                    modifier = Modifier.padding(top = 4.dp),
                )
            } else {
                PausedRow(pause = pause, onResume = { onResume?.invoke() })
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp),
        ) {
            if (onDelete != null) {
                TextButton(onClick = onDelete) {
                    Icon(Icons.Outlined.Delete, null)
                    Text(
                        stringResource(R.string.action_delete),
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
            }
            Row(Modifier.weight(1f)) {}
            Button(onClick = ::save, enabled = canSave) {
                Text(stringResource(if (initial == null) R.string.action_add else R.string.action_save))
            }
        }
    }

    if (autoFocus) LaunchedEffect(Unit) { focus.requestFocus() }

    editingTime?.let { index ->
        PlannerTimePickerDialog(
            initial = times.getOrNull(index) ?: LocalTime.of(21, 0),
            onPick = { picked ->
                editingTime = null
                // Поменяли время или добавили новое; одинаковые времена склеиваются, порядок — по часам
                val changed = if (index in times.indices) times.toMutableList()
                    .also { it[index] = picked } else times + picked
                times = changed.distinct().sorted()
            },
            onDismiss = { editingTime = null },
        )
    }
    if (showRepeat) {
        RepeatSheet(
            initial = repeat,
            today = today,
            date = start,
            simpleOnly = true,
            onDismiss = { showRepeat = false },
            onDone = { repeat = it ?: Repeat.Daily; showRepeat = false },
        )
    }
    if (pickEnd) {
        PlannerDatePickerDialog(
            initial = end ?: start.plusDays(9),
            // Раньше начала курс закончиться не может — тогда это курс в один день
            onPick = { pickEnd = false; end = maxOf(it, start) },
            onDismiss = { pickEnd = false },
        )
    }
    if (showPause) {
        PauseSheet(
            today = today,
            onDismiss = { showPause = false },
            onPause = { until -> showPause = false; onPause?.invoke(until) },
        )
    }
}

/** «До еды», «Во время еды», «После еды» — на кнопках выбора (с большой буквы). */
@Composable
private fun foodChipText(food: FoodRelation): String =
    foodText(food).replaceFirstChar { it.titlecase() }

/** «Бессрочно» или «Курс до 3 ноября · 10 дней» — строка курса в редакторе. */
@Composable
private fun courseValue(start: LocalDate, end: LocalDate?): String {
    if (end == null) return stringResource(R.string.medication_course_endless)
    val days = ChronoUnit.DAYS.between(start, end).toInt() + 1
    return stringResource(
        R.string.medication_course_until,
        DateTexts.dayMonth(end),
        pluralStringResource(R.plurals.medication_course_days, days, days),
    )
}
