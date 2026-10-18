package app.planner.common

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.planner.R
import app.planner.core.designsystem.theme.PlannerTheme
import app.planner.core.domain.model.Reminder
import java.time.LocalTime

/**
 * Строка «🔔 Напомнить» в редакторе задачи. Та же [EditorActionRow], что у даты и повторения:
 * пока напоминания нет — действие «Напомнить» цветом кнопок; выбрали — «Напомнить: за 30 минут» и ✕.
 *
 * Варианты зависят от того, есть ли у задачи время ([timed]):
 * - есть («10:30 врач») — «в момент начала», «за 10 / 30 минут», «за час», «за день»;
 * - нет («купить подарок» в субботу) — «в 9:00», «в 12:00», «в 18:00», «другое время…».
 *
 * РАЗРЕШЕНИЕ НА УВЕДОМЛЕНИЯ (Android 13+). Спрашиваем в тот момент, когда человек впервые выбрал
 * напоминание: тогда понятно, зачем оно приложению. Отказал или выключил уведомления в настройках —
 * под строкой спокойная подсказка «Уведомления выключены — напоминание не придёт» и «Включить»,
 * которое открывает настройки уведомлений приложения.
 */
@Composable
fun ReminderRow(reminder: Reminder?, timed: Boolean, onChange: (Reminder?) -> Unit) {
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    var pickTime by rememberSaveable { mutableStateOf(false) }

    // Включены ли уведомления. Человек может включить их в настройках и вернуться — поэтому
    // перепроверяем каждый раз, когда экран снова на виду (LifecycleResumeEffect — «при возвращении»)
    var notificationsOn by remember { mutableStateOf(notificationsEnabled(context)) }
    LifecycleResumeEffect(Unit) {
        notificationsOn = notificationsEnabled(context)
        onPauseOrDispose { }
    }
    // Системное окно «Разрешить уведомления?». Ответ приходит сюда, в лямбду
    val askPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            notificationsOn = notificationsEnabled(context)
        }

    fun choose(chosen: Reminder) {
        onChange(chosen)
        menuOpen = false
        if (!notificationsOn && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Column {
        Box {
            EditorActionRow(
                icon = Icons.Outlined.Notifications,
                value = reminder?.let { stringResource(R.string.reminder_value, reminderText(it)) },
                action = stringResource(R.string.reminder_add),
                onClick = { menuOpen = true },
                onClear = { onChange(null) },
                clearDescription = stringResource(R.string.cd_clear_reminder),
            )
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                if (timed) {
                    listOf(0, 10, 30, 60, 24 * 60).forEach { minutes ->
                        val option = Reminder.BeforeTime(minutes)
                        DropdownMenuItem(
                            text = { Text(reminderText(option)) },
                            onClick = { choose(option) })
                    }
                } else {
                    listOf(
                        LocalTime.of(9, 0),
                        LocalTime.of(12, 0),
                        LocalTime.of(18, 0)
                    ).forEach { time ->
                        val option = Reminder.AtTime(time)
                        DropdownMenuItem(
                            text = { Text(reminderText(option)) },
                            onClick = { choose(option) })
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.reminder_other_time)) },
                        onClick = { menuOpen = false; pickTime = true },
                    )
                }
            }
        }

        // Напоминание выбрано, а уведомления выключены — оно не придёт. Говорим об этом спокойно, один раз здесь
        if (reminder != null && !notificationsOn) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 36.dp)
            ) {
                Text(
                    stringResource(R.string.reminder_notifications_off),
                    color = PlannerTheme.paper.inkMuted,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { context.startActivity(notificationSettings(context)) }) {
                    Text(stringResource(R.string.reminder_notifications_enable))
                }
            }
        }
    }

    if (pickTime) {
        PlannerTimePickerDialog(
            initial = (reminder as? Reminder.AtTime)?.time ?: LocalTime.of(9, 0),
            onPick = { pickTime = false; choose(Reminder.AtTime(it)) },
            onDismiss = { pickTime = false },
        )
    }
}

/** «в момент начала», «за 30 минут», «за 2 часа», «за день», «в 9:00». */
@Composable
fun reminderText(reminder: Reminder): String = when (reminder) {
    is Reminder.AtTime -> stringResource(R.string.reminder_at, DateTexts.time(reminder.time))
    is Reminder.BeforeTime -> when (val minutes = reminder.minutes) {
        0 -> stringResource(R.string.reminder_at_start)
        24 * 60 -> stringResource(R.string.reminder_day_before)
        // Ровное число часов — часами («за 2 часа»), иначе минутами («за 90 минут»)
        else -> if (minutes % 60 == 0) {
            pluralStringResource(R.plurals.reminder_hours_before, minutes / 60, minutes / 60)
        } else {
            pluralStringResource(R.plurals.reminder_minutes_before, minutes, minutes)
        }
    }
}

/**
 * Включены ли уведомления приложения. Учитывает сразу всё: и разрешение Android 13+,
 * и выключатель «Уведомления» в настройках приложения, который человек мог выключить сам.
 */
private fun notificationsEnabled(context: Context): Boolean =
    NotificationManagerCompat.from(context).areNotificationsEnabled()

/** Системный экран «Уведомления» этого приложения — там их включают. */
private fun notificationSettings(context: Context): Intent =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(
        Settings.EXTRA_APP_PACKAGE,
        context.packageName
    )
