package app.petitrythme.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {
    @Query("SELECT * FROM events WHERE time >= :from AND time < :to ORDER BY time DESC")
    fun observeBetween(from: Long, to: Long): Flow<List<Event>>

    @Query("SELECT * FROM events WHERE type IN (:types) ORDER BY time DESC LIMIT 1")
    fun observeLatest(types: List<EventType>): Flow<Event?>

    @Query("SELECT * FROM events WHERE type = :type ORDER BY time DESC LIMIT :limit")
    fun observeRecent(type: EventType, limit: Int): Flow<List<Event>>

    @Query("SELECT * FROM events WHERE type = 'DIAPER' AND sub IN ('POO', 'BOTH') ORDER BY time DESC LIMIT 1")
    fun observeLastPoo(): Flow<Event?>

    @Query("SELECT * FROM events WHERE type IN (:types) ORDER BY time DESC LIMIT 1")
    suspend fun latest(types: List<EventType>): Event?

    @Query("SELECT * FROM events ORDER BY time ASC")
    suspend fun all(): List<Event>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(e: Event): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(e: List<Event>)

    @Update
    suspend fun update(e: Event)

    @Delete
    suspend fun delete(e: Event)

    @Query("DELETE FROM events")
    suspend fun clear()

    @Transaction
    suspend fun replaceAll(events: List<Event>) {
        clear()
        insertAll(events)
    }
}

class Converters {
    @TypeConverter
    fun fromType(t: EventType): String = t.name

    @TypeConverter
    fun toType(s: String): EventType = EventType.entries.firstOrNull { it.name == s } ?: EventType.NOTE
}

/**
 * Version 2 : ajout de la table des rendez-vous. La migration est générée par Room
 * à partir des schémas exportés (app/schemas) : les données existantes sont conservées.
 */
@Database(
    entities = [Event::class, Appointment::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun events(): EventDao
    abstract fun appointments(): AppointmentDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "petitrythme.db").build()
    }
}
