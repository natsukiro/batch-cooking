package app.petitrythme.sync

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.security.SecureRandom

/** Réglages de synchronisation de ce téléphone. */
data class SyncState(
    val server: String = "",
    val key: String = "",
    /** Prénom affiché à l'autre parent (« Papa », « Maman »…). */
    val me: String = "",
    val cursor: Long = 0,
    val lastSuccessAt: Long = 0,
    val lastError: String? = null,
    val running: Boolean = false,
) {
    val enabled get() = server.isNotBlank() && key.isNotBlank()

    /** Lien à envoyer à l'autre parent (la clé est après le #, jamais envoyée au serveur). */
    val inviteLink get() = "$server/join#k=$key"
}

class SyncConfig(context: Context, prefsName: String = "sync") {
    private val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<SyncState> = _state

    private fun load() = SyncState(
        server = prefs.getString("server", "") ?: "",
        key = prefs.getString("key", "") ?: "",
        me = prefs.getString("me", "") ?: "",
        cursor = prefs.getLong("cursor", 0),
        lastSuccessAt = prefs.getLong("lastSuccessAt", 0),
        lastError = prefs.getString("lastError", null),
    )

    fun update(t: (SyncState) -> SyncState) {
        val s = t(_state.value)
        prefs.edit()
            .putString("server", s.server)
            .putString("key", s.key)
            .putString("me", s.me)
            .putLong("cursor", s.cursor)
            .putLong("lastSuccessAt", s.lastSuccessAt)
            .putString("lastError", s.lastError)
            .apply()
        _state.value = s
    }

    fun setRunning(r: Boolean) {
        _state.value = _state.value.copy(running = r)
    }

    companion object {
        /** Serveur de la famille, proposé par défaut (modifiable). */
        const val DEFAULT_SERVER = "https://rythme.natsukiro.info"

        private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

        /** Clé famille : 160 bits aléatoires en base32 (32 caractères). */
        fun newKey(): String {
            val bytes = ByteArray(20).also { SecureRandom().nextBytes(it) }
            val sb = StringBuilder()
            var buffer = 0
            var bits = 0
            for (b in bytes) {
                buffer = (buffer shl 8) or (b.toInt() and 0xFF)
                bits += 8
                while (bits >= 5) {
                    sb.append(ALPHABET[(buffer shr (bits - 5)) and 31])
                    bits -= 5
                }
            }
            return sb.toString()
        }

        fun normalizeServer(raw: String): String {
            var s = raw.trim().trimEnd('/')
            if (s.isEmpty()) return s
            if (!s.startsWith("http://") && !s.startsWith("https://")) s = "https://$s"
            return s
        }

        fun normalizeKey(raw: String) = raw.uppercase().filter { it in ALPHABET }

        /** Adresse http:// hors réseau local : la clé circulerait en clair sur Internet. */
        fun isInsecure(server: String): Boolean {
            if (!server.startsWith("http://")) return false
            val host = server.removePrefix("http://").substringBefore('/').substringBefore(':')
            val local = host == "localhost" || host.endsWith(".local") || host.startsWith("10.") ||
                host.startsWith("192.168.") || host.startsWith("127.") ||
                Regex("^172\\.(1[6-9]|2[0-9]|3[01])\\.").containsMatchIn(host)
            return !local
        }

        /**
         * Lit une invitation : lien « https://serveur/join#k=CLÉ », lien
         * « petitrythme://join?s=…&k=… », ou code seul (le serveur est alors [fallbackServer]).
         */
        fun parseInvite(input: String, fallbackServer: String = ""): Pair<String, String>? {
            val t = input.trim()
            if (t.startsWith("petitrythme://")) {
                val uri = android.net.Uri.parse(t)
                val s = uri.getQueryParameter("s") ?: return null
                val k = uri.getQueryParameter("k") ?: return null
                return normalizeServer(s) to normalizeKey(k)
            }
            if (t.contains("/join#k=")) {
                val server = t.substringBefore("/join#k=")
                val key = t.substringAfter("/join#k=").substringBefore('&')
                return normalizeServer(server) to normalizeKey(key)
            }
            val key = normalizeKey(t)
            if (key.length < 20) return null
            return normalizeServer(fallbackServer.ifBlank { DEFAULT_SERVER }) to key
        }
    }
}
