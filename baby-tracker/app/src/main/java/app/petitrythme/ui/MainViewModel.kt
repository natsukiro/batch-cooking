package app.petitrythme.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.petitrythme.BabyApp
import app.petitrythme.data.Backup
import app.petitrythme.data.Event
import app.petitrythme.data.EventType
import app.petitrythme.data.Profile
import app.petitrythme.data.Side
import app.petitrythme.data.Timers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val baby = app as BabyApp
    private val dao = baby.db.events()

    val profile: StateFlow<Profile> = baby.profile.state
    val timers: StateFlow<Timers> = baby.timers.state

    private fun <T> Flow<T>.state(initial: T) =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    val lastFeed = dao.observeLatest(EventType.FEEDINGS).state(null)
    val lastNursing = dao.observeLatest(listOf(EventType.NURSING)).state(null)
    val lastBottle = dao.observeLatest(listOf(EventType.BOTTLE)).state(null)
    val lastDiaper = dao.observeLatest(listOf(EventType.DIAPER)).state(null)
    val lastPoo = dao.observeLastPoo().state(null)
    val lastSleep = dao.observeLatest(listOf(EventType.SLEEP)).state(null)
    val growth = dao.observeRecent(EventType.GROWTH, 200).state(emptyList())

    fun eventsBetween(from: Long, to: Long): Flow<List<Event>> = dao.observeBetween(from, to)

    // --- Écriture -----------------------------------------------------------

    suspend fun insert(e: Event): Long = dao.insert(e)

    fun save(e: Event) = viewModelScope.launch {
        if (e.id == 0L) dao.insert(e) else dao.update(e)
    }

    fun delete(e: Event) = viewModelScope.launch { dao.delete(e) }

    fun restore(e: Event) = viewModelScope.launch { dao.insert(e) }

    suspend fun deleteById(id: Long, type: EventType) = dao.delete(Event(id = id, type = type, time = 0))

    fun updateProfile(t: (Profile) -> Profile) = baby.profile.update(t)

    /** Vrai la première fois seulement (pour ne pas redemander une autorisation refusée). */
    fun firstTime(key: String) = baby.profile.once(key)

    // --- Chronomètres -------------------------------------------------------

    fun tapSide(side: Side) = baby.timers.tapSide(side)
    fun cancelNursing() = baby.timers.cancelNursing()
    fun setNursingStart(t: Long) = baby.timers.setNursingStart(t)
    suspend fun finishNursing(): Event? = baby.timers.finishNursing()?.let { it.copy(id = dao.insert(it)) }

    fun startSleep() = baby.timers.startSleep()
    fun cancelSleep() = baby.timers.cancelSleep()
    fun setSleepStart(t: Long) = baby.timers.setSleepStart(t)
    suspend fun finishSleep(): Event? = baby.timers.finishSleep()?.let { it.copy(id = dao.insert(it)) }

    fun startPump() = baby.timers.startPump()
    fun cancelPump() = baby.timers.cancelPump()
    fun setPumpStart(t: Long) = baby.timers.setPumpStart(t)

    // --- Import / export ----------------------------------------------------

    suspend fun exportJson(uri: Uri) = withContext(Dispatchers.IO) {
        val json = Backup.toJson(dao.all(), profile.value)
        write(uri, json)
    }

    suspend fun exportCsv(uri: Uri) = withContext(Dispatchers.IO) {
        write(uri, Backup.toCsv(dao.all(), ::describe))
    }

    /** Remplace toutes les données par celles de la sauvegarde. Renvoie le nombre d'évènements. */
    suspend fun importJson(uri: Uri): Int = withContext(Dispatchers.IO) {
        val text = getApplication<Application>().contentResolver.openInputStream(uri)!!
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
        val r = Backup.fromJson(text)
        dao.replaceAll(r.events)
        baby.profile.update {
            it.copy(name = r.name ?: it.name, birthDate = r.birthDate ?: it.birthDate)
        }
        r.events.size
    }

    suspend fun clearAll() = dao.clear()

    private fun write(uri: Uri, text: String) {
        getApplication<Application>().contentResolver.openOutputStream(uri, "wt")!!
            .bufferedWriter(Charsets.UTF_8).use { it.write(text) }
    }
}
