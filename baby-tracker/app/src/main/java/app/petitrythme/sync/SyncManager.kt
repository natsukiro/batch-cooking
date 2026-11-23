package app.petitrythme.sync

import app.petitrythme.data.AppDatabase
import app.petitrythme.data.Codec
import app.petitrythme.data.ProfileStore
import app.petitrythme.data.Stamp
import app.petitrythme.data.TimerStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Synchronisation entre les téléphones d'une même famille, via le serveur (server/).
 *
 * - Chaque écriture locale est horodatée et marquée « dirty » (voir les DAO).
 * - Une synchro envoie tout ce qui est « dirty », puis applique ce que le serveur
 *   renvoie depuis le dernier curseur. En cas de conflit, le plus récent gagne.
 * - Les suppressions voyagent sous forme de « tombstones ».
 * - Chaque téléphone garde une copie complète : l'appli marche sans réseau.
 */
class SyncManager(
    private val db: AppDatabase,
    private val profile: ProfileStore,
    private val timers: TimerStore,
    val config: SyncConfig,
    private val transport: SyncTransport = HttpTransport(),
) {
    private val mutex = Mutex()

    init {
        Stamp.author = config.state.value.me.ifBlank { null }
    }

    /** Crée l'espace famille sur le serveur et l'active sur ce téléphone. */
    suspend fun create(serverRaw: String, me: String, createToken: String?) {
        val server = SyncConfig.normalizeServer(serverRaw)
        transport.health(server)
        val key = SyncConfig.newKey()
        transport.createFamily(server, key, createToken)
        enable(server, key, me)
    }

    /** Rejoint un espace existant à partir du lien ou du code d'invitation. */
    suspend fun join(invite: String, fallbackServer: String, me: String) {
        val (server, key) = SyncConfig.parseInvite(invite, fallbackServer)
            ?: throw SyncException("bad_invite", "Lien ou code d'invitation incomplet.")
        transport.health(server)
        // Vérifie que l'espace existe avant de l'activer (code mal recopié → message clair).
        transport.sync(server, key, Long.MAX_VALUE, JSONArray())
        enable(server, key, me)
    }

    private suspend fun enable(server: String, key: String, me: String) {
        config.update { SyncState(server = server, key = key, me = me.trim()) }
        Stamp.author = me.trim().ifBlank { null }
        withContext(Dispatchers.IO) {
            // Tout ce qui existe déjà sur ce téléphone est à partager.
            db.events().markAllDirty(me.trim())
            db.appointments().markAllDirty(me.trim())
        }
        profile.markSyncDirtyIfSet()
        timers.markSyncDirtyIfRunning()
        sync()
    }

    fun setMe(me: String) {
        config.update { it.copy(me = me) }
        Stamp.author = me.trim().ifBlank { null }
    }

    /** Arrête la synchro sur ce téléphone (les données locales sont conservées). */
    fun leave() {
        config.update { SyncState(me = it.me) }
    }

    /** Synchronise si c'est activé. Renvoie le nombre de changements reçus, ou une erreur. */
    suspend fun sync(): Result<Int> {
        if (!config.state.value.enabled) return Result.success(0)
        return mutex.withLock {
            config.setRunning(true)
            val r = runCatching { withContext(Dispatchers.IO) { runOnce() } }
            config.update {
                if (r.isSuccess) it.copy(lastSuccessAt = System.currentTimeMillis(), lastError = null)
                else it.copy(lastError = r.exceptionOrNull()?.message ?: "Erreur inconnue")
            }
            config.setRunning(false)
            r
        }
    }

    private suspend fun runOnce(): Int {
        var received = 0
        // Deux passes au plus : si quelque chose a été modifié pendant la synchro, on le renvoie aussitôt.
        repeat(2) {
            val (changes, onPushed) = collectLocalChanges()
            if (it == 1 && changes.length() == 0) return received
            var since = config.state.value.cursor
            var first = true
            do {
                val s = config.state.value
                val resp = transport.sync(s.server, s.key, since, if (first) changes else JSONArray())
                if (first) {
                    onPushed()
                    first = false
                }
                for (i in 0 until resp.changes.length()) {
                    if (apply(resp.changes.getJSONObject(i))) received++
                }
                since = resp.cursor
                config.update { c -> c.copy(cursor = since) }
            } while (resp.more)
        }
        return received
    }

    private fun doc(uid: String, kind: String, updatedAt: Long, data: JSONObject?, deleted: Boolean = false) =
        JSONObject().put("uid", uid).put("kind", kind).put("updatedAt", updatedAt).put("deleted", deleted)
            .put("data", data ?: JSONObject.NULL)

    /** Changements locaux à envoyer, et l'action qui les marque « envoyés ». */
    private suspend fun collectLocalChanges(): Pair<JSONArray, suspend () -> Unit> {
        val events = db.events()
        val appts = db.appointments()
        val out = JSONArray()
        val ev = events.dirty()
        val ap = appts.dirty()
        val tombs = events.dirtyTombstones()
        ev.forEach { out.put(doc(it.uid, "event", it.updatedAt, Codec.event(it))) }
        ap.forEach { out.put(doc(it.uid, "appt", it.updatedAt, Codec.appointment(it))) }
        tombs.forEach { out.put(doc(it.uid, it.kind, it.deletedAt, null, deleted = true)) }
        val profileAt = profile.syncUpdatedAt.takeIf { profile.syncDirty }
        profileAt?.let { out.put(doc("profile", "profile", it, profile.syncData())) }
        val timersAt = timers.syncUpdatedAt.takeIf { timers.syncDirty }
        timersAt?.let { out.put(doc("timers", "timers", it, timers.syncData())) }
        val onPushed: suspend () -> Unit = {
            ev.forEach { events.markClean(it.uid, it.updatedAt) }
            ap.forEach { appts.markClean(it.uid, it.updatedAt) }
            tombs.forEach { events.markTombstoneClean(it.uid, it.deletedAt) }
            profileAt?.let { profile.markSyncClean(it) }
            timersAt?.let { timers.markSyncClean(it) }
        }
        return out to onPushed
    }

    /** Applique un document reçu. Renvoie vrai s'il a changé quelque chose ici. */
    private suspend fun apply(d: JSONObject): Boolean {
        val uid = d.getString("uid")
        val kind = d.getString("kind")
        val updatedAt = d.getLong("updatedAt")
        val deleted = d.optBoolean("deleted")
        val data = d.optJSONObject("data")
        val events = db.events()
        val appts = db.appointments()
        return when (kind) {
            "event" -> {
                val local = events.byUid(uid)
                val tomb = events.tombstone(uid)
                when {
                    local != null && local.updatedAt >= updatedAt -> false
                    deleted -> {
                        if (local != null) events.deleteByUidRaw(uid)
                        local != null
                    }
                    tomb != null && tomb.deletedAt >= updatedAt -> false
                    data == null -> false
                    else -> {
                        events.insertRaw(Codec.event(data, id = local?.id ?: 0).copy(updatedAt = updatedAt, dirty = false))
                        if (tomb != null) events.clearTombstone(uid)
                        true
                    }
                }
            }
            "appt" -> {
                val local = appts.byUid(uid)
                val tomb = events.tombstone(uid)
                when {
                    local != null && local.updatedAt >= updatedAt -> false
                    deleted -> {
                        if (local != null) appts.deleteByUidRaw(uid)
                        local != null
                    }
                    tomb != null && tomb.deletedAt >= updatedAt -> false
                    data == null -> false
                    else -> {
                        appts.insertRaw(Codec.appointment(data, id = local?.id ?: 0).copy(updatedAt = updatedAt, dirty = false))
                        if (tomb != null) events.clearTombstone(uid)
                        true
                    }
                }
            }
            "profile" -> {
                if (data != null && updatedAt != profile.syncUpdatedAt) {
                    profile.applyRemote(data, updatedAt)
                    true
                } else false
            }
            "timers" -> {
                if (data != null && updatedAt != timers.syncUpdatedAt) {
                    timers.applyRemote(data, updatedAt)
                    true
                } else false
            }
            else -> false
        }
    }
}
