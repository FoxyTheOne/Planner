package app.planner.common

import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Текст из ресурсов: ViewModel не знает о Context, строку соберёт UI.
 * Аргументом [Res] может быть и другой [UiText] — например, «завтра» внутри «Запланировано: завтра».
 */
sealed interface UiText {
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText
    data class Plural(@PluralsRes val id: Int, val count: Int) : UiText
    data class Raw(val text: String) : UiText
}

fun UiText.resolve(resources: Resources): String = when (this) {
    is UiText.Raw -> text
    is UiText.Plural -> resources.getQuantityString(id, count, count)
    is UiText.Res -> resources.getString(id, *args.map { if (it is UiText) it.resolve(resources) else it }.toTypedArray())
}

/** Сообщение внизу экрана; если есть [undo] — показываем кнопку «Отменить». */
data class UiMessage(val text: UiText, val undo: (suspend () -> Unit)? = null)

/** Общая шина сообщений: действие на любом экране показывает Snackbar в общем Scaffold. */
@Singleton
class MessageBus @Inject constructor() {
    private val _messages = MutableSharedFlow<UiMessage>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val messages: SharedFlow<UiMessage> = _messages

    fun post(message: UiMessage) {
        _messages.tryEmit(message)
    }
}
