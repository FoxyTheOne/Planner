package app.planner.common

import androidx.compose.runtime.saveable.Saver
import app.planner.core.domain.model.Pause
import app.planner.core.domain.model.RecurrenceRule
import app.planner.core.domain.model.Repeat
import app.planner.core.domain.model.Task
import app.planner.core.domain.recurrence.Recurrence
import java.time.LocalDate

/**
 * Что открыто в редакторе задачи: сама задача (или черновик правила) и, если это повторение, —
 * как часто оно повторяется и стоит ли сейчас на паузе.
 *
 * Раньше экраны держали это в нескольких отдельных переменных (editing, editingRepeat…),
 * и при каждом новом поле их становилось больше. Один объект проще: открыть редактор —
 * положить EditorTarget, закрыть — null.
 *
 * @param repeat как часто повторяется; null — обычная задача.
 * @param pause пауза, которая идёт сейчас; null — не на паузе (или это не повторение).
 * @param pauses все паузы правила — редактору цикла, чтобы правильно показать, какая неделя идёт сейчас.
 * @param isHabit повторение показывается привычкой («таблеткой»), а не строкой плана.
 * @param habitGoalId какой цели помогает привычка; null — ни к какой не привязана.
 */
data class EditorTarget(
    val task: Task,
    val repeat: Repeat? = null,
    val pause: Pause? = null,
    val pauses: List<Pause> = emptyList(),
    val isHabit: Boolean = false,
    val habitGoalId: Long? = null,
) {
    companion object {
        /** Открыть правило повторения: черновик из правила, его повторение, текущая пауза. */
        fun forRule(rule: RecurrenceRule, today: LocalDate) = EditorTarget(
            task = Recurrence.draftFromRule(rule),
            repeat = rule.repeat,
            pause = Recurrence.activePause(rule, today),
            pauses = rule.pauses,
            isHabit = rule.isHabit,
            habitGoalId = rule.goalId,
        )
    }
}

/**
 * Что редактор отдаёт при «Сохранить». Пара к [EditorTarget]: тот — что открыли, этот — что получилось.
 *
 * Почему объект, а не несколько параметров `onSave(task, newListName, repeat, isHabit)`:
 * результат редактора проходит длинный путь — шторка → экран → ViewModel → TaskCommands.save,
 * и так на пяти экранах. С отдельными параметрами каждое новое поле (сейчас привычка,
 * на этапе 3 — напоминание) пришлось бы дописывать во все эти места. С объектом поле
 * добавляется здесь, в шторке, где его заполняют, и в save, где его читают. Остальные просто передают объект дальше.
 * Такой приём называется «объект-параметр» (parameter object).
 *
 * @param task черновик задачи (или правила) с тем, что ввели.
 * @param newListName название нового списка, если его решили создать прямо в редакторе; иначе null.
 * @param repeat повторение; null — «не повторять».
 * @param isHabit показывать повторение привычкой. Без повторения не имеет смысла и всегда false.
 * @param habitGoalId цель, которой помогает привычка. Только у привычки, иначе null.
 * (Не путать с task.goalId: тот — у шага цели, а это — у правила-привычки.)
 */
data class EditorResult(
    val task: Task,
    val newListName: String? = null,
    val repeat: Repeat? = null,
    val isHabit: Boolean = false,
    val habitGoalId: Long? = null,
)

/**
 * Сохранятель для rememberSaveable: собран из уже готовых TaskSaver и RepeatSaver.
 * Внутри save мы находимся в SaverScope, поэтому можем звать save других сохранятелей через `with`.
 */
val EditorTargetSaver: Saver<EditorTarget?, Any> = Saver(
    save = { target ->
        target?.let {
            arrayListOf(
                with(TaskSaver) { save(it.task) },
                with(RepeatSaver) { save(it.repeat) },
                it.pause?.from,
                it.pause?.until,
                // Паузы — парами дат «с, по» (обе — LocalDate, Bundle их хранить умеет)
                ArrayList(it.pauses.flatMap { p -> listOf(p.from, p.until) }),
                it.isHabit,
                it.habitGoalId,
            )
        }
    },
    restore = { saved ->
        val v = saved as List<*>
        EditorTarget(
            task = TaskSaver.restore(v[0]!!)!!,
            repeat = v[1]?.let { RepeatSaver.restore(it) },
            pause = (v[2] as LocalDate?)?.let { from -> Pause(from, v[3] as LocalDate?) },
            pauses = (v[4] as List<*>).chunked(2) { (from, until) ->
                Pause(
                    from as LocalDate,
                    until as LocalDate?
                )
            },
            isHabit = v[5] as Boolean,
            habitGoalId = v[6] as Long?,
        )
    },
)
