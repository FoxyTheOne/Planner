package app.planner.common

import androidx.compose.runtime.saveable.Saver
import app.planner.core.domain.model.Goal
import app.planner.core.domain.model.ListColor
import app.planner.core.domain.model.PartOfDay
import app.planner.core.domain.model.Reminder
import app.planner.core.domain.model.Repeat
import app.planner.core.domain.model.Slot
import app.planner.core.domain.model.Task
import app.planner.core.domain.model.TaskList
import app.planner.core.domain.model.TaskStatus
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.MonthDay

/*
 * «Сохранятели» (Saver) для rememberSaveable — чтобы открытые шторки и введённый текст
 * не пропадали при повороте экрана, смене темы или языка, а также когда Android выгрузил
 * свёрнутое приложение из памяти.
 *
 * ЗАЧЕМ ОНИ НУЖНЫ
 * При повороте Android пересоздаёт Activity, и всё, что было в `remember`, теряется.
 * `rememberSaveable` перед этим складывает значения в Bundle — системный «чемоданчик»,
 * который Android хранит за нас и возвращает новой Activity. Но в Bundle можно положить
 * только то, что он умеет: числа, строки, флаги, а ещё Serializable-объекты
 * (например, LocalDate, LocalTime, Instant и любые enum).
 *
 * Строки, числа и Boolean rememberSaveable сохраняет сам. А наши Task, Slot и TaskList
 * он не знает, поэтому для них мы объясняем: как разобрать объект на простые значения
 * (save) и как собрать обратно (restore). Порядок полей в save и restore должен совпадать.
 *
 * `Saver<Task?, Any>`: сохраняем Task или null. Если save вернул null — сохранять нечего,
 * и после поворота значение будет начальным (null — шторка закрыта).
 *
 * Добавили поле в Task / Slot / TaskList / Goal — добавьте его и сюда, иначе после поворота
 * оно вернётся к значению по умолчанию.
 */

/** Задача: поля по порядку в список. */
val TaskSaver: Saver<Task?, Any> = Saver(
    save = { task ->
        task?.let {
            arrayListOf(
                it.id,
                it.title,
                it.note,
                it.date,
                it.time,
                it.partOfDay,
                it.listId,
                it.status,
                it.isSample,
                it.createdAt,
                it.doneAt,
                it.ruleId,
                it.occurrenceDate,
                it.goalId,
                it.stageId,
                it.position,
                // Напоминание — двумя простыми значениями, как в базе: минуты «до» или час «в»
                (it.reminder as? Reminder.BeforeTime)?.minutes,
                (it.reminder as? Reminder.AtTime)?.time,
            )
        }
    },
    restore = { saved ->
        val v = saved as List<*>
        Task(
            id = v[0] as Long,
            title = v[1] as String,
            note = v[2] as String,
            date = v[3] as LocalDate?,
            time = v[4] as LocalTime?,
            partOfDay = v[5] as PartOfDay?,
            listId = v[6] as Long?,
            status = v[7] as TaskStatus,
            isSample = v[8] as Boolean,
            createdAt = v[9] as Instant,
            doneAt = v[10] as Instant?,
            ruleId = v[11] as Long?,
            occurrenceDate = v[12] as LocalDate?,
            goalId = v[13] as Long?,
            stageId = v[14] as Long?,
            position = v[15] as Int,
            reminder = (v[16] as Int?)?.let { Reminder.BeforeTime(it) }
                ?: (v[17] as LocalTime?)?.let { Reminder.AtTime(it) },
        )
    },
)

/** Место в плане: день, часть дня, время. */
val SlotSaver: Saver<Slot?, Any> = Saver(
    save = { slot -> slot?.let { arrayListOf(it.date, it.partOfDay, it.time) } },
    restore = { saved ->
        val v = saved as List<*>
        Slot(date = v[0] as LocalDate, partOfDay = v[1] as PartOfDay?, time = v[2] as LocalTime?)
    },
)

/** Список задач (для окна «Новый список» / «Изменить список»). */
val TaskListSaver: Saver<TaskList?, Any> = Saver(
    save = { list -> list?.let { arrayListOf(it.id, it.name, it.color, it.position, it.isInbox) } },
    restore = { saved ->
        val v = saved as List<*>
        TaskList(
            id = v[0] as Long,
            name = v[1] as String,
            color = v[2] as ListColor,
            position = v[3] as Int,
            isInbox = v[4] as Boolean,
        )
    },
)

