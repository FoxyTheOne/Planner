package app.planner.core.data.db

import androidx.room.TypeConverter
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * Конвертеры типов для Room. SQLite умеет хранить только числа, текст и двоичные данные,
 * поэтому даты и время переводим в числа и обратно — Room вызывает эти функции сам.
 *
 * - дата → число дней от 1 января 1970 (такие числа правильно сортируются и сравниваются прямо в SQL);
 * - время дня → секунды от полуночи;
 * - момент времени (Instant) → миллисекунды от 1970 года.
 */
class Converters {
    @TypeConverter fun dateToEpochDay(value: LocalDate?): Long? = value?.toEpochDay()
    @TypeConverter fun epochDayToDate(value: Long?): LocalDate? = value?.let(LocalDate::ofEpochDay)

    @TypeConverter fun timeToSecondOfDay(value: LocalTime?): Int? = value?.toSecondOfDay()
    @TypeConverter fun secondOfDayToTime(value: Int?): LocalTime? = value?.let { LocalTime.ofSecondOfDay(it.toLong()) }

    @TypeConverter fun instantToMillis(value: Instant?): Long? = value?.toEpochMilli()
    @TypeConverter fun millisToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)
}
