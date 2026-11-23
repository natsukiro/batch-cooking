package app.petitrythme

import androidx.test.core.app.ApplicationProvider
import app.petitrythme.data.Backup
import app.petitrythme.data.Event
import app.petitrythme.data.EventType
import app.petitrythme.data.Profile
import app.petitrythme.data.Side
import app.petitrythme.data.TimerStore
import app.petitrythme.ui.Fmt
import app.petitrythme.ui.describe
import app.petitrythme.ui.nextSide
import app.petitrythme.ui.summarize
import org.junit.Assert.assertEquals
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
    }
}
