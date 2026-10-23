package app.planner.preview

import app.planner.core.domain.model.FoodRelation
import app.planner.core.domain.model.Goal
import app.planner.core.domain.model.GoalStage
import app.planner.core.domain.model.ListColor
import app.planner.core.domain.model.Medication
import app.planner.core.domain.model.PartOfDay
import app.planner.core.domain.model.RecurrenceRule
import app.planner.core.domain.model.Repeat
import app.planner.core.domain.model.Task
import app.planner.core.domain.model.TaskList
import app.planner.core.domain.model.TaskStatus
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * Выдуманные данные для превью в Android Studio: списки и задачи «как у живого человека».
 *
 * Используются только в превью (файлы *Previews.kt), в работающее приложение не попадают.
 * Дата зафиксирована, чтобы превью выглядело одинаково в любой день.
 */
object SampleData {
    val today: LocalDate = LocalDate.of(2026, 9, 24)

    val home = TaskList(id = 1, name = "Дом", color = ListColor.TERRACOTTA)
    val english = TaskList(id = 2, name = "Английский", color = ListColor.DUSTY_BLUE)
    val flowers = TaskList(id = 3, name = "Цветы", color = ListColor.SAGE)
    val books = TaskList(id = 4, name = "Книги", color = ListColor.LAVENDER)
    val noList = TaskList(id = 9, name = "", isInbox = true)

    val lists = listOf(home, english, flowers, books, noList)

    val callMom = Task(
        id = 1,
        title = "Позвонить маме",
        date = today,
        partOfDay = PartOfDay.MORNING,
        status = TaskStatus.DONE
    )
    val doctor = Task(id = 2, title = "Врач, каб. 214", date = today, time = LocalTime.of(10, 30))
    val documents = Task(
        id = 3,
        title = "Разобрать документы",
        date = today,
        partOfDay = PartOfDay.DAY,
        listId = home.id
    )
    val englishChapter = Task(
        id = 4,
        title = "Английский: гл. 4",
        date = today,
        partOfDay = PartOfDay.DAY,
        listId = english.id
    )
    val buyEpin = Task(id = 6, title = "Купить эпин", date = today, listId = flowers.id)
    val dentist =
        Task(id = 7, title = "Стоматолог", date = today.plusDays(1), time = LocalTime.of(9, 0))

    val printPhotos =
        Task(id = 10, title = "Распечатать фото", listId = home.id, date = today.plusDays(1))
    val readDune = Task(id = 11, title = "Дочитать «Дюну»", listId = books.id)
    val buyBook = Task(id = 12, title = "Купить «Сто лет одиночества»", listId = books.id)
    val returnBook =
        Task(id = 13, title = "Вернуть книгу Оле", listId = books.id, date = today.plusDays(2))
    val forgotten = Task(id = 14, title = "Позвонить в ЖЭК", date = today.minusDays(1))

    /** Цикл полива на 2 недели; 24 сентября 2026 — четверг второй недели. */
    val wateringRule = RecurrenceRule(
        id = 1,
        title = "Полив",
        repeat = Repeat.Cycle(
            weeks = listOf(
                mapOf(DayOfWeek.MONDAY to "все цветы"),
                mapOf(DayOfWeek.THURSDAY to "фикус, монстера + подкормка"),
            ),
            anchorWeekStart = today.minusWeeks(1).with(DayOfWeek.MONDAY),
        ),
        startDate = today.minusWeeks(1),
        partOfDay = PartOfDay.EVENING,
        listId = flowers.id,
    )

    /** Бассейн по вторникам и четвергам в 18:30. */
    val poolRule = RecurrenceRule(
        id = 2,
        title = "Бассейн",
        repeat = Repeat.OnWeekdays(setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY)),
        startDate = today.minusMonths(1),
        time = LocalTime.of(18, 30),
    )

    /** Привычки каждый день: «таблетки» над планом в «Сегодня». */
    val habitRules = listOf("Вода", "Зарядка", "Читать 10 минут").mapIndexed { i, title ->
        RecurrenceRule(
            id = 10L + i,
            title = title,
            repeat = Repeat.Daily,
            startDate = today.minusMonths(1),
            isHabit = true
        )
    }

    // --- Цели ---

    /** Ремонт: два этапа, 2 шага из 5 сделаны, срок через 2 с лишним месяца. */
    val repairGoal = Goal(
        id = 1, title = "Ремонт в ванной", why = "Чтобы утром было приятно туда заходить",
        deadline = LocalDate.of(2026, 12, 15),
    )
    val demolition = GoalStage(id = 1, goalId = 1, title = "Демонтаж", position = 0)
    val tiles = GoalStage(id = 2, goalId = 1, title = "Плитка", position = 1)
    val repairSteps = listOf(
        Task(id = 30, title = "Посчитать бюджет", goalId = 1, status = TaskStatus.DONE),
        Task(
            id = 31,
            title = "Снять старую плитку",
            goalId = 1,
            stageId = 1,
            status = TaskStatus.DONE
        ),
        Task(id = 32, title = "Вынести строительный мусор", goalId = 1, stageId = 1, position = 1),
        Task(id = 33, title = "Купить плитку", goalId = 1, stageId = 2),
        Task(id = 34, title = "Положить плитку", goalId = 1, stageId = 2, position = 1),
    )

    /** Испанский: срок скоро (через 10 дней) — подпись срока цветом кнопок. */
    val spanishGoal =
        Goal(id = 2, title = "Испанский до A2", deadline = today.plusDays(10), position = 1)
    val spanishSteps = listOf(Task(id = 40, title = "Сдать пробный тест", goalId = 2))

    /** Без шагов и без срока — карточка подсказывает добавить первый шаг. */
    val guitarGoal = Goal(id = 3, title = "Научиться играть на гитаре", position = 2)

    /** Достигнутая — в архиве. */
    val runGoal = Goal(id = 4, title = "Пробежать 5 км", achievedOn = LocalDate.of(2026, 8, 30))

    // Лекарства: витамин D дважды в день бессрочно, магний — курсом на 30 дней (идёт уже 10)
    val vitaminD = Medication(
        id = 1, name = "Витамин D", dose = "1 капсула", food = FoodRelation.AFTER,
        times = listOf(LocalTime.of(9, 0), LocalTime.of(21, 0)), startDate = today.minusMonths(2),
    )
    val magnesium = Medication(
        id = 2, name = "Магний", dose = "1 таблетка", times = listOf(LocalTime.of(9, 0)),
        startDate = today.minusDays(10), endDate = today.plusDays(19), position = 1,
    )
    val medications = listOf(vitaminD, magnesium)
}
