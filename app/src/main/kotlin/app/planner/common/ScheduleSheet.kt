package app.planner.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.planner.R
import app.planner.core.designsystem.component.PaperChip
import app.planner.core.designsystem.theme.PlannerTheme
import app.planner.core.domain.model.PartOfDay
import app.planner.core.domain.model.Reminder
import app.planner.core.domain.model.Slot
import app.planner.core.domain.reminder.Reminders
import java.time.LocalDate
import java.time.LocalTime

/**
 * Шторка «Запланировать»: быстрые даты, часть дня, точное время — всё за один заход.
 * Открывается смахиванием на «Сегодня», значком календаря в списках и целях.
 * Время необязательно — большинство дел планируются просто «на завтра».
 * (В редакторе задачи вместо неё — отдельные строки «День» и «Время», см. DayTimeRows.kt.)
 *
 * Напоминание ([withReminder]) — прямо здесь, чтобы не открывать ради него редактор: «к врачу
 * в четверг в 10:30, напомнить за час» — за один заход. Его нет у дней повторения
 * (напоминания повторений — отдельно, в редакторе правила).
 *
 * @param initialReminder напоминание задачи сейчас; в onConfirm приходит то, что выбрали
 *   (null — без напоминания), уже подогнанное под новое время (Reminders.fitting).
 * @param onSkip есть только у дня повторения: кнопка «Пропустить этот раз» внизу.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ScheduleSheet(
    today: LocalDate,
    initial: Slot?,
    onDismiss: () -> Unit,
    onConfirm: (Slot, Reminder?) -> Unit,
    onSkip: (() -> Unit)? = null,
    withReminder: Boolean = false,
    initialReminder: Reminder? = null,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        ScheduleSheetBody(
            today = today,
            initial = initial,
            onConfirm = onConfirm,
            onSkip = onSkip,
            withReminder = withReminder,
            initialReminder = initialReminder,
        )
    }
}

/**
 * Содержимое шторки без выезжающей рамки ModalBottomSheet.
 * Вынесено отдельно, чтобы его можно было показать в превью Android Studio:
 * сама шторка — всплывающее окно, а такие окна обычное превью не рисует.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ScheduleSheetBody(
    today: LocalDate,
    initial: Slot?,
    onConfirm: (Slot, Reminder?) -> Unit,
    onSkip: (() -> Unit)? = null,
    withReminder: Boolean = false,
    initialReminder: Reminder? = null,
) {
    var date by rememberSaveable { mutableStateOf(initial?.date ?: today.plusDays(1)) }
    var part by rememberSaveable { mutableStateOf(initial?.partOfDay) }
    var time by rememberSaveable { mutableStateOf(initial?.time) }
    var reminder by rememberSaveable(stateSaver = ReminderSaver) { mutableStateOf(initialReminder) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showTimePicker by rememberSaveable { mutableStateOf(false) }

    val quickDates = listOf(
        R.string.day_today to today,
        R.string.day_tomorrow to today.plusDays(1),
        R.string.day_weekend to DateTexts.nearestWeekend(today),
        R.string.day_next_week to DateTexts.nextWeekSameDay(today, initial?.date),
    )
    val customDate = quickDates.none { it.second == date }

    Column(
        Modifier
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .padding(bottom = 16.dp),
    ) {
        Text(stringResource(R.string.schedule_title), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Быстрые даты могут совпасть: в субботу «Выходные» — это «Сегодня», а «След. неделя»
            // (если неделя с воскресенья) — «Завтра». Подсвечиваем только первую совпавшую
            val selectedIndex = quickDates.indexOfFirst { it.second == date }
            quickDates.forEachIndexed { index, (label, value) ->
                PaperChip(
                    stringResource(label),
                    selected = index == selectedIndex,
                    onClick = { date = value })
            }
            PaperChip(
                text = if (customDate) DateTexts.shortDay(date) else stringResource(R.string.action_pick_date),
                selected = customDate,
                onClick = { showDatePicker = true },
            )
        }

        Text(
            stringResource(R.string.schedule_part_of_day),
            style = MaterialTheme.typography.labelLarge,
            color = PlannerTheme.paper.inkMuted,
            modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PaperChip(
                stringResource(R.string.part_any),
                selected = part == null && time == null,
                onClick = { part = null; time = null })
            PartOfDay.entries.forEach { p ->
                PaperChip(
                    stringResource(p.labelRes()),
                    selected = part == p && time == null,
                    onClick = { part = p; time = null })
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .clickable { showTimePicker = true }
                .padding(vertical = 8.dp),
        ) {
            Icon(Icons.Outlined.Schedule, null, tint = PlannerTheme.paper.inkMuted)
            Text(
                text = time?.let(DateTexts::time) ?: stringResource(R.string.schedule_exact_time),
                color = if (time != null) PlannerTheme.paper.timeAccent else PlannerTheme.paper.ink,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .weight(1f),
            )
            if (time != null) {
                IconButton(onClick = { time = null }) {
                    Icon(Icons.Outlined.Close, stringResource(R.string.cd_clear_time))
                }
            }
        }

        if (withReminder) {
            // Показываем напоминание, подходящее времени: сняли время — строка снова «Напомнить»,
            // вернули время — выбранное «за 30 минут» вернётся (оно хранится в reminder)
            ReminderRow(
                reminder = Reminders.fitting(reminder, date, time),
                timed = time != null,
                onChange = { reminder = it },
            )
        }

        Button(
            // Напоминание — подходящее новому времени: убрали время — «за 30 минут» не от чего считать
            onClick = {
                onConfirm(
                    Slot(date, part, time),
                    Reminders.fitting(reminder, date, time)
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
        ) { Text(stringResource(R.string.action_done)) }
        // Для дня повторения: «в этот раз не делаем» — ничего никуда не переносим, день помечается пропущенным
        if (onSkip != null) {
            TextButton(
                onClick = onSkip, modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            ) {
                Text(stringResource(R.string.action_skip_once))
            }
        }
    }

    if (showDatePicker) {
        PlannerDatePickerDialog(
            initial = date,
            onPick = { date = it; showDatePicker = false },
            onDismiss = { showDatePicker = false },
        )
    }

    if (showTimePicker) {
        PlannerTimePickerDialog(
            initial = time ?: LocalTime.of(9, 0),
            onPick = { time = it; part = null; showTimePicker = false },
            onDismiss = { showTimePicker = false },
        )
    }
}

fun PartOfDay.labelRes(): Int = when (this) {
    PartOfDay.MORNING -> R.string.part_morning
    PartOfDay.DAY -> R.string.part_day
    PartOfDay.EVENING -> R.string.part_evening
}
