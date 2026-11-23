package app.petitrythme.data

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
@Entity(tableName = "appointments", indices = [Index("time")])
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
)

val Appointment.apptKind get() = ApptKind.of(kind)

fun Appointment.questionList(): List<String> =
    questions.orEmpty().lines().map { it.trim().trimStart('-', '•', '*').trim() }.filter { it.isNotEmpty() }

@Dao
interface AppointmentDao {
    @Query("SELECT * FROM appointments ORDER BY time ASC")
    fun observeAll(): Flow<List<Appointment>>

    @Query("SELECT * FROM appointments WHERE time >= :from ORDER BY time ASC")
    suspend fun from(from: Long): List<Appointment>

    @Query("SELECT * FROM appointments ORDER BY time ASC")
    suspend fun all(): List<Appointment>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(a: Appointment): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(a: List<Appointment>)

    @Delete
    suspend fun delete(a: Appointment)

    @Query("DELETE FROM appointments")
    suspend fun clear()

    @Transaction
    suspend fun replaceAll(list: List<Appointment>) {
        clear()
        insertAll(list)
    }
}
