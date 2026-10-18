package app.planner.feature.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.planner.core.designsystem.preview.PlannerPreviews
import app.planner.core.designsystem.preview.PlannerScreenPreviews
import app.planner.core.designsystem.theme.PlannerTheme
import app.planner.core.domain.goal.GoalOverview
import app.planner.core.domain.goal.stepGroups
import app.planner.preview.SampleData

/*
 * Превью вкладки «Цели» на выдуманных данных (SampleData).
 * Откройте файл в Android Studio и включите вид «Split».
 */

/** Три цели в работе (срок далеко, срок скоро, без шагов) и архив с одной достигнутой. */
@PlannerScreenPreviews
@Composable
private fun GoalsPreview() {
    val state = with(SampleData) {
        GoalsUiState(
            today = today,
            active = listOf(
                GoalOverview.of(repairGoal, listOf(demolition, tiles), repairSteps, today),
                GoalOverview.of(spanishGoal, emptyList(), spanishSteps, today),
                GoalOverview.of(guitarGoal, emptyList(), emptyList(), today),
            ),
            achieved = listOf(GoalOverview.of(runGoal, emptyList(), emptyList(), today)),
            loaded = true,
        )
    }
    PlannerTheme { GoalsContent(state, PaddingValues(), onOpenGoal = {}, onNewGoal = {}) }
}

/** Целей ещё нет — так экран видит человек в первый раз. */
@PlannerScreenPreviews
@Composable
private fun GoalsEmptyPreview() {
    PlannerTheme {
        GoalsContent(
            GoalsUiState(SampleData.today, loaded = true),
            PaddingValues(),
            onOpenGoal = {},
            onNewGoal = {})
    }
}

/** Шторка цели: «Ремонт в ванной» со сроком. */
@PlannerPreviews
@Composable
private fun GoalEditorPreview() {
    PlannerTheme {
        Box(Modifier
            .background(PlannerTheme.paper.highlight)
            .padding(top = 16.dp)) {
            GoalEditorSheetBody(
                initial = SampleData.repairGoal,
                today = SampleData.today,
                onSave = {})
        }
    }
}

/** Экран цели «Ремонт»: «Зачем», срок, прогресс; шаг без этапа и два этапа, один шаг запланирован. */
@PlannerScreenPreviews
@Composable
private fun GoalPreview() {
    val state = with(SampleData) {
        val stages = listOf(demolition, tiles)
        // «Купить плитку» уже в плане на завтра — серая, с датой
        val steps = repairSteps.map { if (it.id == 33L) it.copy(date = today.plusDays(1)) else it }
        GoalUiState(
            today = today,
            goal = repairGoal,
            overview = GoalOverview.of(repairGoal, stages, steps, today),
            groups = stepGroups(stages, steps),
            stages = stages,
            steps = steps,
            loaded = true,
        )
    }
    PlannerTheme {
        GoalContent(
            state = state,
            contentPadding = PaddingValues(),
            onBack = {},
            onEditGoal = {},
            onToggle = {},
            onEditStep = {},
            onSchedule = {},
            onAddStep = { _, _ -> },
            onAddStage = {},
        )
    }
}
