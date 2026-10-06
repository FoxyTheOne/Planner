package app.planner.core.domain.recurrence

import app.planner.core.domain.model.PartOfDay
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * Правило повторения: «что делать» + «как часто» + «с какого и до какого дня».
 *
 * Важная идея: повторяющаяся задача хранится ОДИН раз — как правило, а не как 365 задач
 * на год вперёд. Какие дни выпадают на неделю, движок ([Recurrence]) считает на лету.
 * Так правило можно поменять («теперь по вторникам») — и все будущие дни поменяются сразу.
 *
 * Пока здесь только самое простое: каждый день, по дням недели, раз в N дней.
 * Циклы полива, паузы, лекарства и привычки добавим следующими шагами.
 *
 * @param startDate с какого дня правило действует. Для «раз в N дней» это ещё и точка отсчёта.
 * @param endDate по какой день включительно; null — бессрочно.
 */
data class RecurrenceRule(
    val id: Long = 0,
    val title: String,
    val repeat: Repeat,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val time: LocalTime? = null,
    val partOfDay: PartOfDay? = null,
    val listId: Long? = null,
)

/**
 * Как часто повторяется.
 *
 * `sealed interface` — «закрытый» набор вариантов: все они перечислены здесь, и других
 * быть не может. Зато у каждого варианта свои данные: дням недели нужен набор дней,
 * «раз в N дней» — число N, а «каждый день» не нужно ничего.
 * Когда мы пишем `when (repeat) { ... }`, компилятор проверяет, что разобраны ВСЕ варианты.
 * Добавим новый (например, цикл полива) — компилятор сам покажет места, где его надо учесть.
 *
 * (Альтернатива — enum + куча необязательных полей вроде `days: Set?` и `n: Int?`.
 * Тогда можно случайно создать «раз в N дней» без N. Здесь так не получится.)
 */
sealed interface Repeat {

    /** Каждый день. `data object` — вариант без данных, он один на всё приложение. */
    data object Daily : Repeat

    /** По выбранным дням недели: «пн, ср, пт», «по будням», «по субботам». */
    data class OnWeekdays(val days: Set<DayOfWeek>) : Repeat {
        // init выполняется при создании объекта; require бросит ошибку, если условие нарушено
        init {
            require(days.isNotEmpty()) { "Нужен хотя бы один день недели" }
        }
    }

    /** Раз в N дней, считая от [RecurrenceRule.startDate]: «через день» — это N = 2. */
    data class EveryNDays(val n: Int) : Repeat {
        init {
            require(n >= 1) { "N должно быть не меньше 1" }
        }
    }
}
