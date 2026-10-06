package app.planner.core.domain.usecase

import app.planner.core.domain.list.IInboxNames
import app.planner.core.domain.model.ListColor
import app.planner.core.domain.model.TaskList
import app.planner.core.domain.repository.ITaskListRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Тесты сценария создания списка (сами правила проверяет list/ListRulesTest). Запускаются за доли секунды, без эмулятора:
 * вместо настоящей базы — [FakeListRepository], который хранит списки в обычной переменной.
 * Это и есть польза интерфейса ITaskListRepository: в тесте его легко подменить.
 */
class CreateListUseCaseTest {

    /** «Ненастоящий» репозиторий: списки в памяти, id выдаём по порядку. */
    private class FakeListRepository(initial: List<TaskList>) : ITaskListRepository {
        val lists = MutableStateFlow(initial)
        override fun observeLists(): Flow<List<TaskList>> = lists
        override suspend fun upsert(list: TaskList): Long {
            val id = (lists.value.maxOfOrNull { it.id } ?: 0) + 1
            lists.value = lists.value + list.copy(id = id)
            return id
        }
        override suspend fun delete(list: TaskList) {}
        override suspend fun inboxId(): Long = 1
    }

    private val inbox = TaskList(id = 1, name = "", isInbox = true)

    // «Ненастоящие» названия «Без списка»: в приложении их даёт ResourceInboxNames из strings.xml
    private val inboxNames = object : IInboxNames {
        override val all = setOf("No list", "Без списка", "Без спіса")
    }

    // runBlocking — запустить suspend-функцию в тесте и дождаться её
    @Test
    fun `new list goes to the end with next color`() = runBlocking {
        val repo = FakeListRepository(listOf(inbox, TaskList(id = 2, name = "Дом", color = ListColor.SAGE)))

        val id = CreateListUseCase(repo, inboxNames)("  Книги ")

        val created = repo.lists.value.single { it.id == id }
        assertEquals("Книги", created.name) // пробелы по краям обрезаны
        assertEquals(2, created.position) // после «Без списка» и «Дом»
        assertEquals(ListColor.entries[1], created.color) // один обычный список уже есть → второй цвет
    }

    @Test
    fun `chosen color is kept`() = runBlocking {
        val repo = FakeListRepository(listOf(inbox))

        val id = CreateListUseCase(repo, inboxNames)("Покупки", ListColor.ROSE)

        assertEquals(ListColor.ROSE, repo.lists.value.single { it.id == id }.color)
    }

    @Test
    fun `same name does not create a duplicate`() = runBlocking {
        val home = TaskList(id = 2, name = "Дом")
        val repo = FakeListRepository(listOf(inbox, home))

        // Другой регистр и лишние пробелы — всё равно тот же список
        val id = CreateListUseCase(repo, inboxNames)(" дом ")

        assertEquals(home.id, id)
        assertEquals(2, repo.lists.value.size) // новых списков не появилось
    }

    @Test
    fun `inbox name in any language gives the inbox`() = runBlocking {
        val repo = FakeListRepository(listOf(inbox))

        val id = CreateListUseCase(repo, inboxNames)("Без спіса")

        assertEquals(inbox.id, id)
        assertEquals(1, repo.lists.value.size) // списка «Без спіса» не появилось
    }
}
