package app.planner.reminders

import android.os.Build
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.planner.R

/**
 * Окно «Точное время» — предложение разрешить «Будильники и напоминания». Показывается один раз,
 * когда у человека впервые появилось напоминание, а точное время выключено (когда именно —
 * решает MainViewModel.showExactAlarmOffer).
 *
 * Любой ответ — «Разрешить», «Не сейчас» или просто закрыть окно — закрывает предложение навсегда
 * ([onClose]): напомнить — да, навязывать — нет. Дальше всё есть на странице «Надёжность напоминаний».
 */
@Composable
fun ExactAlarmOfferDialog(onClose: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.reliability_exact)) },
        text = { Text(stringResource(R.string.exact_offer_text)) },
        confirmButton = {
            TextButton(onClick = {
                onClose()
                // Окно показывается, только если точное время выключено, а такое бывает лишь на Android 12+.
                // Но компилятор этого не знает — проверяем версию ещё раз
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.startActivity(ReminderHealth.exactAlarmSettings(context))
                }
            }) { Text(stringResource(R.string.reliability_allow)) }
        },
        dismissButton = {
            TextButton(onClick = onClose) { Text(stringResource(R.string.action_not_now)) }
        },
    )
}
