package app.petitrythme.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

enum class ApptKind(val code: String, val label: String, val emoji: String) {
    PEDIATRE("PEDIATRE", "Pédiatre", "🩺"),
    MEDECIN("MEDECIN", "Médecin", "👩‍⚕️"),
    VACCIN("VACCIN", "Vaccin", "💉"),
    PMI("PMI", "PMI", "🏥"),
    SAGE_FEMME("SAGE_FEMME", "Sage-femme", "🤰"),
    OSTEO("OSTEO", "Ostéopathe", "🙌"),
    KINE("KINE", "Kiné", "💆"),
    EXAMEN("EXAMEN", "Examen", "🔬"),
    AUTRE("AUTRE", "Autre", "📋");

    companion object {
        fun of(code: String?) = entries.firstOrNull { it.code == code } ?: AUTRE
    }
}

/**
 * Un rendez-vous de bébé. Rangé à part du journal : ce n'est pas un évènement
 * du quotidien, mais quelque chose qu'on prépare (questions) et dont on garde
 * une trace (compte rendu).
 */
@Entity(tableName = "appointments", indices = [Index("time"), Index(value = ["uid"], unique = true)])
data class Appointment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val time: Long,
    val kind: String = ApptKind.PEDIATRE.code,
    /** Praticien : « Dr Martin ». */
    val who: String? = null,
    val place: String? = null,
    /** Questions à poser, une par ligne. */
    val questions: String? = null,
    /** Compte rendu après le rendez-vous. */
    val notes: String? = null,
    val remindEve: Boolean = true,
    val remindHour: Boolean = false,
    /** Étape du calendrier vaccinal que ce rendez-vous couvre (ex. « m2 »), v3. */
    val vaccineKey: String? = null,
    // --- Synchronisation (v3) ---
    @ColumnInfo(defaultValue = "") val uid: String = newUid(),
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = 0,
    @ColumnInfo(defaultValue = "0") val dirty: Boolean = false,
    val author: String? = null,
)

/** Trace d'une suppression, pour la propager à l'autre téléphone. */
@Entity(tableName = "tombstones")
data class Tombstone(
    @PrimaryKey val uid: String,
    val kind: String,
    val deletedAt: Long,
    val dirty: Boolean = true,
)

val Appointment.apptKind get() = ApptKind.of(kind)

fun Appointment.questionList(): List<String> =
    questions.orEmpty().lines().map { it.trim().trimStart('-', '•', '*').trim() }.filter { it.isNotEmpty() }

@Dao
interface AppointmentDao {
    @Query("SELECT * FROM appointments WHERE id = :id")
    suspend fun byId(id: Long): Appointment?

    @Query("SELECT * FROM appointments WHERE uid = :uid")
    suspend fun byUid(uid: String): Appointment?

    @Query("SELECT * FROM appointments WHERE dirty = 1")
    suspend fun dirty(): List<Appointment>

    @Query("UPDATE appointments SET dirty = 0 WHERE uid = :uid AND updatedAt = :updatedAt")
    suspend fun markClean(uid: String, updatedAt: Long)

    /** À l'activation de la synchro : tout est à partager ; les saisies sans auteur sont attribuées à ce téléphone. */
    @Query("UPDATE appointments SET dirty = 1, author = COALESCE(author, NULLIF(:me, ''))")
    suspend fun markAllDirty(me: String)

    @Query("DELETE FROM appointments WHERE uid = :uid")
    suspend fun deleteByUidRaw(uid: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRaw(a: Appointment): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTombstone(t: Tombstone)

    @Query("DELETE FROM tombstones WHERE uid = :uid")
    suspend fun clearTombstone(uid: String)

    @Delete
    suspend fun deleteRaw(a: Appointment)

    @Query("DELETE FROM appointments")
    suspend fun deleteAllRaw()

    @Query("SELECT * FROM appointments ORDER BY time ASC")
    fun observeAll(): Flow<List<Appointment>>

    @Query("SELECT * FROM appointments WHERE time >= :from ORDER BY time ASC")
    suspend fun from(from: Long): List<Appointment>

    @Query("SELECT * FROM appointments ORDER BY time ASC")
    suspend fun all(): List<Appointment>

    /** Écriture locale : horodatée et marquée « à envoyer ». */
    @Transaction
    suspend fun insert(a: Appointment): Long {
        clearTombstone(a.uid)
        return insertRaw(a.copy(updatedAt = System.currentTimeMillis(), dirty = true, author = a.author ?: Stamp.author))
    }

    @Transaction
    suspend fun delete(a: Appointment) {
        val cur = byId(a.id) ?: byUid(a.uid) ?: return
        deleteRaw(cur)
        insertTombstone(Tombstone(cur.uid, "appt", System.currentTimeMillis()))
    }

    @Transaction
    suspend fun clear() {
        all().forEach { insertTombstone(Tombstone(it.uid, "appt", System.currentTimeMillis())) }
        deleteAllRaw()
    }

    @Transaction
    suspend fun replaceAll(list: List<Appointment>) {
        clear()
        list.forEach { insert(it) }
    }
}
