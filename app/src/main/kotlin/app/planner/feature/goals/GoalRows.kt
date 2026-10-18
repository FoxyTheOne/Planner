package app.planner.feature.goals

import app.planner.core.domain.goal.StepGroup
import app.planner.core.domain.model.GoalStage
import app.planner.core.domain.model.Task

/*
 * СТРОКИ ЭКРАНА ЦЕЛИ ДЛЯ ПЕРЕТАСКИВАНИЯ
 *
 * Пока шаг тащат, удобнее думать не о «группах шагов по этапам», а об одном плоском списке строк —
 * ровно так, как они стоят на экране:
 *
 *     Шаг «Посчитать бюджет»
 *     + Добавить шаг
 *     [Демонтаж]               ← заголовок этапа
 *     Шаг «Снять плитку»
 *     + Добавить шаг
 *     [Плитка]
 *     …
 *
 * Перетаскивание меняет местами строки в этом списке. А когда шаг отпустили, по строкам снова
 * собираются группы: шаг относится к тому этапу, чей заголовок стоит выше него ([stepGroupsOf]).
 * Поэтому, чтобы перенести шаг в другой этап, достаточно перетащить его под другой заголовок.
 *
 * Здесь только чистые функции над списками — без Compose, их проверяет GoalRowsTest.
 */

/** Строка экрана цели. `key` — ключ элемента LazyColumn: по нему библиотека понимает, какую строку тащат. */
sealed interface GoalRow {
    val key: String

    /** Заголовок этапа. Сам не перетаскивается, но шаг можно протащить мимо него — в соседний этап. */
    data class StageHeader(val stage: GoalStage) : GoalRow {
        override val key get() = "stage_${stage.id}"
    }

    data class Step(val step: Task) : GoalRow {
        override val key get() = "step_${step.id}"
    }

    /** «+ Добавить шаг» в конце этапа ([stageId]; null — у шагов без этапа). */
    data class AddStep(val stageId: Long?) : GoalRow {
        override val key get() = "add_step_$stageId"
    }
}

/** Группы шагов → строки экрана: у каждой группы заголовок (кроме шагов без этапа), шаги и «+ Добавить шаг». */
fun goalRows(groups: List<StepGroup>): List<GoalRow> =
    groups.flatMap { group ->
        // listOfNotNull — список без null: у группы без этапа заголовка нет
        listOfNotNull(group.stage?.let { GoalRow.StageHeader(it) }) +
                group.steps.map { GoalRow.Step(it) } +
                GoalRow.AddStep(group.stage?.id)
    }

/**
 * Строки → группы шагов: шаг относится к последнему заголовку этапа над ним.
 * Шаги выше первого заголовка — шаги без этапа (так шаг можно «вынуть» из этапов, перетащив его наверх).
 */
fun stepGroupsOf(rows: List<GoalRow>): List<StepGroup> {
    val groups = mutableListOf<StepGroup>()
    var stage: GoalStage? = null
    var steps = mutableListOf<Task>()
    // Группа без этапа нужна, только если в ней есть шаги или у неё есть своя строка «+ Добавить шаг»
    var looseUsed = false
    for (row in rows) {
        when (row) {
            is GoalRow.StageHeader -> {
                if (stage != null || looseUsed || steps.isNotEmpty()) groups += StepGroup(
                    stage,
                    steps
                )
                stage = row.stage
                steps = mutableListOf()
            }

            is GoalRow.Step -> steps += row.step
            is GoalRow.AddStep -> if (stage == null) looseUsed = true
        }
    }
    if (stage != null || looseUsed || steps.isNotEmpty()) groups += StepGroup(stage, steps)
    return groups
}

/** Перенести строку [fromKey] на место строки [toKey] — то, что делает перетаскивание на каждом шаге. */
fun List<GoalRow>.moved(fromKey: String, toKey: String): List<GoalRow> {
    val from = indexOfFirst { it.key == fromKey }
    val to = indexOfFirst { it.key == toKey }
    if (from < 0 || to < 0) return this
    // toMutableList — копия списка, которую можно менять; исходный список не трогаем
    return toMutableList().apply { add(to, removeAt(from)) }
}

/**
 * Шаг [key] на одну позицию выше — для TalkBack («Выше»), где перетаскивать неудобно.
 * Строки «+ Добавить шаг» перешагиваем: стоять между ними нет смысла. Выше заголовка этапа —
 * значит в конец предыдущего этапа, а выше первого заголовка — к шагам без этапа.
 */
fun List<GoalRow>.movedUp(key: String): List<GoalRow> {
    val from = indexOfFirst { it.key == key }
    var to = from - 1
    while (to >= 0 && this[to] is GoalRow.AddStep) to--
    if (from < 0 || to < 0) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}

/** Шаг [key] на одну позицию ниже: ниже заголовка следующего этапа — значит первым в нём. */
fun List<GoalRow>.movedDown(key: String): List<GoalRow> {
    val from = indexOfFirst { it.key == key }
    var to = from + 1
    while (to < size && this[to] is GoalRow.AddStep) to++
    if (from < 0 || to >= size) return this
    // После removeAt(from) строка, бывшая на месте to, сдвинется на to − 1; вставка в to ставит шаг сразу после неё
    return toMutableList().apply { add(to, removeAt(from)) }
}
