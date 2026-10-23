package app.planner.common

import app.planner.R
import app.planner.core.domain.medication.Dose
import app.planner.core.domain.medication.Medications
import app.planner.core.domain.model.Medication
import app.planner.core.domain.repository.IMedicationRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Действия с лекарствами, общие для «Сегодня» и экрана «Привычки» (и потом — для уведомлений):
 * сохранить, удалить, отметить приём, перерыв. Как [TaskCommands] для задач: одинаковое поведение
 * и одинаковые сообщения на всех экранах, а всё, что можно потерять, можно и отменить.
 */
@Singleton
class MedicationCommands @Inject constructor(
    private val medications: IMedicationRepository,
    private val messages: MessageBus,
    private val todayClock: TodayClock,
) {

    /**
     * Сохранить из редактора. Новое лекарство встаёт в конец списка — как новая привычка:
     * то, что человек уже расставил, не сдвигается.
     */
    suspend fun save(medication: Medication) {
        val position = if (medication.id == 0L) {
            (medications.observeMedications().first().maxOfOrNull { it.position } ?: -1) + 1
        } else {
            medication.position
        }
        medications.save(medication.copy(position = position))
    }

    /**
     * Удалить лекарство. Вместе с ним база удаляет и все отметки «принято», поэтому для «Отменить»
     * запоминаем и их — иначе вернулось бы лекарство без истории.
     */
    suspend fun delete(medication: Medication) {
        val taken = medications.getTaken(medication.id, LocalDate.MIN)
        medications.delete(medication.id)
        messages.post(
            UiMessage(UiText.Res(R.string.msg_medication_deleted), undo = {
                medications.save(medication)
                medications.addTaken(taken)
            }),
        )
    }

    /** Отметить приём или снять отметку — тап по строке приёма на «Сегодня». */
    suspend fun toggle(dose: Dose) =
        medications.setTaken(dose.medication.id, dose.date, dose.time, taken = !dose.isTaken)

    /** Перерыв с сегодня по [until] включительно; null — пока не нажмут «Продолжить». */
    suspend fun pause(medicationId: Long, until: LocalDate?) {
        val medication = medications.getMedication(medicationId) ?: return
        medications.save(Medications.pause(medication, todayClock.today(), until))
        messages.post(
            UiMessage(
                UiText.Res(R.string.msg_medication_paused),
                undo = { medications.save(medication) }),
        )
    }

    /** «Продолжить» — приёмы снова с сегодня. */
    suspend fun resume(medicationId: Long) {
        val medication = medications.getMedication(medicationId) ?: return
        medications.save(Medications.resume(medication, todayClock.today()))
        messages.post(
            UiMessage(
                UiText.Res(R.string.msg_medication_resumed),
                undo = { medications.save(medication) }),
        )
    }
}
