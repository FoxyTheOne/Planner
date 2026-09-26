package app.planner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.planner.core.designsystem.theme.PlannerTheme
import app.planner.core.domain.model.PlannerSettings
import app.planner.core.domain.repository.SettingsRepository
import app.planner.navigation.PlannerApp
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Единственная Activity приложения — «окно», в котором рисуется весь интерфейс на Compose.
 * Переходы между экранами делает навигация внутри неё (см. `PlannerApp`), а не новые Activity.
 *
 * `@AndroidEntryPoint` разрешает Hilt внедрять зависимости в этот класс
 * (поле с `@Inject` заполнится само ещё до `onCreate`).
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // Настройки нужны уже здесь: тема оборачивает весь интерфейс
    @Inject lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Рисуем под статус-баром и панелью навигации; отступы от них даёт Scaffold в PlannerApp
        enableEdgeToEdge()
        // setContent — с этого места интерфейс описывается на Compose
        setContent {
            // Пока настройки читаются с диска, используем значения по умолчанию
            val settings by settingsRepository.settings.collectAsStateWithLifecycle(PlannerSettings())
            PlannerTheme(
                mode = settings.themeMode,
                darkVariant = settings.darkVariant,
                systemColors = settings.systemColors,
            ) {
                PlannerApp()
            }
        }
    }
}
