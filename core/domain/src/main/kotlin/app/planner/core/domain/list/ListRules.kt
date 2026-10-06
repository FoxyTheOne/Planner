package app.planner.core.domain.list

import app.planner.core.domain.model.ListColor
import app.planner.core.domain.model.TaskList

/*
 * Правила про списки «Когда-нибудь»: что считать одинаковым названием и какой цвет дать новому.
 *
 * Это обычные функции без базы и без Android. Их зовут и UseCase (при сохранении),
 * и экраны (окно «Новый список» заранее отмечает цвет и предупреждает о занятом названии).
 * Поэтому они лежат отдельно, а не спрятаны внутри одного UseCase.
 * Похожим образом устроен overdue/Overdue.kt — правила про просроченные задачи.
 */

/** Почему название нельзя дать списку. От этого зависит подсказка в окне списка. */
enum class ListNameProblem {
    /** Это «Без списка» на одном из языков — такой список уже есть, он системный. */
    INBOX,

    /** Так уже называется другой список. */
    DUPLICATE,
}

/**
 * Совпадает ли название с «Без списка» на любом из языков (без учёта регистра и пробелов).
 * @param inboxNames переводы «Без списка» — из [IInboxNames].
 */
fun isInboxName(name: String, inboxNames: Set<String>): Boolean =
    inboxNames.any { it.equals(name.trim(), ignoreCase = true) }

/**
 * Найти обычный список с таким названием. «Дом», «дом» и « Дом » считаются одним названием.
 * «Без списка» здесь не ищем — для него есть [isInboxName].
 *
 * Это extension-функция: вызывается как будто метод у списка — `lists.findByName("Дом")`.
 *
 * @param exceptId не сравнивать с этим списком — нужно при переименовании,
 *   чтобы список не считался дубликатом самого себя.
 */
fun List<TaskList>.findByName(name: String, exceptId: Long = 0): TaskList? {
    val wanted = name.trim()
    if (wanted.isEmpty()) return null
    return firstOrNull {
        !it.isInbox && it.id != exceptId && it.name.trim().equals(wanted, ignoreCase = true)
    }
}

/**
 * Можно ли дать списку такое название. null — можно; иначе — причина, почему нельзя.
 * Окно «Новый список» / «Изменить список» по ней выбирает подсказку под полем.
 */
fun List<TaskList>.nameProblem(
    name: String,
    inboxNames: Set<String>,
    exceptId: Long = 0,
): ListNameProblem? = when {
    isInboxName(name, inboxNames) -> ListNameProblem.INBOX
    findByName(name, exceptId) != null -> ListNameProblem.DUPLICATE
    else -> null
}

/**
 * Цвет для нового списка: тот, что встречается реже всех; при равенстве — первый по порядку палитры.
 * Так цвета не повторяются, пока есть свободные, даже если какие-то списки удаляли.
 * (Раньше цвет считался по количеству списков, и после удаления мог совпасть с соседним.)
 *
 * «Без списка» (inbox) не считаем: у него нет своей точки-метки.
 */
fun nextListColor(lists: List<TaskList>): ListColor {
    val regular = lists.filter { !it.isInbox }
    // minBy возвращает ПЕРВЫЙ элемент с наименьшим значением — отсюда «при равенстве первый по порядку»
    return ListColor.entries.minBy { color -> regular.count { it.color == color } }
}
