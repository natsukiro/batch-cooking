package app.petitrythme

import androidx.test.core.app.ApplicationProvider
import app.petitrythme.data.Appointment
import app.petitrythme.data.Backup
import app.petitrythme.data.questionList
import app.petitrythme.notify.ApptReminder
import app.petitrythme.data.Event
import app.petitrythme.data.EventType
import app.petitrythme.data.Profile
import app.petitrythme.data.Side
import app.petitrythme.data.TimerStore
import app.petitrythme.notify.FeedReminder
import app.petitrythme.ui.DaySummary
import app.petitrythme.ui.Fmt
import app.petitrythme.ui.hasData
import app.petitrythme.ui.meanFeedInterval
import app.petitrythme.ui.describe
import app.petitrythme.ui.nextSide
import app.petitrythme.ui.summarize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LogicTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun nursingTimer_switchPauseAndFinish() {
        val store = TimerStore(ctx) {}
        store.cancelNursing()
        val t0 = 1_000_000L
        store.tapSide(Side.L, t0)                 // gauche 5 min
        store.tapSide(Side.R, t0 + 300_000)       // droite 3 min
        store.tapSide(Side.R, t0 + 480_000)       // pause 2 min
        store.tapSide(Side.R, t0 + 600_000)       // reprise droite 1 min
        val e = store.finishNursing(t0 + 660_000)!!
        assertEquals(300, e.leftSec)
        assertEquals(240, e.rightSec)
        assertEquals("R", e.sub)
        assertEquals(t0, e.time)
        assertNull(store.state.value.nursing)
        assertEquals(Side.L, nextSide(e))
    }

    @Test
    fun timers_survive_restart() {
        val a = TimerStore(ctx) {}
        a.startSleep(42L)
        val b = TimerStore(ctx) {}
        assertEquals(42L, b.state.value.sleepStart)
        b.cancelSleep()
    }

    @Test
    fun summary_clipsSleepAtMidnight() {
        val day = LocalDate.of(2026, 11, 20)
        val from = Fmt.startOfDay(day)
        val to = Fmt.startOfDay(day.plusDays(1))
        val h = 3_600_000L
        val events = listOf(
            Event(type = EventType.SLEEP, time = from - 2 * h, endTime = from + 3 * h), // 3 h comptées
            Event(type = EventType.SLEEP, time = from + 13 * h, endTime = from + 14 * h),
            Event(type = EventType.BOTTLE, time = from + 8 * h, amountMl = 120.0),
            Event(type = EventType.NURSING, time = from + 11 * h, leftSec = 600, rightSec = 300),
            Event(type = EventType.DIAPER, time = from + 9 * h, sub = "BOTH"),
            Event(type = EventType.DIAPER, time = from + 10 * h, sub = "PEE"),
        )
        val s = summarize(events, from, to)
        assertEquals(4 * h, s.sleepMs)
        assertEquals(2, s.feeds)
        assertEquals(120.0, s.bottleMl, 0.0)
        assertEquals(900_000L, s.nursingMs)
        assertEquals(2, s.pee)
        assertEquals(1, s.poo)
    }

    @Test
    fun backup_roundTrip() {
        val events = listOf(
            Event(id = 3, type = EventType.DIAPER, time = 10, sub = "POO", detail = "PALE", detail2 = "Molle", note = "« test »"),
            Event(id = 4, type = EventType.GROWTH, time = 20, value = 4.25, heightCm = 55.5),
        )
        val json = Backup.toJson(events, Profile(name = "Léa", birthDate = LocalDate.of(2026, 10, 1)))
        val r = Backup.fromJson(json)
        assertEquals(events, r.events)
        assertEquals("Léa", r.name)
        assertEquals(LocalDate.of(2026, 10, 1), r.birthDate)
        val csv = Backup.toCsv(events, ::describe)
        assertTrue(csv.contains("Caca"))
        assertTrue(csv.contains("4,25"))
    }

    @Test
    fun nursingStart_canBeCorrected() {
        val store = TimerStore(ctx) {}
        store.cancelNursing()
        val t0 = 10_000_000L
        store.tapSide(Side.L, t0)
        // Le chrono a été lancé 5 min en retard : on recule le début.
        store.setNursingStart(t0 - 300_000, now = t0 + 60_000)
        val n = store.state.value.nursing!!
        assertEquals(t0 - 300_000, n.startedAt)
        assertEquals(360_000L, n.leftAt(t0 + 60_000))
        // En pause, l'écart est reporté sur le dernier sein utilisé.
        store.tapSide(Side.L, t0 + 60_000)
        store.setNursingStart(t0 - 600_000, now = t0 + 120_000)
        assertEquals(660_000L, store.state.value.nursing!!.leftAt(t0 + 120_000))
        // Une heure de début dans le futur est ramenée à maintenant.
        store.cancelSleep(); store.startSleep(t0)
        store.setSleepStart(t0 + 999_999_999, now = t0 + 1000)
        assertEquals(t0 + 1000, store.state.value.sleepStart)
        store.cancelNursing(); store.cancelSleep()
    }

    @Test
    fun feedInterval_mergesTopUpsAndIgnoresGaps() {
        val h = 3_600_000L
        val m = 60_000L
        val feeds = listOf(0L, 3 * h, 3 * h + 10 * m, 6 * h, 20 * h).map {
            Event(type = EventType.NURSING, time = it, leftSec = 600)
        }
        // repas à 0 h, 3 h (+ complément à 3 h 10, fusionné), 6 h ; l'écart de 14 h est ignoré
        assertEquals(3 * h, meanFeedInterval(feeds, 0, 24 * h))
        assertNull(meanFeedInterval(feeds.take(1), 0, 24 * h))
    }

    @Test
    fun averages_skipEmptyDays() {
        assertFalse(DaySummary().hasData())
        assertTrue(DaySummary(pee = 1).hasData())
    }

    @Test
    fun feedReminder_schedule() {
        val h = 3_600_000L
        assertEquals(4 * h, FeedReminder.nextAt(h, 180, nursingRunning = false, now = 2 * h))
        assertNull(FeedReminder.nextAt(h, 0, false, 2 * h))          // désactivé
        assertNull(FeedReminder.nextAt(h, 180, true, 2 * h))         // tétée en cours
        assertNull(FeedReminder.nextAt(h, 180, false, 5 * h))        // déjà passé
        assertNull(FeedReminder.nextAt(null, 180, false, 2 * h))     // aucun repas
    }

    @Test
    fun launcherShortcuts_areInstalled() {
        val labels = androidx.core.content.pm.ShortcutManagerCompat.getDynamicShortcuts(ctx).sortedBy { it.rank }.map { it.shortLabel.toString() }
        assertEquals(listOf("Pipi", "Caca", "Biberon", "Tétée"), labels)
    }

    @Test
    fun apptReminders() {
        val day = LocalDate.of(2026, 12, 2)
        val at = day.atTime(10, 30).atZone(Fmt.zone).toInstant().toEpochMilli()
        val eve = day.minusDays(1).atTime(19, 0).atZone(Fmt.zone).toInstant().toEpochMilli()
        val a = Appointment(id = 1, time = at, remindEve = true, remindHour = true)
        assertEquals(listOf(eve, at - 3_600_000L), ApptReminder.reminderTimes(a))
        assertEquals(eve, ApptReminder.next(listOf(a), now = eve - 1000))
        assertEquals(at - 3_600_000L, ApptReminder.next(listOf(a), now = eve + 1000))
        assertNull(ApptReminder.next(listOf(a.copy(remindEve = false, remindHour = false)), now = 0))
        // La veille à 19 h : dû entre 18 h 59 et 19 h 01, pas plus tard pour un RDV passé.
        assertEquals(listOf(a), ApptReminder.due(listOf(a), eve - 60_000, eve + 60_000))
        assertEquals(emptyList<Appointment>(), ApptReminder.due(listOf(a), at, at + 60_000))
        val now = day.atTime(8, 0).atZone(Fmt.zone).toInstant().toEpochMilli()
        assertEquals("aujourd'hui à 10:30", Fmt.apptWhen(at, now))
        assertEquals("demain à 10:30", Fmt.apptWhen(at, now - 86_400_000L))
        assertEquals("dans 5 j", Fmt.countdown(at, now - 5 * 86_400_000L))
        assertEquals(listOf("Vitamine D ?", "Régurgitations"), a.copy(questions = "- Vitamine D ?\n\n• Régurgitations ").questionList())
    }

    @Test
    fun backup_withAppointments() {
        val appts = listOf(Appointment(id = 7, time = 99, kind = "VACCIN", who = "PMI", questions = "Q1\nQ2", remindHour = true))
        val r = Backup.fromJson(Backup.toJson(emptyList(), Profile(), appts))
        assertEquals(appts, r.appointments)
        // Une sauvegarde d'avant les rendez-vous ne doit pas effacer ceux du téléphone.
        val old = org.json.JSONObject(Backup.toJson(emptyList(), Profile())).apply { remove("appointments") }.toString()
        assertNull(Backup.fromJson(old).appointments)
    }

    @Test
    fun vaccineSuggestions() {
        val birth = LocalDate.of(2026, 10, 16)
        val steps = app.petitrythme.data.VaccineSchedule
        // À 5 semaines : 2 mois puis 3 mois.
        assertEquals(listOf("m2", "m3"), steps.suggestions(birth, birth.plusDays(38), emptySet()).map { it.key })
        // Une étape planifiée disparaît des suggestions.
        assertEquals(listOf("m3", "m4"), steps.suggestions(birth, birth.plusDays(38), setOf("m2")).map { it.key })
        // À 7 mois, les étapes en retard de plus de 3 semaines ne sont plus proposées.
        assertEquals(listOf("m11", "m12"), steps.suggestions(birth, birth.plusMonths(7), emptySet()).map { it.key })
        assertEquals(LocalDate.of(2026, 12, 16), steps.step("m2")!!.due(birth))
        assertTrue(steps.steps.flatMap { it.vaccines }.filter { !it.mandatory }.all { it.name == "Rotavirus" })
    }

    @Test
    fun formatting() {
        assertEquals("45 min", Fmt.duration(45 * 60_000L))
        assertEquals("2 h 05", Fmt.duration(125 * 60_000L))
        assertEquals("2h05", Fmt.durationCompact(125 * 60_000L))
        assertEquals("1:02:05", Fmt.chrono(3_725_000L))
        val birth = LocalDate.of(2026, 10, 1)
        assertEquals("5 jours", Fmt.age(birth, birth.plusDays(5)))
        assertEquals("3 semaines et 2 j", Fmt.age(birth, birth.plusDays(23)))
        assertEquals("4 mois", Fmt.age(birth, birth.plusMonths(4)))
        assertEquals("37,5", Fmt.num(37.5))
        assertEquals("4,25", Fmt.num(4.25, 3))
        val now = Fmt.startOfDay(LocalDate.of(2026, 11, 20)) + 15 * 3_600_000L
        assertEquals("08:05", Fmt.whenShort(now - (6 * 60 + 55) * 60_000L, now))
        assertEquals("hier 22:10", Fmt.whenShort(now - (16 * 60 + 50) * 60_000L, now))
        assertEquals("il y a 3 j", Fmt.whenShort(now - 3 * 86_400_000L, now))
    }
}
