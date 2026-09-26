package app.planner.core.domain

import app.planner.core.domain.model.Task
import app.planner.core.domain.model.TaskStatus
import app.planner.core.domain.overdue.Overdue
import app.planner.core.domain.overdue.OverdueGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class OverdueTest {

    private val today = LocalDate.of(2026, 9, 24)

    @Test
    fun `открытая задача в прошлом — хвост`() {
        assertTrue(Overdue.isOverdue(Task(title = "Документы", date = today.minusDays(1)), today))
    }

    @Test
    fun `сегодняшняя, сделанная и без даты — не хвост`() {
        assertFalse(Overdue.isOverdue(Task(title = "a", date = today), today))
        assertFalse(Overdue.isOverdue(Task(title = "b", date = today.minusDays(2), status = TaskStatus.DONE), today))
        assertFalse(Overdue.isOverdue(Task(title = "c"), today))
    }

    @Test
    fun `событие с точным временем не копится`() {
        val doctor = Task(title = "Врач", date = today.minusDays(1), time = LocalTime.of(10, 30))
        assertFalse(Overdue.isOverdue(doctor, today))
    }

    @Test
    fun `группировка по давности`() {
        assertEquals(OverdueGroup.YESTERDAY, Overdue.group(Task(title = "a", date = today.minusDays(1)), today))
        assertEquals(OverdueGroup.THIS_WEEK, Overdue.group(Task(title = "b", date = today.minusDays(6)), today))
        assertEquals(OverdueGroup.OLDER, Overdue.group(Task(title = "c", date = today.minusDays(7)), today))
    }

    @Test
    fun `возврат в список только после срока`() {
        assertTrue(Overdue.shouldReturnToList(Task(title = "Фото", date = today.minusDays(8)), today, returnAfterDays = 7))
        assertFalse(Overdue.shouldReturnToList(Task(title = "Фото", date = today.minusDays(7)), today, returnAfterDays = 7))
    }

    @Test
    fun `возвращённая задача без даты и во Входящих, если источника нет`() {
        val returned = Overdue.returnedToList(Task(title = "x", date = today.minusDays(9)), inboxId = 1)
        assertNull(returned.date)
        assertEquals(1L, returned.listId)
        val own = Overdue.returnedToList(Task(title = "y", date = today.minusDays(9), listId = 5), inboxId = 1)
        assertEquals(5L, own.listId)
    }
}
