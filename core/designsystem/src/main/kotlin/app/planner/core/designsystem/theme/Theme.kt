package app.planner.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import app.planner.core.domain.model.DarkVariant
import app.planner.core.domain.model.ThemeMode

/**
 * «Невидимый параметр» с бумажными цветами для всех экранов.
 *
 * CompositionLocal — способ передать значение всем вложенным composable-функциям,
 * не протаскивая его через параметры каждой. [PlannerTheme] кладёт сюда нужную палитру,
 * а любой экран достаёт её через `PlannerTheme.paper`.
 */
val LocalPaperColors = staticCompositionLocalOf { LightPaper }

/** Короткий доступ к бумажной палитре: `PlannerTheme.paper.inkMuted`. */
object PlannerTheme {
    val paper: PaperColors
        @Composable get() = LocalPaperColors.current
}

/**
 * Тема приложения — «обёртка» вокруг всего интерфейса (см. `MainActivity`).
 *
 * Выбирает палитру по настройкам: светлая бумага, одна из трёх тёмных тем
 * или системные цвета Material You (Android 12+), и передаёт её вниз двумя путями:
 * - стандартные цвета Material 3 — через [MaterialTheme] (их используют кнопки, поля, шторки);
 * - наши дополнительные (линовка, акцент времени, метки) — через [LocalPaperColors].
 *
 * @param content весь интерфейс, который рисуется внутри темы.
 */
@Composable
fun PlannerTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    darkVariant: DarkVariant = DarkVariant.GRAPHITE,
    systemColors: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val paper = if (!dark) LightPaper else when (darkVariant) {
        DarkVariant.GRAPHITE -> GraphitePaper
        DarkVariant.INK -> InkPaper
        DarkVariant.BLACK -> BlackPaper
    }
    val scheme = when {
        // Цвета из обоев доступны только с Android 12 (API 31, «S»)
        systemColors && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        dark -> paper.toDarkScheme()
        else -> paper.toLightScheme()
    }
    CompositionLocalProvider(LocalPaperColors provides paper) {
        MaterialTheme(colorScheme = scheme, typography = PlannerTypography, content = content)
    }
}
