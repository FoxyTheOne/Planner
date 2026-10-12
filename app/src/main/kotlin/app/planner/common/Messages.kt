package app.planner.common

import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/*
 * СООБЩЕНИЯ ВНИЗУ ЭКРАНА (Snackbar)
 *
 * Путь сообщения:
 *   1. Пользователь смахнул задачу → TaskCommands.moveToTomorrow(...)
 *   2. TaskCommands кладёт сообщение в MessageBus: post(UiMessage(текст, undo = ...))
 *   3. MainViewModel отдаёт поток сообщений каркасу приложения (PlannerApp)
 *   4. PlannerApp получает сообщение, превращает UiText в строку (resolve) и показывает Snackbar
 *   5. Нажали «Отменить» → выполняется undo из сообщения
 */

/**
 * Текст, который ещё не превращён в строку.
 *
 * Зачем: ViewModel и TaskCommands не должны брать строки из ресурсов сами — для этого нужен
 * Context (доступ к ресурсам Android), а держать его в ViewModel нельзя (утечка памяти,
 * и при смене языка строка останется старой). Поэтому они передают не строку,
 * а «описание строки»: какой ресурс взять и что подставить. Строку соберёт уже экран — [resolve].
 *
 * `sealed interface` — «один из перечисленных вариантов, других не бывает»:
 */
sealed interface UiText {
    /** Строка из strings.xml, например R.string.msg_scheduled = «Запланировано: %s», и что подставить в %s. */
    // @param: — к какой части относится пометка: к параметру конструктора (так её проверяет Android Studio)
    data class Res(@param:StringRes val id: Int, val args: List<Any> = emptyList()) : UiText

    /** Строка с числом из plurals: «1 задача / 2 задачи / 5 задач» — форма зависит от числа. */
    data class Plural(@param:PluralsRes val id: Int, val count: Int) : UiText

    /** Готовый текст, который переводить не нужно (например, «Пт 25» — дата уже на языке телефона). */
    data class Raw(val text: String) : UiText
}

/**
 * Превратить [UiText] в готовую строку.
 *
 * Аргументом [UiText.Res] может быть другой UiText: «Запланировано: %s», где %s — «завтра»
 * (тоже из ресурсов). Тогда сначала превращаем в строку вложенный («завтра»), потом внешний.
 * Функция вызывает сама себя для вложенных — это называется рекурсия.
 */
fun UiText.resolve(resources: Resources): String = when (this) {
    is UiText.Raw -> text
    is UiText.Plural -> resources.getQuantityString(id, count, count)
    is UiText.Res -> {
        // Каждый аргумент: если это UiText — превращаем в строку, иначе (число, готовая строка) оставляем как есть
        val ready = args.map { if (it is UiText) it.resolve(resources) else it }
        // getString ждёт аргументы через запятую; * «раскладывает» массив в такой список
        resources.getString(id, *ready.toTypedArray())
    }
}

/**
 * Одно сообщение для Snackbar.
 *
 * @param undo как отменить действие. Если есть — в Snackbar будет кнопка «Отменить».
 *   Это функция, которую можно передать, сохранить и вызвать потом. `suspend` — потому что
 *   отмена пишет в базу и должна выполняться в корутине.
 */
data class UiMessage(val text: UiText, val undo: (suspend () -> Unit)? = null)

/**
 * Общая «почта» для сообщений: любой экран кладёт сюда сообщение, а каркас приложения
 * показывает его в одном общем Snackbar внизу. `@Singleton` — одна почта на всё приложение.
 */
@Singleton
class MessageBus @Inject constructor() {

    /*
     * SharedFlow — поток событий, на который можно подписаться (collect).
     * В отличие от StateFlow он не хранит «текущее значение»: сообщение — это событие,
     * его нужно показать один раз, а не держать.
     *
     * MutableSharedFlow — версия, в которую можно класть (emit); наружу отдаём только
     * SharedFlow (messages), чтобы снаружи могли читать, но не класть мимо post().
     * Подчёркивание в имени _messages — принятое обозначение такой «внутренней» версии.
     *
     * extraBufferCapacity = 4 — до 4 сообщений могут подождать в очереди, если показывающая
     *   сторона ещё не успела их забрать (например, ушла уборка с тремя сообщениями подряд).
     * onBufferOverflow = DROP_OLDEST — если очередь переполнена, выбрасываем самое старое:
     *   важнее показать свежее сообщение, чем застрять на устаревших.
     *   Благодаря этому tryEmit ниже никогда не «ждёт» и всегда успешно кладёт сообщение.
     */
    private val _messages = MutableSharedFlow<UiMessage>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val messages: SharedFlow<UiMessage> = _messages

    /** Отправить сообщение. Можно вызывать откуда угодно, в том числе не из корутины. */
    fun post(message: UiMessage) {
        _messages.tryEmit(message)
    }
}
