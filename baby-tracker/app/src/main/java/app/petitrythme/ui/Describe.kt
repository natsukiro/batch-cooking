package app.petitrythme.ui

import app.petitrythme.data.Amount
import app.petitrythme.data.DiaperKind
import app.petitrythme.data.Event
import app.petitrythme.data.EventType
import app.petitrythme.data.MilkKind
import app.petitrythme.data.PooColor
import app.petitrythme.data.Side
import app.petitrythme.data.durationMs
import app.petitrythme.data.effectiveEnd

/** Titre + sous-titre lisibles d'un évènement. */
fun describe(e: Event): Pair<String, String?> {
    val parts = mutableListOf<String>()
    val title = when (e.type) {
        EventType.NURSING -> {
            val l = e.leftSec ?: 0
            val r = e.rightSec ?: 0
            if (l > 0) parts += "G ${Fmt.duration(l * 1000L)}"
            if (r > 0) parts += "D ${Fmt.duration(r * 1000L)}"
            "Tétée · ${Fmt.duration(e.durationMs())}"
        }
        EventType.BOTTLE -> {
            MilkKind.of(e.sub)?.let { parts += it.label }
            "Biberon" + (e.amountMl?.let { " · ${Fmt.num(it, 0)} ml" } ?: "")
        }
        EventType.SOLID -> {
            Amount.of(e.sub)?.let { parts += it.label }
            if (e.detail.isNullOrBlank()) "Repas solide" else "Repas · ${e.detail}"
        }
        EventType.PUMP -> {
            if (e.durationMs() > 0) parts += Fmt.duration(e.durationMs())
            when (e.sub) {
                "L" -> parts += "Gauche"
                "R" -> parts += "Droit"
                "BOTH" -> parts += "Les deux seins"
            }
            "Tire-lait" + (e.amountMl?.let { " · ${Fmt.num(it, 0)} ml" } ?: "")
        }
        EventType.DIAPER -> {
            PooColor.of(e.detail)?.let { parts += it.label }
            e.detail2?.let { parts += it }
            when (DiaperKind.of(e.sub)) {
                DiaperKind.PEE -> "Pipi"
                DiaperKind.POO -> "Caca"
                DiaperKind.BOTH -> "Pipi + caca"
                null -> "Couche"
            }
        }
        EventType.SLEEP -> {
            if (e.endTime != null) parts += "${Fmt.time(e.time)} → ${Fmt.time(e.endTime)}"
            "Sommeil · ${Fmt.duration(e.durationMs())}"
        }
        EventType.BATH -> "Bain"
        EventType.ACTIVITY -> {
            val d = e.durationMs()
            (e.detail ?: "Éveil") + if (d > 0) " · ${Fmt.duration(d)}" else ""
        }
        EventType.TEMPERATURE -> "Température" + (e.value?.let { " · ${Fmt.num(it, 1)} °C" } ?: "")
        EventType.MEDICINE -> {
            e.detail2?.takeIf { it.isNotBlank() }?.let { parts += it }
            e.detail?.takeIf { it.isNotBlank() } ?: "Soin"
        }
        EventType.GROWTH -> {
            e.value?.let { parts += "${Fmt.num(it, 3)} kg" }
            e.heightCm?.let { parts += "${Fmt.num(it, 1)} cm" }
            e.headCm?.let { parts += "PC ${Fmt.num(it, 1)} cm" }
            "Croissance"
        }
        EventType.NOTE -> "Note"
    }
    e.note?.takeIf { it.isNotBlank() }?.let { parts += it }
    return title to parts.joinToString(" · ").ifBlank { null }
}

/** Sein à proposer pour la prochaine tétée. */
fun nextSide(lastNursing: Event?): Side? = Side.of(lastNursing?.sub)?.other

data class DaySummary(
    val feeds: Int = 0,
    val nursingCount: Int = 0,
    val nursingMs: Long = 0,
    val bottleCount: Int = 0,
    val bottleMl: Double = 0.0,
    val solids: Int = 0,
    val pee: Int = 0,
    val poo: Int = 0,
    val sleepMs: Long = 0,
    val longestSleepMs: Long = 0,
    val pumpMl: Double = 0.0,
)

/** Totaux d'une journée [from, to[ ; le sommeil est découpé à minuit. */
fun summarize(events: List<Event>, from: Long, to: Long): DaySummary {
    var s = DaySummary()
    for (e in events) {
        val inDay = e.time in from until to
        when (e.type) {
            EventType.NURSING -> if (inDay) s = s.copy(
                feeds = s.feeds + 1, nursingCount = s.nursingCount + 1,
                nursingMs = s.nursingMs + e.durationMs(),
            )
            EventType.BOTTLE -> if (inDay) s = s.copy(
                feeds = s.feeds + 1, bottleCount = s.bottleCount + 1,
                bottleMl = s.bottleMl + (e.amountMl ?: 0.0),
            )
            EventType.SOLID -> if (inDay) s = s.copy(solids = s.solids + 1)
            EventType.PUMP -> if (inDay) s = s.copy(pumpMl = s.pumpMl + (e.amountMl ?: 0.0))
            EventType.DIAPER -> if (inDay) {
                val k = DiaperKind.of(e.sub)
                s = s.copy(
                    pee = s.pee + if (k?.hasPee == true) 1 else 0,
                    poo = s.poo + if (k?.hasPoo == true) 1 else 0,
                )
            }
            EventType.SLEEP -> {
                val a = maxOf(e.time, from)
                val b = minOf(e.effectiveEnd(), to)
                if (b > a) s = s.copy(
                    sleepMs = s.sleepMs + (b - a),
                    longestSleepMs = maxOf(s.longestSleepMs, e.durationMs()),
                )
            }
            else -> Unit
        }
    }
    return s
}

/** Un jour « suivi » : au moins une saisie de repas, couche ou sommeil. */
fun DaySummary.hasData() = feeds > 0 || solids > 0 || pee > 0 || poo > 0 || sleepMs > 0

/**
 * Intervalle moyen entre deux repas, mesuré début à début.
 * Les repas à moins de 30 min d'écart comptent comme un seul (tétée + complément),
 * et les écarts de plus de 8 h sont ignorés (oubli de saisie probable).
 */
fun meanFeedInterval(events: List<Event>, from: Long, to: Long): Long? {
    val times = events.filter { it.type.isFeeding && it.time in from until to }.map { it.time }.sorted()
    val meals = mutableListOf<Long>()
    for (t in times) if (meals.isEmpty() || t - meals.last() > 30 * 60_000L) meals += t
    val gaps = meals.zipWithNext { a, b -> b - a }.filter { it <= 8 * 3_600_000L }
    return if (gaps.isEmpty()) null else gaps.average().toLong()
}
