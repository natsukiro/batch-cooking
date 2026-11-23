package app.petitrythme.data

/** Brouillons pré-remplis pour une nouvelle saisie. */
object Drafts {
    private const val MIN = 60_000L

    fun forType(t: EventType, at: Long = System.currentTimeMillis(), lastBottle: Event? = null): Event = when (t) {
        EventType.SLEEP -> Event(type = t, time = at - 60 * MIN, endTime = at)
        EventType.ACTIVITY -> Event(type = t, time = at - 15 * MIN, endTime = at, detail = "Sur le ventre")
        EventType.PUMP -> Event(type = t, time = at - 15 * MIN, endTime = at, sub = "BOTH", amountMl = 80.0)
        EventType.NURSING -> Event(type = t, time = at - 15 * MIN, leftSec = 0, rightSec = 0)
        EventType.TEMPERATURE -> Event(type = t, time = at, value = 37.0)
        EventType.SOLID -> Event(type = t, time = at, sub = Amount.MID.code)
        EventType.DIAPER -> Event(type = t, time = at, sub = DiaperKind.PEE.code)
        EventType.BOTTLE -> bottle(lastBottle, at)
        else -> Event(type = t, time = at)
    }

    /** Biberon pré-rempli avec la quantité et le lait du dernier biberon. */
    fun bottle(last: Event?, at: Long = System.currentTimeMillis()) = Event(
        type = EventType.BOTTLE,
        time = at,
        amountMl = last?.amountMl ?: 90.0,
        sub = last?.sub ?: MilkKind.FORMULA.code,
    )
}
