package app.planner.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Заголовки с засечками — «блокнот», основной текст без засечек — читаемость.
// Пока системный serif; позже заменим на Lora или PT Serif из ресурсов.
val SerifFamily: FontFamily = FontFamily.Serif

internal val PlannerTypography = Typography().run {
    copy(
        displaySmall = displaySmall.copy(fontFamily = SerifFamily, fontWeight = FontWeight.Normal),
        headlineLarge = headlineLarge.copy(fontFamily = SerifFamily, fontWeight = FontWeight.Normal),
        headlineMedium = headlineMedium.copy(fontFamily = SerifFamily, fontWeight = FontWeight.Normal),
        headlineSmall = headlineSmall.copy(fontFamily = SerifFamily, fontWeight = FontWeight.Normal),
        titleLarge = titleLarge.copy(fontFamily = SerifFamily, fontWeight = FontWeight.Normal),
    )
}

/** Подписи «утро / день / вечер» — курсив с засечками, как пометки от руки. */
val SectionLabelStyle = TextStyle(
    fontFamily = SerifFamily,
    fontStyle = FontStyle.Italic,
    fontSize = 15.sp,
)
