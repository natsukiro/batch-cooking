package app.petitrythme.ui

import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

object Fmt {
    private val FR = Locale.FRANCE
    val zone: ZoneId get() = ZoneId.systemDefault()
    private val hm = DateTimeFormatter.ofPattern("HH:mm", FR)
    private val dayLong = DateTimeFormatter.ofPattern("EEEE d MMMM", FR)
    private val dayShort = DateTimeFormatter.ofPattern("EEE d MMM", FR)
    private val dayFull = DateTimeFormatter.ofPattern("d MMMM yyyy", FR)

    fun time(ms: Long): String = Instant.ofEpochMilli(ms).atZone(zone).format(hm)

    fun date(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

    fun startOfDay(d: LocalDate): Long = d.atStartOfDay(zone).toInstant().toEpochMilli()

    fun today(): LocalDate = LocalDate.now(zone)

    /** "45 min", "2 h 05", "30 s" */
    fun duration(ms: Long): String {
        val totalMin = ms / 60_000
        return when {
            ms < 60_000 -> "${(ms / 1000).coerceAtLeast(0)} s"
            totalMin < 60 -> "$totalMin min"
            totalMin % 60 == 0L -> "${totalMin / 60} h"
            else -> "${totalMin / 60} h ${(totalMin % 60).toString().padStart(2, '0')}"
        }
    }

    /** Version compacte pour les grosses valeurs : "2h05", "45 min". */
    fun durationCompact(ms: Long): String {
        val totalMin = ms / 60_000
        return when {
            totalMin < 1 -> "< 1 min"
            totalMin < 60 -> "$totalMin min"
            else -> "${totalMin / 60}h${(totalMin % 60).toString().padStart(2, '0')}"
        }
    }

    /** Chronomètre : "12:05" ou "1:02:05". */
    fun chrono(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
    }

    /** "08:05" aujourd'hui, "hier 22:10", sinon "il y a 3 j". */
    fun whenShort(ms: Long, now: Long): String {
        val days = ChronoUnit.DAYS.between(date(ms), date(now))
        return when (days) {
            0L -> time(ms)
            1L -> "hier ${time(ms)}"
            else -> "il y a $days j"
        }
    }

    fun ago(ms: Long, now: Long): String {
        val d = now - ms
        return if (d < 60_000) "à l'instant" else "il y a ${duration(d)}"
    }

    fun dayLabel(d: LocalDate, today: LocalDate = today()): String = when (d) {
        today -> "Aujourd'hui"
        today.minusDays(1) -> "Hier"
        else -> d.format(if (d.year == today.year) dayLong else dayFull).cap()
    }

    fun dayShort(d: LocalDate): String = d.format(dayShort).cap()

    fun dayFull(d: LocalDate): String = d.format(dayFull)

    /** Initiale du jour de la semaine : L M M J V S D */
    fun weekdayInitial(d: LocalDate): String = "LMMJVSD"[d.dayOfWeek.value - 1].toString()

    fun age(birth: LocalDate, today: LocalDate = today()): String {
        val days = ChronoUnit.DAYS.between(birth, today)
        if (days < 0) return "pas encore né·e"
        if (days == 0L) return "né·e aujourd'hui"
        if (days < 14) return "$days jour${if (days > 1) "s" else ""}"
        if (days < 7 * 9) {
            val w = days / 7
            val r = days % 7
            return "$w semaines" + if (r > 0) " et $r j" else ""
        }
        val p = Period.between(birth, today)
        val months = p.years * 12 + p.months
        if (months < 24) {
            val w = p.days / 7
            return "$months mois" + if (w > 0) " et $w sem." else ""
        }
        return "${p.years} ans" + if (p.months > 0) " et ${p.months} mois" else ""
    }

    fun num(d: Double, decimals: Int = 1): String {
        val s = String.format(FR, "%.${decimals}f", d)
        return if (s.contains(',')) s.trimEnd('0').trimEnd(',') else s
    }

    private fun String.cap() = replaceFirstChar { it.titlecase(FR) }
}
