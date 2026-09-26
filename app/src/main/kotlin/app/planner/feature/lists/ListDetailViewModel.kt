package app.planner.feature.lists

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.planner.common.TaskCommands
import app.planner.common.TodayClock
import app.planner.core.domain.model.Slot
import app.planner.core.domain.model.Task
import app.planner.core.domain.model.TaskList
import app.planner.core.domain.repository.SettingsRepository
import app.planner.core.domain.repository.TaskListRepository
import app.planner.core.domain.repository.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** Состояние экрана одного списка. [list] = null — список удалили, экран закроется. */
data class ListDetailUiState(
    val today: LocalDate,
    val list: TaskList? = null,
    val tasks: List<Task> = emptyList(),
    val allLists: List<TaskList> = emptyList(),
    val loaded: Boolean = false,
)

/**
 * ViewModel экрана одного списка: его задачи и действия с ними.
 *
 * Какой список открыт, берётся из адреса экрана (`list/{listId}`) через [SavedStateHandle] —
 * это «сумка с параметрами» экрана, которую навигация заполняет сама и которая
 * переживает даже перезапуск процесса системой.
 *
 * При открытии запоминает этот список как последний — в него приложение вернётся при следующем запуске.
 */
@HiltViewModel
class ListDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    tasks: TaskRepository,
    private val listRepository: TaskListRepository,
    private val settingsRepository: SettingsRepository,
    private val commands: TaskCommands,
    todayClock: TodayClock,
) : ViewModel() {

    private val listId: Long = checkNotNull(savedStateHandle.get<Long>(ARG_LIST_ID))

    init {
        viewModelScope.launch { settingsRepository.update { it.copy(lastListId = listId) } }
    }

    val state: StateFlow<ListDetailUiState> = combine(
        listRepository.observeLists(),
        tasks.observeListTasks(listId),
        todayClock.todayFlow,
    ) { lists, listTasks, today ->
        ListDetailUiState(
            today = today,
            list = lists.firstOrNull { it.id == listId },
            tasks = listTasks,
            allLists = lists,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListDetailUiState(todayClock.today()))

    fun toggle(task: Task) = viewModelScope.launch { commands.toggleDone(task) }
    fun delete(task: Task) = viewModelScope.launch { commands.delete(task) }
    fun schedule(task: Task, slot: Slot) = viewModelScope.launch { commands.schedule(task, slot) }
    fun saveTask(task: Task, newListName: String?) = viewModelScope.launch { commands.save(task, newListName) }
    fun saveList(list: TaskList) = viewModelScope.launch { listRepository.upsert(list) }

    /** Удалить этот список. Задачи остаются «Без списка»; последний открытый список забываем. */
    fun deleteList(list: TaskList) = viewModelScope.launch {
        listRepository.delete(list)
        settingsRepository.update { if (it.lastListId == list.id) it.copy(lastListId = null) else it }
    }

    companion object {
        const val ARG_LIST_ID = "listId"
    }
}
