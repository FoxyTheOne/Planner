package app.planner.core.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * Задача — главная сущность приложения.
 *
 * Важная идея: «Сегодня», «Неделя» и «Списки» — это не разные хранилища, а разные *взгляды*
 * на одни и те же задачи, отфильтрованные по полям:
 * - нет [date] — задача ждёт в своём списке «Когда-нибудь»;
 * - есть [date] — она в плане: видна в «Неделе», а в свой день — и в «Сегодня».
 *
 * Поэтому «перенести в неделю» — это просто задать дату, а «вернуть в список» — убрать её.
 * Ничего не копируется, и задача не может «раздвоиться».
 *
 * `data class` — класс для хранения данных: Kotlin сам создаёт `equals`, `toString`
 * и `copy(...)`. Объекты неизменяемые (`val`), изменение = копия с новыми значениями:
 * `task.copy(date = tomorrow)`.
 */
data class Task(
    /** 0 — задача ещё не сохранена; база выдаст настоящий id при вставке. */
    val id: Long = 0,
    val title: String,
    val note: String = "",
    /** День в плане; null — задача без даты, лежит в списке. */
    val date: LocalDate? = null,
    /** Точное время («10:30 врач»). Необязательно — большинство дел планируются просто на день. */
    val time: LocalTime? = null,
    /** Часть дня (утро/день/вечер), если точного времени нет. */
    val partOfDay: PartOfDay? = null,
    /** Список, которому принадлежит задача. При сохранении пустое значение заменяется на «Входящие». */
    val listId: Long? = null,
    val status: TaskStatus = TaskStatus.OPEN,
    /** Пример из онбординга — такие задачи помечены и легко удаляются. */
    val isSample: Boolean = false,
    val createdAt: Instant = Instant.now(),
    val doneAt: Instant? = null,
) {
    // Вычисляемые свойства: не хранятся, а считаются из полей при каждом обращении

    val isDone: Boolean get() = status == TaskStatus.DONE
    val isScheduled: Boolean get() = date != null

    /** Событие с точным временем не копится в «Незавершённом» — после дня оно уходит в историю. */
    val isTimedEvent: Boolean get() = time != null
}

/** Состояние задачи. SKIPPED понадобится для повторений: «полив в эту среду пропущен». */
enum class TaskStatus { OPEN, DONE, SKIPPED }

/** Части дня. Границы (во сколько начинается день и вечер) задаются в [PlannerSettings]. */
enum class PartOfDay { MORNING, DAY, EVENING }

/**
 * «Место» в плане, куда ставится задача: день и, по желанию, часть дня или точное время.
 * Используется, когда задачу переносят: шторка «Запланировать» возвращает именно Slot.
 */
data class Slot(
    val date: LocalDate,
    val partOfDay: PartOfDay? = null,
    val time: LocalTime? = null,
)
