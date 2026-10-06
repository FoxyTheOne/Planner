package app.planner.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.planner.core.designsystem.theme.PlannerTheme
import app.planner.core.designsystem.theme.SectionLabelStyle

/*
 * Общие «кирпичики» интерфейса в бумажном стиле: строка задачи, чекбокс, подписи разделов,
 * пунктирная линовка, таблетки-чипы. Экраны собираются из них, поэтому стиль везде одинаковый.
 *
 * Функция с @Composable описывает кусок интерфейса. Compose вызывает её заново
 * («перерисовывает»), когда меняются её параметры.
 * Modifier — цепочка настроек элемента: отступы, размер, фон, реакция на нажатие.
 * Порядок в цепочке важен: `.padding().background()` и `.background().padding()` выглядят по-разному.
 */

/**
 * Строка задачи в стиле блокнота: квадратный чекбокс, текст, справа — время и точка-метка списка.
 *
 * @param trailingBadge плашка даты у запланированной задачи в списке («Пт 25»).
 * @param dimmed приглушить текст (запланированная задача в списке).
 * @param toggleDescription текст для TalkBack (озвучка экрана для незрячих).
 * @param trailing дополнительная кнопка справа (например, «Запланировать» в списках).
 */
@Composable
fun TaskRow(
    title: String,
    done: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    time: String? = null,
    labelColor: Color? = null,
    trailingBadge: String? = null,
    dimmed: Boolean = false,
    toggleDescription: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val paper = PlannerTheme.paper
    val textColor = when {
        done -> paper.inkFaint
        dimmed -> paper.inkMuted
        else -> paper.ink
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PaperCheckbox(checked = done, onToggle = onToggle, description = toggleDescription)
        if (time != null) {
            Text(time, style = MaterialTheme.typography.bodyLarge, color = paper.timeAccent)
        }
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = textColor,
            textDecoration = if (done) TextDecoration.LineThrough else null,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (trailingBadge != null) {
            Text(
                text = trailingBadge,
                style = MaterialTheme.typography.labelMedium,
                color = paper.inkMuted,
                modifier = Modifier
                    .border(0.5.dp, paper.rule, RoundedCornerShape(10.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        if (labelColor != null) {
            Box(Modifier
                .size(7.dp)
                .background(labelColor, CircleShape))
        }
        trailing?.invoke()
    }
}

/**
 * Квадратный чекбокс «как карандашом». Сам квадрат маленький (18dp), а зона нажатия — 40dp,
 * чтобы по нему было легко попасть пальцем.
 */
@Composable
fun PaperCheckbox(checked: Boolean, onToggle: () -> Unit, description: String? = null) {
    val paper = PlannerTheme.paper
    val shape = RoundedCornerShape(3.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            // Зона нажатия 40dp, сам квадрат — 18dp
            .size(40.dp)
            .clickable(role = Role.Checkbox, onClick = onToggle)
            .then(if (description != null) Modifier.semantics {
                contentDescription = description
            } else Modifier),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(18.dp)
                .then(
                    if (checked) Modifier.background(paper.inkMuted, shape)
                    else Modifier.border(1.2.dp, paper.inkMuted, shape),
                ),
        ) {
            if (checked) Icon(
                Icons.Rounded.Check,
                null,
                tint = paper.paper,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

/** «утро», «день», «вечер» — подпись раздела курсивом. */
@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = PlannerTheme.paper.inkMuted
) {
    Text(
        text,
        style = SectionLabelStyle,
        color = color,
        modifier = modifier.padding(top = 14.dp, bottom = 2.dp)
    )
}

/** Пунктирная линовка между строками. */
@Composable
fun DashedRule(modifier: Modifier = Modifier) {
    val color = PlannerTheme.paper.rule
    Canvas(modifier
        .fillMaxWidth()
        .height(1.dp)) {
        drawLine(
            color = color,
            start = Offset(0f, 0f),
            end = Offset(size.width, 0f),
            strokeWidth = 1f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
        )
    }
}

/** Плашка-таблетка: быстрые даты, части дня, привычки. */
@Composable
fun PaperChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val paper = PlannerTheme.paper
    val shape = RoundedCornerShape(16.dp)
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else paper.ink,
        modifier = modifier
            .then(
                if (selected) Modifier.background(MaterialTheme.colorScheme.primaryContainer, shape)
                else Modifier.border(0.5.dp, MaterialTheme.colorScheme.outline, shape),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}
