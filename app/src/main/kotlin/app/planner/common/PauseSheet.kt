package app.planner.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.planner.R
import app.planner.core.designsystem.theme.PlannerTheme
import java.time.LocalDate

/**
 * Шторка «Пауза»: на сколько поставить повторение на паузу.
 * Пауза начинается сегодня; в [onPause] приходит её последний день (включительно)
 * или null — «Пока не продолжу».
 *
 * «На неделю» — это 7 дней вместе с сегодняшним: с 14-го по 20-е.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PauseSheet(
    today: LocalDate,
    onDismiss: () -> Unit,
    onPause: (until: LocalDate?) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        PauseSheetBody(today = today, onPause = onPause)
    }
}

/** Содержимое шторки «Пауза» — отдельно, чтобы его можно было показать в превью. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PauseSheetBody(
    today: LocalDate,
    onPause: (until: LocalDate?) -> Unit,
) {
    var pickDate by rememberSaveable { mutableStateOf(false) }
    val paper = PlannerTheme.paper

    Column(
        Modifier
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .padding(bottom = 16.dp),
    ) {
        Text(stringResource(R.string.pause_title), style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(R.string.pause_hint),
            color = paper.inkMuted,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        )
        PauseOption(stringResource(R.string.pause_week)) { onPause(today.plusDays(6)) }
        PauseOption(stringResource(R.string.pause_two_weeks)) { onPause(today.plusDays(13)) }
        PauseOption(stringResource(R.string.pause_month)) {
            onPause(
                today.plusMonths(1).minusDays(1)
            )
        }
        PauseOption(stringResource(R.string.pause_until_date)) { pickDate = true }
        PauseOption(stringResource(R.string.pause_open_ended)) { onPause(null) }
    }

    if (pickDate) {
        PlannerDatePickerDialog(
            initial = today.plusDays(6),
            onPick = { picked ->
                pickDate = false
                // Дата в прошлом — пауза хотя бы на сегодня, а не «закончилась раньше, чем началась»
                onPause(maxOf(picked, today))
            },
            onDismiss = { pickDate = false },
        )
    }
}

/** Вариант паузы — строка во всю ширину, нажать можно куда угодно. */
@Composable
private fun PauseOption(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
    )
}
