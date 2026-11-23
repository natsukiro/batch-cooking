package app.petitrythme.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate

enum class ThemeMode(val label: String) { SYSTEM("Auto"), LIGHT("Clair"), DARK("Sombre") }

data class Profile(
    val name: String = "",
    val birthDate: LocalDate? = null,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    /** Rappel « prochain repas » en minutes après le début du dernier repas ; 0 = désactivé. */
    val feedReminderMin: Int = 0,
)

class ProfileStore(context: Context, private val onChange: (Profile) -> Unit = {}) {
    private val prefs = context.getSharedPreferences("profile", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<Profile> = _state

    private fun load() = Profile(
        name = prefs.getString("name", "") ?: "",
        birthDate = prefs.getString("birth", null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        theme = ThemeMode.entries.getOrElse(prefs.getInt("theme", 0)) { ThemeMode.SYSTEM },
        feedReminderMin = prefs.getInt("feedReminderMin", 0),
    )

    fun update(transform: (Profile) -> Profile) {
        val p = transform(_state.value)
        prefs.edit()
            .putString("name", p.name)
            .putString("birth", p.birthDate?.toString())
            .putInt("theme", p.theme.ordinal)
            .putInt("feedReminderMin", p.feedReminderMin)
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
