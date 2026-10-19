package app.planner.feature.lists

import app.planner.core.domain.model.RecurrenceRule
import app.planner.core.domain.model.Task
import app.planner.core.domain.model.TaskList
import java.time.LocalDate

/** Состояние экрана одного списка. [list] = null — список удалили, экран закроется. */
data class ListDetailUiState(
    val today: LocalDate,
    val list: TaskList? = null,
    val tasks: List<Task> = emptyList(),
    /** Повторения, которым выбран этот список, — раздел «повторяется» внизу. */
    val rules: List<RecurrenceRule> = emptyList(),
    val allLists: List<TaskList> = emptyList(),
    val loaded: Boolean = false,
)
