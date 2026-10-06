package app.planner.feature.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.planner.R
import app.planner.common.DateTexts
import app.planner.core.designsystem.component.DashedRule
import app.planner.core.designsystem.component.PaperChip
import app.planner.core.designsystem.component.SectionLabel
import app.planner.core.designsystem.theme.PlannerTheme
import app.planner.core.domain.model.Slot
import app.planner.core.domain.model.Task
import app.planner.core.domain.overdue.OverdueGroup
import java.time.LocalDate

/**
 * Разбор хвостов: по одной задаче или всё сразу. «Не сейчас» прячет баннер до завтра —
 * без упрёков, задачи никуда не денутся.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverdueSheet(
    overdue: Map<OverdueGroup, List<Task>>,
    today: LocalDate,
    onDismiss: () -> Unit,
    onSchedule: (Task, Slot) -> Unit,
    onReturnToList: (Task) -> Unit,
    onAllToToday: () -> Unit,
    onAllBack: () -> Unit,
    onNotNow: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        OverdueSheetBody(
            overdue = overdue,
            today = today,
            onSchedule = onSchedule,
            onReturnToList = onReturnToList,
            onAllToToday = onAllToToday,
            onAllBack = onAllBack,
            onNotNow = onNotNow,
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
fun OverdueSheetBody(
    overdue: Map<OverdueGroup, List<Task>>,
    today: LocalDate,
    onSchedule: (Task, Slot) -> Unit,
    onReturnToList: (Task) -> Unit,
    onAllToToday: () -> Unit,
    onAllBack: () -> Unit,
    onNotNow: () -> Unit,
) {
    Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
        Text(stringResource(R.string.overdue_title), style = MaterialTheme.typography.titleLarge)

        LazyColumn(Modifier.heightIn(max = 440.dp)) {
            OverdueGroup.entries.forEach { group ->
                val groupTasks = overdue[group].orEmpty()
                if (groupTasks.isNotEmpty()) {
                    item(key = "g_$group") { SectionLabel(stringResource(group.labelRes())) }
                    items(groupTasks, key = { it.id }) { task ->
                        OverdueItem(
                            task = task,
                            onToday = { onSchedule(task, Slot(today, task.partOfDay)) },
                            onTomorrow = { onSchedule(task, Slot(today.plusDays(1), task.partOfDay)) },
                            onBack = { onReturnToList(task) },
                        )
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
            OutlinedButton(onClick = onAllToToday, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.overdue_all_today), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            OutlinedButton(onClick = onAllBack, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.overdue_all_back), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        TextButton(onClick = onNotNow, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(stringResource(R.string.overdue_not_now))
        }
    }
}

@Composable
private fun OverdueItem(
    task: Task,
    onToday: () -> Unit,
    onTomorrow: () -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(task.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(DateTexts.shortDay(task.date!!), color = PlannerTheme.paper.inkMuted, style = MaterialTheme.typography.labelMedium)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)) {
            PaperChip(stringResource(R.string.day_today), selected = false, onClick = onToday)
            PaperChip(stringResource(R.string.day_tomorrow), selected = false, onClick = onTomorrow)
            PaperChip(stringResource(R.string.action_to_list), selected = false, onClick = onBack)
        }
        DashedRule()
    }
}

private fun OverdueGroup.labelRes(): Int = when (this) {
    OverdueGroup.YESTERDAY -> R.string.overdue_yesterday
    OverdueGroup.THIS_WEEK -> R.string.overdue_this_week
    OverdueGroup.OLDER -> R.string.overdue_older
}
