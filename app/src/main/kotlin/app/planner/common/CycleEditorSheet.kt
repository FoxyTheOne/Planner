package app.planner.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Button
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.planner.R
import app.planner.core.designsystem.component.PaperChip
import app.planner.core.designsystem.component.SectionLabel
import app.planner.core.designsystem.theme.PlannerTheme
import app.planner.core.domain.model.Pause
import app.planner.core.domain.model.Repeat
import app.planner.core.domain.recurrence.Recurrence
import java.time.DayOfWeek
import java.time.LocalDate

/** Самый длинный цикл, который можно настроить. Больше 12 недель на практике не нужно. */
private const val MAX_WEEKS = 12

/**
 * Таблица цикла для rememberSaveable: список недель «день → дело».
 * ArrayList и HashMap Bundle хранить умеет (они Serializable), DayOfWeek — тоже.
 */
private val WeeksSaver: Saver<List<Map<DayOfWeek, String>>, Any> = Saver(
    save = { weeks -> ArrayList(weeks.map { HashMap(it) }) },
    restore = { saved ->
        @Suppress("UNCHECKED_CAST") // в save мы сами положили туда именно этот тип
        saved as List<Map<DayOfWeek, String>>
    },
)

/**
 * Редактор цикла по неделям: сколько недель, какая идёт сейчас и что делать в каждый день.
 *
 * Чтобы не показывать сразу 7 × 4 = 28 полей, недели — вкладками «Неделя 1, 2, 3…»:
 * видна одна неделя, 7 строк по дням. Пустое поле — в этот день ничего.
 * «Как в неделе N» копирует предыдущую неделю, чтобы не набирать одно и то же.
 *
 * Дни идут с [Repeat.Cycle.weekStart] — у нового цикла это начало недели по языку телефона,
 * как в «Неделе». У существующего — то, с которым его создали (см. комментарий к Cycle).
 *
 * @param initial уже существующий цикл или null, если цикл создаётся.
 * @param pauses паузы правила: недели целиком на паузе в цикле не считаются, и без них
 *   «сейчас идёт неделя N» показывалось бы неверно.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CycleEditorSheet(
    initial: Repeat.Cycle?,
    today: LocalDate,
    pauses: List<Pause> = emptyList(),
    onDismiss: () -> Unit,
    onDone: (Repeat.Cycle) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        CycleEditorSheetBody(initial = initial, today = today, pauses = pauses, onDone = onDone)
    }
}

/** Содержимое редактора цикла — отдельно, чтобы его можно было показать в превью. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CycleEditorSheetBody(
    initial: Repeat.Cycle?,
    today: LocalDate,
    pauses: List<Pause> = emptyList(),
    onDone: (Repeat.Cycle) -> Unit,
) {
    val weekStart = initial?.weekStart ?: DateTexts.firstDayOfWeek()
    // Какая неделя идёт сейчас по уже существующему циклу — чтобы понять, меняли ли её
    val initialCurrent = initial?.let { Recurrence.currentWeekNumber(it, today, pauses) } ?: 1

    // Новый цикл — сразу 2 пустые недели: цикл из одной недели — это просто «по дням недели»
    var weeks by rememberSaveable(stateSaver = WeeksSaver) {
        mutableStateOf(initial?.weeks ?: List(2) { emptyMap() })
    }
    var current by rememberSaveable { mutableIntStateOf(initialCurrent) }
    var tab by rememberSaveable { mutableIntStateOf(current - 1) } // открываем на текущей неделе
    val paper = PlannerTheme.paper

    fun setWeeksCount(count: Int) {
        weeks =
            if (count > weeks.size) weeks + List(count - weeks.size) { emptyMap() } else weeks.take(
                count
            )
        // Недели стало меньше — номер текущей и открытая вкладка не должны указывать «за край»
        current = current.coerceAtMost(count)
        tab = tab.coerceAtMost(count - 1)
    }

    fun result(): Repeat.Cycle = when {
        initial == null -> Recurrence.newCycle(weeks, today, weekStart, current)
        // Номер текущей недели не трогали — опорная неделя остаётся прежней
        current == initialCurrent -> initial.copy(weeks = Recurrence.cleanWeeks(weeks))
        else -> Recurrence.setCurrentWeek(
            initial.copy(weeks = Recurrence.cleanWeeks(weeks)),
            today,
            current
        )
    }

    Column(
        Modifier
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .imePadding()
            // Полей много, а сверху ещё клавиатура — поэтому шторку можно прокручивать
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp),
    ) {
        Text(stringResource(R.string.repeat_cycle), style = MaterialTheme.typography.titleLarge)

        // --- Сколько недель: «−  4  +» ---
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Text(stringResource(R.string.cycle_weeks_count), modifier = Modifier.weight(1f))
            IconButton(onClick = { setWeeksCount(weeks.size - 1) }, enabled = weeks.size > 1) {
                Icon(Icons.Outlined.Remove, stringResource(R.string.cd_less))
            }
            Text("${weeks.size}", style = MaterialTheme.typography.titleMedium)
            IconButton(
                onClick = { setWeeksCount(weeks.size + 1) },
                enabled = weeks.size < MAX_WEEKS
            ) {
                Icon(Icons.Outlined.Add, stringResource(R.string.cd_more))
            }
        }

        // --- Какая неделя цикла идёт сейчас ---
        SectionLabel(stringResource(R.string.cycle_now))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            weeks.indices.forEach { i ->
                PaperChip(
                    text = "${i + 1}",
                    selected = current == i + 1,
                    onClick = { current = i + 1 })
            }
        }

        // --- Вкладки недель и дни выбранной недели ---
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(top = 20.dp),
        ) {
            weeks.indices.forEach { i ->
                PaperChip(
                    text = stringResource(R.string.cycle_week_n, i + 1),
                    selected = tab == i,
                    onClick = { tab = i })
            }
        }
        if (tab > 0) {
            TextButton(onClick = { weeks = weeks.toMutableList().also { it[tab] = it[tab - 1] } }) {
                Text(stringResource(R.string.cycle_copy_prev, tab))
            }
        }
        weekDays(weekStart).forEach { day ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 6.dp)
            ) {
                Text(
                    DateTexts.weekdayShort(day),
                    color = paper.inkMuted,
                    modifier = Modifier.width(44.dp)
                )
                OutlinedTextField(
                    value = weeks[tab][day].orEmpty(),
                    // Новая неделя — копия старой с изменённым днём: состояние неизменяемое
                    onValueChange = { text ->
                        weeks = weeks.toMutableList().also { it[tab] = it[tab] + (day to text) }
                    },
                    placeholder = { Text(stringResource(R.string.cycle_day_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        val canFinish = Recurrence.hasAnyTask(weeks)
        if (!canFinish) {
            Text(
                stringResource(R.string.cycle_empty),
                color = paper.inkMuted,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
        ) {
            Spacer(Modifier.weight(1f))
            Button(onClick = { onDone(result()) }, enabled = canFinish) {
                Text(stringResource(R.string.action_done))
            }
        }
    }
}

/** Семь дней недели, начиная с [first]. */
private fun weekDays(first: DayOfWeek): List<DayOfWeek> = (0L..6L).map { first.plus(it) }
