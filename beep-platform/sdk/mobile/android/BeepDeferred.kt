package io.beep.mobile

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Call after an explicit handoff or the Google Play Install Referrer callback. */
class BeepDeferred(private val origin: String, private val appId: String, private val scheme: String) {
    init {
        val uri = Uri.parse(origin)
        require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.query == null && uri.fragment == null && (uri.path.isNullOrBlank() || uri.path == "/"))
        require(appId.matches(Regex("[0-9a-fA-F-]{36}")))
        require(scheme.matches(Regex("[a-z][a-z0-9+.-]{1,39}")) && scheme !in setOf("http", "https", "javascript", "file", "data", "intent"))
    }
    suspend fun resolve(token: String): Uri = withContext(Dispatchers.IO) {
        require(token.matches(Regex("[A-Za-z0-9_-]{32,128}")))
        val connection = URL(origin.trimEnd('/') + "/v1/mobile/resolve").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 5000
            connection.readTimeout = 10000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            val body = JSONObject().put("appId", appId).put("token", token).put("platform", "android").toString().toByteArray(Charsets.UTF_8)
            connection.setFixedLengthStreamingMode(body.size)
            connection.outputStream.use { it.write(body) }
            check(connection.responseCode == 200) { "invalid_or_consumed_token" }
            val bytes = connection.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(1024)
                while (out.size() <= 8192) { val n = input.read(buffer); if (n < 0) break; out.write(buffer, 0, n) }
                out.toByteArray()
            }
            check(bytes.size <= 8192)
            val destination = Uri.parse(JSONObject(String(bytes, Charsets.UTF_8)).getString("destination"))
            check(destination.scheme == scheme) { "invalid_destination" }
            destination
        } finally { connection.disconnect() }
    }
    companion object {
        fun tokenFromInstallReferrer(referrer: String): String? =
            Uri.parse("https://referrer.invalid/?" + referrer).getQueryParameter("beep_token")?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{32,128}")) }
    }
}
