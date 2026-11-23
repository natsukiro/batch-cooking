package app.petitrythme.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class SyncResponse(val cursor: Long, val changes: JSONArray, val more: Boolean)

/** Erreur compréhensible par l'utilisateur ([code] vient du serveur, ou « network »). */
class SyncException(val code: String, message: String) : Exception(message)

/** Échanges avec le serveur de synchro (remplaçable dans les tests). */
interface SyncTransport {
    suspend fun health(server: String)
    suspend fun createFamily(server: String, key: String, createToken: String?)
    suspend fun sync(server: String, key: String, since: Long, changes: JSONArray): SyncResponse
}

class HttpTransport : SyncTransport {
    override suspend fun health(server: String) {
        val o = request("GET", "$server/api/v1/health", null, null, null)
        if (o.optString("app") != "petit-rythme-sync") {
            throw SyncException("not_a_server", "Cette adresse ne répond pas comme un serveur Petit Rythme.")
        }
    }

    override suspend fun createFamily(server: String, key: String, createToken: String?) {
        request("POST", "$server/api/v1/family", key, JSONObject(), createToken)
    }

    override suspend fun sync(server: String, key: String, since: Long, changes: JSONArray): SyncResponse {
        val body = JSONObject().put("since", since).put("changes", changes)
        val o = request("POST", "$server/api/v1/sync", key, body, null)
        return SyncResponse(o.getLong("cursor"), o.getJSONArray("changes"), o.optBoolean("more"))
    }

    private suspend fun request(method: String, url: String, key: String?, body: JSONObject?, createToken: String?): JSONObject =
        withContext(Dispatchers.IO) {
            val c = try {
                URL(url).openConnection() as HttpURLConnection
            } catch (e: Exception) {
                throw SyncException("bad_url", "Adresse de serveur invalide.")
            }
            try {
                c.requestMethod = method
                c.connectTimeout = 10_000
                c.readTimeout = 30_000
                c.setRequestProperty("Accept", "application/json")
                key?.let { c.setRequestProperty("Authorization", "Bearer $it") }
                createToken?.takeIf { it.isNotBlank() }?.let { c.setRequestProperty("X-Create-Token", it) }
                if (body != null) {
                    c.doOutput = true
                    c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                }
                val code = c.responseCode
                val text = (if (code in 200..299) c.inputStream else c.errorStream)
                    ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                val json = runCatching { JSONObject(text) }.getOrNull()
                if (code !in 200..299) {
                    throw SyncException(
                        json?.optString("error")?.takeIf { it.isNotEmpty() } ?: "http_$code",
                        json?.optString("message")?.takeIf { it.isNotEmpty() } ?: "Le serveur a répondu $code.",
                    )
                }
                json ?: throw SyncException("bad_response", "Réponse du serveur illisible.")
            } catch (e: IOException) {
                throw SyncException("network", "Serveur injoignable (réseau ou adresse ?).")
            } finally {
                c.disconnect()
            }
        }
}
