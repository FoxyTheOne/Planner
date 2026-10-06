package app.planner.feature.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import app.planner.core.designsystem.preview.PlannerScreenPreviews
import app.planner.core.designsystem.theme.PlannerTheme
import app.planner.core.domain.model.PlannerSettings

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
            contentPadding = PaddingValues(),
            onBack = {},
            onUpdate = {},
            onExport = {},
            onImport = {},
            onDonate = {},
        )
    }
}
