package app.planner.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.planner.R
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/**
 * Окно с календарём: выбрать один день. Используется в «Запланировать», «Пауза до…» и сроке цели.
 *
 * Зачем своя обёртка над стандартным DatePickerDialog: календарь Material 3 работает не с датами,
 * а с миллисекундами в часовом поясе UTC. Перевод туда и обратно легко сделать по-разному
 * в разных местах (например, взять часовой пояс телефона — и в Москве после полуночи выбранный
 * день «съедет» на вчера). Здесь он сделан один раз, а наружу выходят обычные LocalDate.
 *
 * @param onPick выбранный день; окно после этого нужно закрыть (вызывающий убирает его сам).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlannerDatePickerDialog(
    initial: LocalDate,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val picked = state.selectedDateMillis
                    ?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                // Ничего не выбрали (сняли выделение) — как «Отмена»
                if (picked != null) onPick(picked) else onDismiss()
            }) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    ) { DatePicker(state = state) }
}

/**
 * Окно выбора времени: часы и минуты, в 24-часовом формате. Используется в «Запланировать»
 * (точное время задачи) и в напоминании («напомнить в другое время»).
 *
 * У Material 3 нет готового «диалога времени» — есть только сам циферблат TimePicker.
 * Поэтому кладём его в тот же DatePickerDialog: это просто окно с кнопками «ОК» и «Отмена».
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlannerTimePickerDialog(
    initial: LocalTime,
    onPick: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = true
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onPick(LocalTime.of(state.hour, state.minute)) }) {
                Text(stringResource(R.string.action_ok))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.Center,
        ) { TimePicker(state = state) }
    }
}