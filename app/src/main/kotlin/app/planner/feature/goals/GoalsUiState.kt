package app.planner.feature.goals

import app.planner.core.domain.goal.GoalOverview
import java.time.LocalDate

/**
 * Состояние экрана «Цели» — то, что ViewModel готовит, а экран рисует.
 *
 * @param active цели в работе — карточками, в том порядке, который выбрал человек.
 * @param achieved достигнутые — внизу, в архиве; свежие первыми.
 * @param loaded база уже ответила. Пока false, экран не показывает «Целей пока нет» —
 *   иначе при каждом открытии на долю секунды мелькала бы пустая подсказка.
 */
data class GoalsUiState(
    val today: LocalDate,
    val active: List<GoalOverview> = emptyList(),
    val achieved: List<GoalOverview> = emptyList(),
    val loaded: Boolean = false,
) {
    val isEmpty: Boolean get() = loaded && active.isEmpty() && achieved.isEmpty()

    /** Все цели достигнуты, в работе ни одной — экран мягко предлагает завести новую. */
    val allAchieved: Boolean get() = loaded && active.isEmpty() && achieved.isNotEmpty()
}
