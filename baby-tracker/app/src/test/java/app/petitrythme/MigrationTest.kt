package app.petitrythme

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import app.petitrythme.data.AppDatabase
import app.petitrythme.data.Appointment
import app.petitrythme.data.EventType
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Simule un téléphone qui avait la version 1.0/1.1 (base v1 avec des données),
 * puis installe la 1.2 : le journal doit être intact et les rendez-vous utilisables.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MigrationTest {

    @Test
    fun v1_to_v2_keepsJournal() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-test.db"
        ctx.deleteDatabase(name)

        // 1) Base telle que créée par la version 1, d'après le schéma exporté.
        val schema = JSONObject(File("schemas/app.petitrythme.data.AppDatabase/1.json").readText()).getJSONObject("database")
        val entity = schema.getJSONArray("entities").getJSONObject(0)
        val table = entity.getString("tableName")
        val db = SQLiteDatabase.openOrCreateDatabase(ctx.getDatabasePath(name).apply { parentFile?.mkdirs() }, null)
        db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
        val indices = entity.getJSONArray("indices")
        for (i in 0 until indices.length()) {
            db.execSQL(indices.getJSONObject(i).getString("createSql").replace("\${TABLE_NAME}", table))
        }
        val setup = schema.getJSONArray("setupQueries")
        for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
        db.execSQL("INSERT INTO events (type, time, sub, amountMl) VALUES ('BOTTLE', 1000, 'FORMULA', 120.0)")
        db.execSQL("INSERT INTO events (type, time, sub) VALUES ('DIAPER', 2000, 'POO')")
        db.version = 1
        db.close()

        // 2) Ouverture par la version actuelle : migrations v1 → v2 (auto) → v3 (synchro).
        val room = AppDatabase.build(ctx, name)
        runBlocking {
            val events = room.events().all()
            assertEquals(2, events.size)
            assertEquals(EventType.BOTTLE, events[0].type)
            assertEquals(120.0, events[0].amountMl!!, 0.0)
            // Chaque ligne existante reçoit un identifiant global unique pour la synchro.
            assertEquals(32, events[0].uid.length)
            assertNotEquals(events[0].uid, events[1].uid)
            assertEquals(1000L, events[0].updatedAt)
            room.appointments().insert(Appointment(time = 5000, who = "Dr Martin", questions = "Vitamine D ?"))
            assertEquals("Dr Martin", room.appointments().all().single().who)
        }
        assertEquals(3, room.openHelper.readableDatabase.version)
        room.close()
    }
}
