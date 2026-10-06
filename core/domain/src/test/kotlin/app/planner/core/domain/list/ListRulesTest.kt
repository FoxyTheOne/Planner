package app.planner.core.domain.list

import app.planner.core.domain.model.ListColor
import app.planner.core.domain.model.TaskList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Тесты правил про списки: одинаковые названия, «Без списка», выбор цвета. */
class ListRulesTest {

    private val inbox = TaskList(id = 1, name = "", isInbox = true)

    @Test
    fun `findByName ignores the list itself when renaming`() {
        val home = TaskList(id = 2, name = "Дом")
        val lists = listOf(inbox, home)
        assertEquals(home, lists.findByName("ДОМ"))
        assertNull(lists.findByName("Дом", exceptId = home.id)) // переименовываем сам «Дом» — не дубликат
        assertNull(lists.findByName("   ")) // пустое название ни с чем не совпадает
    }

    // В тестах названия «Без списка» задаём сами — настоящие приложение берёт из strings.xml
    private val inboxNames = setOf("No list", "Без списка", "Без спіса")

    @Test
    fun `inbox name is a problem in every language`() {
        val lists = listOf(inbox, TaskList(id = 2, name = "Дом"))
        assertEquals(ListNameProblem.INBOX, lists.nameProblem("No list", inboxNames))
        assertEquals(ListNameProblem.INBOX, lists.nameProblem(" без списка ", inboxNames)) // регистр и пробелы не важны
        assertEquals(ListNameProblem.INBOX, lists.nameProblem("Без спіса", inboxNames))
        assertEquals(ListNameProblem.DUPLICATE, lists.nameProblem("дом", inboxNames))
        assertNull(lists.nameProblem("Без списка дел", inboxNames)) // похожее, но другое название — можно
    }

    @Test
    fun `free color is chosen after a list was deleted`() {
        // Были SAGE, TERRACOTTA, DUSTY_BLUE; список с TERRACOTTA удалили
        val lists = listOf(
            inbox,
            TaskList(id = 2, name = "Дом", color = ListColor.SAGE),
            TaskList(id = 4, name = "Покупки", color = ListColor.DUSTY_BLUE),
        )
        // По старому правилу (2 списка → цвет №2) вышел бы DUSTY_BLUE, как у «Покупок»
        assertEquals(ListColor.TERRACOTTA, nextListColor(lists))
    }

    @Test
    fun `when all colors are used, the least used one is chosen`() {
        // Каждый цвет по разу, а SAGE — дважды: следующим должен стать первый из «разовых»
        val lists = listOf(inbox) +
            ListColor.entries.mapIndexed { i, c -> TaskList(id = i + 2L, name = "$i", color = c) } +
            TaskList(id = 100, name = "ещё", color = ListColor.SAGE)
        assertEquals(ListColor.entries[1], nextListColor(lists))
    }
}
