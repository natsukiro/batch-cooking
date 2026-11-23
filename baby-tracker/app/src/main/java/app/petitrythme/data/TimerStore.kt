package app.petitrythme.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import kotlinx.coroutines.flow.StateFlow

/** Chronomètre de tétée : cumule le temps par sein, avec pause et changement de côté. */
data class NursingTimer(
    val startedAt: Long,
    /** Côté en cours, ou null si en pause. */
    val side: Side?,
    val segmentStart: Long,
    val leftMs: Long,
    val rightMs: Long,
    val lastSide: Side,
) {
    val paused get() = side == null
    fun leftAt(now: Long) = leftMs + if (side == Side.L) (now - segmentStart).coerceAtLeast(0) else 0
    fun rightAt(now: Long) = rightMs + if (side == Side.R) (now - segmentStart).coerceAtLeast(0) else 0
    fun totalAt(now: Long) = leftAt(now) + rightAt(now)
}

data class Timers(
    val nursing: NursingTimer? = null,
    val sleepStart: Long? = null,
    val pumpStart: Long? = null,
) {
    val any get() = nursing != null || sleepStart != null || pumpStart != null
}

/**
 * Les chronomètres sont persistés : ils survivent à la fermeture de l'appli
 * ou au redémarrage du téléphone (on ne stocke que des horodatages).
 */
class TimerStore(context: Context, prefsName: String = "timers", private val onChange: (Timers) -> Unit) {
    private val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<Timers> = _state

    init {
        onChange(_state.value)
    }

    private fun load(): Timers {
        val nursing = if (prefs.contains("n_start")) NursingTimer(
            startedAt = prefs.getLong("n_start", 0),
            side = Side.of(prefs.getString("n_side", null)),
            segmentStart = prefs.getLong("n_seg", 0),
            leftMs = prefs.getLong("n_left", 0),
            rightMs = prefs.getLong("n_right", 0),
            lastSide = Side.of(prefs.getString("n_last", null)) ?: Side.L,
        ) else null
        return Timers(
            nursing = nursing,
            sleepStart = if (prefs.contains("s_start")) prefs.getLong("s_start", 0) else null,
            pumpStart = if (prefs.contains("p_start")) prefs.getLong("p_start", 0) else null,
        )
    }

    private fun set(t: Timers, remoteUpdatedAt: Long? = null) {
        val e = prefs.edit().clear()
        // Synchro : un changement local est horodaté et marqué « à envoyer » ;
        // un changement reçu garde l'horodatage de l'autre téléphone.
        e.putLong("sync_updatedAt", remoteUpdatedAt ?: System.currentTimeMillis())
        e.putBoolean("sync_dirty", remoteUpdatedAt == null)
        t.nursing?.let {
            e.putLong("n_start", it.startedAt)
                .putString("n_side", it.side?.code)
                .putLong("n_seg", it.segmentStart)
                .putLong("n_left", it.leftMs)
                .putLong("n_right", it.rightMs)
                .putString("n_last", it.lastSide.code)
        }
        t.sleepStart?.let { e.putLong("s_start", it) }
        t.pumpStart?.let { e.putLong("p_start", it) }
        e.apply()
        _state.value = t
        onChange(t)
    }

    /**
     * Un appui sur un sein : démarre, change de côté, met en pause (même côté)
     * ou reprend (si en pause).
     */
    fun tapSide(side: Side, now: Long = System.currentTimeMillis()) {
        val t = _state.value
        val n = t.nursing
        val next = when {
            n == null -> NursingTimer(now, side, now, 0, 0, side)
            n.side == side -> n.copy(side = null, leftMs = n.leftAt(now), rightMs = n.rightAt(now))
            else -> n.copy(
                side = side, segmentStart = now, lastSide = side,
                leftMs = n.leftAt(now), rightMs = n.rightAt(now),
            )
        }
        set(t.copy(nursing = next))
    }

    fun pauseNursing(now: Long = System.currentTimeMillis()) {
        val n = _state.value.nursing ?: return
        if (n.paused) return
        set(_state.value.copy(nursing = n.copy(side = null, leftMs = n.leftAt(now), rightMs = n.rightAt(now))))
    }

    /** Arrête la tétée et renvoie l'évènement à enregistrer (ou null si moins d'une seconde). */
    fun finishNursing(now: Long = System.currentTimeMillis()): Event? {
        val n = _state.value.nursing ?: return null
        set(_state.value.copy(nursing = null))
        val l = (n.leftAt(now) / 1000).toInt()
        val r = (n.rightAt(now) / 1000).toInt()
        if (l + r < 1) return null
        return Event(
            type = EventType.NURSING,
            time = n.startedAt,
            endTime = now,
            leftSec = l,
            rightSec = r,
            sub = n.lastSide.code,
        )
    }

