package app.planner.feature.lists

import app.planner.core.domain.model.Task
import app.planner.core.domain.model.TaskList
import java.time.LocalDate

/** Строка оглавления: список и сколько в нём открытых задач. */
data class ListRow(val list: TaskList, val openCount: Int)

/** Состояние оглавления списков. */
data class ListsOverviewUiState(
    val today: LocalDate,
    /** Все списки для оглавления; «Без списка» — только если в нём что-то есть. */
    val rows: List<ListRow> = emptyList(),
    /** Все списки (включая пустой «Без списка») — для выбора в шторке задачи. */
    val allLists: List<TaskList> = emptyList(),
    val query: String = "",
    /** Совпадения поиска: подходящие списки и задачи. Пусто, если запрос пустой. */
    val foundLists: List<ListRow> = emptyList(),
    val foundTasks: List<Task> = emptyList(),
)
