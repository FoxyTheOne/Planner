package app.planner.core.domain.model

import app.planner.core.domain.recurrence.Recurrence
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * Задача — главная сущность приложения.
 *
 * Важная идея: «Сегодня», «Неделя» и «Списки» — это не разные хранилища, а разные *взгляды*
 * на одни и те же задачи, отфильтрованные по полям:
 * - нет [date] — задача ждёт в своём списке «Когда-нибудь» или в своей цели (шаг цели);
 * - есть [date] — она в плане: видна в «Неделе», а в свой день — и в «Сегодня».
 *
 * Поэтому «перенести в неделю» — это просто задать дату, а «вернуть в список» — убрать её.
 * Шаг цели устроен так же: запланировали — он в плане дня, но по-прежнему виден в своей цели.
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
    /**
     * Список, которому принадлежит задача. При сохранении пустое значение заменяется на «Входящие» —
     * кроме шагов цели: шаг принадлежит цели, а не списку, и listId у него пустой.
     */
    val listId: Long? = null,
    val status: TaskStatus = TaskStatus.OPEN,
    /**
     * Если задача — сделанный (или перенесённый) день повторения: id правила.
     * Повторения хранятся правилом; конкретный день попадает в таблицу задач, только когда
     * с ним что-то сделали — отметили, перенесли или пропустили. См. [Recurrence.toTask].
     */
    val ruleId: Long? = null,
    /** К какому дню правила относится эта задача (даже если её перенесли на другой день). */
    val occurrenceDate: LocalDate? = null,
    /** Шаг цели: id цели ([Goal]). Такая задача живёт в цели, а не в списке. */
    val goalId: Long? = null,
    /** Этап цели, к которому относится шаг ([GoalStage]); null — шаг без этапа. */
    val stageId: Long? = null,
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

    /** Это день повторения, а не обычная задача. */
    val isOccurrence: Boolean get() = ruleId != null

    /** Шаг цели — живёт в цели, а не в списке. */
    val isGoalStep: Boolean get() = goalId != null

    /*
     * «Черновики» — задачи, которых нет в базе (id = 0), но которые связаны с правилом повторения.
     * Их легко спутать, поэтому они определены здесь, в одном месте:
     * - черновик ПРАВИЛА: правило, открытое в редакторе (Recurrence.draftFromRule). Дня у него нет;
     * - черновик ДНЯ: один день повторения, который сейчас переносят или пропускают
     *   (Recurrence.toTask). У него есть день правила — occurrenceDate.
     */

    /** Открыто правило повторения целиком (а не его день и не обычная задача). */
    val isRuleDraft: Boolean get() = id == 0L && ruleId != null && occurrenceDate == null

    /** Один день повторения, которого ещё нет в базе: его переносят или пропускают. */
    val isOccurrenceDraft: Boolean get() = id == 0L && occurrenceDate != null

    /** Это день повторения — уже сохранённый (отмечен, перенесён, пропущен) или черновик. */
    val isOccurrenceDay: Boolean get() = occurrenceDate != null
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