    fun cancelNursing() = set(_state.value.copy(nursing = null))

    /**
     * Corrige l'heure de début (chrono lancé en retard ou trop tôt). L'écart est
     * reporté sur le sein en cours (ou le dernier utilisé si en pause).
     */
    fun setNursingStart(start: Long, now: Long = System.currentTimeMillis()) {
        val n = _state.value.nursing ?: return
        val s = start.coerceAtMost(now)
        val delta = n.startedAt - s
        val side = n.side ?: n.lastSide
        // Si le côté actif est en cours, on décale le début du segment ; sinon on ajuste le cumul.
        val next = if (n.side != null) {
            n.copy(startedAt = s, segmentStart = (n.segmentStart - delta).coerceAtMost(now))
        } else if (side == Side.L) {
            n.copy(startedAt = s, leftMs = (n.leftMs + delta).coerceAtLeast(0))
        } else {
            n.copy(startedAt = s, rightMs = (n.rightMs + delta).coerceAtLeast(0))
        }
        set(_state.value.copy(nursing = next))
    }

    fun setSleepStart(start: Long, now: Long = System.currentTimeMillis()) {
        if (_state.value.sleepStart == null) return
        set(_state.value.copy(sleepStart = start.coerceAtMost(now)))
    }

    fun setPumpStart(start: Long, now: Long = System.currentTimeMillis()) {
        if (_state.value.pumpStart == null) return
        set(_state.value.copy(pumpStart = start.coerceAtMost(now)))
    }

    fun startSleep(now: Long = System.currentTimeMillis()) = set(_state.value.copy(sleepStart = now))

    fun finishSleep(now: Long = System.currentTimeMillis()): Event? {
        val s = _state.value.sleepStart ?: return null
        set(_state.value.copy(sleepStart = null))
        return Event(type = EventType.SLEEP, time = s, endTime = now)
    }

    fun cancelSleep() = set(_state.value.copy(sleepStart = null))

    fun startPump(now: Long = System.currentTimeMillis()) = set(_state.value.copy(pumpStart = now))

    fun cancelPump() = set(_state.value.copy(pumpStart = null))

    // --- Synchronisation ------------------------------------------------------
    // Les chronos en cours sont partagés : si l'un lance une tétée ou un dodo,
    // l'autre le voit (et peut l'arrêter).

    val syncUpdatedAt get() = prefs.getLong("sync_updatedAt", 0)
    val syncDirty get() = prefs.getBoolean("sync_dirty", false)

    fun syncData(): JSONObject = JSONObject().apply {
        val t = _state.value
        t.nursing?.let {
            put("nursing", JSONObject().apply {
                put("startedAt", it.startedAt)
                it.side?.let { s -> put("side", s.code) }
                put("segmentStart", it.segmentStart)
                put("leftMs", it.leftMs)
                put("rightMs", it.rightMs)
                put("lastSide", it.lastSide.code)
            })
        }
        t.sleepStart?.let { put("sleepStart", it) }
        t.pumpStart?.let { put("pumpStart", it) }
    }

    fun markSyncClean(updatedAt: Long) {
        if (syncUpdatedAt == updatedAt) prefs.edit().putBoolean("sync_dirty", false).apply()
    }

    /** À l'activation : on ne partage nos chronos que s'il y en a un en cours. */
    fun markSyncDirtyIfRunning() {
        if (_state.value.any) prefs.edit().putBoolean("sync_dirty", true).apply()
    }

    fun applyRemote(data: JSONObject, updatedAt: Long) {
        if (syncDirty && syncUpdatedAt > updatedAt) return
        if (!syncDirty && syncUpdatedAt >= updatedAt) return
        val n = data.optJSONObject("nursing")?.let {
            NursingTimer(
                startedAt = it.getLong("startedAt"),
                side = Side.of(it.optString("side", "")),
                segmentStart = it.getLong("segmentStart"),
                leftMs = it.getLong("leftMs"),
                rightMs = it.getLong("rightMs"),
                lastSide = Side.of(it.optString("lastSide", "")) ?: Side.L,
            )
        }
        set(
            Timers(
                nursing = n,
                sleepStart = if (data.has("sleepStart")) data.getLong("sleepStart") else null,
                pumpStart = if (data.has("pumpStart")) data.getLong("pumpStart") else null,
            ),
            remoteUpdatedAt = updatedAt,
        )
    }
}
