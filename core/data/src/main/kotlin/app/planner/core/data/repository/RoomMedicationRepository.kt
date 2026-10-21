package app.planner.core.data.repository

import app.planner.core.data.db.DoseTakenEntity
import app.planner.core.data.db.PlannerDatabase
import app.planner.core.data.db.toDomain
import app.planner.core.data.db.toEntity
import app.planner.core.domain.model.DoseTaken
import app.planner.core.domain.model.Medication
import app.planner.core.domain.repository.IMedicationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Хранилище лекарств и отметок «принято» поверх Room. Переводит доменные [Medication] и [DoseTaken]
 * в строки таблиц `medications` и `doses_taken` и обратно (см. `MedicationEntities.kt`).
 */
@Singleton
class RoomMedicationRepository @Inject constructor(
    db: PlannerDatabase,
) : IMedicationRepository {

    private val dao = db.medicationDao()

    override fun observeMedications(): Flow<List<Medication>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun getMedication(id: Long): Medication? = dao.get(id)?.toDomain()

    override suspend fun save(medication: Medication): Long {
        // Как у задач и правил: у нового лекарства берём id, выданный базой
        val rowId = dao.upsert(medication.toEntity())
        return if (medication.id == 0L) rowId else medication.id
    }

    override suspend fun delete(id: Long) = dao.delete(id)

    override suspend fun reorder(ids: List<Long>) {
        val byId = dao.getAll().associateBy { it.id }
        // Лекарство, которое уже удалили (его нет в byId), просто пропускаем
        dao.upsertAll(ids.mapIndexedNotNull { index, id -> byId[id]?.copy(position = index) })
    }

    override fun observeTaken(from: LocalDate, to: LocalDate): Flow<List<DoseTaken>> =
        dao.observeTaken(from, to).map { rows -> rows.map { it.toDomain() } }

    override suspend fun getTaken(medicationId: Long, from: LocalDate): List<DoseTaken> =
        dao.getTaken(medicationId, from).map { it.toDomain() }

    override suspend fun setTaken(
        medicationId: Long,
        date: LocalDate,
        time: LocalTime,
        taken: Boolean
    ) {
        if (taken) {
            dao.insertTaken(DoseTakenEntity(medicationId, date, time, takenAt = Instant.now()))
        } else {
            dao.deleteTaken(medicationId, date, time)
        }
    }
}
