package app.petitrythme.data

import org.json.JSONObject

/**
 * Conversion JSON des enregistrements, partagée par la sauvegarde (fichier) et la
 * synchronisation (serveur). Les champs locaux (id, dirty) ne sont jamais exportés.
 */
object Codec {
    fun event(e: Event): JSONObject = JSONObject().apply {
        put("uid", e.uid)
        put("type", e.type.name)
        put("time", e.time)
        e.endTime?.let { put("endTime", it) }
        e.sub?.let { put("sub", it) }
        e.amountMl?.let { put("amountMl", it) }
        e.leftSec?.let { put("leftSec", it) }
        e.rightSec?.let { put("rightSec", it) }
        e.value?.let { put("value", it) }
        e.heightCm?.let { put("heightCm", it) }
        e.headCm?.let { put("headCm", it) }
        e.detail?.let { put("detail", it) }
        e.detail2?.let { put("detail2", it) }
        e.note?.let { put("note", it) }
        e.author?.let { put("author", it) }
        put("updatedAt", e.updatedAt)
    }

    fun event(o: JSONObject, id: Long = 0): Event = Event(
        id = id,
        type = EventType.entries.firstOrNull { it.name == o.getString("type") } ?: EventType.NOTE,
        time = o.getLong("time"),
        endTime = o.optLongOrNull("endTime"),
        sub = o.optStringOrNull("sub"),
        amountMl = o.optDoubleOrNull("amountMl"),
        leftSec = o.optIntOrNull("leftSec"),
        rightSec = o.optIntOrNull("rightSec"),
        value = o.optDoubleOrNull("value"),
        heightCm = o.optDoubleOrNull("heightCm"),
        headCm = o.optDoubleOrNull("headCm"),
        detail = o.optStringOrNull("detail"),
        detail2 = o.optStringOrNull("detail2"),
        note = o.optStringOrNull("note"),
        uid = o.optStringOrNull("uid") ?: newUid(),
        updatedAt = o.optLong("updatedAt", 0),
        author = o.optStringOrNull("author"),
    )

    fun appointment(a: Appointment): JSONObject = JSONObject().apply {
        put("uid", a.uid)
        put("time", a.time)
        put("kind", a.kind)
        a.who?.let { put("who", it) }
        a.place?.let { put("place", it) }
        a.questions?.let { put("questions", it) }
        a.notes?.let { put("notes", it) }
        put("remindEve", a.remindEve)
        put("remindHour", a.remindHour)
        a.vaccineKey?.let { put("vaccineKey", it) }
        a.author?.let { put("author", it) }
        put("updatedAt", a.updatedAt)
    }

    fun appointment(o: JSONObject, id: Long = 0): Appointment = Appointment(
        id = id,
        time = o.getLong("time"),
        kind = o.optString("kind", ApptKind.AUTRE.code),
        who = o.optStringOrNull("who"),
        place = o.optStringOrNull("place"),
        questions = o.optStringOrNull("questions"),
        notes = o.optStringOrNull("notes"),
        remindEve = o.optBoolean("remindEve", true),
        remindHour = o.optBoolean("remindHour", false),
        vaccineKey = o.optStringOrNull("vaccineKey"),
        uid = o.optStringOrNull("uid") ?: newUid(),
        updatedAt = o.optLong("updatedAt", 0),
        author = o.optStringOrNull("author"),
    )

    private fun JSONObject.optStringOrNull(k: String) = if (has(k) && !isNull(k)) getString(k) else null
    private fun JSONObject.optLongOrNull(k: String) = if (has(k) && !isNull(k)) getLong(k) else null
    private fun JSONObject.optIntOrNull(k: String) = if (has(k) && !isNull(k)) getInt(k) else null
    private fun JSONObject.optDoubleOrNull(k: String) = if (has(k) && !isNull(k)) getDouble(k) else null
}
