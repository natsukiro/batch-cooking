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
    val lastBottleMl: Int = 90,
    val lastMilk: MilkKind = MilkKind.FORMULA,
)

class ProfileStore(context: Context) {
    private val prefs = context.getSharedPreferences("profile", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<Profile> = _state

    private fun load() = Profile(
        name = prefs.getString("name", "") ?: "",
        birthDate = prefs.getString("birth", null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        theme = ThemeMode.entries.getOrElse(prefs.getInt("theme", 0)) { ThemeMode.SYSTEM },
        lastBottleMl = prefs.getInt("lastBottleMl", 90),
        lastMilk = MilkKind.of(prefs.getString("lastMilk", null)) ?: MilkKind.FORMULA,
    )

    fun update(transform: (Profile) -> Profile) {
        val p = transform(_state.value)
        prefs.edit()
            .putString("name", p.name)
            .putString("birth", p.birthDate?.toString())
            .putInt("theme", p.theme.ordinal)
            .putInt("lastBottleMl", p.lastBottleMl)
            .putString("lastMilk", p.lastMilk.code)
            .apply()
        _state.value = p
    }
}
