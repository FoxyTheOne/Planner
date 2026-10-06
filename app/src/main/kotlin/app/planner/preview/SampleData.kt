package app.planner.preview

import app.planner.core.domain.model.ListColor
import app.planner.core.domain.model.PartOfDay
import app.planner.core.domain.model.Task
import app.planner.core.domain.model.TaskList
import app.planner.core.domain.model.TaskStatus
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

    val callMom = Task(id = 1, title = "Позвонить маме", date = today, partOfDay = PartOfDay.MORNING, status = TaskStatus.DONE)
    val doctor = Task(id = 2, title = "Врач, каб. 214", date = today, time = LocalTime.of(10, 30))
    val documents = Task(id = 3, title = "Разобрать документы", date = today, partOfDay = PartOfDay.DAY, listId = home.id)
    val englishChapter = Task(id = 4, title = "Английский: гл. 4", date = today, partOfDay = PartOfDay.DAY, listId = english.id)
    val watering = Task(id = 5, title = "Полив · вода + фитиль", date = today, partOfDay = PartOfDay.EVENING, listId = flowers.id)
    val buyEpin = Task(id = 6, title = "Купить эпин", date = today, listId = flowers.id)
    val dentist = Task(id = 7, title = "Стоматолог", date = today.plusDays(1), time = LocalTime.of(9, 0))

    val printPhotos = Task(id = 10, title = "Распечатать фото", listId = home.id, date = today.plusDays(1))
    val readDune = Task(id = 11, title = "Дочитать «Дюну»", listId = books.id)
    val buyBook = Task(id = 12, title = "Купить «Сто лет одиночества»", listId = books.id)
    val returnBook = Task(id = 13, title = "Вернуть книгу Оле", listId = books.id, date = today.plusDays(2))
    val forgotten = Task(id = 14, title = "Позвонить в ЖЭК", date = today.minusDays(1))
}
