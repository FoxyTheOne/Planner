package app.planner.common

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.planner.R
import app.planner.core.designsystem.theme.PlannerTheme
import app.planner.core.domain.model.PartOfDay
import java.time.LocalDate
import java.time.LocalTime

/*
 * Строки «📅 День» и «🕐 Время» в редакторе задачи.
 *
 * Раньше это была одна строка «Сегодня», и то, что время прячется там же, было не видно.
 * Теперь на каждый вопрос — своя строка, а сверху вниз читается цепочка:
 * день → время → напоминание к этому времени. Пустая строка выглядит как действие
 * («Добавить время» цветом кнопок) и сама подсказывает, что тут можно сделать.
 *
 * Обе — та же [EditorActionRow] с выпадающим меню, что у «Напомнить»: одинаковые строки
 * ведут себя одинаково.
 */

/**
 * «📅 Сегодня», «📅 Вт, 29 сентября» или действие «Запланировать».
 * Меню: сегодня, завтра, выходные, следующая неделя (у каждого — какой это день) и «Выбрать дату».
 *
 * @param value что показать в строке (день словами); null — дня нет, строка-действие.
 * @param date выбранный день — от него «следующая неделя» берёт день недели и с него открывается календарь.
 * @param onClear ✕ — убрать день; null — крестика нет (у повторения без даты «С сегодня» — подсказка, а не выбор).
 */
@Composable
fun DayRow(
    value: String?,
    date: LocalDate?,
    today: LocalDate,
    onPick: (LocalDate) -> Unit,
    onClear: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var pickDate by rememberSaveable { mutableStateOf(false) }

    Box(modifier) {
        EditorActionRow(
            icon = Icons.Outlined.CalendarMonth,
            value = value,
            action = stringResource(R.string.schedule_title),
            onClick = { menuOpen = true },
            onClear = onClear,
            clearDescription = stringResource(R.string.cd_clear_date),
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            listOf(
                R.string.day_today to today,
                R.string.day_tomorrow to today.plusDays(1),
                R.string.day_weekend to DateTexts.nearestWeekend(today),
                R.string.day_next_week to DateTexts.nextWeekSameDay(today, date),
            ).forEach { (label, day) ->
                DropdownMenuItem(
                    text = { Text(stringResource(label)) },
                    // Справа — какой это день: «Выходные · Сб 10» — чтобы не гадать
                    trailingIcon = {
                        Text(
                            DateTexts.shortDay(day),
                            color = PlannerTheme.paper.inkMuted
                        )
                    },
                    onClick = { menuOpen = false; onPick(day) },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_pick_date)) },
                onClick = { menuOpen = false; pickDate = true },
            )
        }
    }

    if (pickDate) {
        PlannerDatePickerDialog(
            initial = date ?: today,
            onPick = { pickDate = false; onPick(it) },
            onDismiss = { pickDate = false },
        )
    }
}

/**
 * «🕐 утро», «🕐 10:30» или действие «Добавить время».
 * Меню: утро, день, вечер и «Точное время…» (циферблат). ✕ — снова «в любое время».
 *
 * Точное время и часть дня взаимоисключают друг друга: выбрали 10:30 — часть дня
 * определится по времени сама (см. PlannerSettings.partOfDayFor), поэтому передаём null.
 */
@Composable
fun TimeRow(
    partOfDay: PartOfDay?,
    time: LocalTime?,
    onChange: (partOfDay: PartOfDay?, time: LocalTime?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var pickTime by rememberSaveable { mutableStateOf(false) }

    Box(modifier) {
        EditorActionRow(
            icon = Icons.Outlined.Schedule,
            value = time?.let(DateTexts::time) ?: partOfDay?.let { stringResource(it.labelRes()) },
            action = stringResource(R.string.time_add),
            onClick = { menuOpen = true },
            onClear = { onChange(null, null) },
            clearDescription = stringResource(R.string.cd_clear_time),
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            PartOfDay.entries.forEach { part ->
                DropdownMenuItem(
                    // В меню — с большой буквы («Утро»), а в строке и на чипах — как в тексте («утро»)
                    text = { Text(stringResource(part.labelRes()).replaceFirstChar { it.titlecase() }) },
                    onClick = { menuOpen = false; onChange(part, null) },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.time_exact)) },
                onClick = { menuOpen = false; pickTime = true },
            )
        }
    }

    if (pickTime) {
        PlannerTimePickerDialog(
            initial = time ?: LocalTime.of(9, 0),
            onPick = { pickTime = false; onChange(null, it) },
            onDismiss = { pickTime = false },
        )
    }
}
