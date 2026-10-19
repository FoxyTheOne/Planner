package app.planner.feature.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import app.planner.core.designsystem.preview.PlannerScreenPreviews
import app.planner.core.designsystem.theme.PlannerTheme
import app.planner.core.domain.model.PlannerSettings
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
            contentPadding = PaddingValues(),
            onBack = {},
            onUpdate = {},
            onOpenReliability = {},
            onExport = {},
            onImport = {},
            onDonate = {},
        )
    }
}

/** «Надёжность напоминаний»: уведомления включены, точного времени нет, пробное уже отправлено. */
@PlannerScreenPreviews
@Composable
private fun ReliabilityPreview() {
    PlannerTheme {
        ReliabilityContent(
            state = ReliabilityState(
                notificationsOn = true,
                exactAlarmsOn = false,
                exactAlarmsAsked = true,
                unrestricted = false,
            ),
            testAt = LocalTime.of(10, 31),
            contentPadding = PaddingValues(),
            onBack = {},
            onOpenNotifications = {},
            onOpenExactAlarms = {},
            onOpenAppSettings = {},
            onSendTest = {},
        )
    }
}
