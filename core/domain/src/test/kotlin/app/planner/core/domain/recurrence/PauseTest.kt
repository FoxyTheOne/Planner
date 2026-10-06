package app.planner.core.domain.recurrence

import app.planner.core.domain.model.Pause
import app.planner.core.domain.model.RecurrenceRule
import app.planner.core.domain.model.Repeat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalDate

/**
 * Тесты пауз «как с музыкой»: после паузы повторение продолжается с того места, где остановилось.
 *
 * Календарь октября 2026 по неделям (понедельники): 5 · 12 · 19 · 26 · 2 ноября.
 * Цикл полива начинается 5 октября: без пауз недели были бы 1 · 2 · 3 · 4 · 1.
 */
class PauseTest {

    private fun date(day: Int) = LocalDate.of(2026, 10, day)

    private val watering = Repeat.Cycle(
        weeks = listOf(
            mapOf(WEDNESDAY to "неделя 1"),
            mapOf(WEDNESDAY to "неделя 2", SATURDAY to "подкормка"),
            mapOf(WEDNESDAY to "неделя 3"),
            mapOf(WEDNESDAY to "неделя 4"),
        ),
        anchorMonday = date(5),
    )

    private fun rule(repeat: Repeat = watering, vararg pauses: Pause) =
        RecurrenceRule(title = "Полив", repeat = repeat, startDate = date(5), pauses = pauses.toList())

    private fun detailsOn(r: RecurrenceRule, d: LocalDate) = Recurrence.occurrence(r, d)?.details

    @Test
    fun `cycle continues from the same week after a whole-week pause`() {
        // Отпуск на всю неделю 2 (пн 12 — вс 18)
        val r = rule(pauses = arrayOf(Pause(date(12), date(18))))

        assertNull(detailsOn(r, date(14))) // ср в отпуске — ничего
        assertNull(detailsOn(r, date(17))) // и подкормки в сб тоже
        // После отпуска — снова неделя 2, а не 3: она «стояла на паузе»
        assertEquals("неделя 2", detailsOn(r, date(21)))
        assertEquals("подкормка", detailsOn(r, date(24)))
        assertEquals("неделя 3", detailsOn(r, date(28)))
    }

    @Test
    fun `partly paused weeks still count`() {
        // Пауза с чт 15 по вт 27: неделя 12-го начата (пн–ср), неделя 19-го — вся в паузе,
        // неделя 26-го — пн и вт в паузе, а со среды уже нет
        val r = rule(pauses = arrayOf(Pause(date(15), date(27))))

        assertEquals("неделя 2", detailsOn(r, date(14))) // до паузы
        // Неделя 12-го была неделей 2, неделя 19-го пропущена, значит неделя 26-го — неделя 3
        assertEquals("неделя 3", detailsOn(r, date(28)))
    }

    @Test
    fun `open pause lasts until resumed`() {
        val paused = rule(pauses = arrayOf(Pause(date(12)))) // «Пауза» без даты окончания

        assertFalse(Recurrence.occursOn(paused, date(14)))
        assertFalse(Recurrence.occursOn(paused, LocalDate.of(2027, 3, 3))) // хоть через полгода

        // Вернулись в среду 28-го и нажали «Продолжить»
        val resumed = Recurrence.resume(paused, today = date(28))

        assertEquals(Pause(date(12), date(27)), resumed.pauses.single()) // пауза закончилась вчера
        // Недели 12-го и 19-го целиком на паузе, неделя 26-го начата — продолжаем с недели 2
        assertEquals("неделя 2", detailsOn(resumed, date(28)))
    }

    @Test
    fun `resume on the day the pause started removes it`() {
        val paused = rule(pauses = arrayOf(Pause(date(14))))

        val resumed = Recurrence.resume(paused, today = date(14))

        assertTrue(resumed.pauses.isEmpty())
        assertEquals("неделя 2", detailsOn(resumed, date(14)))
    }

    @Test
    fun `resume keeps future pauses`() {
        val future = Pause(date(26), date(30))
        val r = rule(pauses = arrayOf(future))

        assertEquals(listOf(future), Recurrence.resume(r, today = date(14)).pauses)
    }

    @Test
    fun `every n days does not count paused days`() {
        // «Через день» с пн 5: 5, 7, … Пауза с 8 по 10 октября
        val r = rule(Repeat.EveryNDays(2), Pause(date(8), date(10)))

        val days = Recurrence.occurrences(r, date(5), date(14)).map { it.date.dayOfMonth }

        // Без паузы было бы 5, 7, 9, 11, 13. С паузой отсчёт продолжается: 7-го было,
        // 11-го — «день отдыха» (как 8-е), 12-го — снова
        assertEquals(listOf(5, 7, 12, 14), days)
    }

    @Test
    fun `overlapping pauses are not counted twice`() {
        // Две пересекающиеся паузы вместе покрывают ровно неделю 12–18
        val r = rule(pauses = arrayOf(Pause(date(12), date(16)), Pause(date(14), date(18))))

        assertEquals("неделя 2", detailsOn(r, date(21))) // как от одной паузы на неделю
    }

    @Test
    fun `pause just hides days for simple repeats`() {
        val r = rule(Repeat.Daily, Pause(date(6), date(7)))

        val days = Recurrence.occurrences(r, date(5), date(8)).map { it.date.dayOfMonth }

        assertEquals(listOf(5, 8), days) // вторник и среда пропущены
    }
}
