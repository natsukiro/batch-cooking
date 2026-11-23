package app.petitrythme.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class Category { FEED, DIAPER, SLEEP, CARE, HEALTH, GROWTH, NOTE }

enum class EventType(val label: String, val emoji: String, val category: Category) {
    NURSING("Tétée", "🤱", Category.FEED),
    BOTTLE("Biberon", "🍼", Category.FEED),
    SOLID("Repas solide", "🥣", Category.FEED),
    PUMP("Tire-lait", "🥛", Category.FEED),
    DIAPER("Couche", "🧷", Category.DIAPER),
    SLEEP("Sommeil", "😴", Category.SLEEP),
    BATH("Bain", "🛁", Category.CARE),
    ACTIVITY("Éveil", "🧸", Category.CARE),
    TEMPERATURE("Température", "🌡️", Category.HEALTH),
    MEDICINE("Soin", "💊", Category.HEALTH),
    GROWTH("Croissance", "📏", Category.GROWTH),
    NOTE("Note", "📝", Category.NOTE);

    val isFeeding get() = this == NURSING || this == BOTTLE || this == SOLID

    companion object {
        val FEEDINGS = listOf(NURSING, BOTTLE, SOLID)
    }
}

/**
 * Un évènement du journal. Une seule table, des champs optionnels selon le type :
 *
 * - NURSING : [leftSec], [rightSec], [sub] = dernier sein utilisé ("L"/"R")
 * - BOTTLE : [amountMl], [sub] = "BREAST" | "FORMULA"
 * - SOLID : [detail] = aliment, [sub] = quantité ("LOW"/"MID"/"HIGH")
 * - PUMP : [amountMl], [sub] = "L" | "R" | "BOTH"
 * - DIAPER : [sub] = "PEE" | "POO" | "BOTH", [detail] = couleur, [detail2] = texture
 * - SLEEP / ACTIVITY : [endTime], ACTIVITY : [detail] = type d'activité
 * - TEMPERATURE : [value] en °C
 * - MEDICINE : [detail] = nom, [detail2] = dose
 * - GROWTH : [value] = poids (kg), [heightCm], [headCm]
 */
@Entity(tableName = "events", indices = [Index("time"), Index("type")])
data class Event(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: EventType,
    val time: Long,
    val endTime: Long? = null,
    val sub: String? = null,
    val amountMl: Double? = null,
    val leftSec: Int? = null,
    val rightSec: Int? = null,
    val value: Double? = null,
    val heightCm: Double? = null,
    val headCm: Double? = null,
    val detail: String? = null,
    val detail2: String? = null,
    val note: String? = null,
)

fun Event.durationMs(): Long = when {
    type == EventType.NURSING && (leftSec != null || rightSec != null) ->
        ((leftSec ?: 0) + (rightSec ?: 0)) * 1000L
    endTime != null -> (endTime - time).coerceAtLeast(0)
    else -> 0L
}

/** Fin effective (pour les tétées sans heure de fin, on la déduit des durées). */
fun Event.effectiveEnd(): Long = endTime ?: (time + durationMs())

enum class DiaperKind(val code: String, val label: String, val emoji: String) {
    PEE("PEE", "Pipi", "💧"),
    POO("POO", "Caca", "💩"),
    BOTH("BOTH", "Les deux", "💧💩");

    val hasPoo get() = this != PEE
    val hasPee get() = this != POO

    companion object {
        fun of(code: String?) = entries.firstOrNull { it.code == code }
    }
}

enum class MilkKind(val code: String, val label: String) {
    BREAST("BREAST", "Lait maternel"),
    FORMULA("FORMULA", "Lait infantile");

    companion object {
        fun of(code: String?) = entries.firstOrNull { it.code == code }
    }
}

enum class PooColor(val code: String, val label: String, val argb: Long, val warning: String? = null) {
    YELLOW("YELLOW", "Jaune", 0xFFE8B923),
    MUSTARD("MUSTARD", "Moutarde", 0xFFC29A2A),
    GREEN("GREEN", "Verte", 0xFF6E8B3D),
    BROWN("BROWN", "Marron", 0xFF7B4A26),
    BLACK(
        "BLACK", "Noire", 0xFF2B2321,
        "Normal les premiers jours (méconium). Ensuite, demandez un avis médical."
    ),
    PALE(
        "PALE", "Pâle / blanche", 0xFFEDE6C8,
        "Selles décolorées : à signaler rapidement à un médecin (voir la carte de couleur des selles du carnet de santé)."
    ),
    RED(
        "RED", "Avec du sang", 0xFFB3261E,
        "Présence de sang : demandez un avis médical."
    );

    companion object {
        fun of(code: String?) = entries.firstOrNull { it.code == code }
    }
}

val POO_TEXTURES = listOf("Liquide", "Molle", "Grumeleuse", "Moulée", "Dure")

enum class Amount(val code: String, val label: String) {
    LOW("LOW", "Un peu"),
    MID("MID", "Moyen"),
    HIGH("HIGH", "Beaucoup");

    companion object {
        fun of(code: String?) = entries.firstOrNull { it.code == code }
    }
}

enum class Side(val code: String, val label: String, val short: String) {
    L("L", "Gauche", "G"),
    R("R", "Droit", "D");

    val other get() = if (this == L) R else L

    companion object {
        fun of(code: String?) = entries.firstOrNull { it.code == code }
    }
}
