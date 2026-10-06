package app.planner.core.domain.usecase

import app.planner.core.domain.list.IInboxNames
import app.planner.core.domain.list.findByName
import app.planner.core.domain.list.isInboxName
import app.planner.core.domain.list.nextListColor
import app.planner.core.domain.model.ListColor
import app.planner.core.domain.model.TaskList
import app.planner.core.domain.repository.ITaskListRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Создать новый список «Когда-нибудь».
 *
 * UseCase — это сценарий, порядок шагов. Сами правила (что считать одинаковым названием,
 * какой цвет следующий) лежат в list/ListRules.kt: ими пользуются ещё и экраны.
 *
 * Шаги:
 * 1. Название «Без списка» на любом языке — новый список не создаём, отдаём системный.
 * 2. Список с таким названием уже есть (без учёта регистра и пробелов) — отдаём его.
 *    Так задача из редактора с «новым» списком «дом» попадёт в уже существующий «Дом».
 * 3. Иначе создаём: в конец (position = сколько списков уже есть), цвет — выбранный
 *    или самый редкий в палитре.
 *
 * `operator fun invoke` позволяет вызывать объект как функцию: `createList("Дом")`
 * вместо `createList.invoke("Дом")`. Так принято называть «одно действие — один класс».
 *
 * Класс в модуле domain: он знает только интерфейс [ITaskListRepository] и ничего
 * не знает про Room, экраны и Android, поэтому его легко проверить обычным тестом.
 */
class CreateListUseCase @Inject constructor(
    private val lists: ITaskListRepository,
    private val inboxNames: IInboxNames,
) {
    /** Возвращает id созданного списка — или уже существующего с таким же названием. */
    suspend operator fun invoke(name: String, color: ListColor? = null): Long {
        if (isInboxName(name, inboxNames.all)) return lists.inboxId()
        // first() у Flow — взять текущий набор списков один раз (первую «посылку» потока) и отписаться
        val existingLists = lists.observeLists().first()
        existingLists.findByName(name)?.let { return it.id } // Если имя уже есть - выйти из метода
        val list = TaskList(
            name = name.trim(),
            color = color ?: nextListColor(existingLists),
            position = existingLists.size,
        )
        return lists.upsert(list)
    }
}
