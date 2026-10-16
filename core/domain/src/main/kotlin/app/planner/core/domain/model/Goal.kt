package app.planner.core.domain.model

import java.time.Instant
import java.time.LocalDate

/**
 * Цель — что-то большое, к чему идут маленькими шагами: «Выучить испанский до A2», «Сделать ремонт в ванной».
 *
 * Сама цель — только «шапка»: название, зачем она, заметки, срок. Шаги цели — это обычные
 * задачи ([Task]) с [Task.goalId]. Поэтому шаг можно запланировать на день, отметить,
 * смахнуть на завтра — всё как у задачи, ничего не нужно делать заново.
 * Шаги можно (но не обязательно) разложить по этапам ([GoalStage]).
 *
 * @param why «Зачем» — ради чего это всё. Перечитать, когда пропадает запал.
 * @param deadline срок; null — без срока. Необязателен: у многих целей его нет, и это нормально.
 * @param position порядок карточек на экране «Цели».
 * @param achievedOn день, когда цель отметили достигнутой; null — цель ещё в работе.
 *   Достигнутые цели не удаляются, а уходят в архив: приятно иногда посмотреть, сколько сделано.
 */
data class Goal(
    val id: Long = 0,
    val title: String,
    val why: String = "",
    val note: String = "",
    val deadline: LocalDate? = null,
    val position: Int = 0,
    val createdAt: Instant = Instant.now(),
    val achievedOn: LocalDate? = null,
) {
    val isAchieved: Boolean get() = achievedOn != null
}

/**
 * Этап цели: «Подготовка», «Демонтаж», «Плитка». Группирует шаги, чтобы длинная цель
 * не превращалась в простыню из 40 задач.
 *
 * Этапы необязательны: у маленькой цели шаги могут лежать просто списком, без этапов.
 *
 * @param position порядок этапов внутри цели. От него зависит и «следующий шаг»:
 *   сначала шаги первого этапа, потом второго.
 */
data class GoalStage(
    val id: Long = 0,
    val goalId: Long,
    val title: String,
    val position: Int = 0,
)