/**
 * Как часто повторяется (для редактора задачи). Первый элемент — буква варианта,
 * дальше — его данные. Дни недели и даты Bundle умеет хранить сам (это Serializable),
 * а словари недель цикла кладём как HashMap — тоже Serializable.
 */
val RepeatSaver: Saver<Repeat?, Any> = Saver(
    save = { repeat ->
        when (repeat) {
            null -> null
            Repeat.Daily -> arrayListOf<Any?>("D")
            is Repeat.OnWeekdays -> arrayListOf<Any?>("W", ArrayList(repeat.days))
            is Repeat.EveryNDays -> arrayListOf<Any?>("N", repeat.n)
            is Repeat.Yearly -> arrayListOf<Any?>("Y", repeat.day.monthValue, repeat.day.dayOfMonth)
            is Repeat.Cycle -> arrayListOf<Any?>(
                "C", ArrayList(repeat.weeks.map { HashMap(it) }),
                repeat.anchorWeekStart, repeat.anchorWeekNumber, repeat.weekStart,
            )
        }
    },
    restore = { saved ->
        val v = saved as List<*>
        @Suppress("UNCHECKED_CAST") // в save мы сами положили туда именно эти типы
        when (v[0]) {
            "D" -> Repeat.Daily
            "W" -> Repeat.OnWeekdays((v[1] as List<DayOfWeek>).toSet())
            "N" -> Repeat.EveryNDays(v[1] as Int)
            "Y" -> Repeat.Yearly(MonthDay.of(v[1] as Int, v[2] as Int))
            else -> Repeat.Cycle(
                weeks = v[1] as List<Map<DayOfWeek, String>>,
                anchorWeekStart = v[2] as LocalDate,
                anchorWeekNumber = v[3] as Int,
                weekStart = v[4] as DayOfWeek,
            )
        }
    },
)

/** Цель (для шторки «Новая цель» / «Цель»). Даты и Instant Bundle хранит сам. */
val GoalSaver: Saver<Goal?, Any> = Saver(
    save = { goal ->
        goal?.let {
            arrayListOf(
                it.id,
                it.title,
                it.why,
                it.note,
                it.deadline,
                it.position,
                it.createdAt,
                it.achievedOn
            )
        }
    },
    restore = { saved ->
        val v = saved as List<*>
        Goal(
            id = v[0] as Long,
            title = v[1] as String,
            why = v[2] as String,
            note = v[3] as String,
            deadline = v[4] as LocalDate?,
            position = v[5] as Int,
            createdAt = v[6] as Instant,
            achievedOn = v[7] as LocalDate?,
        )
    },
)

/**
 * Напоминание (для редактора задачи). Двумя значениями, как в базе: минуты «до» или час «в»;
 * оба null — напоминания нет.
 */
val ReminderSaver: Saver<Reminder?, Any> = Saver(
    save = { reminder ->
        reminder?.let {
            arrayListOf(
                (it as? Reminder.BeforeTime)?.minutes,
                (it as? Reminder.AtTime)?.time
            )
        }
    },
    restore = { saved ->
        val v = saved as List<*>
        (v[0] as Int?)?.let { Reminder.BeforeTime(it) }
            ?: (v[1] as LocalTime?)?.let { Reminder.AtTime(it) }
    },
)

/** Времена приёмов лекарства (редактор лекарства): LocalTime сам кладётся в Bundle, нужен только список. */
val TimesSaver: Saver<List<LocalTime>, Any> = Saver(
    save = { ArrayList(it) },
    restore = { saved -> (saved as List<*>).map { it as LocalTime } },
)

/** Какое лекарство открыто в редакторе (см. [MedicationTarget]): id и название для нового. */
val MedicationTargetSaver: Saver<MedicationTarget?, Any> = Saver(
    save = { target -> target?.let { arrayListOf(it.id, it.name) } },
    restore = { saved ->
        val v = saved as List<*>
        MedicationTarget(id = v[0] as Long, name = v[1] as String)
    },
)
