package app.petitrythme

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.petitrythme.data.AppDatabase
import app.petitrythme.data.Appointment
import app.petitrythme.data.Event
import app.petitrythme.data.EventType
import app.petitrythme.data.ProfileStore
import app.petitrythme.data.Side
import app.petitrythme.data.TimerStore
import app.petitrythme.sync.HttpTransport
import app.petitrythme.sync.SyncConfig
import app.petitrythme.sync.SyncException
import app.petitrythme.sync.SyncManager
import app.petitrythme.sync.SyncResponse
import app.petitrythme.sync.SyncTransport
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Même logique que server/app.py, en mémoire (le vrai serveur est testé par pytest et par [realServer]). */
class FakeServer : SyncTransport {
    private class Doc(val json: JSONObject, val seq: Long)
    private val families = mutableMapOf<String, MutableMap<String, Doc>>()
    private val seqs = mutableMapOf<String, Long>()

    override suspend fun health(server: String) {}

    override suspend fun createFamily(server: String, key: String, createToken: String?) {
        families.getOrPut(key) { mutableMapOf() }
        seqs.putIfAbsent(key, 0)
    }

    override suspend fun sync(server: String, key: String, since: Long, changes: JSONArray): SyncResponse {
        val docs = families[key] ?: throw SyncException("unknown_family", "inconnu")
        var seq = seqs.getValue(key)
        for (i in 0 until changes.length()) {
            val c = changes.getJSONObject(i)
            val cur = docs[c.getString("uid")]
            if (cur != null && cur.json.getLong("updatedAt") >= c.getLong("updatedAt")) continue
            seq++
            val stored = JSONObject(c.toString())
            if (c.optBoolean("deleted")) stored.put("data", JSONObject.NULL)
            docs[c.getString("uid")] = Doc(stored, seq)
        }
        seqs[key] = seq
        val out = JSONArray()
        docs.values.filter { it.seq > since }.sortedBy { it.seq }.forEach { out.put(it.json) }
        return SyncResponse(seq, out, false)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SyncTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<Context>()

    /** Un « téléphone » : sa base, ses préférences, son moteur de synchro. */
    inner class Phone(name: String, transport: SyncTransport) {
        init {
            ctx.deleteDatabase("$name.db")
            listOf("profile_$name", "timers_$name", "sync_$name").forEach {
                ctx.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
            }
        }
        val db = AppDatabase.build(ctx, "$name.db")
        val profile = ProfileStore(ctx, "profile_$name")
        val timers = TimerStore(ctx, "timers_$name") {}
        val sync = SyncManager(db, profile, timers, SyncConfig(ctx, "sync_$name"), transport)
        fun events() = runBlocking { db.events().all() }
        fun appts() = runBlocking { db.appointments().all() }
        fun syncOk() = runBlocking { sync.sync().getOrThrow() }
    }

    private fun bottle(ml: Double, t: Long) = Event(type = EventType.BOTTLE, time = t, amountMl = ml)

    private fun pairedPhones(transport: SyncTransport, server: String = "https://test"): Pair<Phone, Phone> {
        val a = Phone("papa", transport)
        val b = Phone("maman", transport)
        runBlocking {
            a.db.events().insert(bottle(90.0, 1000))
            a.profile.update { it.copy(name = "Léa") }
            b.db.events().insert(bottle(120.0, 2000))
            a.sync.create(server, "Papa", null)
            b.sync.join(a.sync.config.state.value.inviteLink, "", "Maman")
        }
        a.syncOk()
        return a to b
    }

    @Test
    fun twoPhones_mergeExistingData() {
        val (a, b) = pairedPhones(FakeServer())
        assertEquals(listOf(90.0, 120.0), a.events().map { it.amountMl })
        assertEquals(listOf(90.0, 120.0), b.events().map { it.amountMl })
        assertEquals("Léa", b.profile.state.value.name)
        // Le lien d'invitation contient le serveur et la clé, après le #.
        assertTrue(a.sync.config.state.value.inviteLink.startsWith("https://test/join#k="))
        assertEquals("Papa", a.events().first { it.amountMl == 90.0 }.author)
    }

    @Test
    fun edits_deletes_and_undo_propagate() {
        val (a, b) = pairedPhones(FakeServer())
        val onA = a.events().first { it.amountMl == 120.0 }
        runBlocking { a.db.events().update(onA.copy(amountMl = 130.0)) }
        a.syncOk(); b.syncOk()
        assertEquals(listOf(90.0, 130.0), b.events().map { it.amountMl })

        // Suppression sur B → disparaît sur A
        val onB = b.events().first { it.amountMl == 90.0 }
        runBlocking { b.db.events().delete(onB) }
        b.syncOk(); a.syncOk()
        assertEquals(listOf(130.0), a.events().map { it.amountMl })

        // « Annuler » sur B (réinsertion) → réapparaît sur A
        Thread.sleep(5)
        runBlocking { b.db.events().insert(onB) }
        b.syncOk(); a.syncOk()
        assertEquals(listOf(90.0, 130.0), a.events().map { it.amountMl })
    }

    @Test
    fun conflict_mostRecentWins() {
        val (a, b) = pairedPhones(FakeServer())
        b.syncOk()
        val x = a.events().first()
        runBlocking { a.db.events().update(x.copy(note = "par A")) }
        Thread.sleep(5)
        runBlocking { b.db.events().update(b.events().first { it.uid == x.uid }.copy(note = "par B")) }
        // A synchronise en premier, mais la modification de B est plus récente.
        a.syncOk(); b.syncOk(); a.syncOk()
        assertEquals("par B", a.events().first { it.uid == x.uid }.note)
        assertEquals("par B", b.events().first { it.uid == x.uid }.note)
    }

    @Test
    fun timers_andAppointments_areShared() {
        val (a, b) = pairedPhones(FakeServer())
        b.syncOk()
        a.timers.tapSide(Side.L, System.currentTimeMillis() - 5 * 60_000L)
        runBlocking { a.db.appointments().insert(Appointment(time = 5000, who = "Dr Martin", vaccineKey = "m2")) }
        a.syncOk(); b.syncOk()
        assertNotNull(b.timers.state.value.nursing)
        assertEquals("m2", b.appts().single().vaccineKey)
        // B termine la tétée lancée par A
        val e = b.timers.finishNursing()!!
        runBlocking { b.db.events().insert(e) }
        b.syncOk(); a.syncOk()
        assertNull(a.timers.state.value.nursing)
        assertEquals(3, a.events().size)
    }

    @Test
    fun inviteParsing_defaultsToFamilyServer() {
        val code = "ABCD EFGH IJKL MNOP QRST UVWX YZ23 4567"
        assertEquals(SyncConfig.DEFAULT_SERVER to "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567", SyncConfig.parseInvite(code))
        assertEquals("https://autre.fr" to "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567", SyncConfig.parseInvite(code, "autre.fr/"))
        assertEquals("https://rythme.natsukiro.info" to "K".repeat(32), SyncConfig.parseInvite("https://rythme.natsukiro.info/join#k=" + "K".repeat(32)))
    }

    @Test
    fun wrongInvite_isRejected() {
        val t = FakeServer()
        val p = Phone("intrus", t)
        val r = runCatching { runBlocking { p.sync.join("https://test/join#k=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "", "X") } }
        assertEquals("unknown_family", (r.exceptionOrNull() as SyncException).code)
        assertTrue(!p.sync.config.state.value.enabled)
    }

    /**
     * Bout en bout avec le vrai serveur Python. Lancé par scripts/verify.sh, qui démarre
     * le serveur et passe son adresse (sinon le test est ignoré).
     */
    @Test
    fun realServer() {
        val server = System.getProperty("pr.sync.server").orEmpty()
        assumeTrue("serveur de synchro non lancé (scripts/verify.sh le fait)", server.isNotEmpty())
        val (a, b) = pairedPhones(HttpTransport(), server)
        assertEquals(listOf(90.0, 120.0), b.events().map { it.amountMl })
        val x = b.events().first()
        runBlocking { b.db.events().delete(x) }
        b.syncOk(); a.syncOk()
        assertEquals(1, a.events().size)
    }
}
