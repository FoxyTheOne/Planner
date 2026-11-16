package app.planner.common

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.planner.R
import app.planner.core.designsystem.theme.PlannerTheme
import app.planner.reminders.ReminderHealth

/**
 * Включены ли уведомления — и как попросить разрешение. Общее для строки «Напомнить» у задач
 * (ReminderRow) и для «Напоминать» у лекарства (MedicationEditorBody).
 *
 * РАЗРЕШЕНИЕ НА УВЕДОМЛЕНИЯ (Android 13+). Спрашиваем в тот момент, когда человек впервые выбрал
 * напоминание: тогда понятно, зачем оно приложению. Отказал — спросить второй раз Android не даст
 * (окно просто не появится), поэтому дальше подсказка [NotificationsOffHint] ведёт в настройки.
 *
 * Создаётся через [rememberNotificationAccess].
 */
class NotificationAccess internal constructor(
    private val on: MutableState<Boolean>,
    private val ask: (then: () -> Unit) -> Unit,
) {
    /** Уведомления сейчас включены. */
    val isOn: Boolean get() = on.value

    /**
     * Если уведомления выключены и Android позволяет спросить (13+) — показать системное окно
     * «Разрешить уведомления?». [then] выполнится после ответа, а если спрашивать не нужно — сразу.
     */
    fun requestIfNeeded(then: () -> Unit = {}) = ask(then)
}

/**
 * Запомнить [NotificationAccess] на этом экране.
 *
 * Включены ли уведомления, перепроверяем каждый раз, когда экран снова на виду
 * (LifecycleResumeEffect — «при возвращении»): человек мог включить их в настройках и вернуться.
 */
@Composable
fun rememberNotificationAccess(): NotificationAccess {
    val context = LocalContext.current
    val on = remember { mutableStateOf(ReminderHealth.notificationsOn(context)) }
    LifecycleResumeEffect(Unit) {
        on.value = ReminderHealth.notificationsOn(context)
        onPauseOrDispose { }
    }
    // Что сделать после ответа в системном окне (например, сохранить лекарство)
    var afterAnswer by remember { mutableStateOf<(() -> Unit)?>(null) }
    // Системное окно «Разрешить уведомления?». Ответ приходит сюда, в лямбду
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        on.value = ReminderHealth.notificationsOn(context)
        afterAnswer?.invoke()
        afterAnswer = null
    }
    return remember {
        NotificationAccess(on) { then ->
            if (!on.value && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                afterAnswer = then
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                then()
            }
        }
    }
}

/**
 * Спокойная подсказка «Уведомления выключены — напоминание не придёт · Включить».
 * «Включить» открывает настройки уведомлений приложения. Показывают её, когда напоминание выбрано,
 * а уведомления выключены.
 */
@Composable
fun NotificationsOffHint(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Text(
            stringResource(R.string.reminder_notifications_off),
            color = PlannerTheme.paper.inkMuted,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { context.startActivity(ReminderHealth.notificationSettings(context)) }) {
            Text(stringResource(R.string.reminder_notifications_enable))
        }
    }
}
