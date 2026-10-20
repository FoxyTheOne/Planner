package app.planner.core.data.backup

import app.planner.core.domain.model.Pause
import app.planner.core.domain.model.RecurrenceRule
import app.planner.core.domain.model.Reminder
import app.planner.core.domain.model.Repeat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalDate
import java.time.LocalTime
import java.time.MonthDay

/**
 * Формат файла бэкапа: старые копии читаются, правила повторения сохраняются без потерь.
 * Обычный JVM-тест: файл не пишется на диск, JSON проверяется прямо в памяти.
 */
class BackupFormatTest {

    @Test
    fun `backup of format 1 is still readable`() {
        // Так выглядел файл, сделанный приложением до правил повторения: поля "rules" нет
        val v1 = """
            {"formatVersion":1,"exportedAt":"2026-10-01T10:00:00Z",
             "lists":[{"id":1,"name":"","color":"SAGE","position":0,"isInbox":true}],
             "tasks":[{"id":5,"title":"Купить лампу","date":"2026-10-07","listId":1,
                       "createdAt":"2026-10-01T09:00:00Z"}]}
        """.trimIndent()

        val snapshot = backupJson.decodeFromString(BackupSnapshot.serializer(), v1)

        assertEquals("Купить лампу", snapshot.tasks.single().title)
        assertTrue(snapshot.rules.isEmpty()) // правил нет — и это не ошибка
    }

    @Test
    fun `rules survive export and import`() {
        val oct5 = LocalDate.of(2026, 10, 5)
        val rules = listOf(
            RecurrenceRule(id = 1, title = "Зарядка", repeat = Repeat.Daily, startDate = oct5),
            RecurrenceRule(
                id = 2, title = "Бассейн", repeat = Repeat.OnWeekdays(setOf(MONDAY, WEDNESDAY)),
                startDate = oct5, endDate = oct5.plusMonths(3), time = LocalTime.of(18, 30), listId = 4,
            ),
            RecurrenceRule(id = 3, title = "Таблетки", repeat = Repeat.EveryNDays(2), startDate = oct5),
            RecurrenceRule(
                id = 4,
                title = "Полив",
                repeat = Repeat.Cycle(
                    weeks = listOf(mapOf(WEDNESDAY to "фикус"), mapOf(WEDNESDAY to "фикус", SATURDAY to "подкормка")),
                    anchorWeekStart = oct5,
                    anchorWeekNumber = 2,
                ),
                startDate = oct5,
                pauses = listOf(Pause(oct5.plusDays(7), oct5.plusDays(13)), Pause(oct5.plusDays(30))),
            ),
            // Цикл, у которого неделя начинается с воскресенья
            RecurrenceRule(
                id = 5,
                title = "Подкормка",
                repeat = Repeat.Cycle(listOf(mapOf(SUNDAY to "эпин")), oct5.minusDays(1), weekStart = SUNDAY),
                startDate = oct5,
            ),
        )

        // Правило → запись в файле → JSON-текст → обратно
        val snapshot = BackupSnapshot(2, "2026-10-09T10:00:00Z", emptyList(), emptyList(), rules.map { it.toDto() })
        val text = backupJson.encodeToString(BackupSnapshot.serializer(), snapshot)
        val restored = backupJson.decodeFromString(BackupSnapshot.serializer(), text).rules.map { it.toDomain() }

        assertEquals(rules, restored)
    }

    @Test
    fun `cycle from format 3 is read as starting on monday`() {
        // В формате 3 у цикла было anchorMonday и не было weekStart
        val v3 = """
            {"formatVersion":3,"exportedAt":"2026-10-09T10:00:00Z","lists":[],"tasks":[],
             "rules":[{"id":1,"title":"Полив","startDate":"2026-10-05",
                       "repeat":{"type":"CYCLE","weeks":[{"WEDNESDAY":"фикус"}],
                                 "anchorMonday":"2026-10-05","anchorWeekNumber":1}}]}
        """.trimIndent()

        val rule = backupJson.decodeFromString(BackupSnapshot.serializer(), v3).rules.single().toDomain()

        val cycle = rule.repeat as Repeat.Cycle
        assertEquals(LocalDate.of(2026, 10, 5), cycle.anchorWeekStart)
        assertEquals(DayOfWeek.MONDAY, cycle.weekStart)
    }

