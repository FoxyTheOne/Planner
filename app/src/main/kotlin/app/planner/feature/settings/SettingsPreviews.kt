package app.planner.feature.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import app.planner.R
import app.planner.core.designsystem.preview.PlannerScreenPreviews
import app.planner.core.designsystem.theme.PlannerTheme
import app.planner.core.domain.model.PlannerSettings
import app.planner.reminders.ChannelState
import app.planner.reminders.ReminderNotifications
import java.time.LocalTime

/*
 * Превью экрана настроек. Откройте файл в Android Studio и включите вид «Split».
 */

/** Настройки по умолчанию, как в сборке для RuStore — с блоком «Поддержать проект». */
@PlannerScreenPreviews
@Composable
private fun SettingsPreview() {
    PlannerTheme {
        SettingsContent(
            settings = PlannerSettings(),
            showDonate = true,
            remindersOk = false,
            channels = listOf(
                ChannelInfo(
                    ReminderNotifications.CHANNEL_TASKS,
                    R.string.channel_tasks,
                    ChannelState.SOUND
                ),
                ChannelInfo(
                    ReminderNotifications.CHANNEL_HABITS,
                    R.string.channel_habits,
                    ChannelState.SILENT
                ),
            ),
            contentPadding = PaddingValues(),
            onBack = {},
            onUpdate = {},
            onOpenReliability = {},
            onOpenChannel = {},
            onExport = {},
            onImport = {},
            onDonate = {},
        )
    }
}

/** «Надёжность напоминаний» на Xiaomi: уведомления включены, но без звука, точного времени нет, пробное уже отправлено. */
@PlannerScreenPreviews
@Composable
private fun ReliabilityPreview() {
    PlannerTheme {
        ReliabilityContent(
            state = ReliabilityState(
                notificationsOn = true,
                soundOn = false,
                exactAlarmsOn = false,
                exactAlarmsAsked = true,
                unrestricted = false,
                xiaomi = true,
            ),
            testAt = LocalTime.of(10, 31),
            contentPadding = PaddingValues(),
            onBack = {},
            onOpenNotifications = {},
            onOpenSound = {},
            onOpenExactAlarms = {},
            onOpenAppSettings = {},
            onSendTest = {},
        )
    }
}
