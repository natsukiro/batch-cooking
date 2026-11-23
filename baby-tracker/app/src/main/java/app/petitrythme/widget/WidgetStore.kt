package app.petitrythme.widget

import android.content.Context
import app.petitrythme.data.EventType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Dernière saisie faite depuis le widget, pour pouvoir l'annuler. */
data class LastAction(val id: Long, val type: EventType, val label: String, val at: Long)

class WidgetStore(context: Context) {
    private val prefs = context.getSharedPreferences("widget", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<LastAction?> = _state

    private fun load(): LastAction? {
        if (!prefs.contains("id")) return null
        val type = EventType.entries.firstOrNull { it.name == prefs.getString("type", null) } ?: return null
        return LastAction(prefs.getLong("id", 0), type, prefs.getString("label", "") ?: "", prefs.getLong("at", 0))
    }

    fun set(a: LastAction?) {
        val e = prefs.edit().clear()
        if (a != null) {
            e.putLong("id", a.id).putString("type", a.type.name).putString("label", a.label).putLong("at", a.at)
        }
        e.apply()
        _state.value = a
    }
}
