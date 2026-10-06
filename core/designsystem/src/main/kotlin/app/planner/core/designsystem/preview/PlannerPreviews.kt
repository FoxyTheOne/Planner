package app.planner.core.designsystem.preview

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview

/**
 * Превью сразу в двух вариантах — светлая бумага и тёмный «графит» — на русском.
 *
 * Как пользоваться: поставьте `@PlannerPreviews` над composable-функцией без параметров
 * и откройте файл в Android Studio, вкладка «Split» или «Design» справа сверху.
 * Studio нарисует оба варианта, не запуская эмулятор. Превью работает только в Studio,
 * в приложение оно не попадает.
 *
 * Это «мультипревью»: своя аннотация, на которую навешаны несколько обычных `@Preview`.
 * - `locale = "ru"` — строки берутся из values-ru;
 * - `uiMode = UI_MODE_NIGHT_YES` — Studio притворяется, что на телефоне включена тёмная тема,
 *   и PlannerTheme (режим «как в системе») выбирает тёмную палитру.
 */
@Preview(name = "Светлая", locale = "ru", showBackground = true, backgroundColor = 0xFFF7F2E8)
@Preview(
    name = "Тёмная",
    locale = "ru",
    showBackground = true,
    backgroundColor = 0xFF1E1D1B,
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
)
annotation class PlannerPreviews

/** То же, но размером с экран телефона — для целых экранов, а не отдельных компонентов. */
@Preview(name = "Телефон · светлая", locale = "ru", showBackground = true, widthDp = 380, heightDp = 800)
@Preview(
    name = "Телефон · тёмная",
    locale = "ru",
    showBackground = true,
    widthDp = 380,
    heightDp = 800,
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
)
annotation class PlannerScreenPreviews
