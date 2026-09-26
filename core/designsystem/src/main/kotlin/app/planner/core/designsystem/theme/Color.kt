package app.planner.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import app.planner.core.domain.model.ListColor

/*
 * Палитры приложения: светлая «бумага» и три тёмные темы (графит, чернильная, чёрная).
 *
 * Цвет в Compose записывается как 0xAARRGGBB: AA — прозрачность (FF — непрозрачный),
 * дальше красный, зелёный, синий. Например, 0xFFB5654A — терракота.
 */

/**
 * Цвета, которых нет в Material 3: линовка, приглушённый текст, акцент времени, метки списков.
 * Экраны берут их через `PlannerTheme.paper`.
 */
@Immutable
data class PaperColors(
    val paper: Color,
    val ink: Color,
    val inkMuted: Color,
    val inkFaint: Color,
    val rule: Color,
    val timeAccent: Color,
    val highlight: Color,
    val warningBg: Color,
    val warningInk: Color,
    val labels: Map<ListColor, Color>,
) {
    fun label(color: ListColor): Color = labels.getValue(color)
}

private val Terracotta = Color(0xFFB5654A)

private val LightLabels = mapOf(
    ListColor.SAGE to Color(0xFF8FAE86),
    ListColor.TERRACOTTA to Color(0xFFC08A5B),
    ListColor.DUSTY_BLUE to Color(0xFF7E9CB8),
    ListColor.LAVENDER to Color(0xFFA895C2),
    ListColor.OCHRE to Color(0xFFC9A54E),
    ListColor.ROSE to Color(0xFFC98A93),
)

private val DarkLabels = mapOf(
    ListColor.SAGE to Color(0xFFA6C39C),
    ListColor.TERRACOTTA to Color(0xFFD2A273),
    ListColor.DUSTY_BLUE to Color(0xFF98B4CF),
    ListColor.LAVENDER to Color(0xFFBBA9D6),
    ListColor.OCHRE to Color(0xFFD8B966),
    ListColor.ROSE to Color(0xFFD9A2AA),
)

internal val LightPaper = PaperColors(
    paper = Color(0xFFF7F2E8),
    ink = Color(0xFF3B3428),
    inkMuted = Color(0xFF8A7F6C),
    inkFaint = Color(0xFFA89C87),
    rule = Color(0xFFD9CFBC),
    timeAccent = Terracotta,
    highlight = Color(0xFFEFE6D6),
    warningBg = Color(0xFFEFE3D0),
    warningInk = Color(0xFF8A4F36),
    labels = LightLabels,
)

private fun darkPaper(paper: Color, ink: Color, muted: Color, faint: Color, rule: Color, highlight: Color) =
    PaperColors(
        paper = paper,
        ink = ink,
        inkMuted = muted,
        inkFaint = faint,
        rule = rule,
        timeAccent = Color(0xFFE3A98C),
        highlight = highlight,
        warningBg = Color(0xFF3D3129),
        warningInk = Color(0xFFE0A78C),
        labels = DarkLabels,
    )

internal val GraphitePaper = darkPaper(
    paper = Color(0xFF1E1D1B), ink = Color(0xFFECE6DA), muted = Color(0xFF98928A),
    faint = Color(0xFF6F6A63), rule = Color(0xFF3A3835), highlight = Color(0xFF2A2825),
)

internal val InkPaper = darkPaper(
    paper = Color(0xFF1A2030), ink = Color(0xFFE9E4D6), muted = Color(0xFF8E96A8),
    faint = Color(0xFF667088), rule = Color(0xFF313A4F), highlight = Color(0xFF232B3E),
)

internal val BlackPaper = darkPaper(
    paper = Color(0xFF000000), ink = Color(0xFFE6E1D8), muted = Color(0xFF8C8780),
    faint = Color(0xFF5E5A55), rule = Color(0xFF2A2826), highlight = Color(0xFF151413),
)

// Material 3 ожидает свою ColorScheme — набор «ролей» (primary, surface, outline…).
// Здесь бумажная палитра раскладывается по этим ролям, чтобы стандартные кнопки,
// поля ввода и шторки тоже выглядели по-бумажному.

internal fun PaperColors.toLightScheme(): ColorScheme = lightColorScheme(
    primary = Terracotta,
    onPrimary = Color(0xFFFFF8F0),
    primaryContainer = Color(0xFFEFD9CC),
    onPrimaryContainer = Color(0xFF8A4F36),
    secondary = Color(0xFF6B5E48),
    onSecondary = Color(0xFFFFF8F0),
    secondaryContainer = highlight,
    onSecondaryContainer = ink,
    background = paper,
    onBackground = ink,
    surface = paper,
    onSurface = ink,
    surfaceVariant = highlight,
    onSurfaceVariant = inkMuted,
    surfaceContainerLowest = Color(0xFFFFFCF6),
    surfaceContainerLow = Color(0xFFF3EDE1),
    surfaceContainer = Color(0xFFEFE8DB),
    surfaceContainerHigh = Color(0xFFEAE2D4),
    surfaceContainerHighest = Color(0xFFE4DBCB),
    outline = Color(0xFFC9BBA2),
    outlineVariant = rule,
    inverseSurface = ink,
    inverseOnSurface = paper,
    inversePrimary = Color(0xFFE3A98C),
)

internal fun PaperColors.toDarkScheme(): ColorScheme = darkColorScheme(
    primary = timeAccent,
    onPrimary = paper,
    primaryContainer = warningBg,
    onPrimaryContainer = warningInk,
    secondary = Color(0xFFCBBFA8),
    onSecondary = paper,
    secondaryContainer = highlight,
    onSecondaryContainer = ink,
    background = paper,
    onBackground = ink,
    surface = paper,
    onSurface = ink,
    surfaceVariant = highlight,
    onSurfaceVariant = inkMuted,
    surfaceContainerLowest = paper,
    surfaceContainerLow = highlight,
    surfaceContainer = highlight,
    surfaceContainerHigh = rule,
    surfaceContainerHighest = rule,
    outline = inkFaint,
    outlineVariant = rule,
    inverseSurface = ink,
    inverseOnSurface = paper,
    inversePrimary = Terracotta,
)
