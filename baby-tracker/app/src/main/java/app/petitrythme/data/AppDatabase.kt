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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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

    @Query("SELECT * FROM events WHERE id = :id")
    suspend fun byId(id: Long): Event?

    // --- Écritures brutes (utilisées telles quelles par la synchro) -----------

    @Query("SELECT * FROM events WHERE uid = :uid")
    suspend fun byUid(uid: String): Event?

    @Query("SELECT * FROM events WHERE dirty = 1")
    suspend fun dirty(): List<Event>

    @Query("UPDATE events SET dirty = 0 WHERE uid = :uid AND updatedAt = :updatedAt")
    suspend fun markClean(uid: String, updatedAt: Long)

    /** À l'activation de la synchro : tout est à partager ; les saisies sans auteur sont attribuées à ce téléphone. */
    @Query("UPDATE events SET dirty = 1, author = COALESCE(author, NULLIF(:me, ''))")
    suspend fun markAllDirty(me: String)

    @Query("DELETE FROM events WHERE uid = :uid")
    suspend fun deleteByUidRaw(uid: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRaw(e: Event): Long

    @Delete
    suspend fun deleteRaw(e: Event)

    @Query("DELETE FROM events")
    suspend fun deleteAllRaw()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTombstone(t: Tombstone)

    @Query("DELETE FROM tombstones WHERE uid = :uid")
    suspend fun clearTombstone(uid: String)

    @Query("SELECT * FROM tombstones WHERE dirty = 1")
    suspend fun dirtyTombstones(): List<Tombstone>

    @Query("SELECT * FROM tombstones WHERE uid = :uid")
    suspend fun tombstone(uid: String): Tombstone?

    @Query("UPDATE tombstones SET dirty = 0 WHERE uid = :uid AND deletedAt = :deletedAt")
    suspend fun markTombstoneClean(uid: String, deletedAt: Long)

    // --- Écritures locales : horodatées et marquées « à envoyer » -----------

    @Transaction
    suspend fun insert(e: Event): Long {
        clearTombstone(e.uid)
        return insertRaw(e.copy(updatedAt = System.currentTimeMillis(), dirty = true, author = e.author ?: Stamp.author))
    }

    suspend fun update(e: Event) {
        insert(e)
    }

    @Transaction
    suspend fun insertAll(list: List<Event>) {
        list.forEach { insert(it) }
    }

    /** Supprime (par identifiant local) et garde une trace pour l'autre téléphone. */
    @Transaction
    suspend fun delete(e: Event) {
        val cur = byId(e.id) ?: byUid(e.uid) ?: return
        deleteRaw(cur)
        insertTombstone(Tombstone(cur.uid, "event", System.currentTimeMillis()))
    }

    @Transaction
    suspend fun clear() {
        all().forEach { insertTombstone(Tombstone(it.uid, "event", System.currentTimeMillis())) }
        deleteAllRaw()
    }

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
 * v2 : table des rendez-vous (migration automatique générée par Room).
 * v3 : synchronisation (identifiant global, horodatage, suppressions) et lien vaccin ;
 *      migration écrite à la main car il faut générer un identifiant pour chaque ligne existante.
 * Les données existantes sont conservées (voir MigrationTest).
 */
@Database(
    entities = [Event::class, Appointment::class, Tombstone::class],
    version = 3,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun events(): EventDao
    abstract fun appointments(): AppointmentDao

    companion object {
        fun build(context: Context, name: String = "petitrythme.db"): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, name)
                .addMigrations(MIGRATION_2_3)
                .build()

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                for (table in listOf("events", "appointments")) {
                    db.execSQL("ALTER TABLE `$table` ADD COLUMN `uid` TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE `$table` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE `$table` ADD COLUMN `dirty` INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE `$table` ADD COLUMN `author` TEXT")
                    db.execSQL("UPDATE `$table` SET uid = lower(hex(randomblob(16))), updatedAt = time WHERE uid = ''")
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_${table}_uid` ON `$table` (`uid`)")
                }
                db.execSQL("ALTER TABLE `appointments` ADD COLUMN `vaccineKey` TEXT")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `tombstones` (`uid` TEXT NOT NULL, `kind` TEXT NOT NULL, " +
                        "`deletedAt` INTEGER NOT NULL, `dirty` INTEGER NOT NULL, PRIMARY KEY(`uid`))"
                )
            }
        }
    }
}
