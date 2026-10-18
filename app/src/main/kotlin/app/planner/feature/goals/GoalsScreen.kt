package app.planner.feature.goals

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.planner.R
import app.planner.common.DateTexts
import app.planner.common.GoalSaver
import app.planner.core.designsystem.component.SectionLabel
import app.planner.core.designsystem.theme.PlannerTheme
import app.planner.core.domain.goal.GoalOverview
import app.planner.core.domain.model.Goal
import java.time.LocalDate

/**
 * Экран «Цели»: карточки целей в работе, внизу — свёрнутый архив достигнутых.
 *
 * На карточке — сколько шагов сделано, какой шаг следующий и как обстоит дело со сроком.
 * Тап по карточке открывает экран цели. «Цель» внизу — шторка новой цели; сохранили —
 * и сразу открывается её экран, чтобы записать первые шаги (как с новым списком).
 *
 * @param onOpenGoal открыть экран цели (переход на [GoalScreen]).
 */
@Composable
fun GoalsScreen(
    onOpenGoal: (Long) -> Unit,
    contentPadding: PaddingValues,
    viewModel: GoalsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Новая цель в шторке; null — шторка закрыта. GoalSaver — чтобы пережить поворот экрана
    var creating by rememberSaveable(stateSaver = GoalSaver) { mutableStateOf<Goal?>(null) }

    GoalsContent(
        state = state,
        contentPadding = contentPadding,
        onOpenGoal = { onOpenGoal(it.id) },
        onNewGoal = { creating = Goal(title = "") },
    )

    creating?.let { goal ->
        GoalEditorSheet(
            initial = goal,
            today = state.today,
            onDismiss = { creating = null },
            onSave = { viewModel.create(it, onCreated = onOpenGoal); creating = null },
        )
    }
}

/** Рисующая часть экрана: только разметка, без ViewModel и шторок (для превью, см. `GoalsPreviews.kt`). */
@Composable
fun GoalsContent(
    state: GoalsUiState,
    contentPadding: PaddingValues,
    onOpenGoal: (Goal) -> Unit,
    onNewGoal: () -> Unit,
) {
    // Архив по умолчанию свёрнут: достигнутое приятно иногда посмотреть, но оно не должно
    // отодвигать вниз то, над чем человек работает сейчас
    var showAchieved by rememberSaveable { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .padding(contentPadding)
    ) {
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.nav_goals),
                    style = MaterialTheme.typography.headlineMedium
                )
            }

            if (state.isEmpty) {
                item {
                    Text(stringResource(R.string.goals_empty), color = PlannerTheme.paper.inkMuted)
                }
            }
            // Всё достигнуто — иначе на экране было бы только свёрнутое «достигнутые · 3»
            if (state.allAchieved) {
                item {
                    Text(
                        stringResource(R.string.goals_all_achieved),
                        color = PlannerTheme.paper.inkMuted
                    )
                }
            }

            items(state.active, key = { it.goal.id }) { overview ->
                GoalCard(overview, state.today, onClick = { onOpenGoal(overview.goal) })
            }

            if (state.achieved.isNotEmpty()) {
                item(key = "achieved") {
                    AchievedHeader(
                        count = state.achieved.size,
                        expanded = showAchieved,
                        onClick = { showAchieved = !showAchieved },
                    )
                }
                if (showAchieved) {
                    items(state.achieved, key = { it.goal.id }) { overview ->
                        AchievedCard(overview, state.today, onClick = { onOpenGoal(overview.goal) })
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = onNewGoal,
            icon = { Icon(Icons.Outlined.Add, null) },
            text = { Text(stringResource(R.string.action_new_goal)) },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
        )
    }
}

/**
 * Карточка цели:
 * ```
 * Ремонт в ванной
 * до 31 декабря · ещё 2 месяца
 * ▬▬▬▬▬▬───────────  3 из 8
 * → Вынести мусор
 * ```
 */
@Composable
private fun GoalCard(overview: GoalOverview, today: LocalDate, onClick: () -> Unit) {
    val paper = PlannerTheme.paper
    Column(
        Modifier
            .fillMaxWidth()
            .border(0.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Text(overview.goal.title, style = MaterialTheme.typography.titleMedium)
        DeadlineLine(overview.goal, overview.deadline, today)

        val progress = overview.progress
        if (progress.total > 0) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 12.dp)
            ) {
                ProgressBar(progress, Modifier.weight(1f))
                Text(
                    stringResource(R.string.goal_progress, progress.done, progress.total),
                    color = paper.inkMuted,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }

        // Следующий шаг — главное на карточке: открыл «Цели» и сразу видно, что делать дальше
        val next = overview.nextStep
        when {
            next != null -> Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 10.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowForward,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    next.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    // weight(fill = false): название занимает столько, сколько ему нужно, но не больше
                    // остатка строки — длинное обрежется «…», а дата справа останется видна
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .weight(1f, fill = false),
                )
                // Следующий шаг уже в плане — видно, на какой день: «→ Купить плитку · пт 17»
                next.date?.let { date ->
                    Text(
                        " · ${DateTexts.shortDay(date).lowercase()}",
                        color = PlannerTheme.paper.inkMuted,
                        maxLines = 1,
                    )
                }
            }
            // Шаги есть, но все сделаны — спокойная подсказка; «Достигнута» появится на экране цели
            progress.total > 0 -> Hint(stringResource(R.string.goal_all_steps_done))
            else -> Hint(stringResource(R.string.goal_no_steps))
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        color = PlannerTheme.paper.inkMuted,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(top = 10.dp),
    )
}

/** «достигнутые · 3 ▾» — нажатие сворачивает и разворачивает архив. */
@Composable
private fun AchievedHeader(count: Int, expanded: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(top = 12.dp),
    ) {
        SectionLabel("${stringResource(R.string.section_achieved)} · $count", Modifier.weight(1f))
        Icon(
            if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
            null,
            tint = PlannerTheme.paper.inkMuted,
        )
    }
}

/** Достигнутая цель в архиве — скромнее карточки в работе: название и когда достигнута. */
@Composable
private fun AchievedCard(overview: GoalOverview, today: LocalDate, onClick: () -> Unit) {
    val paper = PlannerTheme.paper
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
    ) {
        Text(
            overview.goal.title,
            color = paper.inkMuted,
            style = MaterialTheme.typography.bodyLarge
        )
        overview.goal.achievedOn?.let {
            Text(
                stringResource(R.string.goal_achieved_on, DateTexts.dayMonthYear(it, today)),
                color = paper.inkFaint,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
