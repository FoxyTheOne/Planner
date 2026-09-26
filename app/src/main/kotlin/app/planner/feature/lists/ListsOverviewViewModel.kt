package app.planner.feature.lists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.planner.common.TaskCommands
import app.planner.common.TodayClock
import app.planner.core.domain.model.Task
import app.planner.core.domain.model.TaskList
import app.planner.core.domain.repository.SettingsRepository
import app.planner.core.domain.repository.TaskListRepository
import app.planner.core.domain.repository.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

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

/**
 * Помнит, возвращались ли уже в последний список с момента запуска приложения.
 *
 * `@Singleton` живёт, пока жив процесс, поэтому «вернуться в последний список» сработает
 * один раз после запуска, а не каждый раз, когда человек нажимает «назад» к оглавлению.
 */
@Singleton
class ListsLaunchMemory @Inject constructor() {
    var restoredThisLaunch = false
}

/**
 * ViewModel оглавления списков: списки со счётчиками задач, поиск,
 * создание и редактирование списков и id последнего открытого списка.
 */
@HiltViewModel
class ListsOverviewViewModel @Inject constructor(
    tasks: TaskRepository,
    private val listRepository: TaskListRepository,
    private val settingsRepository: SettingsRepository,
    private val commands: TaskCommands,
    private val launchMemory: ListsLaunchMemory,
    todayClock: TodayClock,
) : ViewModel() {

    private val query = MutableStateFlow("")

    val state: StateFlow<ListsOverviewUiState> = combine(
        listRepository.observeLists(),
        tasks.observeOpenCountsByList(),
        tasks.observeOpen(),
        query,
        todayClock.todayFlow,
    ) { lists, counts, open, q, today ->
        val rows = lists.map { ListRow(it, counts[it.id] ?: 0) }
            // «Без списка» — ящик для неразобранного: показываем, только когда в нём что-то лежит
            .filter { !it.list.isInbox || it.openCount > 0 }
        val text = q.trim()
        ListsOverviewUiState(
            today = today,
            rows = rows,
            allLists = lists,
            query = q,
            foundLists = if (text.isEmpty()) emptyList() else rows.filter { it.list.name.contains(text, ignoreCase = true) },
            foundTasks = if (text.isEmpty()) emptyList() else open.filter { it.title.contains(text, ignoreCase = true) }.take(50),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListsOverviewUiState(todayClock.today()))

    fun setQuery(text: String) {
        query.value = text
    }

    /**
     * Какой список открыть сразу после запуска приложения: последний открытый, если он ещё существует.
     * Возвращает null, если возвращаться некуда или это уже сделано в этом запуске.
     */
    suspend fun listToRestore(): Long? {
        if (launchMemory.restoredThisLaunch) return null
        launchMemory.restoredThisLaunch = true
        val lastId = settingsRepository.settings.first().lastListId ?: return null
        return lastId.takeIf { id -> listRepository.observeLists().first().any { it.id == id } }
    }

    fun saveList(list: TaskList, onSaved: (Long) -> Unit = {}) = viewModelScope.launch {
        val lists = state.value.allLists
        val id = listRepository.upsert(if (list.id == 0L) list.copy(position = lists.size) else list)
        onSaved(id)
    }

    fun deleteList(list: TaskList) = viewModelScope.launch { listRepository.delete(list) }

    fun saveTask(task: Task, newListName: String?) = viewModelScope.launch { commands.save(task, newListName) }
}
