package app.petitrythme.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Sauvegarde complète (JSON) et export tableur (CSV). */
object Backup {
    private const val FORMAT = 2

    fun toJson(events: List<Event>, profile: Profile, appointments: List<Appointment> = emptyList()): String {
        val root = JSONObject()
        root.put("format", FORMAT)
        root.put("app", "Petit Rythme")
        root.put("exportedAt", System.currentTimeMillis())
        root.put("profile", JSONObject().apply {
            put("name", profile.name)
            profile.birthDate?.let { put("birthDate", it.toString()) }
        })
        val arr = JSONArray()
        for (e in events) {
            arr.put(JSONObject().apply {
                put("id", e.id)
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
            })
        }
        root.put("events", arr)
        root.put("appointments", JSONArray().apply {
            for (a in appointments) put(JSONObject().apply {
                put("id", a.id)
                put("time", a.time)
                put("kind", a.kind)
                a.who?.let { put("who", it) }
                a.place?.let { put("place", it) }
                a.questions?.let { put("questions", it) }
                a.notes?.let { put("notes", it) }
                put("remindEve", a.remindEve)
                put("remindHour", a.remindHour)
            })
        })
        return root.toString(1)
    }

    /** [appointments] vaut null pour une sauvegarde d'avant les rendez-vous (on ne touche alors pas à ceux du téléphone). */
    data class Restored(
        val events: List<Event>,
        val name: String?,
        val birthDate: LocalDate?,
        val appointments: List<Appointment>? = null,
    )

    fun fromJson(text: String): Restored {
        val root = JSONObject(text)
        val arr = root.getJSONArray("events")
        val events = (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            fun optD(k: String) = if (o.has(k)) o.getDouble(k) else null
            fun optI(k: String) = if (o.has(k)) o.getInt(k) else null
            fun optL(k: String) = if (o.has(k)) o.getLong(k) else null
            fun optS(k: String) = if (o.has(k)) o.getString(k) else null
            Event(
                id = o.optLong("id", 0),
                type = EventType.entries.firstOrNull { it.name == o.getString("type") } ?: EventType.NOTE,
                time = o.getLong("time"),
                endTime = optL("endTime"),
                sub = optS("sub"),
                amountMl = optD("amountMl"),
                leftSec = optI("leftSec"),
                rightSec = optI("rightSec"),
                value = optD("value"),
                heightCm = optD("heightCm"),
                headCm = optD("headCm"),
                detail = optS("detail"),
                detail2 = optS("detail2"),
                note = optS("note"),
            )
        }
        val p = root.optJSONObject("profile")
        val appts = root.optJSONArray("appointments")?.let { a ->
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                fun optS(k: String) = if (o.has(k)) o.getString(k) else null
                Appointment(
                    id = o.optLong("id", 0),
                    time = o.getLong("time"),
                    kind = o.optString("kind", ApptKind.AUTRE.code),
                    who = optS("who"),
                    place = optS("place"),
                    questions = optS("questions"),
                    notes = optS("notes"),
                    remindEve = o.optBoolean("remindEve", true),
                    remindHour = o.optBoolean("remindHour", false),
                )
            }
        }
        return Restored(
            events = events,
            name = p?.optString("name")?.takeIf { it.isNotBlank() },
            birthDate = p?.optString("birthDate")?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            appointments = appts,
        )
    }

    fun toCsv(events: List<Event>, describe: (Event) -> Pair<String, String?>): String {
        val zone = ZoneId.systemDefault()
        val date = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val time = DateTimeFormatter.ofPattern("HH:mm")
        fun cell(s: String?) = "\"" + (s ?: "").replace("\"", "\"\"") + "\""
        fun num(d: Double?) = d?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString().replace('.', ',') } ?: ""
        val sb = StringBuilder()
        sb.append('﻿') // BOM pour qu'Excel lise les accents
        sb.append("Date;Heure;Fin;Type;Résumé;Détails;Durée (min);Quantité (ml);Gauche (min);Droit (min);Valeur;Taille (cm);PC (cm);Note\n")
        for (e in events) {
            val start = Instant.ofEpochMilli(e.time).atZone(zone)
            val (title, subtitle) = describe(e)
            val dur = e.durationMs().takeIf { it > 0 }?.let { (it / 60000.0) }
            sb.append(listOf(
                cell(start.format(date)),
                cell(start.format(time)),
                cell(e.endTime?.let { Instant.ofEpochMilli(it).atZone(zone).format(time) }),
                cell(e.type.label),
                cell(title),
                cell(subtitle),
                num(dur?.let { Math.round(it * 10) / 10.0 }),
                num(e.amountMl),
                num(e.leftSec?.let { Math.round(it / 6.0) / 10.0 }),
                num(e.rightSec?.let { Math.round(it / 6.0) / 10.0 }),
                num(e.value),
                num(e.heightCm),
                num(e.headCm),
                cell(e.note),
            ).joinToString(";"))
            sb.append('\n')
        }
        return sb.toString()
    }
}
