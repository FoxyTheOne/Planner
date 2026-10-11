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
 */
data class EditorTarget(
    val task: Task,
    val repeat: Repeat? = null,
    val pause: Pause? = null,
) {
    companion object {
        /** Открыть правило повторения: черновик из правила, его повторение и текущая пауза. */
        fun forRule(rule: RecurrenceRule, today: LocalDate) = EditorTarget(
            task = Recurrence.draftFromRule(rule),
            repeat = rule.repeat,
            pause = Recurrence.activePause(rule, today),
        )
    }
}

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
            )
        }
    },
    restore = { saved ->
        val v = saved as List<*>
        EditorTarget(
            task = TaskSaver.restore(v[0]!!)!!,
            repeat = v[1]?.let { RepeatSaver.restore(it) },
            pause = (v[2] as LocalDate?)?.let { from -> Pause(from, v[3] as LocalDate?) },
        )
    },
)
