package app.planner.common

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import app.planner.R
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Варианты подготовки, в днях: месяц, 2 недели, неделя. */
private val PREP_OPTIONS = listOf(30, 14, 7)

/**
 * Строка «Подготовиться заранее» в редакторе повторения «раз в год»: за месяц / 2 недели / неделю.
 * Та же [EditorActionRow] с выпадающим меню, что у «Напомнить», «День» и «Время».
 *
 * Выбрали «за 2 недели» — за 14 дней до дня (дня рождения, годовщины) в плане появится обычная
 * задача с тем же названием: купить подарок, заказать столик. ✕ — без подготовки.
 *
 * @param days за сколько дней; null — без подготовки.
 */
@Composable
fun PrepRow(days: Int?, onChange: (Int?) -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        EditorActionRow(
            icon = Icons.Outlined.EventAvailable,
            value = days?.let { stringResource(R.string.prep_value, prepText(it)) },
            action = stringResource(R.string.prep_add),
            onClick = { menuOpen = true },
            onClear = { onChange(null) },
            clearDescription = stringResource(R.string.cd_clear_prep),
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            PREP_OPTIONS.forEach { option ->
                DropdownMenuItem(
                    text = { Text(prepText(option)) },
                    onClick = { menuOpen = false; onChange(option) },
                )
            }
        }
    }
}

/** «за месяц», «за 2 недели», «за неделю». */
@Composable
private fun prepText(days: Int): String = when (days) {
    30 -> stringResource(R.string.prep_month)
    14 -> stringResource(R.string.prep_two_weeks)
    else -> stringResource(R.string.prep_week)
}

/**
 * Подпись под задачей-подготовкой: к какому дню и сколько до него осталось —
 * «21 октября · через 30 дней», «… · завтра», «… · сегодня», а если день уже прошёл — «… · уже прошло».
 */
@Composable
fun prepSubtitle(prepFor: LocalDate, today: LocalDate): String {
    val day = DateTexts.dayMonth(prepFor)
    // ChronoUnit.DAYS.between — сколько дней от сегодня до того дня (отрицательное — уже прошёл)
    return when (val left = ChronoUnit.DAYS.between(today, prepFor).toInt()) {
        0 -> stringResource(R.string.prep_today, day)
        1 -> stringResource(R.string.prep_tomorrow, day)
        in 2..Int.MAX_VALUE -> pluralStringResource(R.plurals.prep_in_days, left, day, left)
        else -> stringResource(R.string.prep_passed, day)
    }
}
