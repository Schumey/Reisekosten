package de.reisekosten.app

import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fehler vom Server (kein Netzwerkproblem).
 * [blocking] = Verbindung/Schlüssel/URL stimmt nicht: nichts senden, Warteschlange behalten.
 * Sonst hat der Server nur diesen einen Eintrag abgelehnt (z. B. unbekannte Währung).
 */
class ApiException(message: String, val blocking: Boolean = true) : Exception(message)

/**
 * Spricht mit der Apps-Script-Web-App. Apps Script antwortet auf POST mit einer
 * Weiterleitung auf script.googleusercontent.com; die holen wir per GET ab.
 */
class Api(private val url: String, private val token: String) {

    fun getData(): JSONObject = call(JSONObject().put("action", "getData"))

    fun addExpense(e: Expense): JSONObject =
        call(JSONObject().put("action", "addExpense").put("expense", e.toJson()))

    fun updateExpense(e: Expense): JSONObject =
        call(JSONObject().put("action", "updateExpense").put("expense", e.toJson()))

    fun deleteExpense(id: String): JSONObject =
        call(JSONObject().put("action", "deleteExpense").put("id", id))

    fun addExchange(x: Exchange): JSONObject =
        call(JSONObject().put("action", "addExchange").put("exchange", x.toJson()))

    fun deleteExchange(id: String): JSONObject =
        call(JSONObject().put("action", "deleteExchange").put("id", id))

    private fun call(body: JSONObject): JSONObject {
        body.put("token", token)
        val text = post(url, body.toString())
        val json = try {
            JSONObject(text)
        } catch (_: JSONException) {
            throw ApiException(
                "Die Antwort ist kein JSON. Ist die Web-App mit Zugriff \"Jeder\" bereitgestellt und die URL korrekt?"
            )
        }
        if (!json.optBoolean("ok")) {
            throw ApiException(
                json.optString("error", "Unbekannter Fehler"),
                blocking = json.optString("code") == "auth",
            )
        }
        return json.getJSONObject("data")
    }

    private fun open(target: URL, method: String) =
        (target.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            instanceFollowRedirects = false
            connectTimeout = 15_000
            readTimeout = 30_000
        }

    private fun post(target: String, payload: String): String {
        var conn = open(URL(target), "POST").apply {
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }

        var code = conn.responseCode
        var hops = 0
        while (code in 300..399 && hops < 5) {
            val location = conn.getHeaderField("Location") ?: break
            val next = URL(conn.url, location)
            conn.disconnect()
            conn = open(next, "GET")
            code = conn.responseCode
            hops++
        }
        try {
            if (code != 200) throw ApiException("Server antwortet mit HTTP $code. Stimmt die Web-App-URL?")
            return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