    @Test
    fun `rule from format 4 is not a habit, habit survives export`() {
        // В формате 4 поля isHabit ещё не было
        val v4 = """
            {"formatVersion":4,"exportedAt":"2026-10-10T10:00:00Z","lists":[],"tasks":[],
             "rules":[{"id":1,"title":"Бассейн","startDate":"2026-10-05","repeat":{"type":"DAILY"}}]}
        """.trimIndent()
        val old = backupJson.decodeFromString(BackupSnapshot.serializer(), v4).rules.single().toDomain()
        assertEquals(false, old.isHabit)

        val habit = old.copy(title = "Вода", isHabit = true)
        val back = backupJson.decodeFromString(
            RuleDto.serializer(),
            backupJson.encodeToString(RuleDto.serializer(), habit.toDto()),
        ).toDomain()
        assertEquals(habit, back)
    }

    @Test
    fun `backup of format 5 has no goals and nothing is linked to them`() {
        val v5 = """
            {"formatVersion":5,"exportedAt":"2026-10-12T10:00:00Z","lists":[],
             "tasks":[{"id":5,"title":"Купить лампу","createdAt":"2026-10-01T09:00:00Z"}],
             "rules":[{"id":1,"title":"Вода","startDate":"2026-10-05","repeat":{"type":"DAILY"},"isHabit":true}]}
        """.trimIndent()

        val snapshot = backupJson.decodeFromString(BackupSnapshot.serializer(), v5)

        assertTrue(snapshot.goals.isEmpty())
        assertTrue(snapshot.stages.isEmpty())
        assertEquals(null, snapshot.tasks.single().goalId)
        assertEquals(null, snapshot.rules.single().toDomain().goalId)
    }

    @Test
    fun `backup of format 7 has no reminders`() {
        val v7 = """
            {"formatVersion":7,"exportedAt":"2026-10-17T10:00:00Z","lists":[],
             "tasks":[{"id":5,"title":"Врач","date":"2026-10-19","time":"10:30","createdAt":"2026-10-01T09:00:00Z"}]}
        """.trimIndent()

        val task = backupJson.decodeFromString(BackupSnapshot.serializer(), v7).tasks.single()

        assertEquals(null, task.reminderMinutesBefore)
        assertEquals(null, task.reminderAt)
    }

    @Test
    fun `rule from format 8 has no place yet, place survives export`() {
        // В формате 8 поля position у правил ещё не было
        val v8 = """
            {"formatVersion":8,"exportedAt":"2026-10-19T10:00:00Z","lists":[],"tasks":[],
             "rules":[{"id":1,"title":"Вода","startDate":"2026-10-05","repeat":{"type":"DAILY"},"isHabit":true}]}
        """.trimIndent()
        val old = backupJson.decodeFromString(BackupSnapshot.serializer(), v8).rules.single().toDomain()
        assertEquals(0, old.position)

        val moved = old.copy(position = 3)
        val back = backupJson.decodeFromString(
            RuleDto.serializer(),
            backupJson.encodeToString(RuleDto.serializer(), moved.toDto()),
        ).toDomain()
        assertEquals(moved, back)
    }

    @Test
    fun `rule from format 9 has no reminder, reminder survives export`() {
        // В формате 9 напоминаний у правил ещё не было
        val v9 = """
            {"formatVersion":9,"exportedAt":"2026-10-20T10:00:00Z","lists":[],"tasks":[],
             "rules":[{"id":1,"title":"Полив","startDate":"2026-10-05","repeat":{"type":"DAILY"},"time":"18:00"}]}
        """.trimIndent()
        val old = backupJson.decodeFromString(BackupSnapshot.serializer(), v9).rules.single().toDomain()
        assertEquals(null, old.reminder)

        val withReminder = old.copy(reminder = Reminder.BeforeTime(30))
        val back = backupJson.decodeFromString(
            RuleDto.serializer(),
            backupJson.encodeToString(RuleDto.serializer(), withReminder.toDto()),
        ).toDomain()
        assertEquals(withReminder, back)
    }

    @Test
    fun `weekdays are written as names in the file`() {
        val rule = RecurrenceRule(
            title = "Бассейн", repeat = Repeat.OnWeekdays(setOf(WEDNESDAY, MONDAY)), startDate = LocalDate.of(2026, 10, 5),
        )
        // В файле — понятные названия по порядку недели, а не число-маска, как в базе
        assertEquals(listOf("MONDAY", "WEDNESDAY"), rule.toDto().repeat.days)
    }
    @Test
    fun `yearly rule survives export`() {
        val oct15 = LocalDate.of(2026, 10, 15)
        val birthday = RecurrenceRule(
            id = 3, title = "День рождения мамы",
            repeat = Repeat.Yearly(MonthDay.from(oct15)), startDate = oct15,
        )
        val back = backupJson.decodeFromString(
            RuleDto.serializer(),
            backupJson.encodeToString(RuleDto.serializer(), birthday.toDto()),
        ).toDomain()
        assertEquals(birthday, back)
    }
}
