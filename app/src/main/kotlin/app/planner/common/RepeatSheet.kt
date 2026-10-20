package app.planner.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.planner.R
import app.planner.core.designsystem.component.PaperChip
import app.planner.core.designsystem.theme.PlannerTheme
import app.planner.core.domain.model.Pause
import app.planner.core.domain.model.Repeat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.MonthDay

/** Какой вариант выбран в шторке «Повторять» — отдельно от данных (дней, числа N). */
private enum class RepeatKind { NONE, DAILY, WEEKDAYS, EVERY_N, YEARLY, CYCLE }

/**
 * Шторка «Повторять»: не повторять / каждый день / по дням недели / раз в N дней / раз в год / цикл по неделям.
 * Открывается из редактора задачи по строке «↻». Возвращает выбранное повторение
 * (null — «Не повторять») в [onDone].
 *
 * Таблицу цикла заполняют в отдельной шторке ([CycleEditorSheet]): выбрали «Цикл» — она
 * открывается сама, а потом её можно открыть снова кнопкой «Настроить таблицу».
 *
 * @param today нужен новому циклу: «эта неделя — неделя N».
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepeatSheet(
    initial: Repeat?,
    today: LocalDate,
    date: LocalDate = today,
    pauses: List<Pause> = emptyList(),
    onDismiss: () -> Unit,
    onDone: (Repeat?) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        RepeatSheetBody(
            initial = initial,
            today = today,
            date = date,
            pauses = pauses,
            onDone = onDone
        )
    }
}

/** Содержимое шторки «Повторять» — отдельно, чтобы его можно было показать в превью. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RepeatSheetBody(
    initial: Repeat?,
    today: LocalDate,
    date: LocalDate = today,
    pauses: List<Pause> = emptyList(),
    onDone: (Repeat?) -> Unit,
) {
    // Вариант и его данные храним по отдельности: переключилась на «каждый день» и обратно —
    // отмеченные дни недели и число N не потерялись
    var kind by rememberSaveable {
        mutableStateOf(
            when (initial) {
                null -> RepeatKind.NONE
                Repeat.Daily -> RepeatKind.DAILY
                is Repeat.OnWeekdays -> RepeatKind.WEEKDAYS
                is Repeat.EveryNDays -> RepeatKind.EVERY_N
                is Repeat.Yearly -> RepeatKind.YEARLY
                is Repeat.Cycle -> RepeatKind.CYCLE
            },
        )
    }
    var days by rememberSaveable {
        mutableStateOf(
            (initial as? Repeat.OnWeekdays)?.days ?: emptySet()
        )
    }
    var n by rememberSaveable { mutableIntStateOf((initial as? Repeat.EveryNDays)?.n ?: 2) }
    // Таблица цикла (RepeatSaver хранит Repeat?, поэтому и здесь тип Repeat?, а читаем как Cycle)
    var cycle by rememberSaveable(stateSaver = RepeatSaver) { mutableStateOf<Repeat?>(initial as? Repeat.Cycle) }
    var editingCycle by rememberSaveable { mutableStateOf(false) }

    // Готово можно нажать, только если выбор полный: «по дням недели» — хотя бы один день
    val result: Repeat? = when (kind) {
        RepeatKind.NONE -> null
        RepeatKind.DAILY -> Repeat.Daily
        RepeatKind.WEEKDAYS -> days.takeIf { it.isNotEmpty() }?.let { Repeat.OnWeekdays(it) }
        RepeatKind.EVERY_N -> Repeat.EveryNDays(n)
        // День в году — день повторения из строки «📅» (редактор держит их одинаковыми)
        RepeatKind.YEARLY -> Repeat.Yearly(MonthDay.from(date))
        RepeatKind.CYCLE -> cycle
    }
    val canFinish = kind == RepeatKind.NONE || result != null
    val paper = PlannerTheme.paper

    Column(
        Modifier
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .padding(bottom = 16.dp),
    ) {
        Text(stringResource(R.string.repeat_title), style = MaterialTheme.typography.titleLarge)

        KindOption(stringResource(R.string.repeat_none), kind == RepeatKind.NONE) {
            kind = RepeatKind.NONE
        }
        KindOption(stringResource(R.string.repeat_daily), kind == RepeatKind.DAILY) {
            kind = RepeatKind.DAILY
        }

        KindOption(stringResource(R.string.repeat_weekdays), kind == RepeatKind.WEEKDAYS) {
            kind = RepeatKind.WEEKDAYS
        }
        if (kind == RepeatKind.WEEKDAYS) {
            // Дни в том порядке, в каком идёт неделя по языку телефона (с пн или с вс)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(start = 48.dp, bottom = 4.dp),
            ) {
                weekOrder().forEach { day ->
                    PaperChip(
                        text = DateTexts.weekdayShort(day),
                        selected = day in days,
                        onClick = { days = if (day in days) days - day else days + day },
                    )
                }
            }
        }

        KindOption(stringResource(R.string.repeat_every_n), kind == RepeatKind.EVERY_N) {
            kind = RepeatKind.EVERY_N
        }
        if (kind == RepeatKind.EVERY_N) {
            // «−  Раз в 3 дня  +»: N от 2 (раз в 1 день — это «каждый день») до 365
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 36.dp)
            ) {
                IconButton(onClick = { n = (n - 1).coerceAtLeast(2) }, enabled = n > 2) {
                    Icon(Icons.Outlined.Remove, stringResource(R.string.cd_less))
                }
                Text(
                    pluralStringResource(R.plurals.repeat_every_n_summary, n, n),
                    color = paper.ink
                )
                IconButton(onClick = { n = (n + 1).coerceAtMost(365) }) {
                    Icon(Icons.Outlined.Add, stringResource(R.string.cd_more))
                }
            }
        }

        // Раз в год — в день из строки «📅». Под вариантом видно, какой это день и где его поменять
        KindOption(stringResource(R.string.repeat_yearly), kind == RepeatKind.YEARLY) {
            kind = RepeatKind.YEARLY
        }
        if (kind == RepeatKind.YEARLY) {
            Column(Modifier.padding(start = 48.dp, bottom = 4.dp)) {
                Text(repeatSummary(Repeat.Yearly(MonthDay.from(date))), color = paper.ink)
                Text(
                    stringResource(R.string.repeat_yearly_hint),
                    color = paper.inkMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        // Цикл: выбрали впервые — сразу открываем таблицу, иначе показываем «Цикл: 4 недели · Настроить таблицу»
        KindOption(stringResource(R.string.repeat_cycle), kind == RepeatKind.CYCLE) {
            kind = RepeatKind.CYCLE
            if (cycle == null) editingCycle = true
        }
        if (kind == RepeatKind.CYCLE) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 48.dp)
            ) {
                cycle?.let {
                    Text(
                        repeatSummary(it),
                        color = paper.ink,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
                TextButton(onClick = {
                    editingCycle = true
                }) { Text(stringResource(R.string.cycle_setup)) }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
        ) {
            Spacer(Modifier.weight(1f))
            Button(onClick = { onDone(result) }, enabled = canFinish) {
                Text(stringResource(R.string.action_done))
            }
        }
    }

    if (editingCycle) {
        CycleEditorSheet(
            initial = cycle as? Repeat.Cycle,
            today = today,
            pauses = pauses,
            onDismiss = { editingCycle = false },
            onDone = { cycle = it; editingCycle = false },
        )
    }
}

/** Строка-вариант с круглой кнопкой: нажать можно на всю строку, а не только на кружок. */
@Composable
private fun KindOption(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 2.dp),
    ) {
        // onClick = null: нажатие обрабатывает вся строка, у кружка своего обработчика нет
        RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(12.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Семь дней недели по порядку, начиная с первого дня недели по языку телефона. */
private fun weekOrder(): List<DayOfWeek> {
    val first = DateTexts.firstDayOfWeek()
    return (0L..6L).map { first.plus(it) }
}

/** «Каждый день», «По дням: ср, сб», «Раз в 3 дня», «Каждый год, 15 октября», «Цикл: 4 недели» — повторение словами. */
@Composable
fun repeatSummary(repeat: Repeat?): String = when (repeat) {
    null -> stringResource(R.string.repeat_none)
    Repeat.Daily -> stringResource(R.string.repeat_daily)
    is Repeat.OnWeekdays -> stringResource(
        R.string.repeat_weekdays_summary,
        weekOrder().filter { it in repeat.days }
            .joinToString(", ") { DateTexts.weekdayShort(it).lowercase() },
    )

    is Repeat.EveryNDays -> pluralStringResource(
        R.plurals.repeat_every_n_summary,
        repeat.n,
        repeat.n
    )

    // Год любой високосный (2000): так у 29 февраля тоже есть дата, и подпись получится «29 февраля»
    is Repeat.Yearly -> stringResource(
        R.string.repeat_yearly_summary,
        DateTexts.dayMonth(repeat.day.atYear(2000))
    )

    is Repeat.Cycle -> pluralStringResource(
        R.plurals.repeat_cycle_summary,
        repeat.weeks.size,
        repeat.weeks.size
    )
}
