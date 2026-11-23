package app.petitrythme.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.time.LocalDate

enum class ThemeMode(val label: String) { SYSTEM("Auto"), LIGHT("Clair"), DARK("Sombre") }

data class Profile(
    val name: String = "",
    val birthDate: LocalDate? = null,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    /** Rappel « prochain repas » en minutes après le début du dernier repas ; 0 = désactivé. */
    val feedReminderMin: Int = 0,
)

class ProfileStore(context: Context, prefsName: String = "profile", private val onChange: (Profile) -> Unit = {}) {
    private val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<Profile> = _state

    private fun load() = Profile(
        name = prefs.getString("name", "") ?: "",
        birthDate = prefs.getString("birth", null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        theme = ThemeMode.entries.getOrElse(prefs.getInt("theme", 0)) { ThemeMode.SYSTEM },
        feedReminderMin = prefs.getInt("feedReminderMin", 0),
    )

    fun update(transform: (Profile) -> Profile) {
        val old = _state.value
        val p = transform(old)
        val e = prefs.edit()
            .putString("name", p.name)
            .putString("birth", p.birthDate?.toString())
            .putInt("theme", p.theme.ordinal)
            .putInt("feedReminderMin", p.feedReminderMin)
        // Seuls le prénom et la date de naissance sont partagés avec l'autre parent
        // (thème et rappels restent propres à chaque téléphone).
        if (p.name != old.name || p.birthDate != old.birthDate) {
            e.putLong("sync_updatedAt", System.currentTimeMillis()).putBoolean("sync_dirty", true)
        }
        e.apply()
        _state.value = p
        onChange(p)
    }

    // --- Synchronisation ------------------------------------------------------

    val syncUpdatedAt get() = prefs.getLong("sync_updatedAt", 0)
    val syncDirty get() = prefs.getBoolean("sync_dirty", false)

    fun syncData(): JSONObject = JSONObject().apply {
        put("name", _state.value.name)
        _state.value.birthDate?.let { put("birthDate", it.toString()) }
    }

    fun markSyncClean(updatedAt: Long) {
        if (syncUpdatedAt == updatedAt) prefs.edit().putBoolean("sync_dirty", false).apply()
    }

    /** À l'activation de la synchro : on partage notre profil seulement s'il est renseigné. */
    fun markSyncDirtyIfSet() {
        if (_state.value.name.isNotBlank() || _state.value.birthDate != null) {
            prefs.edit().putBoolean("sync_dirty", true).apply()
        }
    }

    /** Applique le profil reçu de l'autre téléphone (le plus récent gagne). */
    fun applyRemote(data: JSONObject, updatedAt: Long) {
        if (syncDirty && syncUpdatedAt > updatedAt) return
        if (!syncDirty && syncUpdatedAt >= updatedAt && updatedAt != 0L) return
        val p = _state.value.copy(
            name = data.optString("name", ""),
            birthDate = data.optString("birthDate", "").takeIf { it.isNotEmpty() }
                ?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        )
        prefs.edit()
            .putString("name", p.name)
            .putString("birth", p.birthDate?.toString())
            .putLong("sync_updatedAt", updatedAt)
            .putBoolean("sync_dirty", false)
            .apply()
        _state.value = p
        onChange(p)
    }

    /** Indicateurs « déjà fait une fois » (ex. demande d'autorisation des notifications). */
    fun once(key: String): Boolean {
        if (prefs.getBoolean("once_$key", false)) return false
        prefs.edit().putBoolean("once_$key", true).apply()
        return true
    }
}
