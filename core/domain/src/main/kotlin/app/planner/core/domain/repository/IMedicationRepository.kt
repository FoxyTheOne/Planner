package app.planner.core.domain.repository

import app.planner.core.domain.model.DoseTaken
import app.planner.core.domain.model.Medication
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalTime

/**
 * Что приложение умеет делать с лекарствами и отметками «принято».
 * Реализация — `RoomMedicationRepository` в модуле `data`.
 *
 * Как и у повторений, приёмы на каждый день в базу не пишутся — их считает Medications.
 * Хранятся только сами лекарства и отметки о приёмах, которые уже приняли.
 */
interface IMedicationRepository {
    /** Все лекарства по порядку (как расставили); список обновляется сам. */
    fun observeMedications(): Flow<List<Medication>>

    suspend fun getMedication(id: Long): Medication?

    /** Сохранить новое (id = 0) или изменённое лекарство. Возвращает его id. */
    suspend fun save(medication: Medication): Long

    /** Удалить лекарство вместе со всеми его отметками. */
    suspend fun delete(id: Long)

    /** Новый порядок: [ids] в том порядке, в каком их расставили. Остальные не трогаем. */
    suspend fun reorder(ids: List<Long>)

    /** Отметки «принято» с [from] по [to] включительно — для «Сегодня», истории и напоминаний. */
    fun observeTaken(from: LocalDate, to: LocalDate): Flow<List<DoseTaken>>

    /** Отметки одного лекарства с [from] — для его истории. */
    suspend fun getTaken(medicationId: Long, from: LocalDate): List<DoseTaken>

    /**
     * Отметить приём лекарства [medicationId] в [date] в [time] принятым ([taken] = true)
     * или снять отметку. Повторная отметка того же приёма ничего не меняет.
     */
    suspend fun setTaken(medicationId: Long, date: LocalDate, time: LocalTime, taken: Boolean)
}
