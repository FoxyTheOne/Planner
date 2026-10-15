package app.planner.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.planner.core.designsystem.theme.PlannerTheme

/**
 * «Таблетка» привычки над планом дня: «Вода», «Зарядка», «✓ Читать».
 *
 * Не отмечена — только рамка. Отмечена — закрашена, с галочкой. Нажатие переключает,
 * долгое нажатие открывает саму привычку (настроить, поставить на паузу).
 *
 * `combinedClickable` — как `clickable`, но умеет ещё и долгое нажатие (и двойное).
 * `role = Role.Checkbox` и `toggleableState` — для TalkBack: он прочитает «Вода, флажок, отмечено»,
 * то есть поймёт, что это переключатель, а не просто кнопка.
 *
 * @param onLongClickLabel что сделает долгое нажатие — TalkBack предложит это действие голосом.
 */
@Composable
fun HabitPill(
    title: String,
    done: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClickLabel: String? = null,
) {
    val paper = PlannerTheme.paper
    val shape = RoundedCornerShape(16.dp)
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .then(
                if (done) Modifier.background(colors.primaryContainer, shape)
                else Modifier.border(0.5.dp, colors.outline, shape),
            )
            .semantics { toggleableState = ToggleableState(done) }
            .combinedClickable(
                role = Role.Checkbox,
                onLongClickLabel = onLongClickLabel,
                onLongClick = onLongClick,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        if (done) {
            Icon(
                Icons.Rounded.Check,
                null,
                tint = colors.onPrimaryContainer,
                modifier = Modifier.size(16.dp)
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = if (done) colors.onPrimaryContainer else paper.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
