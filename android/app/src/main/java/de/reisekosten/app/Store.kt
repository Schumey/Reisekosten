package de.reisekosten.app

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

/** Lokale Ablage: Verbindung, letzter Datenstand (für offline) und noch nicht gesendete Ausgaben. */
class Store(context: Context) {
    private val p = context.getSharedPreferences("reisekosten", Context.MODE_PRIVATE)

    var url: String
        get() = p.getString("url", "").orEmpty()
        set(v) = p.edit { putString("url", v) }

    var token: String
        get() = p.getString("token", "").orEmpty()
        set(v) = p.edit { putString("token", v) }

    var lastCurrency: String
        get() = p.getString("lastCurrency", "").orEmpty()
        set(v) = p.edit { putString("lastCurrency", v) }

    var lastPaidBy: String
        get() = p.getString("lastPaidBy", "").orEmpty()
        set(v) = p.edit { putString("lastPaidBy", v) }

    var lastPayment: String
        get() = p.getString("lastPayment", PAY_CARD).orEmpty()
        set(v) = p.edit { putString("lastPayment", v) }

    var lastCountry: String
        get() = p.getString("lastCountry", "").orEmpty()
        set(v) = p.edit { putString("lastCountry", v) }

    var cachedJson: String?
        get() = p.getString("cache", null)
        set(v) = p.edit { if (v == null) remove("cache") else putString("cache", v) }

    fun cachedData(): TripData? =
        cachedJson?.let { runCatching { TripData.fromJson(JSONObject(it)) }.getOrNull() }

    var pending: List<Expense>
        get() {
            val arr = runCatching { JSONArray(p.getString("pending", "[]")) }.getOrDefault(JSONArray())
            return List(arr.length()) { Expense.fromJson(arr.getJSONObject(it), pending = true) }
        }
        set(list) = p.edit {
            putString("pending", JSONArray().apply { list.forEach { put(it.toJson()) } }.toString())
        }

    var pendingExchanges: List<Exchange>
        get() {
            val arr = runCatching { JSONArray(p.getString("pendingExchanges", "[]")) }.getOrDefault(JSONArray())
            return List(arr.length()) { Exchange.fromJson(arr.getJSONObject(it), pending = true) }
        }
        set(list) = p.edit {
            putString("pendingExchanges", JSONArray().apply { list.forEach { put(it.toJson()) } }.toString())
        }
}
