package app.planner

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.planner.core.designsystem.theme.PlannerTheme
import app.planner.core.domain.model.ThemeMode
import app.planner.navigation.PlannerApp
import dagger.hilt.android.AndroidEntryPoint

/**
 * Единственная Activity приложения — «окно», в котором рисуется весь интерфейс на Compose.
 * Переходы между экранами делает навигация внутри неё (см. `PlannerApp`), а не новые Activity.
 *
 * Сама Activity ничего не знает о хранилищах: настройки темы ей отдаёт [MainViewModel].
 * `@AndroidEntryPoint` разрешает Hilt создавать для неё ViewModel с зависимостями.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // `by viewModels()` — получить ViewModel этого окна. Hilt сам передаст ей репозиторий настроек.
    // При повороте экрана вернётся тот же объект, а не новый.
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Заставку нужно включить до super.onCreate: она заменяет тему Theme.Planner.Starting на основную
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)

        // Держим заставку, пока настройки не прочитаны с диска — тогда первый кадр
        // сразу будет в выбранной теме, без мигания «графит → чернильная»
        splash.setKeepOnScreenCondition { viewModel.settings.value == null }

        // Рисуем под статус-баром и панелью навигации; отступы от них даёт Scaffold в PlannerApp.
        // Цвет значков в этих панелях подстраивается под тему приложения ниже, в setContent
        enableEdgeToEdge()

        // setContent — с этого места интерфейс описывается на Compose
        setContent {
            // ПОДПИСКА — именно эта строка, а не PlannerTheme ниже. collectAsStateWithLifecycle — это как observe:
            // collectAsStateWithLifecycle() подписывается на StateFlow настроек и кладёт последнее
            // значение в State. Compose запоминает, что этот блок setContent ПРОЧИТАЛ settings,
            // и когда приходят новые настройки (например, в «Настройках» выбрали другую тему),
            // заново выполняет весь блок — и PlannerTheme вызывается ещё раз с новыми аргументами.
            // «WithLifecycle» — пока приложение свёрнуто, подписка ставится на паузу.
            //
            // null бывает только пока видна заставка, поэтому до PlannerApp дело не дойдёт без настроек
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val current = settings ?: return@setContent

            // Тёмная ли сейчас тема приложения: «как в системе» — смотрим на телефон, иначе — на выбор человека
            val dark = when (current.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // Цвет значков в строке состояния (часы, батарея) и в нижней системной панели.
            // По умолчанию Android подбирает их под тему ТЕЛЕФОНА, и если в приложении выбрана
            // тёмная тема, а в телефоне светлая, тёмные значки терялись на тёмном фоне.
            // LaunchedEffect(dark) — выполнить при запуске и заново каждый раз, когда dark меняется
            LaunchedEffect(dark) {
                val transparent = Color.TRANSPARENT
                val style = if (dark) {
                    SystemBarStyle.dark(transparent) // светлые значки на тёмном фоне
                } else {
                    SystemBarStyle.light(transparent, transparent) // тёмные значки на светлом
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }

            PlannerTheme(
                mode = current.themeMode,
                darkVariant = current.darkVariant,
                systemColors = current.systemColors,
            ) {
                // Сообщения и «Отменить» отдаём каркасу приложения из этой же ViewModel
                PlannerApp(messages = viewModel.messages, onUndo = viewModel::runUndo)
            }
        }
    }
}
