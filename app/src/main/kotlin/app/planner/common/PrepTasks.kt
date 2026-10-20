package app.planner.common

import app.planner.core.domain.recurrence.Recurrence
import app.planner.core.domain.repository.IRecurrenceRepository
import app.planner.core.domain.repository.ITaskRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Создаёт задачи-подготовки к дням «раз в год» («День рождения мамы» за 2 недели до него).
 * Какие пора создать — решает домен (Recurrence.prepTasksDue), здесь только запись в базу.
 *
 * Зовут двое:
 * - [DailyMaintenance] — раз в день: вчера было рано, а сегодня подготовка как раз начинается;
 * - TaskCommands — сразу после сохранения повторения: завели день рождения через неделю
 *   с подготовкой «за 2 недели» — задача нужна уже сегодня, а не завтра утром.
 *
 * Mutex — чтобы два вызова одновременно не создали одну и ту же подготовку дважды:
 * второй подождёт, увидит уже созданную задачу и ничего не добавит.
 */
@Singleton
class PrepTasks @Inject constructor(
    private val tasks: ITaskRepository,
    private val rules: IRecurrenceRepository,
) {
    private val mutex = Mutex()

    suspend fun create(today: LocalDate) = mutex.withLock {
        Recurrence.prepTasksDue(rules.observeRules().first(), tasks.getPrepTasks(), today)
            .forEach { tasks.addOrUpdate(it) }
    }
}
