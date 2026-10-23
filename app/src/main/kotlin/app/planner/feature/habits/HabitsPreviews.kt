package app.planner.feature.habits

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import app.planner.core.designsystem.preview.PlannerScreenPreviews
import app.planner.core.designsystem.theme.PlannerTheme
import app.planner.core.domain.medication.MedicationHistory
import app.planner.core.domain.model.Pause
import app.planner.core.domain.recurrence.HabitHistory
import app.planner.preview.SampleData

/*
 * Превью экрана «Привычки». Откройте файл в Android Studio и включите вид «Split».
 */

/** Три привычки (одна идёт, одна начнётся завтра, одна на паузе) и два лекарства. */
@PlannerScreenPreviews
@Composable
private fun HabitsPreview() {
    val today = SampleData.today
    val (water, exercise, reading) = SampleData.habitRules
    // Последние 30 дней: «Вода» выпадала каждый день, отмечена через день
    val days = (0L until HabitHistory.DAYS).map { today.minusDays(it) }.toSet()
    val history = HabitHistory(
        from = today.minusDays(HabitHistory.DAYS - 1L),
        to = today,
        planned = days,
        done = days.filterIndexed { i, _ -> i % 2 == 0 }.toSet(),
    )
    val empty = HabitHistory(today, today, emptySet(), emptySet())
    PlannerTheme {
        HabitsContent(
            state = HabitsUiState(
                today = today,
                habits = listOf(
                    HabitItem(water, history),
                    HabitItem(exercise.copy(startDate = today.plusDays(1)), empty),
                    HabitItem(
                        reading.copy(
                            pauses = listOf(
                                Pause(
                                    today.minusDays(2),
                                    today.plusDays(5)
                                )
                            )
                        ), empty
                    ),
                ),
                // Витамин D принимают давно (28 из 30), магний — курсом: осталось 20 дней
                medications = listOf(
                    MedicationItem(
                        SampleData.vitaminD,
                        MedicationHistory(today.minusDays(29), today, planned = 30, taken = 28)
                    ),
                    MedicationItem(
                        SampleData.magnesium,
                        MedicationHistory(today.minusDays(10), today, planned = 11, taken = 11)
                    ),
                ),
                loaded = true,
            ),
            contentPadding = PaddingValues(),
            onBack = {},
            onOpen = {},
            onAdd = {},
            onReorder = {},
            onOpenMedication = {},
            onAddMedication = {},
        )
    }
}
