package app.planner.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.planner.R
import app.planner.core.designsystem.theme.PlannerTheme
import app.planner.core.domain.medication.MedicationHistory
import app.planner.core.domain.medication.Medications
import app.planner.core.domain.model.FoodRelation
import app.planner.core.domain.model.Medication
import java.time.LocalDate

/**
 * Строка лекарства на экране «Привычки»: значок, название, расписание («Каждый день · 9:00, 21:00»)
 * и как идёт курс. Колокольчик справа — о приёмах напоминают. Тап — редактор лекарства.
 *
 * «Как идёт» зависит от того, что с лекарством сейчас:
 * - перерыв — «На паузе до 12 октября» (и значок паузы вместо таблетки);
 * - курс ещё не начался — «С 4 октября»;
 * - курс закончился — «Курс закончился 3 ноября» (сама строка бледнее);
 * - идёт — «Ещё 5 дней» у курса и «За 30 дней — 28 из 30».
 */
@Composable
fun MedicationRow(
    medication: Medication,
    history: MedicationHistory?,
    today: LocalDate,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val paper = PlannerTheme.paper
    val pause = Medications.activePause(medication, today)
    val finished = Medications.isFinished(medication, today)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
    ) {
        // Та же ширина 40dp, что у чекбокса в строке задачи и у значка повторения — названия стоят ровно
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            Icon(
                if (pause != null) Icons.Outlined.PauseCircle else Icons.Outlined.Medication,
                null,
                tint = if (finished) paper.inkFaint else paper.inkMuted,
            )
        }
        Column(
            Modifier
                .padding(start = 10.dp, end = 8.dp)
                .weight(1f)
        ) {
            Text(
                medication.name,
                color = if (finished) paper.inkMuted else paper.ink,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                medicationScheduleText(medication),
                color = paper.inkMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
            val status = when {
                pause != null -> pausedText(pause)
                Medications.isUpcoming(medication, today) ->
                    stringResource(
                        R.string.repeat_starting,
                        DateTexts.dayMonth(medication.startDate)
                    )

                finished -> courseText(medication, today)
                // Идёт: сколько осталось курса и как принимали — через точку
                else -> listOfNotNull(
                    courseText(medication, today),
                    history?.let { medicationHistoryText(it) },
                ).joinToString(" · ").ifEmpty { null }
            }
            status?.let {
                Text(
                    it,
                    color = paper.inkMuted,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        // Колокольчик — как у задач и повторений: о приёмах напомнят. На паузе и после курса — нет
        if (medication.remind && pause == null && !finished) {
            Icon(
                Icons.Outlined.NotificationsNone,
                null,
                tint = paper.inkMuted,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .size(16.dp),
            )
        }
    }
}

/** «Каждый день · 9:00, 21:00» — как часто и во сколько. */
@Composable
fun medicationScheduleText(medication: Medication): String =
    repeatSummary(medication.repeat) + " · " + medication.doseTimes.joinToString(", ") {
        DateTexts.time(
            it
        )
    }

/** «1 капсула · после еды» — доза и как принимать; null — ни того ни другого не указали. */
@Composable
fun doseText(medication: Medication): String? =
    listOfNotNull(medication.dose.ifBlank { null }, medication.food?.let { foodText(it) })
        .joinToString(" · ")
        .ifEmpty { null }

/** «до еды», «во время еды», «после еды» — как в подписи под приёмом (с маленькой буквы). */
@Composable
fun foodText(food: FoodRelation): String = stringResource(
    when (food) {
        FoodRelation.BEFORE -> R.string.food_before
        FoodRelation.WITH -> R.string.food_with
        FoodRelation.AFTER -> R.string.food_after
    },
)

/**
 * Как идёт курс: «Ещё 5 дней», «Последний день», «Курс закончился 3 ноября». null — курс бессрочный.
 * До начала курса — вся его длина («Ещё 10 дней»): столько и предстоит.
 */
@Composable
fun courseText(medication: Medication, today: LocalDate): String? {
    val end = medication.endDate ?: return null
    val left = Medications.daysLeft(medication, today) ?: return null
    return when (left) {
        0 -> stringResource(R.string.medication_course_finished, DateTexts.dayMonth(end))
        1 -> stringResource(R.string.medication_course_last_day)
        else -> pluralStringResource(R.plurals.medication_days_left, left, left)
    }
}

/**
 * «За 30 дней — 28 из 30» или «С 10 октября — 8 из 9», если курс начался недавно.
 * null — приёмов ещё не было (курс начался сегодня, а время первого приёма не пришло).
 */
@Composable
fun medicationHistoryText(history: MedicationHistory): String? {
    if (history.planned == 0) return null
    return if (history.startedRecently) {
        stringResource(
            R.string.medication_history_since,
            DateTexts.dayMonth(history.from),
            history.taken,
            history.planned,
        )
    } else {
        stringResource(
            R.string.medication_history,
            MedicationHistory.DAYS,
            history.taken,
            history.planned
        )
    }
}
